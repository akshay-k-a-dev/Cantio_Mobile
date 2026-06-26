package com.appplayer.music.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appplayer.music.data.api.models.AuthResponse
import com.appplayer.music.data.repository.ApiResult
import com.appplayer.music.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<ApiResult<AuthResponse>?>(null)
    val authState: StateFlow<ApiResult<AuthResponse>?> = _authState.asStateFlow()

    fun sendOtp(email: String, onResult: (ApiResult<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendOtp(email, "register")
            onResult(result)
        }
    }

    fun register(
        email: String,
        password: String,
        username: String?,
        name: String?,
        otp: String,
        onResult: (ApiResult<AuthResponse>) -> Unit
    ) {
        viewModelScope.launch {
            val result = authRepository.register(email, password, username, name, otp)
            if (result is ApiResult.Success) {
                _authState.value = result
            }
            onResult(result)
        }
    }

    fun login(email: String, password: String, onResult: (ApiResult<AuthResponse>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.login(email, password)
            if (result is ApiResult.Success) {
                _authState.value = result
            }
            onResult(result)
        }
    }

    fun sendOtpForReset(email: String, onResult: (ApiResult<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.sendOtp(email, "reset")
            onResult(result)
        }
    }

    fun resetPassword(email: String, otp: String, newPassword: String, onResult: (ApiResult<com.appplayer.music.data.api.models.SuccessResponse>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.resetPassword(email, otp, newPassword)
            onResult(result)
        }
    }

    fun logout() {
        authRepository.logout()
        _authState.value = null
    }

    fun checkNeedsOnboarding(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            when (val res = authRepository.needsOnboarding()) {
                is ApiResult.Success -> onResult(res.data.needsOnboarding)
                else -> onResult(false)
            }
        }
    }
}
