package cn.org.subit.database

import cn.org.subit.dataClasses.Slice.Companion.singleOrNull
import cn.org.subit.dataClasses.UserId
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.insert

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    object StudentIdTable: IdTable<String>("student_id")
    {
        val studentId = StudentIdTable.varchar("student_id", 40).entityId()
        val user = StudentIdTable.reference("user", Users.UserTable).index()
        override val id = studentId
        override val primaryKey = PrimaryKey(id)
    }

    suspend fun getUserStudentIds(userId: UserId): List<String> = query()
    {
        select(studentId).where { user eq userId }.map { it[studentId].value }
    }

    suspend fun getStudentIdUsers(studentId: String): UserId? = query()
    {
        select(user).where { table.studentId eq studentId }.singleOrNull()?.get(user)?.value
    }

    suspend fun addStudentId(userId: UserId, studentId: String) = query()
    {
        insert {
            it[user] = userId
            it[this.studentId] = studentId
        }
    }
}