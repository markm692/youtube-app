package com.youtubeapp

import android.app.Application
import com.youtubeapp.data.auth.AuthManager
import com.youtubeapp.data.cache.FeedCache
import com.youtubeapp.data.settings.ChannelPreferences
import com.youtubeapp.data.remote.RetrofitInstance
import com.youtubeapp.data.repository.YouTubeRepository

class YouTubeApp : Application() {

    lateinit var authManager: AuthManager
        private set

    lateinit var repository: YouTubeRepository
        private set

    lateinit var feedCache: FeedCache
        private set

    lateinit var channelPreferences: ChannelPreferences
        private set

    override fun onCreate() {
        super.onCreate()
        authManager = AuthManager(this)
        feedCache = FeedCache(this)
        channelPreferences = ChannelPreferences(this)
        // The interceptor reads the token lazily, so signing in later takes
        // effect without rebuilding the Retrofit stack.
        val apiService = RetrofitInstance.create { authManager.accessToken.value }
        repository = YouTubeRepository(apiService)
    }
}
