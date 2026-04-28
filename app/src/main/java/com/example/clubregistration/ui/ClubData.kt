package com.example.clubregistration.ui

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf

data class User(
    val email: String,
    val name: String,
    val role: String,
    val club: String,
    val password: String
)

// Simple object to simulate shared state/database
object ClubData {
    var joinedClubsCount = mutableIntStateOf(1)
    val joinRequests = mutableStateListOf<String>()
    val availableClubs = mutableStateListOf(
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
