package com.ojashealing.family.ui.ask

import com.ojashealing.family.data.user.MemberRow
import com.ojashealing.family.domain.Role
import org.junit.Assert.assertEquals
import org.junit.Test

class FindMemberTest {

    private fun row(uid: String, role: Role) = MemberRow(uid, uid, "", role, 1, 0, 0)

    @Test
    fun staffFindMembersAndSeniorMembersOnly() {
        val rows = listOf(
            row("me", Role.Swamiji),
            row("m1", Role.Member),
            row("s1", Role.SeniorMember),
            row("a1", Role.Admin),
            row("sw2", Role.Swamiji),
        )
        assertEquals(listOf("m1", "s1"), reachable(rows, myUid = "me").map { it.uid })
    }

    @Test
    fun neverListsMyself() {
        assertEquals(emptyList<String>(), reachable(listOf(row("me", Role.Member)), myUid = "me").map { it.uid })
    }
}
