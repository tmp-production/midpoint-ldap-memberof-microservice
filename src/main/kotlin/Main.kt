package com.tmpproduction.ldapservice

import io.ktor.client.*
import io.ktor.client.call.*
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
import io.netty.handler.logging.LogLevel
import kotlinx.serialization.json.*

data class Midpoint(
    val host: String,
    val port: Int,
    val userName: String,
    val password: String
)

val userInfoEndPoint: String get() =
    with(midpoint){
        "http://${host}:${port}/midpoint/ws/rest/users"
    }

lateinit var midpoint: Midpoint

@Serializable
data class UserShadowsRequestInfo(val userId: String)

@Serializable
data class ShadowAddMemberOfRequestInfo(val userId: String, val newValue: String)

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
                val userInfo = call.receive<UserShadowsRequestInfo>()
                (HttpClient(CIO)).use { client ->
                    val response = client.get(userInfoEndPoint + "/${userInfo.userId}") {
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

                (HttpClient(CIO)).use { client ->
                    println("before request")
                    val response = client.get(userInfoEndPoint + "/${userInfo.userId}") {
                        basicAuth(userName, password)
                        accept(ContentType.Application.Json)
                    }
                    println("after request")
                    println(response.bodyAsText())
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
        }
    }.start(wait = true)
}
