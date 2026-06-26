package com.appplayer.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.ui.theme.NeonViolet
import com.appplayer.music.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    isRegisterMode: Boolean = false,
    onLoginSuccess: (needsOnboarding: Boolean) -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    var isOtpSent by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val authState by viewModel.authState.collectAsState()

    // Handle authentication state success
    LaunchedEffect(authState) {
        if (authState is ApiResult.Success) {
            isLoading = true
            viewModel.checkNeedsOnboarding { needsOnboarding ->
                isLoading = false
                onLoginSuccess(needsOnboarding)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        NeonViolet.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (isRegisterMode) "Create Account" else "Welcome Back",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp
            )

            Text(
                text = if (isRegisterMode) "Verify email with OTP to register" else "Sign in to access your Cantio library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            if (isRegisterMode) {
                // Username (Optional)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username (Optional)") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        cursorColor = NeonViolet
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Name (Optional)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Display Name (Optional)") },
                    leadingIcon = { Icon(Icons.Default.PersonOutline, contentDescription = null) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        cursorColor = NeonViolet
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Email Field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email Address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                enabled = !(isRegisterMode && isOtpSent),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonViolet,
                    cursorColor = NeonViolet
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Password Field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonViolet,
                    cursorColor = NeonViolet
                ),
                shape = RoundedCornerShape(12.dp)
            )

            if (!isRegisterMode) {
                // Forgot Password link
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(
                        onClick = {
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://music-mu-p6h9.vercel.app/forgot-password"))
                                context.startActivity(intent)
                                android.widget.Toast.makeText(
                                    context,
                                    "Reset your password on the browser and return to login",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Could not open browser", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Forgot Password?", color = NeonViolet)
                    }
                }
            }

            // OTP Field (Register Mode & OTP Sent Only)
            if (isRegisterMode && isOtpSent) {
                OutlinedTextField(
                    value = otpCode,
                    onValueChange = { otpCode = it; errorMessage = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Verification Code (6-digit OTP)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        cursorColor = NeonViolet
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Error / Success displays
            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (successMessage != null) {
                Text(
                    text = successMessage!!,
                    color = Color(0xFF4CAF50),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(8.dp))

            // Primary Action Button
            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    if (isRegisterMode) {
                        if (!isOtpSent) {
                            if (trimmedEmail.isEmpty() || password.isEmpty()) {
                                errorMessage = "Email and Password cannot be empty"
                                return@Button
                            }
                            isLoading = true
                            viewModel.sendOtp(trimmedEmail) { result ->
                                isLoading = false
                                when (result) {
                                    is ApiResult.Success -> {
                                        isOtpSent = true
                                        successMessage = "OTP sent successfully to $trimmedEmail!"
                                    }
                                    is ApiResult.Error -> errorMessage = result.message
                                }
                            }
                        } else {
                            if (otpCode.isEmpty()) {
                                errorMessage = "Please enter the OTP verification code"
                                return@Button
                            }
                            isLoading = true
                            viewModel.register(
                                email = trimmedEmail,
                                password = password,
                                username = username.takeIf { it.isNotBlank() },
                                name = name.takeIf { it.isNotBlank() },
                                otp = otpCode.trim()
                            ) { result ->
                                isLoading = false
                                if (result is ApiResult.Error) {
                                    errorMessage = result.message
                                }
                            }
                        }
                    } else {
                        if (trimmedEmail.isEmpty() || password.isEmpty()) {
                            errorMessage = "Email and Password cannot be empty"
                            return@Button
                        }
                        isLoading = true
                        viewModel.login(trimmedEmail, password) { result ->
                            isLoading = false
                            if (result is ApiResult.Error) {
                                errorMessage = result.message
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = if (isRegisterMode) {
                            if (isOtpSent) "Verify & Register" else "Send Registration OTP"
                        } else "Sign In",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Toggle Mode Button
            TextButton(
                onClick = {
                    onNavigateToRegister()
                    successMessage = null
                    errorMessage = null
                },
                enabled = !isLoading
            ) {
                Text(
                    text = if (isRegisterMode) "Already have an account? Sign In" else "Don't have an account? Sign Up",
                    color = NeonViolet
                )
            }
        }
    }
}
