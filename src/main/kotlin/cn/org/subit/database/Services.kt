package cn.org.subit.database

import cn.org.subit.dataClasses.*
import cn.org.subit.dataClasses.Slice
import cn.org.subit.database.utils.asSlice
import cn.org.subit.database.utils.singleOrNull
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.kotlin.datetime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.time.ZoneOffset

class Services: SqlDao<Services.ServiceTable>(ServiceTable)
{
    object ServiceTable: IdTable<ServiceId>("services")
    {
        override val id = serviceId("id").autoIncrement().entityId()
        val name = text("name").uniqueIndex()
        val description = text("description")
        val owner = reference("owner", Users.UserTable)
        val status = enumerationByName<ServiceStatus>("status", 20).index().default(ServiceStatus.PENDING)
        val unauthorized = enumerationByName<ServicePermission>("unauthorized", 20).index().default(ServicePermission.NONE)
        val authorized = enumerationByName<ServicePermission>("authorized", 20).index().default(ServicePermission.NONE)
        val cancelAuthorization = enumerationByName<ServicePermission>("cancelAuthorization", 20).index().default(ServicePermission.NONE)
        // 在审核的信息
        val pendingName = text("pendingName").nullable()
        val pendingDescription = text("pendingDescription").nullable()
        val pendingUnauthorized = enumerationByName<ServicePermission>("pendingUnauthorized", 20).nullable()
        val pendingAuthorized = enumerationByName<ServicePermission>("pendingAuthorized", 20).nullable()
        val pendingCancelAuthorization = enumerationByName<ServicePermission>("pendingCancelAuthorization", 20).nullable()
        // 作废的服务秘钥时间
        val secretKeyRevokedTime = timestampWithTimeZone("secretKeyRevokedTime").default(OffsetDateTime.ofInstant(Instant.fromEpochMilliseconds(0).toJavaInstant(), ZoneOffset.UTC))
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow) = ServiceInfo(
        id = row[ServiceTable.id].value,
        name = row[ServiceTable.name],
        description = row[ServiceTable.description],
        owner = row[ServiceTable.owner].value,
        status = row[ServiceTable.status],
        unauthorized = row[ServiceTable.unauthorized],
        authorized = row[ServiceTable.authorized],
        cancelAuthorization = row[ServiceTable.cancelAuthorization],
        pendingName = row[ServiceTable.pendingName],
        pendingDescription = row[ServiceTable.pendingDescription],
        pendingUnauthorized = row[ServiceTable.pendingUnauthorized],
        pendingAuthorized = row[ServiceTable.pendingAuthorized],
        pendingCancelAuthorization = row[ServiceTable.pendingCancelAuthorization],
    )

    suspend fun createService(
        name: String,
        description: String,
        owner: UserId,
    ): ServiceId = query()
    {
        insertAndGetId {
            it[ServiceTable.name] = name
            it[ServiceTable.description] = description
            it[ServiceTable.owner] = owner
        }.value
    }

    suspend fun getService(id: ServiceId): ServiceInfo? = query()
    {
        selectAll().where { ServiceTable.id eq id }.singleOrNull()?.let(::deserialize)
    }

    suspend fun getServiceWithSecretRevokedTime(id: ServiceId): Pair<ServiceInfo, Instant>? = query()
    {
        val row = selectAll().where { ServiceTable.id eq id }.singleOrNull() ?: return@query null
        deserialize(row) to row[secretKeyRevokedTime].toInstant().toKotlinInstant()
    }

    suspend fun getServiceByName(name: String): ServiceInfo? = query()
    {
        selectAll().where { ServiceTable.name eq name }.singleOrNull()?.let(::deserialize)
    }

    /**
     * 获得服务列表, 仅能看到自己有权看到的服务
     */
    suspend fun getServices(
        loginUser: UserInfo?,
        owner: UserId?,
        status: ServiceStatus?,
        begin: Long,
        count: Int,
    ): Slice<ServiceInfo> = query()
    {
        selectAll()
            .apply { if (!loginUser.hasAdmin) andWhere { (table.owner eq loginUser?.id) or (table.status eq ServiceStatus.NORMAL) } }
            .apply { owner?.let { andWhere { ServiceTable.owner eq owner } } }
            .apply { status?.let { andWhere { ServiceTable.status eq status } } }
            .asSlice(begin, count)
            .map(::deserialize)
    }

    suspend fun getNeedPendingServices(
        begin: Long,
        count: Int,
    ): Slice<ServiceInfo> = query()
    {
        selectAll()
            .orWhere { status eq ServiceStatus.PENDING }
            .orWhere { pendingName.isNotNull() }
            .orWhere { pendingDescription.isNotNull() }
            .orWhere { pendingUnauthorized.isNotNull() }
            .orWhere { pendingAuthorized.isNotNull() }
            .orWhere { pendingCancelAuthorization.isNotNull() }
            .asSlice(begin, count)
            .map(::deserialize)
    }

    suspend fun updateService(service: ServiceInfo) = query()
    {
        update(where = { id eq service.id })
        {
            it[name] = service.name
            it[description] = service.description
            it[status] = service.status
            it[unauthorized] = service.unauthorized
            it[authorized] = service.authorized
            it[cancelAuthorization] = service.cancelAuthorization
            it[pendingName] = service.pendingName
            it[pendingDescription] = service.pendingDescription
            it[pendingUnauthorized] = service.pendingUnauthorized
            it[pendingAuthorized] = service.pendingAuthorized
            it[pendingCancelAuthorization] = service.pendingCancelAuthorization
        }
    }
}