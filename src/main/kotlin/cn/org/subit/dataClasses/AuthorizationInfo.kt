package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
data class AuthorizationInfo(
    val id: AuthorizationId,
    val user: UserId,
    val service: ServiceId,
    val grantedAt: Long,
    val cancel: Boolean,
)
{
    companion object
    {
        val example get() = AuthorizationInfo(
            id = AuthorizationId(1),
            user = UserId(1),
            service = ServiceId(1),
            grantedAt = 0,
            cancel = false,
        )
    }
}

@Serializable
enum class AuthorizationStatus
{
    /**
     * 未授权
     */
    UNAUTHORIZED,

    /**
     * 已授权
     */
    AUTHORIZED,

    /**
     * 已取消
     */
    CANCELED,
}