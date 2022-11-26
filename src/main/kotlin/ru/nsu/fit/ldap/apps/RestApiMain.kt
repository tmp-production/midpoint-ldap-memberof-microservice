package ru.nsu.fit.ldap.apps

import ru.nsu.fit.ldap.MemberOfService
import ru.nsu.fit.ldap.impl.RemoteMidpointRepository
import ru.nsu.fit.ldap.impl.RestApiProviderService

/**
 * args:
 *  - port on which to listen
 *  - midpoint host (ip)
 *  - midpoint port
 *  - midpoint username
 *  - midpoint password
 */
fun main(args: Array<String>) {
    if (args.size != 5) {
        throw IllegalArgumentException("wrong amount of args")
    }
    val (port, midpointHost, midpointPort, userName, password) = args
    val service = MemberOfService(
        requestsProviderService = RestApiProviderService(port.toInt()),
        midpointStateProvider = RemoteMidpointRepository(
            midpointHost,
            midpointPort.toInt(),
            userName,
            password
        )
    )
    service.start()
}
