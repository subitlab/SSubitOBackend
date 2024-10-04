package cn.org.subit.database

import cn.org.subit.dataClasses.UserFull
import cn.org.subit.dataClasses.UserId
import cn.org.subit.database.utils.singleOrNull
import cn.org.subit.plugin.contentNegotiation.dataJson
import kotlinx.serialization.encodeToString
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.selectAll

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    object StudentIdTable: IdTable<String>("student_id")
    {
        val studentId = varchar("student_id", 40).entityId()
        val user = reference("user", Users.UserTable).index()
        val realName = varchar("real_name", 100)
        val archived = bool("archived").default(false)
        val rawData = jsonb("raw_data", { it }, { it })
        override val id = studentId
        override val primaryKey = PrimaryKey(id)
    }

    suspend fun getSeiue(userId: UserId): List<UserFull.Seiue> = query()
    {
        select(studentId, realName, archived)
            .where { user eq userId }
            .map { row ->
                UserFull.Seiue(
                    studentId = row[studentId].value,
                    realName = row[realName],
                    archived = row[archived],
                )
            }
    }

    suspend fun getStudentIdUsers(studentId: String): UserId? = query()
    {
        select(user).where { table.studentId eq studentId }.singleOrNull()?.get(user)?.value
    }

    suspend inline fun <reified T> addStudentId(userId: UserId, studentId: String, realName: String, archived: Boolean, seiue: T) = query()
    {
        insert {
            it[user] = userId
            it[this.studentId] = studentId
            it[this.realName] = realName
            it[this.archived] = archived
            it[rawData] = dataJson.encodeToString<T>(seiue)
        }
    }

    suspend fun getStudentIdCount(userId: UserId): Long = query()
    {
        selectAll().where { user eq userId }.count()
    }

    suspend fun removeStudentId(userId: UserId, studentId: String): Boolean = query()
    {
        deleteWhere { (user eq userId) and (table.studentId eq studentId) } > 0
    }
}