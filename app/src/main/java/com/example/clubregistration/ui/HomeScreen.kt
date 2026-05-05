package com.example.clubregistration.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val primaryGreen = Color(0xFF006400)
    
    // Dialog States
    var showNotificationDialog by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showEventsDialog by remember { mutableStateOf(false) }
    var showJoinClubDialog by remember { mutableStateOf(false) }
    var showMyClubsDialog by remember { mutableStateOf(false) }
    var showAddEventDialog by remember { mutableStateOf(false) }
    
    // Firebase references
    val auth = remember(isPreview) { if (isPreview) null else FirebaseAuth.getInstance() }
    val userId = remember(auth) { auth?.currentUser?.uid ?: "" }
    val database = remember(isPreview) { if (isPreview) null else FirebaseDatabase.getInstance() }
    val userRef = remember(userId, database) { if (userId.isEmpty()) null else database?.getReference("Users")?.child(userId) }
    val requestsRef = remember(database) { database?.getReference("Requests") }
    val usersRef = remember(database) { database?.getReference("Users") }
    val eventsRef = remember(database) { database?.getReference("Events") }

    // State for Firebase data
    var userName by remember { mutableStateOf(if (isPreview) "Preview User" else initialName) }
    var patronClub by remember { mutableStateOf(if (isPreview) "Science Club" else initialClub) }
    val memberClubs = remember { mutableStateListOf<String>() }
    val pendingClubs = remember { mutableStateListOf<String>() } // Clubs waiting for member to register
    val joinRequests = remember { mutableStateListOf<Pair<String, String>>() }
    val clubMembers = remember { mutableStateListOf<Triple<String, String, String>>() } // name, email, phone
    val events = remember { mutableStateListOf<Event>() }

    // Initial Preview Data
    if (isPreview) {
        if (role == "Patron") {
            joinRequests.add("1" to "John Doe requested to join Science Club")
            clubMembers.add(Triple("Alice Smith", "alice@example.com", "0712345678"))
        } else {
            memberClubs.add("Science Club")
            pendingClubs.add("Drama Club")
        }
        events.add(Event("Annual Science Fair", "Science Club", "Oct 12", "All welcome"))
    }

    // Listen for current user data
    LaunchedEffect(userId) {
        if (userId.isNotEmpty()) {
            userRef?.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    userName = snapshot.child("name").getValue(String::class.java) ?: initialName
                    val primaryClub = snapshot.child("club").getValue(String::class.java) ?: ""
                    
                    memberClubs.clear()
                    if (primaryClub.isNotEmpty()) {
                        memberClubs.add(primaryClub)
                        patronClub = primaryClub
                    }
                    
                    snapshot.child("joinedClubs").children.forEach { child ->
                        val c = child.getValue(String::class.java) ?: ""
                        if (c.isNotEmpty() && !memberClubs.contains(c)) {
                            memberClubs.add(c)
                        }
                    }

                    pendingClubs.clear()
                    snapshot.child("pendingClubs").children.forEach { child ->
                        val c = child.getValue(String::class.java) ?: ""
                        if (c.isNotEmpty()) pendingClubs.add(c)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // Events listener
    LaunchedEffect(role, patronClub, memberClubs.size) {
        eventsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                events.clear()
                for (child in snapshot.children) {
                    val event = child.getValue(Event::class.java)
                    if (event != null) {
                        // Patrons see their own club events, Members see events for clubs they joined
                        if (role == "Patron") {
                            if (event.clubName == patronClub) events.add(event)
                        } else {
                            if (memberClubs.contains(event.clubName)) events.add(event)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
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
                                val phone = child.child("phone").getValue(String::class.java) ?: "No Phone"
                                clubMembers.add(Triple(name, email, phone))
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
                title = { Text(if (role == "Patron") "Patron Dashboard" else "Member Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryGreen,
                    titleContentColor = Color.White
                ),
                actions = {
                    val badgeCount = if (role == "Patron") joinRequests.size else pendingClubs.size
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge { Text(badgeCount.toString()) }
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        IconButton(onClick = { showNotificationDialog = true }) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = Color.White)
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                text = if (role == "Patron") "Club: $patronClub" else "Welcome, $userName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = primaryGreen,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (role == "Member") {
                Text(
                    text = "Clubs Joined: ${memberClubs.size} / 2",
                    fontSize = 16.sp,
                    color = if (memberClubs.size >= 2) Color.Red else Color.Gray,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = if (role == "Patron") "Members (${clubMembers.size})" else "My Clubs",
                    icon = if (role == "Patron") Icons.Default.Person else Icons.Default.Home,
                    primaryColor = primaryGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { 
                        if (role == "Patron") showMembersDialog = true 
                        else showMyClubsDialog = true
                    }
                )
                DashboardCard(
                    title = "Events",
                    icon = Icons.Default.DateRange, 
                    primaryColor = primaryGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { showEventsDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashboardCard(
                    title = "Notifications",
                    icon = Icons.Default.Notifications,
                    primaryColor = primaryGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { showNotificationDialog = true }
                )
                if (role == "Member") {
                    DashboardCard(
                        title = "Join New Club",
                        icon = Icons.Default.AddCircle,
                        primaryColor = primaryGreen,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (memberClubs.size + pendingClubs.size >= 2) {
                                Toast.makeText(context, "Limit reached! (Joined + Pending = 2)", Toast.LENGTH_SHORT).show()
                            } else {
                                showJoinClubDialog = true
                            }
                        }
                    )
                } else {
                    DashboardCard(
                        title = "Settings",
                        icon = Icons.Default.Settings,
                        primaryColor = primaryGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- Dialogs ---

        // Notification Dialog
        if (showNotificationDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationDialog = false },
                title = { Text(if (role == "Patron") "Join Requests" else "Alerts & Invites", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = {
                    if (role == "Patron") {
                        if (joinRequests.isEmpty()) {
                            Text("No pending requests.")
                        } else {
                            LazyColumn {
                                items(joinRequests) { (reqId, message) ->
                                    ListItem(
                                        headlineContent = { Text(message) },
                                        trailingContent = {
                                            Button(
                                                onClick = {
                                                    requestsRef?.child(reqId)?.get()?.addOnSuccessListener { snapshot ->
                                                        val mId = snapshot.child("memberId").getValue(String::class.java) ?: ""
                                                        val requestedClub = snapshot.child("clubName").getValue(String::class.java) ?: ""
                                                        if (mId.isNotEmpty() && requestedClub.isNotEmpty()) {
                                                            val mUserRef = database?.getReference("Users")?.child(mId)
                                                            mUserRef?.get()?.addOnSuccessListener { uSnap ->
                                                                val pendingList = uSnap.child("pendingClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                                                if (!pendingList.contains(requestedClub)) {
                                                                    pendingList.add(requestedClub)
                                                                    mUserRef.child("pendingClubs").setValue(pendingList)
                                                                }
                                                                requestsRef.child(reqId).removeValue()
                                                                Toast.makeText(context, "Approved! Member notified.", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen),
                                                shape = RoundedCornerShape(8.dp)
                                            ) { Text("Approve") }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    } else {
                        // Member Notifications
                        if (pendingClubs.isEmpty()) {
                            Text("No new notifications.")
                        } else {
                            LazyColumn {
                                items(pendingClubs) { club ->
                                    ListItem(
                                        headlineContent = { Text("Accepted by $club!", fontWeight = FontWeight.Bold) },
                                        supportingContent = { Text("Complete your registration to join this club.") },
                                        trailingContent = {
                                            Button(
                                                onClick = {
                                                    userRef?.get()?.addOnSuccessListener { snapshot ->
                                                        val joinedList = snapshot.child("joinedClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                                        val updatedPending = snapshot.child("pendingClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                                        if (!joinedList.contains(club)) {
                                                            joinedList.add(club)
                                                            userRef.child("joinedClubs").setValue(joinedList)
                                                            userRef.child("joinedClubsCount").setValue(joinedList.size)
                                                        }
                                                        updatedPending.remove(club)
                                                        userRef.child("pendingClubs").setValue(updatedPending)
                                                        Toast.makeText(context, "Registered for $club!", Toast.LENGTH_SHORT).show()
                                                        if (updatedPending.isEmpty()) showNotificationDialog = false
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                                            ) { Text("Register") }
                                        }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showNotificationDialog = false }) { Text("Close", color = primaryGreen) } }
            )
        }

        // Members Dialog
        if (showMembersDialog) {
            AlertDialog(
                onDismissRequest = { showMembersDialog = false },
                title = { Text("Club Members", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = {
                    if (clubMembers.isEmpty()) {
                        Text("No members in this club.")
                    } else {
                        LazyColumn {
                            items(clubMembers) { (name, email, phone) ->
                                ListItem(
                                    headlineContent = { Text(name, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text("$email • $phone") },
                                    leadingContent = { Icon(Icons.Default.AccountCircle, contentDescription = null, tint = primaryGreen) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMembersDialog = false }) { Text("Close", color = primaryGreen) } }
            )
        }

        // Events Dialog
        if (showEventsDialog) {
            AlertDialog(
                onDismissRequest = { showEventsDialog = false },
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Club Events", color = primaryGreen, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (role == "Patron") {
                            IconButton(onClick = { showAddEventDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Event", tint = primaryGreen)
                            }
                        }
                    }
                },
                text = {
                    if (events.isEmpty()) {
                        Text("No events for your clubs.")
                    } else {
                        LazyColumn {
                            items(events) { event ->
                                ListItem(
                                    headlineContent = { Text(event.title, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text("${event.clubName} • ${event.date}") },
                                    leadingContent = { Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryGreen) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showEventsDialog = false }) { Text("Close", color = primaryGreen) } }
            )
        }

        // Add Event Dialog (for Patrons)
        if (showAddEventDialog) {
            var eventTitle by remember { mutableStateOf("") }
            var eventDate by remember { mutableStateOf("") }
            var eventDesc by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddEventDialog = false },
                title = { Text("Post New Event", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            label = { Text("Event Title") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = primaryGreen) }, 
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, focusedLabelColor = primaryGreen)
                        )
                        OutlinedTextField(
                            value = eventDate,
                            onValueChange = { eventDate = it },
                            label = { Text("Date (e.g., Oct 20)") },
                            leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, tint = primaryGreen) }, 
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, focusedLabelColor = primaryGreen)
                        )
                        OutlinedTextField(
                            value = eventDesc,
                            onValueChange = { eventDesc = it },
                            label = { Text("Description") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = primaryGreen) }, 
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, focusedLabelColor = primaryGreen)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (eventTitle.isNotBlank() && eventDate.isNotBlank()) {
                                val newEvent = Event(eventTitle, patronClub, eventDate, eventDesc)
                                eventsRef?.push()?.setValue(newEvent)
                                showAddEventDialog = false
                                Toast.makeText(context, "Event posted!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                    ) { Text("Post") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddEventDialog = false }) { Text("Cancel", color = primaryGreen) }
                }
            )
        }

        // My Clubs Dialog
        if (showMyClubsDialog) {
            AlertDialog(
                onDismissRequest = { showMyClubsDialog = false },
                title = { Text("My Clubs", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = {
                    LazyColumn {
                        items(memberClubs) { club ->
                            ListItem(
                                headlineContent = { Text(club) },
                                leadingContent = { Icon(Icons.Default.Home, contentDescription = null, tint = primaryGreen) }
                            )
                            HorizontalDivider()
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showMyClubsDialog = false }) { Text("Close", color = primaryGreen) } }
            )
        }

        // Registration Prompt Dialog (for Members)
        if (role == "Member" && pendingClubs.isNotEmpty()) {
            val clubToRegister = pendingClubs[0]
            AlertDialog(
                onDismissRequest = { }, // Force registration action
                title = { Text("Registration Required", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = { Text("You have been accepted into $clubToRegister. Please complete your registration to join.") },
                confirmButton = {
                    Button(
                        onClick = {
                            userRef?.get()?.addOnSuccessListener { snapshot ->
                                val joinedList = snapshot.child("joinedClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                val updatedPending = snapshot.child("pendingClubs").children.mapNotNull { it.getValue(String::class.java) }.toMutableList()
                                
                                if (!joinedList.contains(clubToRegister)) {
                                    joinedList.add(clubToRegister)
                                    userRef.child("joinedClubs").setValue(joinedList)
                                    userRef.child("joinedClubsCount").setValue(joinedList.size)
                                }
                                updatedPending.remove(clubToRegister)
                                userRef.child("pendingClubs").setValue(updatedPending)
                                
                                Toast.makeText(context, "Successfully registered for $clubToRegister!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                    ) { Text("Register Now") }
                }
            )
        }

        // Join Club Dialog
        if (showJoinClubDialog) {
            var expandedDropdown by remember { mutableStateOf(false) }
            val availableToJoin = ClubData.availableClubs.filter { !memberClubs.contains(it) && !pendingClubs.contains(it) }
            var selectedJoinClub by remember { mutableStateOf(if (availableToJoin.isNotEmpty()) availableToJoin[0] else "") }

            AlertDialog(
                onDismissRequest = { showJoinClubDialog = false },
                title = { Text("Join a New Club", color = primaryGreen, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Select a club to join:")
                        Spacer(modifier = Modifier.height(16.dp))
                        ExposedDropdownMenuBox(
                            expanded = expandedDropdown,
                            onExpandedChange = { expandedDropdown = !expandedDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedJoinClub,
                                onValueChange = {},
                                readOnly = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = primaryGreen) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = primaryGreen, focusedLabelColor = primaryGreen)
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
                    Button(
                        onClick = {
                            if (selectedJoinClub.isNotEmpty()) {
                                val request = mapOf(
                                    "memberId" to userId,
                                    "clubName" to selectedJoinClub,
                                    "message" to "$userName wants to join $selectedJoinClub"
                                )
                                requestsRef?.push()?.setValue(request)
                                Toast.makeText(context, "Request sent!", Toast.LENGTH_SHORT).show()
                                showJoinClubDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryGreen)
                    ) { Text("Send Request") }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinClubDialog = false }) { Text("Cancel", color = primaryGreen) }
                }
            )
        }
    }
}

@Composable
fun DashboardCard(title: String, icon: ImageVector, primaryColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(44.dp), tint = primaryColor)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, fontWeight = FontWeight.Bold, color = Color.DarkGray)
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
