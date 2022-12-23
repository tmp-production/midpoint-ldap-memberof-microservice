package com.tmpproduction.ldapservice.impl

import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.OID
import com.tmpproduction.ldapservice.RequestsProviderService
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class UserShadowsRequestInfo(val userId: String)

@Serializable
private data class ShadowAddMemberOfRequestInfo(val shadowId: String, val newValue: String)

@Serializable
private data class ShadowOIDRequestInfo(val shadowOID: OID)

@Serializable
private data class MidpointUserByFullNameRequestInfo(val fullName: String)

class RestApiProviderService(private val port: Int) : RequestsProviderService {

    override fun start(target: MidpointRepository) {
        embeddedServer(Netty, port = port) {
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
                    val maybeOid = target.getUserByFullName(fullName)
                    val res = maybeOid?.let { "\"${it}\"" }
                    call.respondText("{\"result\": $res}")
                }
                get("/midpoint-user-shadows") {
                    val userInfo = call.receive<UserShadowsRequestInfo>()
                    call.respond(target.getUserShadows(userInfo.userId))
                }
                get("/shadow-is-ldap") {
                    val shadowOID = call.receive<ShadowOIDRequestInfo>().shadowOID
                    call.respond("{\"result\" : ${target.shadowBelongsToLDAP(shadowOID)} }")
                }
                patch("/midpoint-member-of") {
                    val requestInfo = call.receive<ShadowAddMemberOfRequestInfo>()
                    call.respond(target.setMemberOf(requestInfo.shadowId, requestInfo.newValue))
                }
            }
        }.start(wait = true)
    }

    override fun close() = Unit

}
