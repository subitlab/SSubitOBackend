package cn.org.subit.database

import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.Slice
import cn.org.subit.database.Users.UserTable
import cn.org.subit.database.utils.CustomExpressionWithColumnType
import cn.org.subit.database.utils.asSlice
import cn.org.subit.database.utils.singleOrNull
import cn.org.subit.plugin.contentNegotiation.dataJson
import cn.org.subit.route.seiue.RawSeiue
import kotlinx.serialization.serializer
import org.jetbrains.exposed.dao.id.CompositeIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull
import org.jetbrains.exposed.sql.json.jsonb
import org.koin.core.component.inject

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    object StudentIdTable: CompositeIdTable("student_id")
    {
        val studentId = varchar("student_id", 40)
        val school = integer("school").index()
        val user = reference("user", UserTable).index()
        val realName = varchar("real_name", 100).index()
        val role = enumerationByName<UserFull.Seiue.Role>("role", 16).index().default(UserFull.Seiue.Role.UNKNOWN)
        val archived = bool("archived").default(false).index()
        val rawData = jsonb<RawSeiue>("raw_data", dataJson, dataJson.serializersModule.serializer())

        override val primaryKey = PrimaryKey(studentId, school)

        init
        {
            uniqueIndex(studentId, school)
        }
    }

    private val services: Services by inject()
    private val authorizations: Authorizations by inject()

    suspend fun getSeiue(userId: UserId): List<UserFull.Seiue> = query()
    {
        select(studentId, school, realName, role, archived)
            .where { user eq userId }
            .map { row ->
                UserFull.Seiue(
                    studentId = row[studentId],
                    realName = row[realName],
                    role = row[role],
                    archived = row[archived],
                    schoolId = row[school],
                )
            }
    }

    suspend fun getStudentIdUsers(studentId: String, schoolId: Int): UserId? = query()
    {
        select(user)
            .where {
            (table.studentId eq studentId) and (table.school eq schoolId)
        }.singleOrNull()?.get(user)?.value
    }

    suspend inline fun addStudentId(userId: UserId, seiue: RawSeiue): Boolean = query()
    {
        val res = insertIgnore()
        {
            it[table.user] = userId
            it[table.studentId] = seiue.usin
            it[table.school] = seiue.schoolId
            it[table.realName] = seiue.name
            it[table.role] = UserFull.Seiue.Role.fromSeiueRole(seiue.role)
            it[table.archived] = !seiue.status.equals("normal", true)
            it[table.rawData] = seiue
        }.insertedCount > 0
        if (!res) updateSeiue(seiue)
        res
    }

    suspend fun updateSeiue(seiue: RawSeiue) = query()
    {
        update({ table.studentId eq seiue.usin and (table.school eq seiue.schoolId) })
        {
            it[realName] = seiue.name
            it[school] = seiue.schoolId
            it[role] = UserFull.Seiue.Role.fromSeiueRole(seiue.role)
            it[archived] = !seiue.status.equals("normal", true)
            it[rawData] = seiue
        } > 0
    }

    suspend fun getStudentIdCount(userId: UserId, schoolId: Int): Long = query()
    {
        selectAll().where { user eq userId and (school eq schoolId) }.count()
    }

    suspend fun removeStudentId(userId: UserId, studentId: String, schoolId: Int): Boolean = query()
    {
        deleteWhere { (user eq userId) and (table.studentId eq studentId) and (table.school eq schoolId) } > 0
    }

    suspend fun searchUserByStudentId(sid: String, service: ServiceId, begin: Long, count: Int, authorizationStatuses: List<AuthorizationStatus>?): Slice<UserId> = query()
    {
        val serviceInfo = services.getService(service) ?: return@query Slice.empty()

        table
            .join(authorizations.table, JoinType.LEFT, table.user, authorizations.table.user) { authorizations.table.service eq service }
            .selectAll()
            .andWhere { table.studentId like "$sid%" }
            .apply()
            {
                authorizationStatuses?.let { statuses ->
                    if( statuses.isEmpty() ) return@apply
                    val conditions = statuses.map { status ->
                        when (status) {
                            AuthorizationStatus.UNAUTHORIZED -> authorizations.table.id.isNull()
                            AuthorizationStatus.AUTHORIZED -> authorizations.table.id.isNotNull() and (authorizations.table.cancel eq false)
                            AuthorizationStatus.CANCELED -> authorizations.table.id.isNotNull() and (authorizations.table.cancel eq true)
                        }
                    }
                    this.andWhere { conditions.reduce{ acc, condition -> acc or condition } }
                } ?: this
            }
            .andWhere()
            {
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

    suspend fun searchUserByRealName(name: String, service: ServiceId, begin: Long, count: Int, authorizationStatuses: List<AuthorizationStatus>?): Slice<UserId> = query()
    {
        val serviceInfo = services.getService(service) ?: return@query Slice.empty()

        table
            .join(authorizations.table, JoinType.LEFT, table.user, authorizations.table.user) { authorizations.table.service eq service }
            .selectAll()
            .andWhere { table.realName like "%$name%" }
            .apply()
            {
                authorizationStatuses?.let { statuses ->
                    if( statuses.isEmpty() ) return@apply
                    val conditions = statuses.map { status ->
                        when (status) {
                            AuthorizationStatus.UNAUTHORIZED -> authorizations.table.id.isNull()
                            AuthorizationStatus.AUTHORIZED -> authorizations.table.id.isNotNull() and (authorizations.table.cancel eq false)
                            AuthorizationStatus.CANCELED -> authorizations.table.id.isNotNull() and (authorizations.table.cancel eq true)
                        }
                    }
                    this.andWhere { conditions.reduce{ acc, condition -> acc or condition } }
                } ?: this
            }
            .andWhere()
            {
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