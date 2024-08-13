package cn.org.subit.database

import cn.org.subit.JWTAuth
import cn.org.subit.dataClasses.Permission
import cn.org.subit.dataClasses.Slice.Companion.singleOrNull
import cn.org.subit.dataClasses.UserInfo
import cn.org.subit.dataClasses.UserId
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.koin.core.component.inject

class Users: SqlDao<Users.UserTable>(UserTable)
{
    /**
     * 用户信息表
     */
    object UserTable: IdTable<UserId>("users")
    {
        override val id = userId("id").autoIncrement().entityId()
        val username = varchar("username", 100).index()
        val registrationTime = timestamp("registration_time").defaultExpression(CurrentTimestamp)
        val permission = enumerationByName<Permission>("permission", 20).default(Permission.NORMAL)
        val password = text("password")
        val lastPasswordChange = timestamp("last_password_change").defaultExpression(CurrentTimestamp)
        val phone = varchar("phone", 20).nullable()
        override val primaryKey = PrimaryKey(id)
    }

    val emails: Emails by inject()
    val studentIds: StudentIds by inject()

    private fun deserialize(row: ResultRow) = UserInfo(
        id = row[UserTable.id].value,
        username = row[UserTable.username],
        registrationTime = row[UserTable.registrationTime],
        permission = row[UserTable.permission],
        phone = row[UserTable.phone] ?: ""
    )

    suspend fun createUser(
        username: String,
        password: String
    ): UserId = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        insertAndGetId {
            it[UserTable.username] = username
            it[UserTable.password] = psw
        }.value
    }

    suspend fun getUser(id: UserId): UserInfo? = query()
    {
        selectAll().where { UserTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun setPassword(id: UserId, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        update({ UserTable.id eq id }) {
            it[UserTable.password] = psw
            it[lastPasswordChange] = CurrentTimestamp
        } > 0
    }

    suspend fun setPassword(email: String, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password) // 加密密码
        table
            .join(emails.table, JoinType.RIGHT, table.id, emails.table.user)
            .update({ emails.table.email eq email }) { it[UserTable.password] = psw } > 0
    }

    /**
     * 获取某一用户的数据及其上次密码修改时间
     */
    suspend fun getUserWithLastPasswordChange(id: UserId): Pair<UserInfo, Instant>? = query()
    {
        selectAll().where { UserTable.id eq id }.singleOrNull()?.let { deserialize(it) to it[lastPasswordChange] }
    }

    /**
     * 检查用户密码是否正确
     * @param userId 用户id
     * @param password 密码
     * @return 密码是否正确
     */
    suspend fun checkLogin(userId: UserId, password: String): Boolean = query()
    {
        val psw = select(table.password).where { id eq userId }.singleOrNull()?.get(table.password) ?: return@query false
        return@query JWTAuth.verifyPassword(password, psw)
    }

    /**
     * 检查用户密码是否正确
     * @param email 用户邮箱
     * @param password 密码
     * @return 当用户不存在或密码错误时返回null, 否则返回用户id
     */
    suspend fun checkLogin(email: String, password: String): UserId? = query()
    {
        val (id, psw) = table
            .join(emails.table, JoinType.RIGHT, table.id, emails.table.user)
            .select(table.password, table.id)
            .where { emails.table.email eq email }
            .singleOrNull()?.let { it[table.id].value to it[table.password] } ?: return@query null
        return@query if (JWTAuth.verifyPassword(password, psw)) id else null
    }
}