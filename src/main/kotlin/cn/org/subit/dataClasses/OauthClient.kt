package cn.org.subit.dataClasses

data class OauthClient(
    val clientId: OauthClientId,
    val clientSecret: String,
    val redirectUri: String
)