package com.lich.updater.UI.About

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class GitHubAvatarLoader(private val context: Context) {
    
    companion object {
        private const val TAG = "GitHubAvatarLoader"
        private const val AVATAR_SIZE = 96
    }
    
    private val cache = mutableMapOf<String, Bitmap>()
    
    suspend fun loadAvatar(username: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            cache[username]?.let { return@withContext it }
            
            val avatarUrl = "https://github.com/$username.png?size=$AVATAR_SIZE"
            Log.d(TAG, "Loading avatar for $username from $avatarUrl")
            
            val url = URL(avatarUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap != null) {
                    val circularBitmap = BitmapUtils.getCircularBitmap(bitmap)
                    cache[username] = circularBitmap
                    Log.d(TAG, "Successfully loaded avatar for $username")
                    return@withContext circularBitmap
                }
            }
            
            Log.w(TAG, "Failed to load avatar for $username, response code: ${connection.responseCode}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading avatar for $username", e)
            null
        }
    }
    
    fun getCachedAvatar(username: String): Bitmap? = cache[username]
}
