package org.jetbrains.ktor.transform

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.io.*

object TransformationSupport : ApplicationFeature<ApplicationCallPipeline, ApplicationTransform<PipelineContext<ResponsePipelineState>>> {
    override val key = AttributeKey<ApplicationTransform<PipelineContext<ResponsePipelineState>>>("Transformation Support")

    override fun install(pipeline: ApplicationCallPipeline, configure: ApplicationTransform<PipelineContext<ResponsePipelineState>>.() -> Unit): ApplicationTransform<PipelineContext<ResponsePipelineState>> {
        val table = ApplicationTransform<PipelineContext<ResponsePipelineState>>()

        configure(table)

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            call.respond.intercept(RespondPipeline.Transform) { state ->
                transform()
            }
        }

        return table
    }

}

fun ApplicationTransform<PipelineContext<ResponsePipelineState>>.registerDefaultHandlers() {
    register<String> { value ->
        val responseContentType = call.response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
        val contentType = responseContentType ?: ContentType.Text.Plain.withParameter("charset", "UTF-8")
        TextContentResponse(null, contentType, value)
    }

    register<TextContent> { value -> TextContentResponse(null, value.contentType, value.text) }

    register<HttpStatusContent> { value ->
        TextContentResponse(value.code,
                ContentType.Text.Html.withParameter("charset", "UTF-8"),
                "<H1>${value.code}</H1>${value.message.escapeHTML()}")
    }

    register<HttpStatusCode> { value ->
        object : FinalContent.NoContent() {
            override val status: HttpStatusCode
                get() = value

            override val headers: ValuesMap
                get() = ValuesMap.Empty
        }
    }

    register<URIFileContent> { value ->
        if (value.uri.scheme == "file") {
            LocalFileContent(File(value.uri))
        } else value
    }
}
