package com.lich.updater.UI.About

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.preference.Preference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ImagePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : Preference(context, attrs, defStyleAttr) {
    
    private var avatarLoader: GitHubAvatarLoader? = null
    private var username: String? = null
    
    fun setAvatarLoader(loader: GitHubAvatarLoader) {
        this.avatarLoader = loader
    }
    
    fun setUsername(username: String) {
        this.username = username
        loadAvatar()
    }
    
    private fun loadAvatar() {
        val loader = avatarLoader ?: return
        val user = username ?: return
        
        // Try to get cached avatar first
        val cachedAvatar = loader.getCachedAvatar(user)
        if (cachedAvatar != null) {
            val drawable = BitmapDrawable(context.resources, cachedAvatar)
            drawable.setBounds(0, 0, 96, 96) // Set explicit size
            setIcon(drawable)
            return
        }
        
        // Load avatar asynchronously
        CoroutineScope(Dispatchers.Main).launch {
            val avatar = loader.loadAvatar(user)
            if (avatar != null) {
                val drawable = BitmapDrawable(context.resources, avatar)
                drawable.setBounds(0, 0, 96, 96) // Set explicit size
                setIcon(drawable)
            }
        }
    }
}
