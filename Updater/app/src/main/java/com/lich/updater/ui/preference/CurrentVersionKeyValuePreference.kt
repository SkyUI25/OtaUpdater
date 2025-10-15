package com.lich.updater.ui.preference

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.lich.updater.R

class CurrentVersionKeyValuePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    var oneUiValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    var lichUiValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    var buildDateValue: CharSequence? = null
        set(value) {
            field = value
            notifyChanged()
        }

    init {
        layoutResource = R.layout.preference_current_version_kv
        isSelectable = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val oneUiText = holder.itemView.findViewById<TextView>(R.id.value_oneui)
        val lichUiText = holder.itemView.findViewById<TextView>(R.id.value_lichui)
        val buildDateText = holder.itemView.findViewById<TextView>(R.id.value_build_date)

        oneUiText.text = oneUiValue ?: context.getString(R.string.unknown)
        lichUiText.text = lichUiValue ?: context.getString(R.string.unknown)
        buildDateText.text = buildDateValue ?: context.getString(R.string.unknown)
    }
}


