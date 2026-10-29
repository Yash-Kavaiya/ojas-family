package com.ojashealing.family.domain

// Free user level, computed on the server from active days and Moksha Points (config/levels)
enum class UserLevel(val rank: Int) {
    L1(1), L2(2), L3(3), L4(4);

    companion object {
        fun fromRank(rank: Int): UserLevel = entries.firstOrNull { it.rank == rank } ?: L1
    }
}

// Paid member level, derived on the server from continuous paid months (1, 3, 6)
enum class MemberLevel(val rank: Int) {
    None(0), M1(1), M2(2), M3(3);

    companion object {
        fun fromRank(rank: Int): MemberLevel = entries.firstOrNull { it.rank == rank } ?: None
    }
}

enum class Role {
    Member, SeniorMember, Swamiji, Admin;

    companion object {
        // Firestore stores the role as a lowercase id, e.g. "senior_member"
        fun fromId(id: String?): Role = when (id) {
            "senior_member" -> SeniorMember
            "swamiji" -> Swamiji
            "admin" -> Admin
            else -> Member
        }
    }
}

data class Viewer(val userLevel: UserLevel, val memberLevel: MemberLevel, val role: Role)
