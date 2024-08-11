package cn.org.subit.database

import cn.org.subit.dataClasses.OauthClient
import cn.org.subit.dataClasses.OauthClientId
import cn.org.subit.dataClasses.UserId
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.orWhere

class OauthClients: SqlDao<OauthClients.OauthClientTable>(OauthClientTable)
{
    object OauthClientTable: IdTable<OauthClientId>()
    {
        val clientId = oauthClientId("client_id").autoIncrement().entityId()
        val clientName = varchar("client_name", 100)
        val clientSecret = varchar("client_secret", 100)
        val redirectUri = varchar("redirect_uri", 100)
        val userId = reference("user", Users.UserTable.id).index()
        override val id = clientId
        override val primaryKey = PrimaryKey(id)
    }

    private fun deserialize(row: ResultRow): OauthClient = OauthClient(
        clientId = row[table.clientId].value,
        clientSecret = row[table.clientSecret],
        redirectUri = row[table.redirectUri]
    )

    suspend fun createOauthClient(
        clientSecret: String,
        redirectUri: String,
        userId: UserId
    ): OauthClientId? = query()
    {
        val isExist = table
            .select(id)
            .where { table.clientSecret eq clientSecret }
            .orWhere { table.redirectUri eq redirectUri }
            .orWhere { table.userId eq userId }
            .singleOrNull() != null

        if (isExist) return@query null

        table.insertAndGetId()
        {
            it[table.clientSecret] = clientSecret
            it[table.redirectUri] = redirectUri
            it[table.userId] = userId
        }.value
    }

    suspend fun getOauthClient(clientId: OauthClientId): OauthClient? = query()
    {
        table
            .select(table.clientId eq clientId)
            .singleOrNull()
            ?.let(::deserialize)
    }
}