package com.example.clubregistration.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController, role: String) {
    val context = LocalContext.current
    var showNotificationDialog by remember { mutableStateOf(false) }
    
    // Firebase references
    val auth = FirebaseAuth.getInstance()
    val userId = auth.currentUser?.uid ?: ""
    val database = FirebaseDatabase.getInstance()
    val userRef = database.getReference("Users").child(userId)
    val requestsRef = database.getReference("Requests")

    // State for Firebase data
    var joinedClubsCount by remember { mutableIntStateOf(0) }
    var userName by remember { mutableStateOf("") }
    val joinRequests = remember { mutableStateListOf<Pair<String, String>>() } // ID to Message

    // Listen for current user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            userRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    joinedClubsCount = snapshot.child("joinedClubsCount").getValue(Int::class.java) ?: 0
                    userName = snapshot.child("name").getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // Listen for join requests (for Patron)
    if (role == "Patron") {
        LaunchedEffect(Unit) {
            requestsRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    joinRequests.clear()
                    for (child in snapshot.children) {
                        val msg = child.child("message").getValue(String::class.java) ?: ""
                        joinRequests.add(child.key!! to msg)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (role == "Patron") "Patron Dashboard" else "Member Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF006400),
                    titleContentColor = Color.White
                ),
                actions = {
                    if (role == "Patron") {
                        BadgedBox(
                            badge = {
                                if (joinRequests.isNotEmpty()) {
                                    Badge { Text(joinRequests.size.toString()) }
                                }
                            },
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            IconButton(onClick = { showNotificationDialog = true }) {
                                Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
                            }
                        }
                    }
                    IconButton(onClick = {
                        auth.signOut()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = if (role == "Patron") "Manage your Clubs" else "Welcome, $userName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            if (role == "Member") {
                Text(
                    text = "Clubs Joined: $joinedClubsCount / 2",
                    fontSize = 16.sp,
                    color = if (joinedClubsCount >= 2) Color.Red else Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = if (role == "Patron") "Members" else "My Clubs",
                    icon = if (role == "Patron") Icons.Default.Person else Icons.Default.Home,
                    modifier = Modifier.weight(1f)
                )
                DashboardCard(
                    title = "Events",
                    icon = Icons.Default.Info,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (role == "Patron") {
                    DashboardCard(
                        title = "Notifications",
                        icon = Icons.Default.Notifications,
                        modifier = Modifier.weight(1f),
                        onClick = { showNotificationDialog = true }
                    )
                } else {
                    DashboardCard(
                        title = "Join New Club",
                        icon = Icons.Default.AddCircle,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (joinedClubsCount >= 2) {
                                Toast.makeText(context, "Limit reached!", Toast.LENGTH_SHORT).show()
                            } else {
                                val request = mapOf(
                                    "memberId" to userId,
                                    "message" to "$userName requested to join a club"
                                )
                                requestsRef.push().setValue(request)
                                Toast.makeText(context, "Request sent to Patron!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                DashboardCard(
                    title = "Settings",
                    icon = Icons.Default.Settings,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                title = { Text("Join Requests") },
                text = {
                    if (joinRequests.isEmpty()) {
                        Text("No pending requests.")
                    } else {
                        LazyColumn {
                            items(joinRequests) { (reqId, message) ->
                                ListItem(
                                    headlineContent = { Text(message) },
                                    trailingContent = {
                                        Button(onClick = {
                                            requestsRef.child(reqId).get().addOnSuccessListener { snapshot ->
                                                val memberId = snapshot.child("memberId").getValue(String::class.java) ?: ""
                                                if (memberId.isNotEmpty()) {
                                                    val memberCountRef = database.getReference("Users").child(memberId).child("joinedClubsCount")
                                                    memberCountRef.get().addOnSuccessListener { countSnapshot ->
                                                        val currentCount = countSnapshot.getValue(Int::class.java) ?: 0
                                                        memberCountRef.setValue(currentCount + 1)
                                                        requestsRef.child(reqId).removeValue()
                                                        Toast.makeText(context, "Approved!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Approve")
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNotificationDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(120.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color(0xFF006400))
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontWeight = FontWeight.Medium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomePreview() {
    HomeScreen(rememberNavController(), "Member")
}
