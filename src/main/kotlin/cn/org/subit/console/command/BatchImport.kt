package cn.org.subit.console.command

import cn.org.subit.config.systemConfig
import cn.org.subit.dataClasses.UserFull
import cn.org.subit.database.Emails
import cn.org.subit.database.StudentIds
import cn.org.subit.database.Users
import cn.org.subit.plugin.contentNegotiation.contentNegotiationJson
import cn.org.subit.route.seiue.RawSeiue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

object BatchImport: Command, KoinComponent
{
    private val studentIds: StudentIds by inject()
    private val users: Users by inject()
    private val emails: Emails by inject()

    @Serializable
    private data class ImportData(
        val name: String,
        val studentId: String,
        val email: String,
    )


    override suspend fun execute(sender: CommandSet.CommandSender, args: List<String>): Boolean
    {
        if (args.size != 1) return false

        val filePath = args[0]
        val file = File(filePath)
        if (!file.exists() || !file.isFile)
        {
            sender.out("文件不存在或不是一个有效的文件: $filePath")
            return false
        }
        if (!file.canRead())
        {
            sender.out("无法读取文件: $filePath")
            return false
        }
        if (file.extension != "json")
        {
            sender.out("文件格式不正确, 仅支持 JSON 文件: $filePath")
            return false
        }
        val data = runCatching()
        {
            contentNegotiationJson.decodeFromString<List<ImportData>>(file.readText())
        }.getOrElse()
        {
            sender.out("解析文件失败: ${it.message}")
            return false
        }
        if (data.isEmpty())
        {
            sender.out("文件内容为空")
            return false
        }
        sender.out("共找到 ${data.size} 条记录")

        CoroutineScope(Dispatchers.IO).launch()
        {
            sender.out("开始批量导入: $filePath")
            for ((index, item) in data.withIndex())
            {
                if (emails.getEmailUser(item.email) != null)
                {
                    sender.out("因为邮箱已存在, 跳过导入: $item")
                    continue
                }
                if (studentIds.getStudentIdUsers(item.studentId) != null)
                {
                    sender.out("因为学号已存在, 跳过导入: $item")
                    continue
                }
                runCatching()
                {
                    val userId = users.createUser(item.name, item.studentId)
                    studentIds.addStudentId(
                        userId, RawSeiue(
                            id = 0,
                            schoolId = systemConfig.schoolId,
                            name = item.name,
                            role = UserFull.Seiue.Role.STUDENT.name,
                            departmentNames = emptyList(),
                            pinyin = "",
                            gender = "f",
                            userId = 0,
                            usin = item.studentId,
                            ename = item.name,
                            email = item.email,
                            phone = "",
                            idcard = "",
                            photo = "",
                            status = "normal",
                        )
                    )
                    emails.addEmail(userId, item.email)
                }.onFailure()
                {
                    sender.err("导入失败: $item, 错误: ${it.message}")
                }
                if (index % 20 == 0)
                {
                    sender.out("已导入 $index 条记录")
                }
            }
            sender.out("批量导入完成")
        }
        return true
    }
}