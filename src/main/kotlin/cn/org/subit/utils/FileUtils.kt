package cn.org.subit.utils

import cn.org.subit.dataClasses.UserId
import cn.org.subit.logger.SSubitOLogger
import cn.org.subit.workDir
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * 头像工具类
 * 头像存储在本地, 按照用户ID给每个用户创建一个文件夹, 文件夹中存放用户的头像
 * 头像文件名为数字, 从0开始, 依次递增, 数字最大的即为当前使用的头像
 * 默认头像存放在 default 文件夹中, 可以在其中添加任意数量的头像, 用户被设置为默认头像时, 会随机选择一个头像
 */
object FileUtils
{
    val dataFolder = File(workDir, "data")
    private val logger = SSubitOLogger.getLogger()
    private val avatarFolder = File(dataFolder, "/avatars")
    private val defaultAvatarFolder = File(avatarFolder, "default")
    private val iconFolder = File(dataFolder, "/icons")
    private const val AVATAR_SIZE = 1024
    private const val ICON_SIZE = 1024

    init
    {
        avatarFolder.mkdirs()
    }

    fun setAvatar(user: UserId, avatar: BufferedImage)
    {
        val userAvatarFolder = File(avatarFolder, user.value.toString(16).padStart(16, '0'))
        userAvatarFolder.mkdirs()
        // 文件夹中已有的头像数量
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount}.png")
        avatarFile.createNewFile()
        // 将头像大小调整为 1024x1024
        val resizedAvatar = BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB)
        val graphics = resizedAvatar.createGraphics()
        graphics.drawImage(avatar, 0, 0, AVATAR_SIZE, AVATAR_SIZE, null)
        graphics.dispose()
        // 保存头像
        ImageIO.write(resizedAvatar, "png", avatarFile)
    }

    fun setDefaultAvatar(user: UserId): BufferedImage
    {
        val userAvatarFolder = File(avatarFolder, user.value.toString(16).padStart(16, '0'))
        userAvatarFolder.mkdirs()
        // 文件夹中已有的头像数量
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount}.png")
        // 在默认头像文件夹中随机选择一个头像
        val defaultAvatarFiles = defaultAvatarFolder.listFiles()
        val defaultAvatar = defaultAvatarFiles?.randomOrNull()
        if (defaultAvatar == null)
        {
            logger.warning("No default avatar found")
            return BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB)
        }
        // 保存头像
        defaultAvatar.copyTo(avatarFile)
        return ImageIO.read(defaultAvatar)
    }

    fun getAvatar(user: UserId): BufferedImage
    {
        val userAvatarFolder = File(avatarFolder, user.value.toString(16).padStart(16, '0'))
        val avatarCount = userAvatarFolder.listFiles()?.size ?: 0
        val avatarFile = File(userAvatarFolder, "${avatarCount-1}.png")
        return if (avatarFile.exists()) ImageIO.read(avatarFile) else setDefaultAvatar(user)
    }
}