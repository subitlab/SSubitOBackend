package cn.org.subit.utils

import io.ktor.server.application.*
import io.ktor.server.engine.*
import org.koin.core.component.KoinComponent
import cn.org.subit.console.AnsiStyle.Companion.RESET
import cn.org.subit.console.SimpleAnsiColor.Companion.CYAN
import cn.org.subit.console.SimpleAnsiColor.Companion.PURPLE
import cn.org.subit.logger.SSubitOLogger
import kotlin.system.exitProcess

@Suppress("unused")
object Power: KoinComponent
{
    val logger = SSubitOLogger.getLogger()

    fun shutdown(code: Int, cause: String = "unknown"): Nothing
    {
        val application = runCatching { getKoin().get<Application>() }.getOrNull()
        application.shutdown(code, cause)
    }
    fun Application?.shutdown(code: Int, cause: String = "unknown"): Nothing
    {
        logger.warning("${PURPLE}Server is shutting down: ${CYAN}$cause${RESET}")
        // 尝试主动结束Ktor, 这一过程不一定成功, 例如Ktor本来就在启动过程中出错将关闭失败
        if (this != null) runCatching()
        {
            val environment = this.environment
            environment.monitor.raise(ApplicationStopPreparing, environment)
            if (environment is ApplicationEngineEnvironment) environment.stop()
            else this@shutdown.dispose()
        }.onFailure {
            logger.warning("Failed to stop Ktor: ${it.message}")
            it.printStackTrace(SSubitOLogger.err)
        }
        else logger.warning("Application is null")
        // 无论是否成功关闭, 都强制退出
        exitProcess(code)
    }
}