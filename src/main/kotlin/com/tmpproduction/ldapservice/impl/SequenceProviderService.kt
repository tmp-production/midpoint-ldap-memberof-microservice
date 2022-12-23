package com.tmpproduction.ldapservice.impl

import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.RequestsProviderService

class SequenceProviderService(
    private inline val task: MidpointRepository.() -> Unit
) : RequestsProviderService {
    override fun start(target: MidpointRepository) = task(target)
    override fun close() = Unit
}