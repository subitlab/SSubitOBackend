package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
data class ServiceInfo(
    val id: ServiceId,
    val name: String,
    val description: String,
    val owner: UserId,
    val status: ServiceStatus,
    val unauthorized: ServicePermission,
    val authorized: ServicePermission,
    val cancelAuthorization: ServicePermission,
    val pendingName: String?,
    val pendingDescription: String?,
    val pendingUnauthorized: ServicePermission?,
    val pendingAuthorized: ServicePermission?,
    val pendingCancelAuthorization: ServicePermission?,
): SsoPrincipal
{
    fun toBasicServiceInfo() = BasicServiceInfo(id, name, description)
    companion object
    {
        val example get() = ServiceInfo(
            id = ServiceId(1),
            name = "服务名称",
            description = "服务描述",
            owner = UserId(1),
            status = ServiceStatus.NORMAL,
            unauthorized = ServicePermission.NONE,
            authorized = ServicePermission.BASIC,
            cancelAuthorization = ServicePermission.ALL,
            pendingName = null,
            pendingDescription = null,
            pendingUnauthorized = null,
            pendingAuthorized = null,
            pendingCancelAuthorization = null,
        )
    }
}

@Serializable
data class BasicServiceInfo(
    val id: ServiceId,
    val name: String,
    val description: String,
)
{
    companion object
    {
        val example get() = ServiceInfo.example.toBasicServiceInfo()
    }
}