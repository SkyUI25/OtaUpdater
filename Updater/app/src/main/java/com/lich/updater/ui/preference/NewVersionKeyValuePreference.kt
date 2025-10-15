package com.lich.updater.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lich.updater.R

class NewVersionKeyValuePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var buildDateValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    var versionValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    var sizeValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_new_version_kv
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val buildDateText = holder.itemView.findViewById<TextView>(R.id.value_build_date)
        val versionText = holder.itemView.findViewById<TextView>(R.id.value_version)
        val sizeText = holder.itemView.findViewById<TextView>(R.id.value_size)

        buildDateText.text = buildDateValue ?: context.getString(R.string.unknown)
        versionText.text = versionValue ?: context.getString(R.string.unknown)
        sizeText.text = sizeValue ?: context.getString(R.string.unknown)
    }
}


