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
import androidx.compose.ui.platform.LocalInspectionMode
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
fun HomeScreen(navController: NavController, role: String, initialClub: String = "", initialName: String = "") {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    
    // Dialog States
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showEventsDialog by remember { mutableStateOf(false) }
    var showJoinClubDialog by remember { mutableStateOf(false) }
    var showMyClubsDialog by remember { mutableStateOf(false) }
    
    // Firebase references
    val auth = remember(isPreview) { if (isPreview) null else FirebaseAuth.getInstance() }
    val userId = remember(auth) { auth?.currentUser?.uid ?: "" }
    val database = remember(isPreview) { if (isPreview) null else FirebaseDatabase.getInstance() }
    val userRef = remember(userId, database) { if (userId.isEmpty()) null else database?.getReference("Users")?.child(userId) }
    val requestsRef = remember(database) { database?.getReference("Requests") }
    val usersRef = remember(database) { database?.getReference("Users") }

    // State for Firebase data
    var joinedClubsCount by remember { mutableIntStateOf(if (isPreview) 1 else 0) }
    var userName by remember { mutableStateOf(if (isPreview) "Preview User" else initialName) }
    var patronClub by remember { mutableStateOf(if (isPreview) "Science Club" else initialClub) }
    val memberClubs = remember { mutableStateListOf<String>() }
    val joinRequests = remember { mutableStateListOf<Pair<String, String>>() }
    val clubMembers = remember { mutableStateListOf<Pair<String, String>>() } 
    val events = remember { mutableStateListOf<String>() }

    // Initial Preview Data
    if (isPreview) {
        if (role == "Patron") {
            joinRequests.add("1" to "John Doe requested to join Science Club")
            clubMembers.add("Alice Smith" to "alice@example.com")
        } else {
            memberClubs.add("Science Club")
        }
        events.add("Annual Science Fair - Oct 12")
    }

    // Listen for current user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            userRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    joinedClubsCount = snapshot.child("joinedClubsCount").getValue(Int::class.java) ?: 0
                    userName = snapshot.child("name").getValue(String::class.java) ?: initialName
                    val primaryClub = snapshot.child("club").getValue(String::class.java) ?: ""
                    
                    memberClubs.clear()
                    if (primaryClub.isNotEmpty()) {
                        memberClubs.add(primaryClub)
                        patronClub = primaryClub
                    }
                    
                    // Also fetch extra clubs if they exist in a list
                    snapshot.child("joinedClubs").children.forEach { child ->
                        val c = child.getValue(String::class.java) ?: ""
                        if (c.isNotEmpty() && !memberClubs.contains(c)) {
                            memberClubs.add(c)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // Patron logic: Listen for Requests and Members
    if (role == "Patron") {
        LaunchedEffect(patronClub) {
            if (patronClub.isNotEmpty()) {
                requestsRef?.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        joinRequests.clear()
                        for (child in snapshot.children) {
                            if (child.child("clubName").getValue(String::class.java) == patronClub) {
                                joinRequests.add(child.key!! to (child.child("message").getValue(String::class.java) ?: ""))
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })

                usersRef?.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        clubMembers.clear()
                        for (child in snapshot.children) {
                            val roleInDb = child.child("role").getValue(String::class.java) ?: ""
                            val primaryClubInDb = child.child("club").getValue(String::class.java) ?: ""
                            var isMemberOfClub = primaryClubInDb == patronClub
                            
                            if (!isMemberOfClub) {
                                child.child("joinedClubs").children.forEach { cChild ->
                                    if (cChild.getValue(String::class.java) == patronClub) isMemberOfClub = true
                                }
                            }

                            if (isMemberOfClub && roleInDb == "Member") {
                                val name = child.child("name").getValue(String::class.java) ?: "Unknown"
                                val email = child.child("email").getValue(String::class.java) ?: ""
                                clubMembers.add(name to email)
                            }
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {}
                })
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (role == "Patron") "Patron Dashboard ($patronClub)" else "Member Dashboard") },
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
                        auth?.signOut()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White)
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
                    text = "Clubs Joined: ${memberClubs.size} / 2",
                    fontSize = 16.sp,
                    color = if (memberClubs.size >= 2) Color.Red else Color.Gray,
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
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        if (role == "Patron") showMembersDialog = true 
                        else showMyClubsDialog = true
                    }
                )
                DashboardCard(
                    title = "Events",
                    icon = Icons.Default.Info,
                    modifier = Modifier.weight(1f),
                    onClick = { showEventsDialog = true }
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
                            if (memberClubs.size >= 2) {
                                Toast.makeText(context, "Limit reached!", Toast.LENGTH_SHORT).show()
                            } else {
                                showJoinClubDialog = true
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

        // Dialogs...
        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                title = { Text("Join Requests for $patronClub") },
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
                                            requestsRef?.child(reqId)?.get()?.addOnSuccessListener { snapshot ->
                                                val mId = snapshot.child("memberId").getValue(String::class.java) ?: ""
                                                val requestedClub = snapshot.child("clubName").getValue(String::class.java) ?: ""
                                                if (mId.isNotEmpty() && requestedClub.isNotEmpty()) {
                                                    val mUserRef = database?.getReference("Users")?.child(mId)
                                                    mUserRef?.get()?.addOnSuccessListener { uSnap ->
                                                        val currentJoinedCount = uSnap.child("joinedClubsCount").getValue(Int::class.java) ?: 0
                                                        val joinedList = uSnap.child("joinedClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                                        
                                                        if (!joinedList.contains(requestedClub)) {
                                                            joinedList.add(requestedClub)
                                                            mUserRef.child("joinedClubs").setValue(joinedList)
                                                            mUserRef.child("joinedClubsCount").setValue(currentJoinedCount + 1)
                                                        }
                                                        requestsRef.child(reqId).removeValue()
                                                        Toast.makeText(context, "Approved!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        }) { Text("Approve") }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showNotificationDialog = false }) { Text("Close") } }
            )
        }

        if (showMembersDialog) {
            AlertDialog(
                onDismissRequest = { showMembersDialog = false },
                title = { Text("Members of $patronClub") },
                text = {
                    if (clubMembers.isEmpty()) {
                        Text("No members in this club.")
                    } else {
                        LazyColumn {
                            items(clubMembers) { (name, email) ->
                                ListItem(
                                    headlineContent = { Text(name) },
                                    supportingContent = { Text(email) },
                                    leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMembersDialog = false }) { Text("Close") } }
            )
        }

        if (showEventsDialog) {
            AlertDialog(
                onDismissRequest = { showEventsDialog = false },
                title = { Text("Club Events") },
                text = {
                    if (events.isEmpty()) {
                        Text("No upcoming events.")
                    } else {
                        LazyColumn {
                            items(events) { event ->
                                ListItem(
                                    headlineContent = { Text(event) },
                                    leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showEventsDialog = false }) { Text("Close") } }
            )
        }

        if (showMyClubsDialog) {
            AlertDialog(
                onDismissRequest = { showMyClubsDialog = false },
                title = { Text("My Clubs") },
                text = {
                    LazyColumn {
                        items(memberClubs) { club ->
                            ListItem(
                                headlineContent = { Text(club) },
                                leadingContent = { Icon(Icons.Default.Home, contentDescription = null, tint = Color(0xFF006400)) }
                            )
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMyClubsDialog = false }) { Text("Close") } }
            )
        }

        if (showJoinClubDialog) {
            var expandedDropdown by remember { mutableStateOf(false) }
            val availableToJoin = ClubData.availableClubs.filter { !memberClubs.contains(it) }
            var selectedJoinClub by remember { mutableStateOf(if (availableToJoin.isNotEmpty()) availableToJoin[0] else "") }

            AlertDialog(
                onDismissRequest = { showJoinClubDialog = false },
                title = { Text("Join a New Club") },
                text = {
                    Column {
                        Text("Select a club to send a join request:")
                        Spacer(modifier = Modifier.height(16.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedDropdown,
                            onExpandedChange = { expandedDropdown = !expandedDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedJoinClub,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = expandedDropdown,
                                onDismissRequest = { expandedDropdown = false }
                            ) {
                                availableToJoin.forEach { club ->
                                    DropdownMenuItem(
                                        text = { Text(club) },
                                        onClick = {
                                            selectedJoinClub = club
                                            expandedDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (selectedJoinClub.isNotEmpty()) {
                            val request = mapOf(
                                "memberId" to userId,
                                "clubName" to selectedJoinClub,
                                "message" to "$userName requested to join $selectedJoinClub"
                            )
                            requestsRef?.push()?.setValue(request)
                            Toast.makeText(context, "Request sent to Patron!", Toast.LENGTH_SHORT).show()
                            showJoinClubDialog = false
                        }
                    }) { Text("Send Request") }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinClubDialog = false }) { Text("Cancel") }
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

@Preview(showBackground = true)
@Composable
fun PatronHomePreview() {
    HomeScreen(rememberNavController(), "Patron")
}
