package cn.org.subit.database

import cn.org.subit.dataClasses.Slice.Companion.singleOrNull
import cn.org.subit.dataClasses.UserId
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.insert

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    object StudentIdTable: IdTable<String>("student_id")
    {
        val email = StudentIdTable.varchar("student_id", 40).entityId()
        val user = StudentIdTable.reference("user", Users.UserTable.id).index()
        override val id = email
        override val primaryKey = PrimaryKey(id)
    }

    suspend fun getUserStudentIds(userId: UserId): List<String> = query()
    {
        select(email).where { user eq userId }.map { it[email].value }
    }

    suspend fun getStudentIdUsers(studentId: String): UserId? = query()
    {
        select(user).where { table.email eq studentId }.singleOrNull()?.get(user)?.value
    }

    suspend fun addStudentId(userId: UserId, studentId: String) = query()
    {
        insert {
            it[user] = userId
            it[this.email] = studentId
        }
    }
}