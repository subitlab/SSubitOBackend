package cn.org.subit.plugin

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.swagger.compileInlining
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.withAutoTitle
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*

/**
 * 在/api-docs 路径下安装SwaggerUI
 */
fun Application.installApiDoc() = install(SwaggerUI)
{
    info()
    {
        title = "SSubitO后端API文档"
        version = cn.org.subit.version
        description = "SSubitO后端API文档"
    }
    server {
        url = "http://localhost:8080"
    }
    this.ignoredRouteSelectors += RateLimitRouteSelector::class
    schemas {
        generator = {
            it.processReflection()
                .generateSwaggerSchema()
                .withAutoTitle(TitleType.SIMPLE)
                .compileInlining()
        }
    }
}