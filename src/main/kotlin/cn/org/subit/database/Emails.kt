package cn.org.subit.database

import cn.org.subit.dataClasses.UserId
import cn.org.subit.database.utils.singleOrNull
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert

class Emails: SqlDao<Emails.EmailTable>(EmailTable)
{
    object EmailTable: IdTable<String>("emails")
    {
        val email = varchar("email", 100).entityId()
        val user = reference("user", Users.UserTable).index()
        override val id = email
        override val primaryKey = PrimaryKey(id)
    }

    suspend fun getUserEmails(userId: UserId): List<String> = query()
    {
        select(email).where { user eq userId }.map { it[email].value }
    }

    suspend fun getEmailUsers(email: String): UserId? = query()
    {
        select(user).where { table.email eq email.lowercase() }.singleOrNull()?.get(user)?.value
    }

    suspend fun addEmail(userId: UserId, email: String) = query()
    {
        insert {
            it[user] = userId
            it[this.email] = email.lowercase()
        }
    }

    suspend fun removeEmail(userId: UserId, email: String): Boolean = query()
    {
        deleteWhere { (user eq userId) and (table.email eq email.lowercase()) } > 0
    }
}