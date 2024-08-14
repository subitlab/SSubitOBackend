package cn.org.subit.config

import cn.org.subit.logger.SSubitOLogger
import kotlinx.serialization.Serializable

@Serializable
data class SystemConfig(
    val clientId: String,
    val schoolId: Int,
    val redirectUri: String
)


var systemConfig: SystemConfig by config("system.yml", SystemConfig("", 0, ""))