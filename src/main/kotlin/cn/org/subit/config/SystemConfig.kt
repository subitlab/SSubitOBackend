package cn.org.subit.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class SystemConfig(
    val clientId: String,
    val schoolId: List<Int>,
    val frontendPattern: String,
)
{
    @Transient
    val frontendRegex = Regex(frontendPattern, RegexOption.IGNORE_CASE)
}


var systemConfig: SystemConfig by config(
    "system.yml",
    SystemConfig(
        "",
        listOf(1,2,3),
        "https://(pkus\\.)?sso\\.subit\\.org\\.cn(/.*)?"
    )
)