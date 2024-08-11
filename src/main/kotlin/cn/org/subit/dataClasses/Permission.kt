package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Serializable
enum class Permission
{
    BANNED,
    NORMAL,
    ADMIN,
}