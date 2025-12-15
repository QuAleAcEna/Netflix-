package com.example.netflix.view

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.netflix.network.RetrofitInstance
import com.example.netflix.data.AuthStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first

@Composable
fun SignInScreen(navController: NavController) {
    val context = LocalContext.current
    val authStore = remember { AuthStore(context.applicationContext) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showAccountCreatedDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val performSignIn: suspend (String, String, Boolean) -> Unit = { user, pass, rememberFlag ->
        isSigningIn = true
        try {
            val response = withContext(Dispatchers.IO) {
                RetrofitInstance.api.connectUser(user, pass)
            }
            if (response.isSuccessful) {
                val u = response.body()
                if (u != null) {
                    authStore.saveCredentials(user, pass, rememberFlag)
                    navController.navigate(
                        "profiles/${u.id}/${Uri.encode(u.name)}"
                    ) {
                        popUpTo("signin") { inclusive = true }
                    }
                } else {
                    snackbarHostState.showSnackbar("Invalid credentials.")
                }
            } else {
                snackbarHostState.showSnackbar("Invalid credentials.")
            }
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Could not reach the server.")
        } finally {
            isSigningIn = false
        }
    }

    LaunchedEffect(Unit) {
        val stored = authStore.rememberedCredentials.first()
        if (stored != null) {
            username = stored.username
            password = stored.password
            rememberMe = true
            performSignIn(stored.username, stored.password, true)
        }
    }

    if (showCreateAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingAccount) showCreateAccountDialog = false },
            title = { Text("Create new account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username") }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newUsername.isBlank() || newPassword.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Please fill username and password to create the account.")
                            }
                            return@TextButton
                        }
                        coroutineScope.launch {
                            isCreatingAccount = true
                            try {
                                val trimmedUsername = newUsername.trim()
                                val response = withContext(Dispatchers.IO) {
                                    RetrofitInstance.api.createUser(
                                        trimmedUsername,
                                        newPassword
                                    )
                                }
                                if (response.isSuccessful && response.body() != null) {
                                    username = trimmedUsername
                                    password = newPassword
                                    showCreateAccountDialog = false
                                    showAccountCreatedDialog = true
                                    newUsername = ""
                                    newPassword = ""
                                } else {
                                    snackbarHostState.showSnackbar("Could not create the account. Check the data and try again.")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Could not reach the server. Please try again.")
                            } finally {
                                isCreatingAccount = false
                            }
                        }
                    },
                    enabled = !isCreatingAccount
                ) {
                    Text(if (isCreatingAccount) "Please wait..." else "Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isCreatingAccount) {
                            showCreateAccountDialog = false
                            newUsername = ""
                            newPassword = ""
                        }
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAccountCreatedDialog) {
        AlertDialog(
            onDismissRequest = { showAccountCreatedDialog = false },
            title = { Text("Account created") },
            text = { Text("Account created successfully. You can sign in now!") },
            confirmButton = {
                TextButton(onClick = { showAccountCreatedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Sign in to Netflix++", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") }
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberMe,
                        onCheckedChange = { rememberMe = it },
                        enabled = !isSigningIn
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Remember me on this device")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val trimmedUsername = username.trim()
                        if (trimmedUsername.isEmpty() || password.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Enter username and password.")
                            }
                            return@Button
                        }

                        coroutineScope.launch {
                            performSignIn(trimmedUsername, password, rememberMe)
                        }
                    },
                    enabled = !isSigningIn
                ) {
                    Text(if (isSigningIn) "Signing in..." else "Sign In")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { showCreateAccountDialog = true }) {
                    Text("Create new account")
                }
            }
        }
    }
}
