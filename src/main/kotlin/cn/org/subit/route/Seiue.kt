@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.seiue

import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.config.systemConfig
import cn.org.subit.database.StudentIds
import cn.org.subit.route.Context
import cn.org.subit.route.authenticated
import cn.org.subit.route.get
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.respond
import cn.org.subit.utils.statuses
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

fun Route.seiue() = route("/seiue", {
    tags = listOf("Seiue")
})
{
    get("/bind", {
        description = "绑定学号"
    })
    {
        call.respondRedirect("https://passport.seiue.com/authorize?response_type=token" +
                             "&client_id=${systemConfig.clientId}" +
                             "&school_id=${systemConfig.schoolId}" +
                             "&redirect_uri=${systemConfig.redirectUri}")
    }

    post("/bind", {
        description = "完成绑定"
        request {
            authenticated(true)
            queryParameter<String>("access_token")
        }
        response {
            statuses(HttpStatus.OK, HttpStatus.EmailExist.copy(message = "学号已存在"), HttpStatus.BadRequest)
        }
    }) { postBind() }
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

@Suppress("PropertyName")
@Serializable
data class Seiue(
    val id: Int,
    val school_id: Int,
    val name: String,
    val role: String,
    val department_names: List<String>? = null,
    val pinyin: String,
    val gender: String? = null,
    val user_id: Int? = null,
    val usin: String? = null,
    val ename: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val idcard: String? = null,
    val photo: String? = null,
    val status: String? = null,
    val archived_type_id: Int? = null,
    val archived_type: ArchivedType? = null,
    val outer_id: String? = null,
    val deleted_at: String? = null,
)

private suspend fun Context.postBind()
{
    val loginUser = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    val token = call.request.queryParameters["access_token"]

    val url = URL("https://open.seiue.com/api/v3/oauth/me")
    val connection = withContext(Dispatchers.IO) {
        url.openConnection()
    } as HttpURLConnection
    connection.setRequestProperty("Authorization", "Bearer $token")
    // GET
    connection.requestMethod = "GET"
    val result = runCatching { connection.inputStream.bufferedReader().readText() }.getOrNull()
    if (result == null) return call.respond(HttpStatus.BadRequest.copy(message = "seiue token 无效"))
    val seiue = StudentIds.json.decodeFromString(Seiue.serializer(), result)
    if (seiue.usin == null) return call.respond(HttpStatus.BadRequest.copy(message = "seiue token 无效(学号为空)"))
    val studentIds = get<StudentIds>()

    if (seiue.school_id != systemConfig.schoolId)
        return call.respond(HttpStatus.BadRequest.copy(message = "学校不匹配"))
    if (studentIds.getStudentIdUsers(seiue.usin) != null)
        return call.respond(HttpStatus.EmailExist.copy(message = "学号已存在"))
    else
    {
        studentIds.addStudentId(loginUser.id, seiue.usin, seiue.name, seiue)
        return call.respond(HttpStatus.OK, "学号添加成功")
    }
}