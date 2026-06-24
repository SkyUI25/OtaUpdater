package com.lich.updater.ui.preference
import com.lich.updater.MainActivity

import android.content.Context

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lich.updater.R
import android.text.style.MetricAffectingSpan
import android.text.TextPaint
import android.transition.TransitionManager
import android.view.ViewGroup
import android.view.View
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.graphics.drawable.GradientDrawable

class StatusCardPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {
    private var statusText: String = ""
    private var subtitleText: String = ""
    private var isBlinking: Boolean = false
    private var prevStatus: String = ""
    private var lastHeight: Int = -1
    private var isWaveAnimating: Boolean = false

    init {
        layoutResource = R.layout.preference_status_card
        isSelectable = false
    }
    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val textView = holder.findViewById(R.id.text_status) as? TextView
        val subtitleView = holder.findViewById(R.id.text_last_check) as? TextView
        
        // Samsung View Mapping
        val shadowView = holder.findViewById(R.id.shadow_view)
        val overlayView = holder.findViewById(R.id.overlay_view)
        val contentView = holder.findViewById(R.id.content)
        val density = context.resources.displayMetrics.density
        val rootContainer = holder.findViewById(R.id.status_view_root_container)
        
        // Forced Samsung 26dp Outline Provider (Ensures shadow matches shape)
        val samsungOutlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 26f * density)
            }
        }
        
        // Samsung elevation hierarchy
        shadowView?.elevation = 4f * density
        val gradientView = holder.findViewById(R.id.gradient_view) as? ImageView
        gradientView?.elevation = 12f * density
        overlayView?.elevation = 6f * density
        contentView?.elevation = 7f * density
        
        // Samsung "Background Transplant" - Adapt Real Preference style
        var nativeBg = holder.itemView.getTag(R.id.status_view_root_container) as? android.graphics.drawable.Drawable
        if (nativeBg == null) {
            nativeBg = holder.itemView.background
            if (nativeBg != null) {
                holder.itemView.setTag(R.id.status_view_root_container, nativeBg)
            }
        }
        
        if (nativeBg != null) {
            // Shadow Layer (shadow_view) - Must be SOLID to cast strong shadow
            val shadowBg = nativeBg.constantState?.newDrawable()?.mutate()
            shadowBg?.alpha = 255 // Ensure 100% solid
            shadowView?.background = shadowBg
            shadowView?.outlineProvider = samsungOutlineProvider
            shadowView?.clipToOutline = true
            
            // High-Fidelity Shadow Relief: Force Spot and Ambient shadow colors
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                shadowView?.outlineSpotShadowColor = 0x99000000.toInt() // 60% Black
                shadowView?.outlineAmbientShadowColor = 0xFF000000.toInt() // 100% Black
            }
            
            // Glass Layer (overlay_view)
            val glassBg = nativeBg.constantState?.newDrawable()?.mutate()
            glassBg?.alpha = 115 // dashboard_status_card_alpha (0.45)
            
            // Apply 0.5dp White Stroke (The "Borders" request)
            if (glassBg is GradientDrawable) {
                glassBg.setStroke((0.5f * density).toInt(), Color.argb(26, 255, 255, 255))
            }
            
            overlayView?.background = glassBg
            
            // CRITICAL: Samsung logic - glass has NO outline provider to avoid double casting
            overlayView?.outlineProvider = null 
            overlayView?.clipToOutline = false
            
            // Content Layer elevation matching (Topmost)
            contentView?.outlineProvider = samsungOutlineProvider
            contentView?.clipToOutline = false
            
            // Prevent Preference/RecyclerView from clipping internal shadows
            holder.itemView.background = null
            rootContainer?.setBackgroundColor(Color.TRANSPARENT)
            
            // Deep anti-clipping traverse (Critical for spill)
            var current: View? = holder.itemView
            while (current != null && current is ViewGroup) {
                current.clipChildren = false
                current.clipToPadding = false
                current = current.parent as? View
            }
        }
        
        // Apply Blur
        applyPreferenceEffects(overlayView)
        
        // Height Animation (Visual card size 150dp/100dp + 32dp total margins)
        val normalHeight = (182 * density).toInt()
        val shrunkenHeight = (132 * density).toInt() 
        val targetHeight = if (isBlinking) shrunkenHeight else normalHeight
        
        val targetView = holder.itemView
        if (lastHeight == -1) {
            targetView.layoutParams.height = targetHeight
            lastHeight = targetHeight
        } else if (lastHeight != targetHeight) {
            animateHeight(targetView, lastHeight, targetHeight)
            lastHeight = targetHeight
        }

        textView?.text = statusText
        
        if (isBlinking) {
            val anim = android.view.animation.AlphaAnimation(0.2f, 1.0f).apply {
                duration = 2000
                repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            textView?.startAnimation(anim)
            subtitleView?.visibility = View.GONE
            gradientView?.visibility = View.GONE
            gradientView?.alpha = 0f
        } else {
            textView?.clearAnimation()
            
            // Generate a 'base' status to avoid re-animating when only dots change
            val currentBaseStatus = statusText.trimEnd('.')
            val prevBaseStatus = prevStatus.trimEnd('.')
            
            val isNewStatus = currentBaseStatus != prevBaseStatus
            
            if (isNewStatus) {
                // Initial state for animation: subtitleView and glow are GONE
                subtitleView?.visibility = View.GONE
                gradientView?.visibility = View.GONE
                gradientView?.alpha = 0f
                
                textView?.let { 
                    startWaveAnimation(it, statusText) {
                        // After wave finishes, if we have subtitle OR it's ready to install, animate it
                        if (subtitleText.isNotEmpty() || statusText == context.getString(R.string.install_ready)) {
                            val container = holder.itemView as? ViewGroup
                            if (container != null) {
                                // Special Case: ROM ready for installation -> show Tip instead of Rom version
                                val displaySubtitle = if (statusText == context.getString(R.string.install_ready)) {
                                    context.getString(R.string.backup_tip)
                                } else {
                                    subtitleText
                                }
                                
                                TransitionManager.beginDelayedTransition(container)
                                subtitleView?.text = displaySubtitle
                                subtitleView?.visibility = View.VISIBLE
                                
                                // Entrance animation for subtitleView
                                subtitleView?.alpha = 0f
                                subtitleView?.translationY = 20f
                                subtitleView?.animate()
                                    ?.alpha(1f)
                                    ?.translationY(0f)
                                    ?.setDuration(500)
                                    ?.setInterpolator(DecelerateInterpolator())
                                    ?.withEndAction {
                                        // FINALLY: Fade in the glow after subtitle is done
                                        applyStatusGlow(gradientView)
                                        gradientView?.alpha = 0f
                                        gradientView?.animate()?.alpha(1f)?.setDuration(1000)?.start()
                                    }
                                    ?.start()
                            }
                        } else {
                            // No subtitle, show glow immediately
                            applyStatusGlow(gradientView)
                            gradientView?.alpha = 0f
                            gradientView?.animate()?.alpha(1f)?.setDuration(1000)?.start()
                        }
                    }
                }
            } else {
                // Regular binding for stability
                // CRITICAL: If wave is animating, skip this to avoid "popping" subtitle/glow
                if (!isWaveAnimating) {
                    if (statusText == context.getString(R.string.install_ready)) {
                        subtitleView?.text = context.getString(R.string.backup_tip)
                        subtitleView?.visibility = View.VISIBLE
                        subtitleView?.alpha = 1f
                        subtitleView?.translationY = 0f
                        applyStatusGlow(gradientView)
                        gradientView?.alpha = 1f
                    } else if (subtitleText.isNotEmpty() && !isBlinking) {
                        subtitleView?.text = subtitleText
                        subtitleView?.visibility = View.VISIBLE
                        subtitleView?.alpha = 1f
                        subtitleView?.translationY = 0f
                        applyStatusGlow(gradientView)
                        gradientView?.alpha = 1f
                    } else {
                        subtitleView?.visibility = View.GONE
                        if (statusText == context.getString(R.string.internet_not_available)) {
                            applyStatusGlow(gradientView)
                            gradientView?.alpha = 1f
                        } else {
                            gradientView?.visibility = View.GONE
                        }
                    }
                    
                    if (textView?.text.toString() != statusText) {
                        textView?.text = statusText
                    }
                }
            }
        }
        
        if (statusText.isNotEmpty()) {
            prevStatus = statusText
        }
    }

    private fun startWaveAnimation(textView: TextView, text: String, onComplete: (() -> Unit)? = null) {
        isWaveAnimating = true
        val builder = SpannableStringBuilder(text)
        val baseColor = textView.currentTextColor
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)
        
        val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 1500
        animator.interpolator = DecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val progress = animation.animatedValue as Float
            builder.clearSpans()
            
            for (i in text.indices) {
                // Calculation for sequential entrance and wave effect
                // Each letter starts with a delay relative to its position
                val letterDelay = i.toFloat() / text.length * 0.5f
                val letterProgress = ((progress - letterDelay) / 0.5f).coerceIn(0f, 1f)
                
                if (letterProgress > 0) {
                    val alpha = (letterProgress * 255).toInt()
                    // Shift effect: move from below (baseline shift)
                    val shift = ((1f - letterProgress) * 20).toInt()
                    
                    builder.setSpan(
                        ForegroundColorSpan(Color.argb(alpha, r, g, b)),
                        i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.setSpan(
                        CustomBaselineShiftSpan(shift),
                        i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    // Hidden until progress reaches its delay
                    builder.setSpan(
                        ForegroundColorSpan(Color.TRANSPARENT),
                        i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            textView.text = builder
        }
        
        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isWaveAnimating = false
                onComplete?.invoke()
            }
        })
        
        animator.start()
    }

    // Helper span to animate vertical position
    private class CustomBaselineShiftSpan(val shift: Int) : MetricAffectingSpan() {
        override fun updateMeasureState(tp: TextPaint) {
            tp.baselineShift -= shift
        }
        override fun updateDrawState(tp: TextPaint) {
            tp.baselineShift -= shift
        }
    }

    private fun applyStatusGlow(view: ImageView?) {
        view?.let { glowView ->
            if (statusText.isEmpty() || isBlinking) {
                glowView.visibility = View.GONE
                return
            }

            val density = context.resources.displayMetrics.density
            
            val isUpdateAvailable = statusText.contains("Update", ignoreCase = true) || 
                                   statusText.contains("actualización", ignoreCase = true)
            val isUpToDate = statusText.contains("up to date", ignoreCase = true) || 
                             statusText.contains("actualizado", ignoreCase = true)
            val isNoInternet = statusText == context.getString(R.string.internet_not_available)
            val isProcess = statusText == context.getString(R.string.downloading) || 
                            statusText == context.getString(R.string.verifying_md5)
            
            val useBlue = isUpdateAvailable || isProcess || (!isUpToDate && statusText.isNotEmpty()) && !isNoInternet
            
            var centerColor = if (useBlue) Color.parseColor("#33399efd") else Color.parseColor("#333ec822")
            var edgeColor = if (useBlue) Color.parseColor("#00399efd") else Color.parseColor("#003ec822")
            
            if (isNoInternet) {
                centerColor = Color.parseColor("#33f44336") // OneUI Red
                edgeColor = Color.parseColor("#00f44336")
            }
            
            // Special case: Both Downloading and MD5 use the same specific Blue
            if (isProcess) {
                centerColor = Color.parseColor("#33399efd")
                edgeColor = Color.parseColor("#00399efd")
            }
            
            val gradient = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                colors = intArrayOf(centerColor, edgeColor)
                gradientRadius = 85f * density // Reverting to Samsung Original
                setGradientCenter(0.5f, 0.82f) // Reverting to Samsung Original (82% from top)
            }
            
            glowView.setImageDrawable(gradient)
            glowView.visibility = View.VISIBLE
        }
    }

    private fun applyPreferenceEffects(view: View?) {
        view?.let { overlay ->
            // Apply blue-tinted elevation shadow (API 28+) - Matching Samsung Relief
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                overlay.outlineSpotShadowColor = Color.rgb(0, 120, 255) // Blue Spot for relief
                overlay.outlineAmbientShadowColor = Color.argb(40, 0, 0, 0) // Black Ambient for top shadow
            }
            
            // Apply RenderEffect blur (API 31+) - Glassmorphism
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurEffect = RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                overlay.setRenderEffect(blurEffect)
            }
        }
    }

    private fun animateHeight(view: View, start: Int, end: Int) {
        val animator = android.animation.ValueAnimator.ofInt(start, end)
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Int
            val lp = view.layoutParams
            lp.height = value
            view.layoutParams = lp
            
            // Force parent to acknowledge change
            view.parent?.requestLayout()
        }
        animator.duration = 400
        animator.interpolator = DecelerateInterpolator()
        animator.start()
    }

    fun setStatus(text: String, blinking: Boolean = false, subtitle: String = "") {
        statusText = text
        isBlinking = blinking
        subtitleText = subtitle
        notifyChanged()
    }
}
