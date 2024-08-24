package cn.org.subit.database

import cn.org.subit.dataClasses.Slice.Companion.singleOrNull
import cn.org.subit.dataClasses.UserId
import cn.org.subit.route.seiue.Seiue
import cn.org.subit.utils.Locks
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.component.inject
import java.util.WeakHashMap

class StudentIds: SqlDao<StudentIds.StudentIdTable>(StudentIdTable)
{
    companion object
    {
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            allowStructuredMapKeys = true
            encodeDefaults = true
        }
    }

    object StudentIdTable: IdTable<String>("student_id")
    {
        val studentId = StudentIdTable.varchar("student_id", 40).entityId()
        val user = StudentIdTable.reference("user", Users.UserTable).index()
        val realName = varchar("real_name", 100)
        val rawData = jsonb<Seiue>("raw_data", json)
        override val id = studentId
        override val primaryKey = PrimaryKey(id)
    }

    suspend fun getUserStudentIdAndName(userId: UserId): Map<String, String> = query()
    {
        select(studentId, realName).where { user eq userId }.associate { it[studentId].value to it[realName] }
    }

    suspend fun getStudentIdUsers(studentId: String): UserId? = query()
    {
        select(user).where { table.studentId eq studentId }.singleOrNull()?.get(user)?.value
    }

    suspend fun addStudentId(userId: UserId, studentId: String, realName: String, seiue: Seiue) = query()
    {
        insert {
            it[user] = userId
            it[this.studentId] = studentId
            it[this.realName] = realName
            it[rawData] = seiue
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