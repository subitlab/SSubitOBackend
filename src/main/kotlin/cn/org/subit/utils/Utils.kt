package cn.org.subit.utils

import cn.org.subit.Loader
import cn.org.subit.config.emailConfig
import cn.org.subit.database.EmailCodes
import cn.org.subit.logger.SSubitOLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import java.util.*
import javax.mail.Address
import javax.mail.Message
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import kotlin.time.Duration.Companion.seconds

private val logger = SSubitOLogger.getLogger()

/**
 * 检查邮箱格式是否正确
 */
fun checkEmail(email: String): Boolean = emailConfig.pattern.matcher(email).matches()

/**
 * 检查密码是否合法
 * 要求密码长度在 8-20 之间，且仅包含数字、字母和特殊字符 !@#$%^&*()_+-=
 */
fun checkPassword(password: String): Boolean =
    password.length in 8..20 &&
    password.all { it.isLetterOrDigit() || it in "!@#$%^&*()_+-=" }

/**
 * 检查用户名是否合法
 * 要求用户名长度在 2-20 之间，且仅包含中文、数字、字母和特殊字符 _-.
 */
fun checkUsername(username: String): Boolean =
    username.length in 2..20 &&
    username.all { it in '\u4e00'..'\u9fa5' || it.isLetterOrDigit() || it in "_-." }

fun checkUserInfo(username: String, password: String, email: String): HttpStatus
{
    if (!checkEmail(email)) return HttpStatus.EmailFormatError
    if (!checkPassword(password)) return HttpStatus.PasswordFormatError
    if (!checkUsername(username)) return HttpStatus.UsernameFormatError
    return HttpStatus.OK
}

@Suppress("unused")
fun String?.toUUIDOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

private val sendEmailScope = CoroutineScope(Dispatchers.IO)

fun sendEmail(email: String, code: String, usage: EmailCodes.EmailCodeUsage) = sendEmailScope.async()
{
    withTimeout(15.seconds)
    {
        @Suppress("NAME_SHADOWING")
        val email = email.lowercase()
        val props = Properties()
        props.setProperty("mail.smtp.auth", "true")
        props.setProperty("mail.host", emailConfig.host)
        props.setProperty("mail.port", emailConfig.port.toString())
        props.setProperty("mail.smtp.starttls.enable", "true")
        val session = Session.getInstance(props)
        val message = MimeMessage(session)
        message.setFrom(InternetAddress(emailConfig.sender))
        message.setRecipient(Message.RecipientType.TO, InternetAddress(email))
        message.subject = emailConfig.verifyEmailTitle

        val body =
            Loader
                .getResource("email.html")
                ?.readAllBytes()
                ?.decodeToString()
                ?.replace("{code}", code)
                ?.replace("{usage}", usage.description)
            ?: run {
                logger.severe("Failed to load email.html")
                logger.severe("Send email failed: email: $email, code: $code, usage: $usage")
                return@withTimeout
            }

        val mimeMultipart = MimeMultipart()
        val mimeBodyPart = MimeBodyPart()
        mimeBodyPart.setContent(body, "text/html; charset=utf-8")
        mimeMultipart.addBodyPart(mimeBodyPart)
        message.setContent(mimeMultipart)

        val transport = session.getTransport("smtp")
        transport.connect(emailConfig.host, emailConfig.port, emailConfig.sender, emailConfig.password)
        transport.sendMessage(message, arrayOf<Address>(InternetAddress(email)))
        transport.close()
    }
}