@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.seiue

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.config.systemConfig
import cn.org.subit.dataClasses.UserId
import cn.org.subit.database.EmailCodes
import cn.org.subit.database.Emails
import cn.org.subit.database.StudentIds
import cn.org.subit.database.Users
import cn.org.subit.logger.SSubitOLogger
import cn.org.subit.plugin.contentNegotiation.contentNegotiationJson
import cn.org.subit.route.utils.Context
import cn.org.subit.route.utils.example
import cn.org.subit.route.utils.finishCall
import cn.org.subit.route.utils.finishCallWithRedirect
import cn.org.subit.route.utils.get
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.Locks
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.request.receiveNullable
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val logger = SSubitOLogger.getLogger()

private val httpClient = HttpClient(Java)
{
    engine()
    {
        pipelining = true
        protocolVersion = java.net.http.HttpClient.Version.HTTP_2
    }
    install(ContentNegotiation)
    {
        json(contentNegotiationJson)
    }
}

fun Route.seiue() = route("/seiue", {
    tags = listOf("Seiue")
})
{
    get("/bind", {
        description = "绑定学号"
        request {
            queryParameter<String>("redirect_uri")
            {
                required = false
            }
        }
    })
    {
        val redirectUri = call.request.queryParameters["redirect_uri"]
        if (redirectUri != null && !systemConfig.frontendRegex.matches(redirectUri))
            finishCall(HttpStatus.BadRequest.copy(message = "redirect_uri 不合法"))
        val redirect = redirectUri?.let { "&redirect_uri=" + it.encodeURLParameter() } ?: ""
        finishCallWithRedirect(
            "https://passport.seiue.com/authorize?response_type=token" +
            "&client_id=${systemConfig.clientId}" +
            "&school_id=${systemConfig.schoolId}" +
            redirect
        )
    }

    post("/bind", {
        description = "完成绑定"
        request {
            queryParameter<String>("access_token")
            {
                required = true
                description = "希悦的access_token"
            }
            queryParameter<Long>("active_reflection_id")
            {
                required = true
                description = "希悦的active_reflection_id"
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.EmailExist.copy(message = "学号已绑定其他账号"), HttpStatus.BadRequest)
        }
    }, Context::postBind)

    post("/login", {
        description = "希悦登陆/注册"
        request {
            queryParameter<String>("access_token")
            {
                required = true
                description = "希悦的access_token"
            }
            queryParameter<Long>("active_reflection_id")
            {
                required = true
                description = "希悦的active_reflection_id"
            }
            body<SeiueLoginRequest>
            {
                description = "希悦登陆/注册请求体"
                example("example", SeiueLoginRequest(password = "password", email = "example@example.com", emailCode = "123456"))
            }
        }
        response {
            statuses<SeiueLoginResponse>(HttpStatus.OK, example = SeiueLoginResponse("token"))
            statuses<SeiueLoginResponse>(HttpStatus.OK.subStatus("需要密码或邮箱", 1), example = SeiueLoginResponse(needPassword = true, email = null))
            statuses(
                HttpStatus.WrongEmailCode,
                HttpStatus.BadRequest.subStatus("access_token 为空", 1),
                HttpStatus.BadRequest.subStatus("active_reflection_id 为空", 2),
                HttpStatus.BadRequest.subStatus("seiue token 无效", 3),
                HttpStatus.BadRequest.subStatus("seiue token 无效(学号为空)", 4),
                HttpStatus.BadRequest.subStatus("学校不匹配", 5),
            )

        }
    }, Context::seiueLogin)

    delete("/bind", {
        description = "解绑学号"
        request {
            queryParameter<String>("studentId")
            {
                required = true
                description = "学号"
            }
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.BadRequest)
        }
    }) { deleteBind() }
}

@Suppress("PropertyName")
@Serializable
data class ArchivedType(
    val id: Int,
    val school_id: Int,
    val parent_id: Int,
    val weight: Int,
    val name: String,
    val label: String? = null,
    val description: String? = null,
    val type: String,
    val created_at: String,
    val archived_at: String? = null,
)

@Serializable
data class RawSeiue(
    val id: Int,
    @SerialName("school_id")
    val schoolId: Int,
    val name: String,
    val role: String,
    @SerialName("department_names")
    val departmentNames: List<String>,
    val pinyin: String?,
    val gender: String?,
    @SerialName("user_id")
    val userId: Int,
    val usin: String,
    val ename: String?,
    val email: String?,
    val phone: String?,
    val idcard: String?,
    val photo: String?,
    val status: String?,
    @SerialName("archived_type_id")
    val archivedTypeId: Int? = null,
    @SerialName("archived_type")
    val archivedType: ArchivedType? = null,
    @SerialName("outer_id")
    val outerId: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
)

private val addBindLocks = Locks<String>()

