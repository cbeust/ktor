package org.jetbrains.ktor.tests.application

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class HandlerTest {

    Test fun `application with empty handler`() = withTestApplication {
        on("making a request") {
            val request = handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with transparent handler`() = withTestApplication {
        application.handler.intercept { request, next -> next(request) }
        on("making a request") {
            val request = handleRequest { }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with handler returning true`() = withTestApplication {
        application.handler.intercept { request, next -> ApplicationRequestStatus.Handled }
        on("making a request") {
            val request = handleRequest { }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should not contain response") {
                assertNull(request.response)
            }
        }
    }

    Test fun `application with handler that returns a valid response`() = withTestApplication {
        application.handler.intercept { request, next ->
            request.respond {
                ApplicationRequestStatus.Handled
            }
        }
        on("making a request") {
            val request = handleRequest { }

            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should contain response") {
                assertNotNull(request.response)
            }
        }
    }

    Test fun `application with handler that returns two responses`() = withTestApplication {
        application.handler.intercept { request, next ->
            request.respond { ApplicationRequestStatus.Handled }
            request.respond { ApplicationRequestStatus.Handled }
        }
        on("making a request") {
            val request = fails {
                handleRequest { }
            }!!
            it("should throw invalid operation") {
                assertEquals(request.javaClass, javaClass<IllegalStateException>())
            }
        }
    }

    Test fun `application with handler that checks body on POST method`() = withTestApplication {
        application.handler.intercept { request, next ->
            if (request.httpMethod == HttpMethod.Post) {
                request.respond {
                    status(HttpStatusCode.OK)
                    assertEquals(request.body, "Body")
                    ApplicationRequestStatus.Handled
                }
            } else
                ApplicationRequestStatus.Unhandled
        }
        val result = handleRequest {
            method = HttpMethod.Post
            body = "Body"
        }
        assertEquals(ApplicationRequestStatus.Handled, result.requestResult)
    }

    Test fun `application with handler that returns true on POST method`() = withTestApplication {
        application.handler.intercept { request, next ->
            if (request.httpMethod == HttpMethod.Post) {
                request.respond {
                    status(HttpStatusCode.OK)
                    ApplicationRequestStatus.Handled
                }
            } else
                ApplicationRequestStatus.Unhandled
        }
        on("making a GET request") {
            val request = handleRequest { method = HttpMethod.Get }
            it("should not be handled") {
                assertEquals(ApplicationRequestStatus.Unhandled, request.requestResult)
            }
            it("should not return response") {
                assertNull(request.response)
            }
        }
        on("making a POST request") {
            val request = handleRequest { method = HttpMethod.Post }
            it("should be handled") {
                assertEquals(ApplicationRequestStatus.Handled, request.requestResult)
            }
            it("should return response") {
                assertNotNull(request.response)
            }
        }
    }

    Test fun `application with handler that intercepts creation of response`() = withTestApplication {
        var interceptedResponse = false
        application.handler.intercept { request, next ->
            request.createResponse.intercept { next ->
                val response = next()
                interceptedResponse = true
                response.header("intercepted", "header")
                response
            }
            next(request)
        }

        application.handler.intercept { request, handler ->
            request.createResponse.intercept { createResponse ->
                val response = createResponse()
                response.header.intercept { name, value, header ->
                    if (name == "Content-Type" && value == "text/plain")
                        header(name, "text/xml")
                    else
                        header(name, value)
                }
                response
            }
            handler(request)
        }

        on("not asking for a response") {
            application.handler.intercept { request, next -> next(request); ApplicationRequestStatus.Handled }
            handleRequest { method = HttpMethod.Get }.let {
                it("should be handled") {
                    assertEquals(it.requestResult, ApplicationRequestStatus.Handled)
                }
                it("response interceptor shouldn't be called") {
                    assertFalse(interceptedResponse)
                }
                it("should not return response") {
                    assertNull(it.response)
                }
            }
        }

        on("asking for a response") {
            application.handler.intercept { request, next ->
                request.respond {
                    contentType(ContentType.Text.Plain)
                    ApplicationRequestStatus.Asynchronous
                }
            }
            handleRequest { method = HttpMethod.Get }.let {
                it("should be handled overwritten by prior interception") {
                    assertEquals(it.requestResult, ApplicationRequestStatus.Handled)
                }
                it("response interceptor should be called") {
                    assertTrue(interceptedResponse)
                }
                it("should return response") {
                    assertNotNull(it.response)
                }
                it("should have modified content type to text/xml") {
                    assertEquals(it.response!!.headers["Content-Type"], "text/xml")
                }
                it("should have injected header") {
                    assertEquals(it.response!!.headers["intercepted"], "header")
                }
            }
        }
    }

    /*
    TODO: This is fundamentally wrong since you shouldn't be setting ApplicationRequest "contentType" or "accept" since these are values passed in.
        Test fun `application with handler that returns true on text/plain content type`() {
            val testHost = createTestHost()
            testHost.application.intercept { request, next ->
                if (request.contentType() == ContentType.Text.Plain ) {
                    request.response().status(HttpStatusCode.OK)
                    true
                }
                else
                    false
            }
            on("making a request for content type text/plain") {
                val request = testHost.makeRequest { contentType = ContentType.Text.Plain }
                it("should be handled") {
                    assertTrue(request.handled)
                }
                it("should return response") {
                    shouldNotBeNull(request.response)
                }
            }
            on("making a request for content type any") {
                val request = testHost.makeRequest { contentType = ContentType.Any }
                it("should not be handled") {
                    shouldBeFalse(request.handled)
                }
                it("should not return response") {
                    shouldBeNull(request.response)
                }
            }
        }
    */
}

