package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Suppress("unused")
@JvmInline
@Serializable
value class ServiceId(val value: Int): Comparable<ServiceId>
{
    override fun compareTo(other: ServiceId): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    companion object
    {
        fun String.toServiceId() = ServiceId(toInt())
        fun String.toServiceIdOrNull() = toIntOrNull()?.let(::ServiceId)
        fun Number.toServiceId() = ServiceId(toInt())
    }
}