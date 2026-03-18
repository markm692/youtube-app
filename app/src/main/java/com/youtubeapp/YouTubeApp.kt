package com.youtubeapp

import android.app.Application

class YouTubeApp : Application() {
    lateinit var repository: com.youtubeapp.data.repository.YouTubeRepository

    override fun onCreate() {
        super.onCreate()
        val apiService = com.youtubeapp.data.remote.RetrofitInstance.apiService
        repository = com.youtubeapp.data.repository.YouTubeRepository(apiService)
    }
}
