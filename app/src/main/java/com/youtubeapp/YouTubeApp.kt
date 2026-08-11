package com.youtubeapp

import android.app.Application
import com.youtubeapp.data.auth.AuthManager
import com.youtubeapp.data.remote.RetrofitInstance
import com.youtubeapp.data.repository.YouTubeRepository

class YouTubeApp : Application() {

    lateinit var authManager: AuthManager
        private set

    lateinit var repository: YouTubeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
        // The interceptor reads the token lazily, so signing in later takes
        // effect without rebuilding the Retrofit stack.
        val apiService = RetrofitInstance.create { authManager.accessToken.value }
        repository = YouTubeRepository(apiService)
    }
}
