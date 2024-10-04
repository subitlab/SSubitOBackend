package cn.org.subit.database

import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.Slice
import cn.org.subit.database.utils.asSlice
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone

class Authorizations: SqlDao<Authorizations.AuthorizationTable>(AuthorizationTable)
{
    object AuthorizationTable: IdTable<AuthorizationId>("authorization")
    {
        override val id = authorizationId("id").autoIncrement().entityId()
        val user = reference("user", Users.UserTable).index()
        val service = reference("service", Services.ServiceTable).index()
        val grantedAt = timestampWithTimeZone("granted_at").defaultExpression(CurrentTimestampWithTimeZone)
        val cancel = bool("cancel").default(false)
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = AuthorizationInfo(
        id = row[AuthorizationTable.id].value,
        user = row[AuthorizationTable.user].value,
        service = row[AuthorizationTable.service].value,
        grantedAt = row[AuthorizationTable.grantedAt].toInstant().toEpochMilli(),
        cancel = row[AuthorizationTable.cancel],
    )

    suspend fun grantAuthorization(user: UserId, service: ServiceId): AuthorizationId = query()
    {
        val x =
            updateReturning(listOf(id), where = { (AuthorizationTable.user eq user) and (AuthorizationTable.service eq service) })
            {
                it[cancel] = false
                it[grantedAt] = CurrentTimestampWithTimeZone
            }.map { it[id].value }.singleOrNull()
        if (x != null) return@query x

        insertAndGetId {
            it[AuthorizationTable.user] = user
            it[AuthorizationTable.service] = service
        }.value
    }

    suspend fun revokeAuthorization(id: AuthorizationId): Boolean = query()
    {
        update({ AuthorizationTable.id eq id }) { it[cancel] = true } > 0
    }

    suspend fun revokeAuthorization(user: UserId, service: ServiceId): Boolean = query()
    {
        update({ (AuthorizationTable.user eq user) and (AuthorizationTable.service eq service) }) { it[cancel] = true } > 0
    }

    suspend fun getAuthorizations(user: UserId, begin: Long, count: Int): Slice<AuthorizationInfo> = query()
    {
        selectAll()
            .andWhere { AuthorizationTable.user eq user }
            .andWhere { cancel eq false }
            .asSlice(begin, count)
            .map(::deserialize)
    }

    suspend fun getAuthorizations(service: ServiceId, begin: Long, count: Int): Slice<AuthorizationInfo> = query()
    {
        selectAll().where { AuthorizationTable.service eq service }.asSlice(begin, count).map(::deserialize)
    }

    suspend fun getAuthorization(id: AuthorizationId): AuthorizationInfo? = query()
    {
        selectAll().where { AuthorizationTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun getAuthorization(user: UserId, service: ServiceId): AuthorizationInfo? = query()
    {
        selectAll().where { (AuthorizationTable.user eq user) and (AuthorizationTable.service eq service) }.singleOrNull()?.let(::deserialize)
    }
}