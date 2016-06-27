package org.jetbrains.ktor.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.util.*

object ConditionalHeadersSupport : ApplicationFeature<ApplicationCallPipeline, Unit> {
    override val name = "ConditionalHeaders"
    override val key = AttributeKey<Unit>(name)

    private val conditionalHeaders = listOf(HttpHeaders.IfModifiedSince, HttpHeaders.IfUnmodifiedSince, HttpHeaders.IfMatch, HttpHeaders.IfNoneMatch)

    override fun install(pipeline: ApplicationCallPipeline, configure: Unit.() -> Unit) {
        configure(Unit)

        pipeline.intercept(ApplicationCallPipeline.Infrastructure) { call ->
            if (conditionalHeaders.any { it in call.request.headers }) {
                call.respond.intercept(RespondPipeline.After) {
                    val message = subject.message
                    when (message) {
                        is HasVersions -> checkVersions(call, message.versions)
                        is FinalContent -> checkVersions(call, message.lastModifiedAndEtagVersions())
                    }
                }
            }
        }
    }

    private fun checkVersions(call: ApplicationCall, versions: List<Version>) {
        for (version in versions) {
            val result = when (version) {
                is EntityTagVersion -> call.checkEtag(version.etag)
                is LastModifiedVersion -> call.checkLastModified(version.lastModified)
                else -> ConditionalHeaderCheckResult.OK
            }

            if (result != ConditionalHeaderCheckResult.OK) {
                call.respond(result.statusCode)
            }
        }
    }
}
