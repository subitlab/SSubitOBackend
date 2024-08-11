package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class OauthClientId(val value: Int): Comparable<OauthClientId>
{
    override fun compareTo(other: OauthClientId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toOauthClientId() = OauthClientId(toInt())
        fun String.toOauthClientIdOrNull() = toIntOrNull()?.let(::OauthClientId)
        fun Number.toOauthClientId() = OauthClientId(toInt())
    }
}