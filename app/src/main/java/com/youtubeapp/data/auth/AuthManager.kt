package com.youtubeapp.data.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Holds the OAuth access token used to call the YouTube Data API as the signed-in user.
 *
 * Uses Play Services' AuthorizationClient, which needs an OAuth 2.0 Client ID of type
 * "Android" registered in the Google Cloud project for this package name + signing
 * SHA-1. No client ID or secret is embedded in the app.
 *
 * The token is deliberately held in memory only: it is short-lived (~1h) and
 * re-authorizing is silent once consent has been granted.
 */
class AuthManager(private val appContext: Context) {

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn

    private val request: AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READONLY_SCOPE)))
            .build()

    /**
     * Attempts authorization. Returns null when it succeeded without user interaction.
     * Returns an [Intent] sender request payload when the user must grant consent —
     * the caller launches it and then feeds the result to [onConsentResult].
     */
    suspend fun authorize(): AuthorizationResult = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(appContext)
            .authorize(request)
            .addOnSuccessListener { result ->
                if (!result.hasResolution()) {
                    applyResult(result)
                }
                cont.resume(result)
            }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }

    /** Feed back the Intent returned from the consent screen. */
    fun onConsentResult(data: Intent?): Boolean {
        if (data == null) return false
        return runCatching {
            val result = Identity.getAuthorizationClient(appContext)
                .getAuthorizationResultFromIntent(data)
            applyResult(result)
            result.accessToken != null
        }.getOrDefault(false)
    }

    private fun applyResult(result: AuthorizationResult) {
        _accessToken.value = result.accessToken
        _signedIn.value = result.accessToken != null
    }

    fun signOut() {
        _accessToken.value = null
        _signedIn.value = false
    }

    /** Called when the API rejects the token so the next call re-authorizes. */
    fun invalidateToken() {
        _accessToken.value = null
    }

    companion object {
        const val YOUTUBE_READONLY_SCOPE = "https://www.googleapis.com/auth/youtube.readonly"
    }
}
