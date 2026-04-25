package app.lawnchair.settings.ui.preference

import android.content.Context
import android.content.res.TypedArray
import android.os.Bundle
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.android.launcher3.R
import kotlin.math.roundToInt

class FloatSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Preference(context, attrs, defStyleAttr) {

    var minValue: Float = 0f
    var maxValue: Float = 1f
    var stepSize: Float = 0.1f
    var showAsPercentage: Boolean = true
    var value: Float = 0f
        set(newVal) {
            val clamped = newVal.coerceIn(minValue, maxValue)
            if (field != clamped) {
                field = clamped
                persistFloat(clamped)
                notifyChanged()
            }
        }

    private val steps: Int get() = ((maxValue - minValue) / stepSize).roundToInt()

    init {
        layoutResource = R.layout.preference_float_seekbar
        attrs?.let {
            val ta = context.obtainStyledAttributes(it, R.styleable.FloatSeekBarPreference)
            minValue = ta.getFloat(R.styleable.FloatSeekBarPreference_minValue, 0f)
            maxValue = ta.getFloat(R.styleable.FloatSeekBarPreference_maxValue, 1f)
            stepSize = ta.getFloat(R.styleable.FloatSeekBarPreference_stepSize, 0.1f)
            showAsPercentage = ta.getBoolean(R.styleable.FloatSeekBarPreference_showAsPercentage, true)
            ta.recycle()
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getFloat(index, minValue)
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        value = getPersistedFloat((defaultValue as? Float) ?: minValue)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val seekBar = holder.findViewById(R.id.seekbar) as? SeekBar ?: return
        val valueText = holder.findViewById(R.id.seekbar_value) as? TextView ?: return

        seekBar.max = steps
        seekBar.progress = ((value - minValue) / stepSize).roundToInt()
        valueText.text = formatValue(value)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newVal = minValue + progress * stepSize
                    val snapped = (newVal / stepSize).roundToInt() * stepSize
                    valueText.text = formatValue(snapped)
                    if (callChangeListener(snapped)) {
                        value = snapped
                    }
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun formatValue(v: Float): String {
        return if (showAsPercentage) {
            "${(v * 100).roundToInt()}%"
        } else {
            String.format("%.1f", v)
        }
    }

    override fun isPersistent(): Boolean = false
}