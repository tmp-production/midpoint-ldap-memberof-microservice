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
import io.ktor.util.reflect.*
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

val usersEndPoint: String by lazy { "$midpointApiUrl/users" }

val shadowsEndPoint: String by lazy { "$midpointApiUrl/shadows" }

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

lateinit var midpoint: Midpoint

@Serializable
data class UserShadowsRequestInfo(val userId: String)

@Serializable
data class ShadowAddMemberOfRequestInfo(val shadowId: String, val newValue: String)

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
            get("/midpoint-user-shadows") {
				// todo(Roman) some error-handling would be good
                val userInfo = call.receive<UserShadowsRequestInfo>()
                (HttpClient(CIO)).use { client ->
                    val response = client.get(usersEndPoint + "/${userInfo.userId}") {
                        basicAuth(userName, password)
                        accept(ContentType.Application.Json)
                    }
                    val user = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val shadowObjectsOrSingleShadowObject = user["user"]!!.jsonObject["linkRef"]!!
                    val res = if (shadowObjectsOrSingleShadowObject is JsonArray) {
                        shadowObjectsOrSingleShadowObject.jsonArray
                    } else {
                        JsonArray(listOf(shadowObjectsOrSingleShadowObject.jsonObject))
                    }
                    call.respond(res)
                }
            }
            get("/midpoint-member-of") {
                val requestInfo = call.receive<ShadowAddMemberOfRequestInfo>()
                (HttpClient(CIO)).use { client ->
                    val response = client.patch(shadowsEndPoint + "/${requestInfo.shadowId}") {
                        basicAuth(userName, password)
                        contentType(ContentType.Application.Xml)
                        setBody(makeMemberOfPayload(requestInfo.newValue))
                    }
                    val res = response.bodyAsText()
                    if (res.isBlank()) {
                        call.respond("{\"result\": \"ok\"}")
                    } else {
                        call.respond("{\"error\": \"${res}\"}")
                    }
                }
            }
        }
    }.start(wait = true)
}
