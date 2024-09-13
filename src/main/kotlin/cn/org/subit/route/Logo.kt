package cn.org.subit.route

import cn.org.subit.Loader
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.logo() = route("/logo", {
    hidden = true
})
{
    get("/dark")
    {
        call.respondBytes(Loader.getResource("logo/SubIT-dark.png")!!.readAllBytes())
    }

    get("/light")
    {
        call.respondBytes(Loader.getResource("logo/SubIT-light.png")!!.readAllBytes())
    }

    get("/normal")
    {
        call.respondBytes(Loader.getResource("logo/SubIT-normal.png")!!.readAllBytes())
    }
}