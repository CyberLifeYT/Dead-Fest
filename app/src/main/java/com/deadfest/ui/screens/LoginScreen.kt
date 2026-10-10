package com.deadfest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.deadfest.BuildConfig
import com.deadfest.ui.components.deadFestFieldColors
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.AuthState
import com.deadfest.ui.viewmodel.MainViewModel

@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var isLogin by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val loading = authState is AuthState.Loading

    fun submit() {
        focusManager.clearFocus()
        if (isLogin) viewModel.login(email, password) else viewModel.signup(email, password, name)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A0707), DarkBg, DarkBg)))
            // IME padding + scroll: the keyboard never covers the form on small screens.
            .imePadding()
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(24.dp))
            Text("DEAD-FEST", color = RedAccent, fontSize = 46.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(
                "VERSION ${BuildConfig.VERSION_NAME} // SECURE TERMINAL",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(36.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, Outline)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        if (isLogin) "LOGIN" else "SIGN UP",
                        color = TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(20.dp))

                    AnimatedVisibility(visible = !isLogin) {
                        Column {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it; viewModel.resetAuthState() },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Display Name") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = deadFestFieldColors(),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )
                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; viewModel.resetAuthState() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        singleLine = true,
                        colors = deadFestFieldColors()
                    )

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; viewModel.resetAuthState() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        shape = RoundedCornerShape(16.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (!loading) submit() }),
                        singleLine = true,
                        colors = deadFestFieldColors()
                    )

                    AnimatedVisibility(visible = authState is AuthState.Error) {
                        Text(
                            (authState as? AuthState.Error)?.message ?: "",
                            color = RedAccent,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        )
                    }

                    Spacer(Modifier.height(22.dp))

                    Button(
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedAccent, disabledContainerColor = RedAccent.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                        } else {
                            Text(if (isLogin) "ENTER THE WASTELAND" else "JOIN SURVIVORS", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    TextButton(onClick = { isLogin = !isLogin; viewModel.resetAuthState() }, enabled = !loading) {
                        Text(
                            if (isLogin) "Need an account? Sign up" else "Already a survivor? Log in",
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Friends & family only. Unauthorised access is logged.",
                color = TextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
