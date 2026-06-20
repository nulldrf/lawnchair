package app.lawnchair.smartspace

import android.content.Context
import android.graphics.Color
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import app.lawnchair.smartspace.model.SmartspaceTarget
import com.android.launcher3.R
import com.android.launcher3.util.Themes

class CardPagerAdapter(context: Context) : PagerAdapter() {

    // Computed once at construction as a fallback. May be overridden reactively
    // via updateTextColor() when the user picks a custom workspaceIconTextColor.
    // Falls back to white when workspaceTextColor resolves to 0 (e.g. widget host context).
    private var currentTextColor = Themes.getAttrColor(context, R.attr.workspaceTextColor)
        .takeIf { it != 0 } ?: Color.WHITE

    private val targets = mutableListOf<SmartspaceTarget>()
    private var smartspaceTargets = targets
    private val holders = SparseArray<ViewHolder>()

    fun setTargets(newTargets: List<SmartspaceTarget>) {
        targets.clear()
        targets.addAll(newTargets)
        notifyDataSetChanged()
    }

    /**
     * Updates the text color used for all cards and immediately re-applies it
     * to any currently bound holders, so a color preference change reflects
     * without needing to wait for the next target update.
     */
    fun updateTextColor(color: Int) {
        if (currentTextColor == color) return
        currentTextColor = color
        for (i in 0 until holders.size()) {
            holders.valueAt(i).card.setPrimaryTextColor(currentTextColor)
        }
    }

    override fun instantiateItem(container: ViewGroup, position: Int): ViewHolder {
        val target = smartspaceTargets[position]
        val card = createBaseCard(container, getFeatureType(target))
        val viewHolder = ViewHolder(position, card, target)
        onBindViewHolder(viewHolder)
        container.addView(card)
        holders.put(position, viewHolder)
        return viewHolder
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val viewHolder = obj as ViewHolder
        container.removeView(viewHolder.card)
        if (holders[position] == viewHolder) {
            holders.remove(position)
        }
    }

    fun getCardAtPosition(position: Int) = holders[position]?.card

    override fun getItemPosition(obj: Any): Int {
        val viewHolder = obj as ViewHolder
        val target = getTargetAtPosition(viewHolder.position)
        if (viewHolder.target === target) {
            return POSITION_UNCHANGED
        }
        if (target == null ||
            getFeatureType(target) !== getFeatureType(viewHolder.target) ||
            target.id != viewHolder.target.id
        ) {
            return POSITION_NONE
        }
        viewHolder.target = target
        onBindViewHolder(viewHolder)
        return POSITION_UNCHANGED
    }

    private fun getTargetAtPosition(position: Int): SmartspaceTarget? {
        if (position !in 0 until smartspaceTargets.size) {
            return null
        }
        return smartspaceTargets[position]
    }

    private fun onBindViewHolder(viewHolder: ViewHolder) {
        val target = smartspaceTargets[viewHolder.position]
        val card = viewHolder.card
        card.setSmartspaceTarget(target, smartspaceTargets.size > 1)
        card.setPrimaryTextColor(currentTextColor)
    }

    override fun getCount() = smartspaceTargets.size

    override fun isViewFromObject(view: View, obj: Any): Boolean {
        return view === (obj as ViewHolder).card
    }

    private fun createBaseCard(
        container: ViewGroup,
        featureType: SmartspaceTarget.FeatureType,
    ): BcSmartspaceCard {
        val layout = when (featureType) {
            SmartspaceTarget.FeatureType.FEATURE_WEATHER -> R.layout.smartspace_card_date
            else -> R.layout.smartspace_card
        }
        return LayoutInflater.from(container.context)
            .inflate(layout, container, false) as BcSmartspaceCard
    }

    private fun getFeatureType(target: SmartspaceTarget) = target.featureType

    class ViewHolder internal constructor(
        val position: Int,
        val card: BcSmartspaceCard,
        var target: SmartspaceTarget,
    )
}
