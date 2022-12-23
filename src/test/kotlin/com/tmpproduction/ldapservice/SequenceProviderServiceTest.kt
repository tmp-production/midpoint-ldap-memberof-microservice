package com.tmpproduction.ldapservice

import com.tmpproduction.ldapservice.impl.RemoteMidpointRepository
import com.tmpproduction.ldapservice.impl.SequenceProviderService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class SequenceProviderServiceTest {

    @Test
    fun test() {
        val midpoint = mockk<RemoteMidpointRepository>()
        with(midpoint) {
            coEvery { isAvailable() } returns false
            coEvery { getUserByFullName("") } returns ""
            coEvery { setMemberOf("", "") } just Runs
            coEvery { getUserShadows("") } returns listOf()
            coEvery { shadowBelongsToLDAP("") } returns true
        }
        val sequenceProviderService = SequenceProviderService {
            runBlocking {
                isAvailable()
                getUserByFullName("")
                setMemberOf("", "")
                getUserShadows("")
                shadowBelongsToLDAP("")
            }
        }
        MemberOfService(
            requestsProviderService = sequenceProviderService,
            midpointStateProvider = midpoint
        ).start()
        coVerifySequence {
            with(midpoint) {
                isAvailable()
                getUserByFullName(any())
                setMemberOf(any(), any())
                getUserShadows(any())
                shadowBelongsToLDAP(any())
            }
        }
        sequenceProviderService.close()
    }

}