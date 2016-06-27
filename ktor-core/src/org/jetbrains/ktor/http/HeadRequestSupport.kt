package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*

object HeadRequestSupport : ApplicationFeature<ApplicationCallPipeline, Unit> {
    override val name = "head-request-handler"
    override val key = AttributeKey<Unit>(name)

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        configure(Unit)

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) {
            if (call.request.httpMethod == HttpMethod.Head) {
                it.respond.intercept(RespondPipeline.After) {
                    val message = subject.message
                    if (message is FinalContent && message !is FinalContent.NoContent) {
                        call.respond(HeadResponse(message))
                    }
                }
            }
        }
    }

    private class HeadResponse(val delegate: FinalContent) : FinalContent.NoContent() {
        override val headers by lazy { delegate.headers }
        override val status: HttpStatusCode?
            get() = delegate.status
    }
}
