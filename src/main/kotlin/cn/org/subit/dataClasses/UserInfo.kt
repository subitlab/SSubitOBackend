package cn.org.subit.dataClasses

import cn.org.subit.database.Emails
import cn.org.subit.database.StudentIds
import cn.org.subit.database.Users
import io.ktor.server.auth.*
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * 用户数据库数据类
 * @property id 用户ID
 * @property username 用户名
 * @property registrationTime 注册时间
 * @property permission 用户管理权限
 * @property phone 电话号码
 */
@Serializable
data class UserInfo(
    val id: UserId,
    val username: String,
    val registrationTime: Instant,
    val permission: Permission,
    val phone: String,
): Principal
{
    fun toBasicUserInfo() = BasicUserInfo(id, username, registrationTime)
    fun toUserFull(email: List<String>, studentId: List<String>) = UserFull(id, username, registrationTime, permission, phone, email, studentId)
    suspend fun toUserFull() = UserFull(
        id = id,
        username = username,
        registrationTime = registrationTime,
        permission = permission,
        phone = phone,
        email = emails.getUserEmails(id),
        studentId = studentIds.getUserStudentIds(id)
    )

    companion object: KoinComponent
    {
        private val users: Users by inject()
        private val emails: Emails by inject()
        private val studentIds: StudentIds by inject()
    }
}

/**
 * 完整用户数据
 */
@Serializable
data class UserFull(
    val id: UserId,
    val username: String,
    val registrationTime: Instant,
    val permission: Permission,
    val phone: String,
    val email: List<String>,
    val studentId: List<String>,
)

@Serializable
data class BasicUserInfo(
    val id: UserId,
    val username: String,
    val registrationTime: Instant,
)