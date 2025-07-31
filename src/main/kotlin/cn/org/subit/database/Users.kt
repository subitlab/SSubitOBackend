package cn.org.subit.database

import cn.org.subit.JWTAuth
import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.Slice
import cn.org.subit.database.utils.CustomExpressionWithColumnType
import cn.org.subit.database.utils.asSlice
import cn.org.subit.database.utils.singleOrNull
import kotlinx.datetime.Clock
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

    private val emails: Emails by inject()
    private val studentIds: StudentIds by inject()
    private val services: Services by inject()
    private val authorizations: Authorizations by inject()

    private fun deserialize(row: ResultRow) = UserInfo(
        id = row[UserTable.id].value,
        username = row[UserTable.username],
        registrationTime = row[UserTable.registrationTime].toEpochMilliseconds(),
        permission = row[UserTable.permission],
        phone = row[UserTable.phone] ?: ""
    )

    suspend fun createUser(username: String, password: String): UserId = query()
    {
        val psw = JWTAuth.encryptPassword(password)
        val time = Instant.fromEpochSeconds(Clock.System.now().epochSeconds, 0)
        insertAndGetId {
            it[UserTable.username] = username
            it[UserTable.password] = psw
            it[table.registrationTime] = Clock.System.now()
            it[UserTable.lastPasswordChange] = time
        }.value
    }

    suspend fun setUsername(id: UserId, username: String): Boolean = query()
    {
        update({ UserTable.id eq id }) { it[UserTable.username] = username } > 0
    }

    suspend fun getUser(id: UserId): UserInfo? = query()
    {
        selectAll().where { UserTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun setPassword(id: UserId, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password)
        val time = Instant.fromEpochSeconds(Clock.System.now().epochSeconds, 0)
        update({ UserTable.id eq id }) {
            it[UserTable.password] = psw
            it[lastPasswordChange] = time
        } > 0
    }

    suspend fun setPassword(email: String, password: String): Boolean = query()
    {
        val psw = JWTAuth.encryptPassword(password)
        val time = Instant.fromEpochSeconds(Clock.System.now().epochSeconds, 0)
        table
            .join(emails.table, JoinType.RIGHT, table.id, emails.table.user)
            .update({ emails.table.email eq email.lowercase() })
            {
                it[UserTable.password] = psw
                it[lastPasswordChange] = time
            } > 0
    }

    /**
     * 获取某用户的数据及其上次密码修改时间
     */
    suspend fun getUserWithLastPasswordChange(id: UserId): Pair<UserInfo, Instant>? = query()
    {
        selectAll()
            .where { UserTable.id eq id }
            .singleOrNull()
            ?.let { deserialize(it) to it[lastPasswordChange] }
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
    suspend fun checkLoginByEmail(email: String, password: String): UserId? = query()
    {
        val (id, psw) = table
            .join(emails.table, JoinType.RIGHT, table.id, emails.table.user)
            .select(table.password, table.id)
            .where { emails.table.email eq email.lowercase() }
            .singleOrNull()
            ?.let { it[table.id].value to it[table.password] } ?: return@query null
        return@query if (JWTAuth.verifyPassword(password, psw)) id else null
    }

    suspend fun checkLoginByStudentId(studentId: String, password: String): UserId? = query()
    {
        val (id, psw) = table
            .join(studentIds.table, JoinType.RIGHT, table.id, studentIds.table.user)
            .select(table.password, table.id)
            .where { studentIds.table.studentId eq studentId }
            .singleOrNull()
            ?.let { it[table.id].value to it[table.password] } ?: return@query null
        return@query if (JWTAuth.verifyPassword(password, psw)) id else null
    }


    suspend fun searchUser(
        key: String,
        service: ServiceId,
        begin: Long,
        count: Int,
        authorizationState: AuthorizationStatus?
    ): Slice<UserInfo> = query()
    {
        val serviceInfo = services.getService(service) ?: return@query Slice.empty()
        table
            .join(authorizations.table, JoinType.LEFT, table.id, authorizations.table.user) { authorizations.table.service eq service }
            .selectAll()
            .andWhere { table.username like "%$key%" }
            .apply()
            {
                when (authorizationState)
                {
                    null -> this
                    AuthorizationStatus.UNAUTHORIZED -> andWhere { authorizations.table.id.isNull() }
                    AuthorizationStatus.AUTHORIZED -> andWhere { authorizations.table.id.isNotNull() and (authorizations.table.cancel eq false) }
                    AuthorizationStatus.CANCELED -> andWhere { authorizations.table.id.isNotNull() and (authorizations.table.cancel eq true) }
                }
            }
            .andWhere {
                case()
                    .When(authorizations.table.cancel eq false, booleanParam(serviceInfo.authorized >= ServicePermission.BASIC))
                    .When(authorizations.table.cancel eq true, booleanParam(serviceInfo.cancelAuthorization >= ServicePermission.BASIC))
                    .Else(CustomExpressionWithColumnType(booleanParam(serviceInfo.unauthorized >= ServicePermission.BASIC), BooleanColumnType()))
                    .eq(true)
            }
            .orderBy(table.id to SortOrder.ASC)
            .asSlice(begin, count)
            .map(::deserialize)
    }
}