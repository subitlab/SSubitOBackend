package cn.org.subit.dataClasses

import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class Permission
{
    /**
     * 被封禁的用户
     */
    BANNED,

    /**
     * 普通用户
     */
    NORMAL,

    /**
     * 管理员用户
     */
    ADMIN,

    /**
     * 根用户(超级管理员)
     */
    ROOT,
}

enum class ServiceStatus
{
    /**
     * 被封禁的服务
     */
    BANNED,

    /**
     * 待审核的服务
     */
    PENDING,

    /**
     * 普通服务
     */
    NORMAL,
}

enum class ServicePermission
{
    /**
     * 无全限获得任何信息
     */
    NONE,

    /**
     * 只能读取基本信息
     */
    BASIC,

    /**
     * 可以获得所有信息
     */
    ALL,
}