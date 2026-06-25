package com.appplayer.music.data.repository

import com.appplayer.music.data.api.CantioApiService
import com.appplayer.music.data.api.models.*
import com.appplayer.music.utils.TokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = -1) : ApiResult<Nothing>()
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: CantioApiService,
    private val tokenManager: TokenManager
) {
    suspend fun sendOtp(email: String, purpose: String = "register"): ApiResult<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.sendOtp(SendOtpRequest(email, purpose))
                if (response.isSuccessful) ApiResult.Success(Unit)
                else ApiResult.Error(response.errorBody()?.string() ?: "Failed to send OTP", response.code())
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun register(
        email: String,
        password: String,
        username: String?,
        name: String?,
        otp: String
    ): ApiResult<AuthResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.register(RegisterRequest(email, password, username, name, otp))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                tokenManager.saveToken(body.token)
                tokenManager.saveUserId(body.user.id)
                tokenManager.saveUserEmail(body.user.email)
                tokenManager.saveUserName(body.user.name ?: body.user.username)
                ApiResult.Success(body)
            } else {
                ApiResult.Error(response.errorBody()?.string() ?: "Registration failed", response.code())
            }
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    suspend fun login(email: String, password: String): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.login(LoginRequest(email, password))
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    tokenManager.saveToken(body.token)
                    tokenManager.saveUserId(body.user.id)
                    tokenManager.saveUserEmail(body.user.email)
                    tokenManager.saveUserName(body.user.name ?: body.user.username)
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(response.errorBody()?.string() ?: "Login failed", response.code())
                }
            }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
        }

    suspend fun getMe(): ApiResult<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getMe()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!.user)
            } else {
                ApiResult.Error(response.errorBody()?.string() ?: "Failed to fetch profile", response.code())
            }
        }.getOrElse { ApiResult.Error(it.message ?: "Network error") }
    }

    fun logout() = tokenManager.clear()
    fun isLoggedIn() = tokenManager.isLoggedIn()
    fun getToken() = tokenManager.getToken()
}
