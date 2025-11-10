package com.example.netflix.view

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.netflix.network.RetrofitInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SignInScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showCreateAccountDialog by remember { mutableStateOf(false) }
    var showAccountCreatedDialog by remember { mutableStateOf(false) }
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var isCreatingAccount by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    if (showCreateAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingAccount) showCreateAccountDialog = false },
            title = { Text("Criar nova conta") },
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
                                snackbarHostState.showSnackbar("Preencha username e password para criar a conta.")
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
                                    snackbarHostState.showSnackbar("Não foi possível criar a conta. Verifique os dados e tente novamente.")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Erro ao comunicar com o servidor. Tente novamente.")
                            } finally {
                                isCreatingAccount = false
                            }
                        }
                    },
                    enabled = !isCreatingAccount
                ) {
                    Text(if (isCreatingAccount) "Aguarde..." else "Criar")
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
                    Text("Cancelar")
                }
            }
        )
    }

    if (showAccountCreatedDialog) {
        AlertDialog(
            onDismissRequest = { showAccountCreatedDialog = false },
            title = { Text("Conta criada") },
            text = { Text("Conta criada com sucesso. Já pode iniciar sessão!") },
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

                Button(
                    onClick = {
                        val trimmedUsername = username.trim()
                        if (trimmedUsername.isEmpty() || password.isBlank()) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Introduza username e password.")
                            }
                            return@Button
                        }

                        coroutineScope.launch {
                            isSigningIn = true
                            try {
                                val response = withContext(Dispatchers.IO) {
                                    RetrofitInstance.api.connectUser(
                                        trimmedUsername,
                                        password
                                    )
                                }
                                if (response.isSuccessful) {
                                    val user = response.body()
                                    if (user != null) {
                                        navController.navigate(
                                            "profiles/${user.id}/${Uri.encode(user.name)}"
                                        ) {
                                            popUpTo("signin") { inclusive = true }
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Credenciais inválidas.")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Credenciais inválidas.")
                                }
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Erro ao comunicar com o servidor.")
                            } finally {
                                isSigningIn = false
                            }
                        }
                    },
                    enabled = !isSigningIn
                ) {
                    Text(if (isSigningIn) "A autenticar..." else "Sign In")
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { showCreateAccountDialog = true }) {
                    Text("Criar nova conta")
                }
            }
        }
    }
}
