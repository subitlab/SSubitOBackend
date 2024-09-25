package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class Permission
{
    BANNED,
    NORMAL,
    READONLY_ADMIN,
    ADMIN,
}