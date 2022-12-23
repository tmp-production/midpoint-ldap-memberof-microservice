package com.tmpproduction.ldapservice.impl

import com.tmpproduction.ldapservice.AuthenticationError
import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.OID
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*

class RemoteMidpointRepository(
    private val config: Config,
    private val client: HttpClient
) : MidpointRepository {

    data class Config(
        val host: String,
        val port: Int,
        val userName: String,
        val password: String
    )

    private val midpointApiUrl: String by lazy {
        with(config) {
            "http://${host}:${port}/midpoint/ws/rest"
        }
    }

    private val usersEndPoint: String by lazy { "$midpointApiUrl/users" }
    private val shadowsEndPoint: String by lazy { "$midpointApiUrl/shadows" }
    private val resourcesEndPoint: String by lazy { "$midpointApiUrl/resources" }

    fun makeMemberOfPayload(newValue: String): String {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <objectModification
          xmlns='http://midpoint.evolveum.com/xml/ns/public/common/api-types-3'
          xmlns:c='http://midpoint.evolveum.com/xml/ns/public/common/common-3'
          xmlns:t="http://prism.evolveum.com/xml/ns/public/types-3"
          xmlns:ri="http://midpoint.evolveum.com/xml/ns/public/resource/instance-3">
          <itemDelta>
            <t:modificationType>replace</t:modificationType>
              <t:path>c:attributes/ri:memberOf</t:path>
              <t:value>${newValue}</t:value>
          </itemDelta>
        </objectModification>
    """.trimIndent()
    }

    // https://github.com/Evolveum/midpoint-samples/blob/master/samples/rest/query-all-users.xml
    fun makeQueryAllUsersPayload(): String
            = """
        <q:query xmlns:q="http://prism.evolveum.com/xml/ns/public/query-3">
            <!-- empty query = return all records -->
        </q:query>
    """.trimIndent()

    override suspend fun isAvailable(): Boolean {
        val res = try {
            client.get(usersEndPoint) {
                with(config) {
                    basicAuth(userName, password)
                }
                contentType(ContentType.Application.Xml)
                accept(ContentType.Application.Json)
            }
        } catch (e: Exception) {
            return false
        }
        if (res.status.value == 401) {
            throw AuthenticationError(res.status.description)
        }
        return true
    }

    override suspend fun getUserByFullName(fullName: String): OID? {
        // TODO(Roman B) we could use some caching here, because
        //  this operation is quite costly. Midpoint sends too much
        //  information, including history of editing(?)
        //  Possible solution: cache OR bake a better request, so that
        //  the user is found on midpoint server
        val response = client.post("$usersEndPoint/search/") {
            with(config) {
                basicAuth(userName, password)
            }
            contentType(ContentType.Application.Xml)
            accept(ContentType.Application.Json)
            setBody(makeQueryAllUsersPayload())
        }
        val users = try {
            Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["object"]!!.jsonObject["object"]!!.jsonArray
        } catch (e: SerializationException) {
            throw RuntimeException("Midpoint user list response is not json", e)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Wrong json structure when parsing users midpoint response", e)
        } catch (e: Exception) {
            throw RuntimeException("Unknown error when parsing midpoint users response", e)
        }
        val usersFullNamesToOID: List<Pair<String?, OID>> = users.map { it.jsonObject }.map {
            it["fullName"]?.jsonPrimitive?.contentOrNull to it["oid"]!!.jsonPrimitive.content
        }
        return usersFullNamesToOID.find { it.first == fullName }?.second
    }

    override suspend fun getUserShadows(user: OID): List<OID> {
        try {
            val response = client.get("$usersEndPoint/$user") {
                with(config) {
                    basicAuth(userName, password)
                }
                accept(ContentType.Application.Json)
            }
            val receivedUser = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val userKey = "user"
            if (!receivedUser.containsKey(userKey)) {
                throw IllegalArgumentException(response.bodyAsText())
            }
            val shadowObjectsOrSingleShadowObject = receivedUser["user"]!!.jsonObject["linkRef"]!!
            val res = if (shadowObjectsOrSingleShadowObject is JsonArray) {
                shadowObjectsOrSingleShadowObject.jsonArray
            } else {
                JsonArray(listOf(shadowObjectsOrSingleShadowObject.jsonObject))
            }
            // todo(Roman) warn if something inside is not string
            return res.jsonArray.map { it.jsonObject["oid"]!!.jsonPrimitive.content }
        } catch (e: SerializationException) {
            throw RuntimeException("Midpoint user-shadow response is not json", e)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Midpoint user-shadow response has unexpected structure", e)
        } catch (e: Exception) {
            throw RuntimeException("Unknown error processing midpoint user-shadow response", e)
        }
    }

    override suspend fun shadowBelongsToLDAP(shadow: OID): Boolean {
        return getResourceName(getShadowResourceOID(shadow)) == "LDAP"
    }

    override suspend fun setMemberOf(shadow: OID, newMemberOf: String) {
        try {
            val response = client.patch("$shadowsEndPoint/$shadow") {
                with(config) {
                    basicAuth(userName, password)
                }
                contentType(ContentType.Application.Xml)
                setBody(makeMemberOfPayload(newMemberOf))
            }
            val res = response.bodyAsText()
            if (res.isBlank()) {
                return
            }
            throw RuntimeException("Set member of failed with following error: $res")
        } catch (e: Exception) {
            throw RuntimeException("Unknown error sending member-of request", e)
        }
    }

    override fun close() {
        client.close()
    }

    private suspend fun getShadowResourceOID(shadow: OID): OID {
        try {
            val url = shadowsEndPoint + "/${shadow}"
            // todo(Roman B) error handling
            val response = client.get(url) {
                with(config) {
                    basicAuth(userName, password)
                }
                accept(ContentType.Application.Json)
            }
            val shadowObject = Json.parseToJsonElement(response.bodyAsText())
            return shadowObject.jsonObject["shadow"]!!
                .jsonObject["resourceRef"]!!
                .jsonObject["oid"]!!
                .jsonPrimitive.content
        } catch (e: SerializationException) {
            throw RuntimeException("Midpoint shadow-resource-oid response is not json", e)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Midpoint shadow-resource-oid response has unexpected structure", e)
        } catch (e: Exception) {
            throw RuntimeException("Unknown error processing midpoint shadow-resource-oid response", e)
        }
    }

    private suspend fun getResourceName(resource: OID): String {
        try {
            val url = resourcesEndPoint + "/${resource}"
            // Todo(Roman B) repetition
            val response = client.get(url) {
                with(config) {
                    basicAuth(userName, password)
                }
                accept(ContentType.Application.Json)
            }
            val resourceObject = Json.parseToJsonElement(response.bodyAsText())
            return resourceObject.jsonObject["resource"]!!
                .jsonObject["name"]!!
                .jsonPrimitive.content
        } catch (e: SerializationException) {
            throw RuntimeException("Midpoint resource-name response is not json", e)
        } catch (e: IllegalArgumentException) {
            throw RuntimeException("Midpoint resource-name response has unexpected structure", e)
        } catch (e: Exception) {
            throw RuntimeException("Unknown error processing midpoint resource-name response", e)
        }
    }
}
