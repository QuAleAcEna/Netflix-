package com.example.netflix.view

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import com.example.netflix.R
import com.example.netflix.model.CreateProfileRequest
import com.example.netflix.model.Profile
import com.example.netflix.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var nameError by remember { mutableStateOf<String?>(null) }
    var isLoadingProfiles by remember { mutableStateOf(false) }
    var isSavingProfile by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val avatarPalette = listOf("#E50914", "#B81D24", "#221F1F", "#5A0F27", "#0071EB")

    LaunchedEffect(userId) {
        if (userId <= 0) {
            snackbarHostState.showSnackbar("Não foi possível identificar o utilizador. Volte a iniciar sessão.")
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
                snackbarHostState.showSnackbar("Erro ao carregar perfis (${response.code()}).")
            }
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Erro de ligação ao carregar perfis.")
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
            title = { Text(text = "Novo perfil") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newProfileName,
                        onValueChange = {
                            newProfileName = it
                            if (nameError != null) nameError = null
                        },
                        label = { Text("Nome") },
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Perfil infantil")
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
                                nameError = "O nome não pode estar vazio"
                            profiles.any { it.name.equals(trimmedName, ignoreCase = true) } ->
                                nameError = "Já existe um perfil com esse nome"
                            else -> {
                                coroutineScope.launch {
                                    isSavingProfile = true
                                    try {
                                        val colorHex =
                                            avatarPalette[profiles.size % avatarPalette.size]
                                        val response = withContext(Dispatchers.IO) {
                                            RetrofitInstance.api.createProfile(
                                                CreateProfileRequest(
                                                    userId = userId,
                                                    name = trimmedName,
                                                    avatarColor = colorHex,
                                                    kids = isKidsProfile
                                                )
                                            )
                                        }
                                        if (response.isSuccessful) {
                                            response.body()?.let { created ->
                                                profiles.add(created)
                                                snackbarHostState.showSnackbar("Perfil criado.")
                                            }
                                            showAddDialog = false
                                            newProfileName = ""
                                            isKidsProfile = false
                                            nameError = null
                                        } else if (response.code() == 409) {
                                            nameError = "Esse nome já está a ser usado."
                                        } else {
                                            snackbarHostState.showSnackbar("Não foi possível criar o perfil (${response.code()}).")
                                        }
                                    } catch (e: Exception) {
                                        snackbarHostState.showSnackbar("Erro ao criar o perfil. Tente novamente.")
                                    } finally {
                                        isSavingProfile = false
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Text(if (isSavingProfile) "A guardar..." else "Guardar")
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
                    Text("Cancelar")
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
                                "Quem está a assistir, $accountName?"
                            } else {
                                "Quem está a assistir?"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            },
            floatingActionButton = {
                if (profiles.size < MAX_PROFILES) {
                    FloatingActionButton(
                        onClick = { showAddDialog = true },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Adicionar perfil",
                            tint = Color.White
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Escolha um perfil para continuar",
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
                                text = "Ainda não existem perfis. Crie um para começar.",
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
                                    }
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
private fun ProfileCard(profile: Profile, onClick: () -> Unit) {
    val color = remember(profile.avatarColor) {
        try {
            Color(AndroidColor.parseColor(profile.avatarColor))
        } catch (_: IllegalArgumentException) {
            Color(0xFFE50914)
        }
    }

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
                    .background(color)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profile.name.first().uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = profile.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (profile.kids) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Perfil infantil",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
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
                    contentDescription = "Adicionar perfil",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Adicionar perfil",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }
    }
}
