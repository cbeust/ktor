package org.jetbrains.ktor.logging

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*

object CallLogging : ApplicationFeature<Application, Unit> {
    override val name: String = "Call logging"
    override val key: AttributeKey<Unit> = AttributeKey("request-logging")

    private val loggingPhase = PipelinePhase("Logging")
    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        pipeline.phases.insertBefore(ApplicationCallPipeline.Infrastructure, loggingPhase)
        pipeline.intercept(loggingPhase) { call ->
            onSuccess { pipeline.logCallFinished(call) }
            onFail { pipeline.logCallFailed(call, it) }
        }
    }

    private fun Application.logCallFinished(call: ApplicationCall) {
        val status = call.response.status()
        when (status) {
            HttpStatusCode.Found -> environment.log.trace("$status: ${call.request.requestLine} -> ${call.response.headers[HttpHeaders.Location]}")
            else -> environment.log.trace("$status: ${call.request.requestLine}")
        }
    }

    private fun Application.logCallFailed(call: ApplicationCall, e: Throwable) {
        val status = call.response.status()
        environment.log.error("$status: ${call.request.requestLine}", e)
    }
}