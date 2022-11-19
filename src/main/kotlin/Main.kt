import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

data class Midpoint(
    val host: String,
    val port: Int,
    val userName: String,
    val password: String
)

val midpointApiUrl: String by lazy {
    with(midpoint) {
        "http://${host}:${port}/midpoint/ws/rest"
    }
}

typealias OID = String

val usersEndPoint: String by lazy { "$midpointApiUrl/users" }
val shadowsEndPoint: String by lazy { "$midpointApiUrl/shadows" }
val resourcesEndPoint: String by lazy { "$midpointApiUrl/resources" }

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

lateinit var midpoint: Midpoint

@Serializable
data class UserShadowsRequestInfo(val userId: String)

@Serializable
data class ShadowAddMemberOfRequestInfo(val shadowId: String, val newValue: String)

@Serializable
data class ShadowOIDRequestInfo(val shadowOID: OID)

@Serializable
data class MidpointUserByFullNameRequestInfo(val fullName: String)

/**
 * args:
 *  - port on which to run
 *  - midpoint host (ip)
 *  - midpoint port
 *  - api username
 *  - api user password
 */
fun main(args: Array<String>) {
    if (args.size != 5) {
        throw IllegalArgumentException("wrong amount of args")
    }
    val (selectedPortString, host, portString, userName, password) = args
    midpoint = Midpoint(host, portString.toInt(), userName, password)

    // todo validate midpoint exists and credentials are ok

    embeddedServer(Netty, port = selectedPortString.toInt()) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
            })
        }
        routing {
            get("/") {
                call.respondText("This is an ldap service")
            }
            get("/midpoint-user-id-by-fullname") {
                val fullName = call.receive<MidpointUserByFullNameRequestInfo>().fullName
                val maybeOid = getMidpointUserByFullName(fullName)
                val res = maybeOid?.let { "\"${it}\"" }
                call.respondText("{\"result\": $res}")
            }
            get("/midpoint-user-shadows") {
                val userInfo = call.receive<UserShadowsRequestInfo>()
                call.respond(getUserShadows(userInfo))
            }
            get("/shadow-is-ldap") {
                val shadowOID = call.receive<ShadowOIDRequestInfo>().shadowOID
                call.respond("{\"result\" : ${isLdapShadow(shadowOID)} }")
            }
            patch("/midpoint-member-of") {
                val requestInfo = call.receive<ShadowAddMemberOfRequestInfo>()
                call.respond(changeMemberOf(requestInfo))
            }
        }
    }.start(wait = true)
}

/**
 * @return null, if no such user was found. Otherwise, return midpoint user OID
 */
suspend fun getMidpointUserByFullName(fullName: String): OID? {
    // TODO(Roman B) we could use some caching here, because
    //  this operation is quite costly. Midpoint sends too much
    //  information, including history of editing(?)
    //  Possible solution: cache OR bake a better request, so that
    //  the user is found on midpoint server
    (HttpClient(CIO)).use { client ->
        val response = client.post("$usersEndPoint/search/") {
            with(midpoint) {
                basicAuth(userName, password)
            }
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

/**
 * @return list of user shadows ids
 */
suspend fun getUserShadows(userInfo: UserShadowsRequestInfo): List<String> {
    (HttpClient(CIO)).use { client ->
        val response = client.get(usersEndPoint + "/${userInfo.userId}") {
            with(midpoint) {
                basicAuth(userName, password)
            }
            accept(ContentType.Application.Json)
        }
        val user = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val userKey = "user"
        if (!user.containsKey(userKey)) {
            throw IllegalArgumentException(response.bodyAsText())
        }
        val shadowObjectsOrSingleShadowObject = user["user"]!!.jsonObject["linkRef"]!!
        val res = if (shadowObjectsOrSingleShadowObject is JsonArray) {
            shadowObjectsOrSingleShadowObject.jsonArray
        } else {
            JsonArray(listOf(shadowObjectsOrSingleShadowObject.jsonObject))
        }
        // todo(Roman) warn if something inside is not string
        return res.jsonArray.map { it.jsonObject["oid"]!!.jsonPrimitive.content }
    }
}

/**
 * @return response. if error, return error
 */
suspend fun changeMemberOf(requestInfo: ShadowAddMemberOfRequestInfo): String {
    (HttpClient(CIO)).use { client ->
        val response = client.patch(shadowsEndPoint + "/${requestInfo.shadowId}") {
            with(midpoint) {
                basicAuth(userName, password)
            }
            contentType(ContentType.Application.Xml)
            setBody(makeMemberOfPayload(requestInfo.newValue))
        }
        val res = response.bodyAsText()
        if (res.isBlank()) {
            return "{\"result\": \"ok\"}"
        } else {
            return "{\"error\": \"${res}\"}"
        }
    }
}

private suspend fun getShadowResourceOID(shadowOID: OID): OID {
    val url = shadowsEndPoint + "/${shadowOID}"
    // todo(Roman B) error handling
    (HttpClient(CIO)).use { client ->
        val response = client.get(url) {
            with(midpoint) {
                basicAuth(userName, password)
            }
            accept(ContentType.Application.Json)
        }
        val shadowObject = Json.parseToJsonElement(response.bodyAsText())
        return shadowObject.jsonObject["shadow"]!!
            .jsonObject["resourceRef"]!!
            .jsonObject["oid"]!!
            .jsonPrimitive.content
    }
}

suspend fun getResourceName(resource: OID): String {
    val url = resourcesEndPoint + "/${resource}"
    // todo(Roman B) error handling
    (HttpClient(CIO)).use { client ->
        // Todo(Roman B) repetition
        val response = client.get(url) {
            with(midpoint) {
                basicAuth(userName, password)
            }
            accept(ContentType.Application.Json)
        }
        val resourceObject = Json.parseToJsonElement(response.bodyAsText())
        return resourceObject.jsonObject["resource"]!!
            .jsonObject["name"]!!
            .jsonPrimitive.content
    }
}

suspend fun isLdapShadow(shadow: OID): Boolean {
    return getResourceName(getShadowResourceOID(shadow)) == "LDAP"
}