private const val getInfoUrl = "https://open.seiue.com/api/v3/oauth/me?expand=school_id,name,role,department_names,pinyin,gender,user_id,usin,ename,email,phone,idcard,photo,status,archived_type_id,archived_type,outer_id,deleted_at,id"

private suspend fun Context.postBind()
{
    val loginUser = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val token = call.request.queryParameters["access_token"] ?: finishCall(HttpStatus.BadRequest.subStatus("access_token 为空"))
    val activeReflectionId =
        call.request.queryParameters["active_reflection_id"]?.toLongOrNull() ?: finishCall(HttpStatus.BadRequest)

    val response = httpClient.get(getInfoUrl)
    {
        bearerAuth(token)
        header("X-Reflection-Id", activeReflectionId)
    }

    val seiue = runCatching { response.body<RawSeiue>() }.getOrNull() ?: finishCall(HttpStatus.BadRequest.copy(message = "seiue token 无效"))
    val studentIds = get<StudentIds>()

    if (seiue.schoolId != systemConfig.schoolId)
        finishCall(HttpStatus.BadRequest.copy(message = "学校不匹配"))

    addBindLocks.withLock(seiue.usin)
    {
        if (studentIds.addStudentId(loginUser.id, seiue))
            finishCall(HttpStatus.OK, "学号添加成功")
        else
            finishCall(HttpStatus.EmailExist.copy(message = "学号已绑定其他账号"))
    }
}

@Serializable
private data class SeiueLoginRequest(
    val password: String? = null,
    val email: String? = null,
    val emailCode: String? = null,
)

@Serializable
private data class SeiueLoginResponse(
    val token: String? = null,
    val needPassword: Boolean = false,
    val email: String? = null,
)

private suspend fun Context.seiueLogin()
{
    val token = call.request.queryParameters["access_token"] ?: finishCall(HttpStatus.BadRequest.subStatus("access_token 为空", 1))
    val activeReflectionId =
        call.request.queryParameters["active_reflection_id"]?.toLongOrNull() ?: finishCall(HttpStatus.BadRequest.subStatus("active_reflection_id 为空", 2))

    val response = httpClient.get(getInfoUrl)
    {
        bearerAuth(token)
        header("X-Reflection-Id", activeReflectionId)
    }

    val seiue = runCatching { response.body<RawSeiue>() }.getOrNull() ?: finishCall(HttpStatus.BadRequest.subStatus("seiue token 无效", 3))

    val studentIds = get<StudentIds>()
    val emails = get<Emails>()
    val users = get<Users>()

    if (seiue.schoolId != systemConfig.schoolId)
        finishCall(HttpStatus.BadRequest.subStatus("学校不匹配", 5))

    logger.fine("Seiue login: ${seiue.usin}(${seiue.name}) - ${seiue.email ?: "无邮箱"}")

    addBindLocks.withLock(seiue.usin)
    {
        val user = studentIds.getStudentIdUsers(seiue.usin) ?: seiue.email?.let { emails.getEmailUser(it) }
        if (user != null)
        {
            studentIds.addStudentId(user, seiue)
            seiue.email?.let { emails.addEmail(user, it) }

            val token = JWTAuth.makeUserToken(user)
            finishCall(HttpStatus.OK, SeiueLoginResponse(token.token))
        }

        val body = call.receiveNullable<SeiueLoginRequest?>()
        val email = body?.email ?: seiue.email
        if (email == null || body?.password == null)
            finishCall(HttpStatus.OK.subStatus("需要密码或邮箱", 1), SeiueLoginResponse(needPassword = true, email = email))
        val emailCodes: EmailCodes = get()
        if (email != seiue.email && !emailCodes.verifyEmailCode(email, body.emailCode ?: "", EmailCodes.EmailCodeUsage.REGISTER))
            finishCall(HttpStatus.WrongEmailCode)

        val newUser = users.createUser(seiue.name, body.password)
        emails.addEmail(newUser, email)
        studentIds.addStudentId(newUser, seiue)
        val token = JWTAuth.makeUserToken(newUser)
        finishCall(HttpStatus.OK, SeiueLoginResponse(token.token))
    }
}

private val deleteBindLocks = Locks<UserId>()

private suspend fun Context.deleteBind()
{
    val loginUser = getLoginUser() ?: finishCall(HttpStatus.NotLoggedIn)
    val studentId = call.request.queryParameters["studentId"] ?: finishCall(HttpStatus.BadRequest)
    val studentIds = get<StudentIds>()

    deleteBindLocks.withLock(loginUser.id)
    {
        if (studentIds.getStudentIdCount(loginUser.id) >= 2)
        {
            if (studentIds.removeStudentId(loginUser.id, studentId)) finishCall(HttpStatus.OK)
            else finishCall(HttpStatus.NotFound)
        }
        else finishCall(HttpStatus.BadRequest.copy(message = "无法解绑唯一的希悦账号"))
    }
}