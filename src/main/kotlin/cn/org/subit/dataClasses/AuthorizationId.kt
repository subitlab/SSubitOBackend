package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class AuthorizationId(val value: Long): Comparable<AuthorizationId>
{
    override fun compareTo(other: AuthorizationId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toAuthorizationId() = AuthorizationId(toLong())
        fun String.toAuthorizationIdOrNull() = toLongOrNull()?.let(::AuthorizationId)
        fun Number.toAuthorizationId() = AuthorizationId(toLong())
    }
}