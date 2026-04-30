package com.example.clubregistration.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf

data class User(
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val role: String = "",
    val club: String = "", // This can be the primary club (for Patron) or first club (for Member)
    val password: String = "",
    val joinedClubsCount: Int = 0,
    val joinedClubs: List<String> = emptyList()
)

data class Event(
    val title: String = "",
    val clubName: String = "",
    val date: String = "",
    val description: String = ""
)

// Simple object to simulate shared state/database
object ClubData {
    var joinedClubsCount = mutableIntStateOf(1)
    val joinRequests = mutableStateListOf<String>()
    val availableClubs = listOf(
        "Science Club",
        "Drama Club",
        "Sports Club",
        "Music Club",
        "Coding Club",
        "Art Club",
        "Chess Club",
        "Debate Club",
        "Environment Club",
        "Photography Club"
    )
    val registeredUsers = mutableStateListOf<User>()
}
