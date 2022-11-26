package com.tmpproduction.ldapservice

typealias OID = String

class AuthenticationError(msg: String) : RuntimeException(msg)

/**
 * Our main microservice
 */
class MemberOfService(
    private val requestsProviderService: RequestsProviderService,
    private val midpointStateProvider: MidpointRepository
) {
    fun start() = requestsProviderService.start(midpointStateProvider)
}

/**
 * Provider which can get state from midpoint
 */
interface MidpointRepository {
    /**
     * @throws AuthenticationError if service is available, but credentials are bad
     */
    suspend fun isAvailable(): Boolean
    suspend fun getUserByFullName(fullName: String): OID?
    suspend fun getUserShadows(user: OID): List<OID>
    suspend fun shadowBelongsToLDAP(shadow: OID): Boolean
    suspend fun setMemberOf(shadow: OID, newMemberOf: String)
}

/**
 * A service which handles requests in any form and uses ru.nsu.fit.ldap.MemberOfService
 * to respond to them
 */
interface RequestsProviderService {
    fun start(target: MidpointRepository)
}
