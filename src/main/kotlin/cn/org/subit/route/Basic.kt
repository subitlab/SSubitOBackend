@file:Suppress("PackageDirectoryMismatch")

package cn.org.subit.route.basic

import cn.org.subit.JWTAuth
import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.dataClasses.UserId
import cn.org.subit.database.EmailCodes
import cn.org.subit.database.Emails
import cn.org.subit.database.Users
import cn.org.subit.database.sendEmailCode
import cn.org.subit.plugin.RateLimit
import cn.org.subit.route.Context
import cn.org.subit.route.authenticated
import cn.org.subit.route.example
import cn.org.subit.route.get
import cn.org.subit.utils.*
import io.github.smiley4.ktorswaggerui.dsl.routing.*
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.basic() = route("/auth", {
    tags = listOf("账户")
})
{
    post("/register", {
        description = "注册, 若成功返回token"
        request {
            body<RegisterInfo>
            {
                required = true
                description = "注册信息"
                example("example", RegisterInfo("username", "password", "email", "code"))
            }
        }
        this.response {
            statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
            statuses(
                HttpStatus.WrongEmailCode,
                HttpStatus.EmailExist,
                HttpStatus.EmailFormatError,
                HttpStatus.UsernameFormatError,
                HttpStatus.PasswordFormatError,
                HttpStatus.NotInWhitelist
            )
        }
    }) { register() }

    post("/login", {
        description = "登陆, 若成功返回token"
        request {
            body<Login>()
            {
                required = true
                description = "登陆信息, id(用户ID)和email(用户的邮箱)二选一"
                example("example", Login(email = "email", password = "password", id = UserId(0)))
            }
        }
        this.response {
            statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
            statuses(
                HttpStatus.PasswordError,
                HttpStatus.AccountNotExist,
            )
        }
    }) { login() }

    post("/loginByCode", {
        description = "通过邮箱验证码登陆, 若成功返回token"
        request {
            body<LoginByCodeInfo>()
            {
                required = true
                description = "登陆信息"
                example("example", LoginByCodeInfo(email = "email@abc.com", code = "123456"))
            }
        }
        this.response {
            statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
            statuses(
                HttpStatus.AccountNotExist,
                HttpStatus.WrongEmailCode,
            )
        }
    }) { loginByCode() }

    post("/resetPassword", {
        description = "重置密码(忘记密码)"
        request {
            body<ResetPasswordInfo>
            {
                required = true
                description = "重置密码信息"
                example("example", ResetPasswordInfo("email@abc.com", "code", "newPassword"))
            }
        }
        this.response {
            statuses(HttpStatus.OK)
            statuses(
                HttpStatus.WrongEmailCode,
                HttpStatus.AccountNotExist,
            )
        }
    }) { resetPassword() }

    rateLimit(RateLimit.SendEmail.rateLimitName)
    {
        post("/sendEmailCode", {
            description = "发送邮箱验证码"
            request {
                body<EmailInfo>
                {
                    required = true
                    description = "邮箱信息"
                    example("example", EmailInfo("email@abc.com", EmailCodes.EmailCodeUsage.REGISTER))
                }
            }
            this.response {
                statuses(HttpStatus.OK)
                statuses(
                    HttpStatus.EmailFormatError,
                    HttpStatus.TooManyRequests
                )
            }
        }) { sendEmailCode() }
    }

    post("/changePassword", {
        description = "修改密码"
        request {
            authenticated(true)
            body<ChangePasswordInfo>
            {
                required = true
                description = "修改密码信息"
                example("example", ChangePasswordInfo("oldPassword", "newPassword"))
            }
        }
        this.response {
            statuses<JWTAuth.Token>(HttpStatus.OK, example = JWTAuth.Token("token"))
            statuses(
                HttpStatus.Unauthorized,
                HttpStatus.PasswordError,
                HttpStatus.PasswordFormatError,
            )
        }
    }) { changePassword() }
}

@Serializable
private data class RegisterInfo(val username: String, val password: String, val email: String, val code: String)

