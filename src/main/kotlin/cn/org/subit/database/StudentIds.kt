package cn.org.subit.database

import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.Slice
import cn.org.subit.database.Users.UserTable
import cn.org.subit.database.utils.CustomExpressionWithColumnType
import cn.org.subit.database.utils.asSlice
import cn.org.subit.database.utils.singleOrNull
import cn.org.subit.plugin.contentNegotiation.dataJson
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.jsonb
import org.koin.core.component.inject

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    object StudentIdTable: IdTable<String>("student_id")
    {
        val studentId = varchar("student_id", 40).entityId()
        val user = reference("user", Users.UserTable).index()
        val realName = varchar("real_name", 100).uniqueIndex()
        val archived = bool("archived").default(false)
        val rawData = jsonb("raw_data", { it }, { it })
        override val id = studentId
        override val primaryKey = PrimaryKey(id)
    }

    private val services: Services by inject()
    private val authorizations: Authorizations by inject()

    suspend fun getSeiue(userId: UserId): List<UserFull.Seiue> = query()
    {
        select(studentId, realName, archived)
            .where { user eq userId }
            .map { row ->
                UserFull.Seiue(
                    studentId = row[studentId].value,
                    realName = row[realName],
                    archived = row[archived],
                )
            }
    }

    suspend fun getStudentIdUsers(studentId: String): UserId? = query()
    {
        select(user).where { table.studentId eq studentId }.singleOrNull()?.get(user)?.value
    }

    suspend inline fun <reified T> addStudentId(userId: UserId, studentId: String, realName: String, archived: Boolean, seiue: T): Boolean = query()
    {
        insertIgnoreAndGetId {
            it[table.user] = userId
            it[table.studentId] = studentId
            it[table.realName] = realName
            it[table.archived] = archived
            it[table.rawData] = dataJson.encodeToString<T>(seiue)
        } != null
    }

    suspend fun getStudentIdCount(userId: UserId): Long = query()
    {
        selectAll().where { user eq userId }.count()
    }

    suspend fun removeStudentId(userId: UserId, studentId: String): Boolean = query()
    {
        deleteWhere { (user eq userId) and (table.studentId eq studentId) } > 0
    }

    suspend fun searchUserByStudentId(sid: String, service: ServiceId, begin: Long, count: Int): Slice<UserId> = query()
    {
        val serviceInfo = services.getService(service) ?: return@query Slice.empty()

        table
            .join(authorizations.table, JoinType.LEFT, table.id, authorizations.table.user) { authorizations.table.service eq service }
            .selectAll()
            .andWhere { UserTable.username like "$sid%" }
            .andWhere {
                case()
                    .When(authorizations.table.cancel eq false, booleanParam(serviceInfo.authorized >= ServicePermission.ALL))
                    .When(authorizations.table.cancel eq true, booleanParam(serviceInfo.cancelAuthorization >= ServicePermission.ALL))
                    .Else(CustomExpressionWithColumnType(booleanParam(serviceInfo.unauthorized >= ServicePermission.ALL), BooleanColumnType()))
                    .eq(true)
            }
            .orderBy(table.user to SortOrder.ASC)
            .asSlice(begin, count)
            .map { it[table.user].value }
    }

    suspend fun searchUserByRealName(name: String, service: ServiceId, begin: Long, count: Int): Slice<UserId> = query()
    {
        val serviceInfo = services.getService(service) ?: return@query Slice.empty()

        table
            .join(authorizations.table, JoinType.LEFT, table.id, authorizations.table.user) { authorizations.table.service eq service }
            .selectAll()
            .andWhere { UserTable.username like "%$name%" }
            .andWhere {
                case()
                    .When(authorizations.table.cancel eq false, booleanParam(serviceInfo.authorized >= ServicePermission.ALL))
                    .When(authorizations.table.cancel eq true, booleanParam(serviceInfo.cancelAuthorization >= ServicePermission.ALL))
                    .Else(CustomExpressionWithColumnType(booleanParam(serviceInfo.unauthorized >= ServicePermission.ALL), BooleanColumnType()))
                    .eq(true)
            }
            .orderBy(table.user to SortOrder.ASC)
            .asSlice(begin, count)
            .map { it[table.user].value }
    }
}