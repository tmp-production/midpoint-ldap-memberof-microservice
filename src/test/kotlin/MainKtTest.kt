import kotlin.test.Test

class MainKtTest {

    @Test
    fun testMakeMemberOfPayload() {
        val target = """
        <?xml version="1.0" encoding="UTF-8"?>
        <objectModification
          xmlns='http://midpoint.evolveum.com/xml/ns/public/common/api-types-3'
          xmlns:c='http://midpoint.evolveum.com/xml/ns/public/common/common-3'
          xmlns:t="http://prism.evolveum.com/xml/ns/public/types-3"
          xmlns:ri="http://midpoint.evolveum.com/xml/ns/public/resource/instance-3">
          <itemDelta>
            <t:modificationType>replace</t:modificationType>
              <t:path>c:attributes/ri:memberOf</t:path>
              <t:value>10</t:value>
          </itemDelta>
        </objectModification>
    """.trimIndent()
        val result = makeMemberOfPayload("10")

        assert(target == result)
    }
}