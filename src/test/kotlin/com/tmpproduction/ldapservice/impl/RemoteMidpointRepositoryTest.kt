package com.tmpproduction.ldapservice.impl

import com.tmpproduction.ldapservice.AuthenticationError
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

internal class RemoteMidpointRepositoryTest {
    @Test
    fun authFailure() {
        val mockHttp = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode(401, "Unauthorized"),
                )
            }
        )
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            assertThrows(AuthenticationError::class.java) {
                runBlocking {
                    it.isAvailable()
                }
            }
        }
    }

    @Test
    fun available() {
        val mockHttp = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK
                )
            }
        ) {
            install(HttpTimeout) {
                requestTimeoutMillis = 500
            }
        }
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            assertTrue(
                runBlocking {
                    it.isAvailable()
                }
            )
        }
    }

    @Test
    fun unavailable() {
        val mockHttp = HttpClient(
            MockEngine {
                withContext(Dispatchers.IO) {
                    Thread.sleep(5000)
                }
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK
                )
            }
        ) {
            install(HttpTimeout) {
                requestTimeoutMillis = 500
            }
        }
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            assertFalse(
                runBlocking {
                    it.isAvailable()
                }
            )
        }
    }

    @Test
    fun userByFullName() {
        val allUsersResponse = """
            {
            "object": {
            "object": [
                {"fullName": "Delec", "oid": 10},
                {"fullName": "bruce-wayne", "oid": "000-1-10"}
                ]
            }
            }
        """.trimIndent()
        val mockHttp = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(allUsersResponse),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            runBlocking {
                val user = it.getUserByFullName("bruce-wayne")
                assertEquals("000-1-10", user)
            }
        }
    }

    @Test
    fun setMemberOfWithError() {
        val mockHttp = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(
                        "Ugh I am midpoint I do not work correctly sorry java lang npe"
                    ),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            assertThrows(RuntimeException::class.java) {
                runBlocking {
                    it.setMemberOf("any", "any")
                }
            }
        }
    }

    @Test
    fun getShadowResourceOIDTest() {
        val response = """
            {
            "user": {
            "linkRef" : { "oid": "FOUND_OID" }
            }
            }
        """.trimIndent()
        val mockHttp = HttpClient(
            MockEngine {
                respond(
                    content = ByteReadChannel(response),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            runBlocking {
                assertEquals(listOf("FOUND_OID"), it.getUserShadows("any"))
            }
        }
    }


    @Test
    fun shadowBelongsToLDAP() {
        fun shadowsResponse(oid: String) = """
            {
            "shadow": {
            "resourceRef": {"oid" : "$oid"}
            }
            }
        """.trimIndent()
        fun resourcesResponse(name: String) = """
            {"resource" : {"name" : "$name"}}
        """.trimIndent()
        val mockHttp = HttpClient(
            MockEngine { req ->
                if (req.url.toString().contains("shadows")) {
                    val respOid =
                        if (req.url.toString().endsWith("LDAP_SHADOW")) "LDAP_OID" else "NOTHING"
                    respond(
                        content = ByteReadChannel(shadowsResponse(respOid)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    val isGood = req.url.toString().endsWith("LDAP_OID")
                    respond(
                        content = ByteReadChannel(resourcesResponse(if (isGood) "LDAP" else "no")),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        )
        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            mockHttp
        ).use {
            runBlocking {
                assertTrue(it.shadowBelongsToLDAP("LDAP_SHADOW"))
                assertFalse(it.shadowBelongsToLDAP("NO_ONE"))
            }
        }
    }

    private val brokenMidpointEngine = MockEngine {
        respond(
            content = ByteReadChannel("""{"object": "oh no not json:(((111 """),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Test
    fun brokenMidpoint() {
        fun brokenActions(mp: RemoteMidpointRepository): List<Executable> = listOf(
            suspend { mp.shadowBelongsToLDAP("") },
            { mp.getUserByFullName("") },
            { mp.getUserShadows("") },
            { mp.setMemberOf("", "") }
        ).map { Executable { runBlocking { it() } } }

        RemoteMidpointRepository(
            config = RemoteMidpointRepository.Config(
                host = "stub.com",
                password = "abcd",
                port = 0,
                userName = ""
            ),
            HttpClient(brokenMidpointEngine)
        ).use { midpoint ->
            brokenActions(midpoint).forEach {
                assertThrows(Exception::class.java) {
                    it.execute()
                }
            }
        }
    }

}
