class RemoteMidpointRepository(
    private val host: String,
    private val port: Int,
    private val userName: String,
    private val password: String
) : MidpointRepository {
    override fun isAvailable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun getUserByFullName(fullName: String): OID? {
        TODO("Not yet implemented")
    }

    override fun getUserShadows(user: OID): List<OID> {
        TODO("Not yet implemented")
    }

    override fun shadowBelongsToLDAP(shadow: OID): Boolean {
        TODO("Not yet implemented")
    }

    override fun setMemberOf(shadow: OID, newMemberOf: String) {
        TODO("Not yet implemented")
    }
}
