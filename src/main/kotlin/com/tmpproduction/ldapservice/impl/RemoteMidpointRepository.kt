package com.tmpproduction.ldapservice.impl

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*
import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.OID

class RemoteMidpointRepository(
    private val host: String,
    private val port: Int,
    private val userName: String,
    private val password: String
) : MidpointRepository {

    private val midpointApiUrl: String by lazy {
        "http://${host}:${port}/midpoint/ws/rest"
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
        TODO("Not yet implemented")
    }

    override suspend fun getUserByFullName(fullName: String): OID? {
        // TODO(Roman B) we could use some caching here, because
        //  this operation is quite costly. Midpoint sends too much
        //  information, including history of editing(?)
        //  Possible solution: cache OR bake a better request, so that
        //  the user is found on midpoint server
        (HttpClient(CIO)).use { client ->
            val response = client.post("$usersEndPoint/search/") {
                basicAuth(userName, password)
                contentType(ContentType.Application.Xml)
                accept(ContentType.Application.Json)
                setBody(makeQueryAllUsersPayload())
            }
            val users = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["object"]!!.jsonObject["object"]!!.jsonArray
            val usersFullNamesToOID: List<Pair<String?, OID>> = users.map { it.jsonObject }.map {
                it["fullName"]?.jsonPrimitive?.contentOrNull to it["oid"]!!.jsonPrimitive.content
            }
            return usersFullNamesToOID.find { it.first == fullName }?.second
        }
    }

    override suspend fun getUserShadows(user: OID): List<OID> {
        (HttpClient(CIO)).use { client ->
            val response = client.get("$usersEndPoint/$user") {
                basicAuth(userName, password)
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
        }
    }

    override suspend fun shadowBelongsToLDAP(shadow: OID): Boolean {
        return getResourceName(getShadowResourceOID(shadow)) == "LDAP"
    }

    override suspend fun setMemberOf(shadow: OID, newMemberOf: String) {
        (HttpClient(CIO)).use { client ->
            val response = client.patch("$shadowsEndPoint/$shadow") {
                basicAuth(userName, password)
                contentType(ContentType.Application.Xml)
                setBody(makeMemberOfPayload(newMemberOf))
            }
            val res = response.bodyAsText()
            if (res.isBlank()) {
                return
            }
            throw RuntimeException("Set member of failed with following error: $res")
        }
    }

    private suspend fun getShadowResourceOID(shadow: OID): OID {
        val url = shadowsEndPoint + "/${shadow}"
        // todo(Roman B) error handling
        (HttpClient(CIO)).use { client ->
            val response = client.get(url) {
                basicAuth(userName, password)
                accept(ContentType.Application.Json)
            }
            val shadowObject = Json.parseToJsonElement(response.bodyAsText())
            return shadowObject.jsonObject["shadow"]!!
                .jsonObject["resourceRef"]!!
                .jsonObject["oid"]!!
                .jsonPrimitive.content
        }
    }

    private suspend fun getResourceName(resource: OID): String {
        val url = resourcesEndPoint + "/${resource}"
        // todo(Roman B) error handling
        (HttpClient(CIO)).use { client ->
            // Todo(Roman B) repetition
            val response = client.get(url) {
                basicAuth(userName, password)
                accept(ContentType.Application.Json)
            }
            val resourceObject = Json.parseToJsonElement(response.bodyAsText())
            return resourceObject.jsonObject["resource"]!!
                .jsonObject["name"]!!
                .jsonPrimitive.content
        }
    }
}
