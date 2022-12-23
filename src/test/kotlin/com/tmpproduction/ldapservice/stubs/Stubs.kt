package com.tmpproduction.ldapservice.stubs

import com.tmpproduction.ldapservice.MidpointRepository
import com.tmpproduction.ldapservice.OID

open class UnavailableRepo : MidpointRepository {
    override suspend fun isAvailable(): Boolean {
        return false
    }

    override suspend fun getUserByFullName(fullName: String): OID? {
        throw NotImplementedError()
    }

    override suspend fun getUserShadows(user: OID): List<OID> {
        throw NotImplementedError()
    }

    override suspend fun shadowBelongsToLDAP(shadow: OID): Boolean {
        throw NotImplementedError()
    }

    override suspend fun setMemberOf(shadow: OID, newMemberOf: String) {
        throw NotImplementedError()
    }

    override fun close() = Unit

}

/**
 * Contains a user under fullname "target user"
 */
class SimpleRepo(
    private val userFullName: String
) : MidpointRepository {

    private val userOID: OID = "0000-0000-0000-0021"
    private val userLdapShadow: OID = "0000-0000-0020-0021"
    private val userShadows  = listOf(
        "0000-0000-0001-0021",
        userLdapShadow
    )

    override suspend fun isAvailable(): Boolean = true

    override suspend fun getUserByFullName(fullName: String): OID? {
        if (fullName == userFullName) {
            // Todo(Roman B.) check it is a valid OID just in case
            return userOID
        }
        return null
    }

    override suspend fun getUserShadows(user: OID): List<OID> {
        if (user == userOID) {
            return userShadows
        }
        return emptyList()
    }

    override suspend fun shadowBelongsToLDAP(shadow: OID): Boolean = shadow == userLdapShadow


    override suspend fun setMemberOf(shadow: OID, newMemberOf: String) {
        if (shadow != userLdapShadow) {
            throw IllegalArgumentException()
        }
    }

    override fun close() = Unit

}
