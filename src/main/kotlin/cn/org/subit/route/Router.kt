package cn.org.subit.route

import cn.org.subit.JWTAuth.getLoginUser
import cn.org.subit.dataClasses.Permission
import cn.org.subit.route.basic.basic
import cn.org.subit.route.info.info
import cn.org.subit.route.seiue.seiue
import cn.org.subit.route.terminal.terminal
import cn.org.subit.utils.HttpStatus
import cn.org.subit.utils.respond
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.router() = routing()
{
    val rootPath = this.application.environment.rootPath

    get("/", { hidden = true })
    {
        call.respondRedirect("$rootPath/api-docs")
    }

    authenticate("auth-api-docs")
    {
        route("/api-docs")
        {
            route("/api.json")
            {
                openApiSpec()
            }
            swaggerUI("$rootPath/api-docs/api.json")
        }
    }

    authenticate("ssubito-auth", optional = true)
    {
        intercept(ApplicationCallPipeline.Call)
        {
            val permission = getLoginUser()?.permission ?: return@intercept
            if (permission < Permission.NORMAL) call.respond(HttpStatus.Prohibit)
        }

        basic()
        info()
        seiue()
        terminal()
    }

    logo()
}