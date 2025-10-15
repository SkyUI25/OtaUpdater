package com.lich.updater.UI.About

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF

object BitmapUtils {
    
    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }
        
        val rect = Rect(0, 0, size, size)
        val rectF = RectF(rect)
        
        // Draw circle
        canvas.drawOval(rectF, paint)
        
        // Use source in mode to crop the bitmap
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        
        // Draw the bitmap centered
        val srcRect = if (bitmap.width > bitmap.height) {
            Rect((bitmap.width - bitmap.height) / 2, 0, 
                 (bitmap.width + bitmap.height) / 2, bitmap.height)
        } else {
            Rect(0, (bitmap.height - bitmap.width) / 2, 
                 bitmap.width, (bitmap.height + bitmap.width) / 2)
        }
        
        canvas.drawBitmap(bitmap, srcRect, rect, paint)
        
        return output
    }
}
