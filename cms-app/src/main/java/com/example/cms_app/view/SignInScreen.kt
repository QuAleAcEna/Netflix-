package com.example.cms_app.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.cms_app.viewmodel.AuthViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SignInScreen(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit
) {
    val isLoading = viewModel.isLoading.collectAsState()
    val error = viewModel.error.collectAsState()
    val isAuthenticated = viewModel.isAuthenticated.collectAsState()
    val username = remember { mutableStateOf("") }
    val password = remember { mutableStateOf("") }

    LaunchedEffect(isAuthenticated.value) {
        if (isAuthenticated.value) {
            onSignedIn()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("CMS Sign In") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = username.value,
                onValueChange = { username.value = it },
                label = { Text("Username") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(12.dp))
            OutlinedTextField(
                value = password.value,
                onValueChange = { password.value = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.size(16.dp))
            if (error.value != null) {
                Text(error.value!!, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                Spacer(Modifier.size(8.dp))
            }
            Button(
                onClick = {
                    viewModel.login(username.value, password.value)
                },
                enabled = !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    Text("Sign in")
                }
            }
        }
    }
}
