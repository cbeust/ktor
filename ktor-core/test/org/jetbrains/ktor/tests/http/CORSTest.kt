package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.http.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.junit.*
import kotlin.test.*

class CORSTest {

    @Test
    fun testNoOriginHeader() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {

            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testWrongOriginHeader() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "invalid-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testWrongOriginHeaderIsEmpty() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequest() {
        withTestApplication {
            application.CORS {
                host("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestPort1() {
        withTestApplication {
            application.CORS {
                host("my-host")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:80")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host:80", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:90")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestPort2() {
        withTestApplication {
            application.CORS {
                host("my-host:80")
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host:80")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host:80", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestExposed() {
        withTestApplication {
            application.CORS {
                host("my-host")
                exposeHeader(HttpHeaders.ETag)
                exposeHeader(HttpHeaders.Vary)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf(HttpHeaders.ETag, HttpHeaders.Vary), call.response.headers[HttpHeaders.AccessControlExposeHeaders]?.split(", ")?.toSet())
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestHttps() {
        withTestApplication {
            application.CORS {
                host("my-host", schemes = listOf("http", "https"))
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "https://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("https://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleRequestSubDomains() {
        withTestApplication {
            application.CORS {
                host("my-host", subDomains = listOf("www"))
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://www.my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://www.my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://other.my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
                assertNull(call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStar() {
        withTestApplication {
            application.CORS {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertNull(call.response.headers[HttpHeaders.Vary])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleStarCredentials() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("http://my-host", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("true", call.response.headers[HttpHeaders.AccessControlAllowCredentials])
                assertEquals(HttpHeaders.Origin, call.response.headers[HttpHeaders.Vary])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleNull() {
        withTestApplication {
            application.CORS {
                anyHost()
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "null")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testSimpleNullAllowCredentials() {
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "null")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("null", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testMultipleDomainsOriginNotSupported() {
        // the specification is not clear whether we should support multiple domains Origin header and how do we validate them
        withTestApplication {
            application.CORS {
                anyHost()
                allowCredentials = true
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Get, "/") {
                addHeader(HttpHeaders.Origin, "http://host1 http://host2")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertNull(call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals("OK", call.response.content)
            }
        }
    }

    @Test
    fun testPreFlight() {
        withTestApplication {
            application.CORS {
                anyHost()
                header(HttpHeaders.Range)
            }

            application.routing {
                get("/") {
                    call.respond("OK")
                }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "PUT")
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.CacheControl)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.ALPN)
            }.let { call ->
                assertEquals(HttpStatusCode.Forbidden, call.response.status())
            }

            handleRequest(HttpMethod.Options, "/") {
                addHeader(HttpHeaders.Origin, "http://my-host")
                addHeader(HttpHeaders.AccessControlRequestMethod, "GET")
                addHeader(HttpHeaders.AccessControlRequestHeaders, HttpHeaders.Range)
            }.let { call ->
                assertEquals(HttpStatusCode.OK, call.response.status())
                assertEquals("*", call.response.headers[HttpHeaders.AccessControlAllowOrigin])
                assertEquals(setOf("GET", "POST", "HEAD"), call.response.headers[HttpHeaders.AccessControlAllowMethods]?.split(", ")?.toSet())
                assertTrue { call.response.headers.values(HttpHeaders.AccessControlAllowHeaders).isNotEmpty() }
                assertTrue { HttpHeaders.Range in call.response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty() }
            }
        }
    }
}