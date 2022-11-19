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
    fun isAvailable(): Boolean
    fun getUserByFullName(fullName: String): OID?
    fun getUserShadows(user: OID): List<OID>
    fun shadowBelongsToLDAP(shadow: OID): Boolean
    fun setMemberOf(shadow: OID, newMemberOf: String)
}

/**
 * A service which handles requests in any form and uses MemberOfService
 * to respond to them
 */
interface RequestsProviderService {
    fun start(target: MidpointRepository)
}
