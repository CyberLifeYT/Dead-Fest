package com.deadfest.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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
import com.deadfest.R
import com.deadfest.ui.components.*
import com.deadfest.ui.theme.*
import com.deadfest.ui.viewmodel.AuthState
import com.deadfest.ui.viewmodel.MainViewModel

/**
 * The gate.
 *
 * First impression, so it carries the brand: the skull mark, an ember gradient wordmark, and a
 * single card holding one obvious action. Auth state, session persistence and the Firebase calls
 * are untouched — only the presentation changed.
 */
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
            // IME padding + scroll: the keyboard never covers the form on small screens.
            .imePadding()
    ) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewportHeight)
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(Spacing.xl))

            // ---- brand ----
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(Ember.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                )
                Image(
                    painter = painterResource(R.drawable.ic_deadfest_mark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Ember),
                    modifier = Modifier.size(76.dp)
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                "DEAD-FEST",
                style = TextStyle(
                    brush = Brush.linearGradient(listOf(EmberGlow, Ember, EmberDeep)),
                    fontWeight = FontWeight.Black,
                    fontSize = 42.sp,
                    letterSpacing = 2.sp
                )
            )
            Spacer(Modifier.height(Spacing.xxs))
            Text(
                "VERSION ${BuildConfig.VERSION_NAME} // SECURE TERMINAL",
                color = TextLow,
                style = DeadFestType.microLabel,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xl))

            // ---- form ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xl))
                    .background(
                        Brush.verticalGradient(
                            listOf(Ember.copy(alpha = 0.10f), Color.Transparent)
                        )
                    )
                    .background(Ink2)
                    .border(BorderStroke(Stroke.hair, Ember.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.xl))
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode switch is a pair of pills, so the current mode is obvious at a glance.
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    PillChip("Log in", isLogin, { isLogin = true; viewModel.resetAuthState() })
                    PillChip("Sign up", !isLogin, { isLogin = false; viewModel.resetAuthState() })
                }
                Spacer(Modifier.height(Spacing.lg))

                AnimatedVisibility(visible = !isLogin) {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; viewModel.resetAuthState() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Display Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            shape = RoundedCornerShape(Radius.md),
                            singleLine = true,
                            colors = deadFestFieldColors(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                        Spacer(Modifier.height(Spacing.sm))
                    }
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; viewModel.resetAuthState() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    shape = RoundedCornerShape(Radius.md),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    singleLine = true,
                    colors = deadFestFieldColors()
                )

                Spacer(Modifier.height(Spacing.sm))

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
                    shape = RoundedCornerShape(Radius.md),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (!loading) submit() }),
                    singleLine = true,
                    colors = deadFestFieldColors()
                )

                AnimatedVisibility(visible = authState is AuthState.Error) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(Ember.copy(alpha = Tint.wash))
                            .border(BorderStroke(Stroke.hair, Ember.copy(alpha = Tint.edge)), RoundedCornerShape(Radius.sm))
                            .padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (authState as? AuthState.Error)?.message ?: "",
                            color = Ember,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))

                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ember,
                        contentColor = Color.White,
                        disabledContainerColor = Ink4,
                        disabledContentColor = TextLow
                    ),
                    shape = RoundedCornerShape(Radius.md),
                    elevation = null,
                    enabled = !loading
                ) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            if (isLogin) "ENTER THE WASTELAND" else "JOIN SURVIVORS",
                            style = DeadFestType.action
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                "Friends & family only. Unauthorised access is logged.",
                color = TextLow,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}