private suspend fun Context.register()
{
    val registerInfo: RegisterInfo = call.receive()
    // 检查用户名、密码、邮箱是否合法
    checkUserInfo(registerInfo.username, registerInfo.password, registerInfo.email).apply {
        if (this != HttpStatus.OK) return call.respond(this)
    }
    // 验证邮箱验证码
    if (!get<EmailCodes>().verifyEmailCode(
            registerInfo.email,
            registerInfo.code,
            EmailCodes.EmailCodeUsage.REGISTER
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 创建用户
    if (get<Emails>().getEmailUsers(registerInfo.email) != null) return call.respond(HttpStatus.EmailExist)
    val id = get<Users>().createUser(
        username = registerInfo.username,
        password = registerInfo.password,
    )
    get<Emails>().addEmail(id, registerInfo.email)
    // 创建成功, 返回token
    val token = JWTAuth.makeToken(id)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class Login(val email: String? = null, val id: UserId? = null, val password: String)

private suspend fun Context.login()
{
    val users = get<Users>()
    val loginInfo = call.receive<Login>()
    val checked = if (loginInfo.id != null && users.checkLogin(loginInfo.id, loginInfo.password)) loginInfo.id
    else if (loginInfo.email != null) users.checkLogin(loginInfo.email, loginInfo.password)
    else return call.respond(HttpStatus.BadRequest)
    // 若登陆失败，返回错误信息
    if (checked == null) return call.respond(HttpStatus.PasswordError)
    val token = JWTAuth.makeToken(checked)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class LoginByCodeInfo(val email: String, val code: String)

private suspend fun Context.loginByCode()
{
    val loginInfo = call.receive<LoginByCodeInfo>()
    if (!get<EmailCodes>().verifyEmailCode(loginInfo.email, loginInfo.code, EmailCodes.EmailCodeUsage.LOGIN))
        return call.respond(HttpStatus.WrongEmailCode)
    val user = get<Emails>().getEmailUsers(loginInfo.email) ?: return call.respond(HttpStatus.AccountNotExist)
    val token = JWTAuth.makeToken(user)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
private data class ResetPasswordInfo(val email: String, val code: String, val password: String)

private suspend fun Context.resetPassword()
{
    // 接收重置密码的信息
    val resetPasswordInfo = call.receive<ResetPasswordInfo>()
    // 验证邮箱验证码
    if (!get<EmailCodes>().verifyEmailCode(
            resetPasswordInfo.email,
            resetPasswordInfo.code,
            EmailCodes.EmailCodeUsage.RESET_PASSWORD
        )
    ) return call.respond(HttpStatus.WrongEmailCode)
    // 重置密码
    if (get<Users>().setPassword(resetPasswordInfo.email, resetPasswordInfo.password))
        call.respond(HttpStatus.OK)
    else
        call.respond(HttpStatus.AccountNotExist)
}

@Serializable
private data class ChangePasswordInfo(val oldPassword: String, val newPassword: String)

private suspend fun Context.changePassword()
{
    val users = get<Users>()

    val (oldPassword, newPassword) = call.receive<ChangePasswordInfo>()
    val user = getLoginUser() ?: return call.respond(HttpStatus.Unauthorized)
    if (!users.checkLogin(user.id, oldPassword)) return call.respond(HttpStatus.PasswordError)
    if (!checkPassword(newPassword)) return call.respond(HttpStatus.PasswordFormatError)
    users.setPassword(user.id, newPassword)
    val token = JWTAuth.makeToken(user.id)
    return call.respond(HttpStatus.OK, token)
}

@Serializable
data class EmailInfo(val email: String, val usage: EmailCodes.EmailCodeUsage)

private suspend fun Context.sendEmailCode()
{
    val emailInfo = call.receive<EmailInfo>()
    if (!checkEmail(emailInfo.email))
        return call.respond(HttpStatus.EmailFormatError)
    val emailCodes = get<EmailCodes>()
    emailCodes.sendEmailCode(emailInfo.email, emailInfo.usage)
    call.respond(HttpStatus.OK)
}