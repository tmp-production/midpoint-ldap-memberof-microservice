package com.tmpproduction.ldapservice

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class Midpoint(val host: String, val port: Int)
lateinit var midpoint: Midpoint

@Serializable
data class UserShadowsRequestInfo(val userId: String)

/**
 * args: port on which to run, midpoint host (ip), midpoint port
 */
fun main(args: Array<String>) {
    if (args.size != 3) {
        throw IllegalArgumentException("wrong amount of args")
    }
    val selectedPort = args[0].toInt()
    midpoint = Midpoint(
        host = args[1],
        port = args[2].toInt()
    )

    embeddedServer(Netty, port = selectedPort) {
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
                call.respondText("info: ${userInfo.userId}")
            }
        }
    }.start(wait = true)
}
