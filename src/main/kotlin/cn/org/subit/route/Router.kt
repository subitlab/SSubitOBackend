package cn.org.subit.route

import cn.org.subit.route.basic.basic
import cn.org.subit.route.info.info
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.router() = routing()
{
    get("/", { hidden = true })
    {
        call.respondRedirect("/api-docs")
    }

    authenticate("auth-api-docs")
    {
        route("/api-docs")
        {
            route("/api.json")
            {
                openApiSpec()
            }
            swaggerUI("/api-docs/api.json")
        }
    }

    basic()
    info()
}