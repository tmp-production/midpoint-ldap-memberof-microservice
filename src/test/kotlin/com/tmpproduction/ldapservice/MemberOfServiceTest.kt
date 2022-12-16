package com.tmpproduction.ldapservice

import com.tmpproduction.ldapservice.impl.SequenceProviderService
import com.tmpproduction.ldapservice.stubs.SimpleRepo
import com.tmpproduction.ldapservice.stubs.UnavailableRepo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class MemberOfServiceTest {

    @Test
    fun serviceIsUnavailable() {
        val providerService = SequenceProviderService {
            runBlocking {
                assertFalse(isAvailable())
            }
        }
        val memberOfService = MemberOfService(
            requestsProviderService = providerService,
            midpointStateProvider = UnavailableRepo()
        )
        memberOfService.start()
    }

    @Test
    fun simpleUseCase() {
        val userName = "bill gates"
        val newMemberOf = "new member of"
        val providerService = SequenceProviderService {
            runBlocking {
                assertTrue(isAvailable())
                val userOid = getUserByFullName(userName)!!
                val shadows = getUserShadows(userOid)
                assertTrue(shadows.isNotEmpty())
                val ldapShadow = shadows.filter { shadowBelongsToLDAP(it) }
                assertTrue(ldapShadow.size == 1)
                setMemberOf(ldapShadow[0], newMemberOf)
            }
        }
        val memberOfService = MemberOfService(
            requestsProviderService = providerService,
            midpointStateProvider = SimpleRepo(userName)
        )
        memberOfService.start()
    }

}
