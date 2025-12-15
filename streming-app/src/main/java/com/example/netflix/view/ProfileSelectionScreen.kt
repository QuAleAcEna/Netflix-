package com.example.netflix.view
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.net.Uri
import com.example.netflix.R
import com.example.netflix.model.CreateProfileRequest
import com.example.netflix.model.Profile
import com.example.netflix.model.UpdateProfileRequest
import com.example.netflix.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

private const val MAX_PROFILES = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ProfileSelectionScreen(
    navController: NavController,
    userId: Int,
    accountName: String
) {
    val profiles = remember { mutableStateListOf<Profile>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var newProfileName by remember { mutableStateOf("") }
    var isKidsProfile by remember { mutableStateOf(false) }
    // Local drawable names; add matching images in res/drawable (e.g., avatar1.png, avatar2.png, etc.)
    val avatarOptions = listOf("avatar1", "avatar2", "avatar3", "avatar4", "avatar5", "avatar6", "avatar7", "avatar8", "avatar9")
    var selectedAvatarSeed by remember { mutableStateOf(avatarOptions.first()) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var isLoadingProfiles by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }
    var isUpdatingProfile by remember { mutableStateOf(false) }
    var isDeletingProfile by remember { mutableStateOf(false) }
    var profileToEdit by remember { mutableStateOf<Profile?>(null) }
    var profileToDelete by remember { mutableStateOf<Profile?>(null) }
    var editName by remember { mutableStateOf("") }
    var editAvatarSeed by remember { mutableStateOf(avatarOptions.first()) }
    var editKids by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val avatarSeeds = avatarOptions

    LaunchedEffect(userId) {
        if (userId <= 0) {
            snackbarHostState.showSnackbar("Could not identify the user. Please sign in again.")
            navController.navigate("signin") {
                popUpTo("signin") { inclusive = true }
            }
            return@LaunchedEffect
        }

        isLoadingProfiles = true
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitInstance.api.getProfiles(userId)
            }
            if (response.isSuccessful) {
                profiles.clear()
                response.body()?.let { profiles.addAll(it) }
            } else {
                snackbarHostState.showSnackbar("Failed to load profiles (${response.code()}).")
            }
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Connection error while loading profiles.")
        } finally {
            isLoadingProfiles = false
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSavingProfile) {
                    showAddDialog = false
                    newProfileName = ""
                    isKidsProfile = false
                    nameError = null
                }
            },
            title = { Text(text = "New profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = {
                            newProfileName = it
                            if (nameError != null) nameError = null
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = nameError != null
                    )
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                        Text("Avatar", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        avatarSeeds.forEach { seed ->
                            val selected = selectedAvatarSeed == seed
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { selectedAvatarSeed = seed }
                            ) {
                                val context = LocalContext.current
                                val resId = context.resources.getIdentifier(seed, "drawable", context.packageName)
                                Image(
                                    painter = painterResource(id = if (resId != 0) resId else R.drawable.ic_launcher_foreground),
                                    contentDescription = "Avatar $seed",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kids profile")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = isKidsProfile,
                            onCheckedChange = { isKidsProfile = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSavingProfile,
                    onClick = {
                        val trimmedName = newProfileName.trim()
                        when {
                            trimmedName.isEmpty() ->
                                nameError = "Name cannot be empty"
                            profiles.any { it.name.equals(trimmedName, ignoreCase = true) } ->
                                nameError = "A profile with this name already exists"
                            else -> {
                                coroutineScope.launch {
                                    isSavingProfile = true
                                    try {
                                        val response = withContext(Dispatchers.IO) {
                                            RetrofitInstance.api.createProfile(
                                                CreateProfileRequest(
                                                    userId = userId,
                                                    name = trimmedName,
                                                    avatarColor = selectedAvatarSeed,
                                                    kids = isKidsProfile
                                                )
                                            )
                                        }
                                        if (response.isSuccessful) {
                                            response.body()?.let { created ->
                                                profiles.add(created)
                                                snackbarHostState.showSnackbar("Profile created.")
                                            }
                                            showAddDialog = false
                                            newProfileName = ""
                                            isKidsProfile = false
                                            selectedAvatarSeed = avatarSeeds.first()
                                            nameError = null
                                        } else if (response.code() == 409) {
                                            nameError = "That name is already in use."
                                        } else {
                                            snackbarHostState.showSnackbar("Could not create profile (${response.code()}).")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Error creating profile. Please try again.")
                                    } finally {
                                        isSavingProfile = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isSavingProfile) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isSavingProfile) {
                            showAddDialog = false
                            newProfileName = ""
                            isKidsProfile = false
                            nameError = null
                        }
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (profileToEdit != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isUpdatingProfile) {
                    profileToEdit = null
                    nameError = null
                }
            },
            title = { Text(text = "Edit profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = {
                            editName = it
                            if (nameError != null) nameError = null
                        },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = nameError != null
                    )
                    if (nameError != null) {
                        Text(
                            text = nameError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Avatar", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        avatarSeeds.forEach { seed ->
                            val selected = editAvatarSeed == seed
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { editAvatarSeed = seed }
                            ) {
                                val context = LocalContext.current
                                val resId = context.resources.getIdentifier(seed, "drawable", context.packageName)
                                Image(
                                    painter = painterResource(id = if (resId != 0) resId else R.drawable.ic_launcher_foreground),
                                    contentDescription = "Avatar $seed",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Kids profile")
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = editKids,
                            onCheckedChange = { editKids = it }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isUpdatingProfile,
                    onClick = {
                        val trimmedName = editName.trim()
                        if (trimmedName.isEmpty()) {
                            nameError = "Name cannot be empty"
                            return@TextButton
                        }
                        profileToEdit?.let { profile ->
                            coroutineScope.launch {
                                isUpdatingProfile = true
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                            RetrofitInstance.api.updateProfile(
                                                profile.id,
                                                UpdateProfileRequest(
                                                    name = trimmedName,
                                                avatarColor = editAvatarSeed,
                                                kids = editKids
                                            )
                                        )
                                    }
                                    if (response.isSuccessful) {
                                        response.body()?.let { updated ->
                                            val index = profiles.indexOfFirst { it.id == updated.id }
                                            if (index >= 0) {
                                                profiles[index] = updated
                                            }
                                            snackbarHostState.showSnackbar("Profile updated.")
                                        }
                                        profileToEdit = null
                                        nameError = null
                                    } else {
                                        snackbarHostState.showSnackbar("Could not update (${response.code()}).")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error updating profile.")
                                } finally {
                                    isUpdatingProfile = false
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isUpdatingProfile) "Saving..." else "Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isUpdatingProfile) profileToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (profileToDelete != null) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingProfile) profileToDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("This will remove the profile and its associated progress.") },
            confirmButton = {
                TextButton(
                    enabled = !isDeletingProfile,
                    onClick = {
                        profileToDelete?.let { profile ->
                            coroutineScope.launch {
                                isDeletingProfile = true
                                try {
                                    val response = withContext(Dispatchers.IO) {
                                        RetrofitInstance.api.deleteProfile(profile.id)
                                    }
                                    if (response.isSuccessful) {
                                        profiles.removeAll { it.id == profile.id }
                                        snackbarHostState.showSnackbar("Profile removed.")
                                    } else {
                                        snackbarHostState.showSnackbar("Could not delete (${response.code()}).")
                                    }
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Error deleting profile.")
                                } finally {
                                    isDeletingProfile = false
                                    profileToDelete = null
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isDeletingProfile) "Deleting..." else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!isDeletingProfile) profileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.background_wavy),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = if (accountName.isNotBlank()) {
                                "Who's watching, $accountName?"
                            } else {
                                "Who's watching?"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                navController.navigate("signin") {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Logout,
                                contentDescription = "Sign out",
                                tint = Color.White
                            )
                        }
                    }
                )
            },
            floatingActionButton = { }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Choose a profile to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                when {
                    isLoadingProfiles -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    profiles.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No profiles yet. Create one to get started.",
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(profiles, key = { it.id }) { profile ->
                                ProfileCard(
                                    profile = profile,
                                    onClick = {
                                        val encodedName = Uri.encode(profile.name)
                                        val accountSegment = Uri.encode(accountName.ifBlank { "_" })
                                        navController.navigate(
                                            "home/${profile.userId}/$accountSegment/${profile.id}/$encodedName"
                                        )
                                    },
                                    onEdit = {
                                        profileToEdit = profile
                                        editName = profile.name
                                        editAvatarSeed = profile.avatarColor.ifBlank { "seed1" }
                                        editKids = profile.kids
                                        nameError = null
                                    },
                                    onDelete = { profileToDelete = profile }
                                )
                            }

                            if (profiles.size < MAX_PROFILES) {
                                item {
                                    AddProfileCard(onClick = { showAddDialog = true })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: Profile,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val avatarSeed = profile.avatarColor.ifBlank { profile.name.ifBlank { "avatar1" } }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit profile", tint = Color.White)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete profile", tint = MaterialTheme.colorScheme.error)
                }
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val resId = context.resources.getIdentifier(avatarSeed, "drawable", context.packageName)
                Image(
                    painter = if (resId != 0) painterResource(id = resId) else painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "Avatar of ${profile.name}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable(onClick = onClick)
            )
            if (profile.kids) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Kids profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add profile",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Add profile",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
