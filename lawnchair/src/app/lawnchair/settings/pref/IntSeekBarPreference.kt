package app.lawnchair.settings.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.R

class IntSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    var min: Int = 0
    var max: Int = 100
    var value: Int = 0
        set(newVal) {
            val clamped = newVal.coerceIn(min, max)
            if (field != clamped) {
                field = clamped
                persistInt(clamped)
                notifyChanged()
            }
        }

    init {
        layoutResource = R.layout.preference_float_seekbar
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.IntSeekBarPreference)
            min = ta.getInt(R.styleable.IntSeekBarPreference_android_min, 0)
            max = ta.getInt(R.styleable.IntSeekBarPreference_android_max, 100)
            ta.recycle()
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any = a.getInt(index, min)

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedInt((defaultValue as? Int) ?: min)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.seekbar) as? SeekBar ?: return
        val valueText = holder.findViewById(R.id.seekbar_value) as? TextView ?: return

        seekBar.max = max - min
        seekBar.progress = value - min
        valueText.text = value.toString()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newVal = min + progress
                    valueText.text = newVal.toString()
                    if (callChangeListener(newVal)) {
                        value = newVal
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    override fun isPersistent(): Boolean = false
}