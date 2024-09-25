package cn.org.subit.route

import cn.org.subit.Loader
import cn.org.subit.route.utils.finishCallWithBytes
import cn.org.subit.utils.HttpStatus
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.ktor.http.*
import io.ktor.server.routing.*

fun Route.logo() = route("/logo", {
    hidden = true
})
{
    get("/dark")
    {
        finishCallWithBytes(
            HttpStatus.OK,
            ContentType.Image.PNG,
            Loader.getResource("logo/SubIT-dark.png")!!
        )
    }

    get("/light")
    {
        finishCallWithBytes(
            HttpStatus.OK,
            ContentType.Image.PNG,
            Loader.getResource("logo/SubIT-light.png")!!
        )
    }

    get("/normal")
    {
        finishCallWithBytes(
            HttpStatus.OK,
            ContentType.Image.PNG,
            Loader.getResource("logo/SubIT-normal.png")!!
        )
    }
}