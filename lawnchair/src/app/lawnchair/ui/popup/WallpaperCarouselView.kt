package app.lawnchair.ui.popup

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import app.lawnchair.data.wallpaper.Wallpaper
import app.lawnchair.data.wallpaper.model.WallpaperViewModel
import app.lawnchair.launcher
import app.lawnchair.util.decodeSampledBitmapFromFile
import app.lawnchair.views.component.IconFrame
import app.lawnchair.views.createContainedLoadingIndicator
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WallpaperCarouselView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val launcher = context.launcher
    private val viewModel = WallpaperViewModel(launcher.application)
    private val deviceProfile = launcher.deviceProfile
    private var currentItemIndex = 0
    private val iconFrame = IconFrame(context).apply {
        setIcon(R.drawable.ic_tick)
        setBackgroundWithRadius(Themes.getColorAccent(context), 100F)
    }
    private val loadingView = createContainedLoadingIndicator(context)
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val decodeJobs = mutableListOf<Job>()
    private var lastDisplayedWallpapers: List<Wallpaper> = emptyList()
    private var lastDisplayedWidth: Int = 0

    init {
        orientation = HORIZONTAL
        addView(loadingView)
        observeWallpapers()
    }

    private fun observeWallpapers() {
        viewScope.launch {
            viewModel.wallpapers.collect { wallpapers ->
                visibility = if (wallpapers.isEmpty()) GONE else VISIBLE
                loadingView.visibility = if (wallpapers.isEmpty()) GONE else VISIBLE
                if (wallpapers.isNotEmpty()) {
                    displayWallpapers(wallpapers)
                }
            }
        }
    }

    private fun displayWallpapers(wallpapers: List<Wallpaper>) {
        if (wallpapers.isEmpty()) return
        val totalWidth = calculateTotalWidth()
        if (totalWidth <= 0) return

        // Avoid redundant rebuilds that discard already-decoded bitmaps for the exact same state.
        if (wallpapers == lastDisplayedWallpapers && totalWidth == lastDisplayedWidth && childCount == wallpapers.size) {
            return
        }
        lastDisplayedWallpapers = wallpapers
        lastDisplayedWidth = totalWidth
        if (currentItemIndex !in wallpapers.indices) {
            currentItemIndex = 0
        }

        decodeJobs.forEach { it.cancel() }
        decodeJobs.clear()
        removeAllViews()

        val firstItemWidth = if (wallpapers.size == 1) totalWidth.toDouble() else totalWidth * 0.4
        val itemWidth = calculateItemWidth(totalWidth, wallpapers.size, firstItemWidth)
        val margin = (totalWidth * 0.03).toInt()
        val targetHeight = height.takeIf { it > 0 }
            ?: resources.getDimensionPixelSize(R.dimen.wallpaper_carousel_height).coerceAtLeast(1)

        wallpapers.forEachIndexed { index, wallpaper ->
            val cardWidth = (if (index == currentItemIndex) firstItemWidth else itemWidth)
                .toInt()
                .coerceAtLeast(1)
            val cardView = createCardView(index, firstItemWidth, itemWidth, margin, wallpaper, cardWidth)
            val imageView = ImageView(context).apply {
                setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_deepshortcut_placeholder))
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            cardView.addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(cardView)
            if (index == currentItemIndex) {
                addIconFrameToCenter(cardView)
            }
            loadWallpaperImage(wallpaper, cardView, imageView, cardWidth, targetHeight, index == currentItemIndex)
        }
        loadingView.visibility = GONE
    }

    private fun calculateTotalWidth(): Int {
        return width.takeIf { it > 0 }
            ?: (deviceProfile.deviceProperties.widthPx * if (deviceProfile.deviceProperties.isLandscape || deviceProfile.deviceProperties.isPhone) 0.5 else 0.8).toInt()
    }

    private fun calculateItemWidth(totalWidth: Int, itemCount: Int, firstItemWidth: Double): Double {
        if (itemCount <= 1) return totalWidth.toDouble()
        val remainingWidth = totalWidth - firstItemWidth
        val marginBetweenItems = totalWidth * 0.03
        return ((remainingWidth - (marginBetweenItems * (itemCount - 1))) / (itemCount - 1))
            .coerceAtLeast(1.0)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createCardView(
        index: Int,
        firstItemWidth: Double,
        itemWidth: Double,
        margin: Int,
        wallpaper: Wallpaper,
        cardWidth: Int,
    ): CardView {
        return CardView(context).apply {
            radius = Themes.getDialogCornerRadius(context) / 2
            layoutParams = LayoutParams(
                cardWidth,
                LayoutParams.MATCH_PARENT,
            ).apply { setMargins(if (index > 0) margin else 0, 0, 0, 0) }

            isClickable = true
            setOnClickListener {
                if (index != currentItemIndex) {
                    animateWidthTransition(index, firstItemWidth, itemWidth)
                } else {
                    setWallpaper(wallpaper)
                }
            }
        }
    }

    private fun loadWallpaperImage(
        wallpaper: Wallpaper,
        cardView: CardView,
        imageView: ImageView,
        reqWidth: Int,
        reqHeight: Int,
        isCurrent: Boolean,
    ) {
        val job = viewScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeSampledWallpaper(wallpaper.imagePath, reqWidth, reqHeight)
            }
            if (bitmap != null) {
                imageView.alpha = 0f
                imageView.setImageBitmap(bitmap)
                imageView.animate().alpha(1f).setDuration(200L).withEndAction {
                    if (isCurrent) addIconFrameToCenter(cardView)
                }.start()
            } else if (isCurrent) {
                addIconFrameToCenter(cardView)
            }
        }
        decodeJobs.add(job)
    }

    /**
     * Decode the wallpaper roughly at the size it is shown at. Carousel thumbnails are small, so
     * decoding full-resolution wallpapers (which can be several megabytes each) risks OutOfMemory.
     */
    private fun decodeSampledWallpaper(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val file = File(path).takeIf { it.exists() } ?: return null
        if (reqWidth <= 0 || reqHeight <= 0) return BitmapFactory.decodeFile(file.path)
        return decodeSampledBitmapFromFile(file.path, reqWidth, reqHeight)
            ?: BitmapFactory.decodeFile(file.path)
    }

    private fun setWallpaper(wallpaper: Wallpaper) {
        val currentCardView = getChildAt(currentItemIndex) as? CardView ?: return
        val spinner = createLoadingSpinner()

        currentCardView.removeView(iconFrame)
        currentCardView.addView(spinner)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                WallpaperManager.getInstance(context).setBitmap(
                    BitmapFactory.decodeFile(wallpaper.imagePath),
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM,
                )
                viewModel.updateWallpaperRank(wallpaper)
            } catch (e: Exception) {
                Log.e("WallpaperCarouselView", "Failed to set wallpaper: ${e.message}")
            } finally {
                withContext(Dispatchers.Main) {
                    currentCardView.removeView(spinner)
                    addIconFrameToCenter(currentCardView)
                }
            }
        }
    }

    private fun createLoadingSpinner() = createContainedLoadingIndicator(context).apply {
        layoutParams = FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun addIconFrameToCenter(cardView: CardView? = getChildAt(currentItemIndex) as? CardView) {
        (iconFrame.parent as? ViewGroup)?.removeView(iconFrame)
        cardView?.addView(
            iconFrame,
            FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            },
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        decodeJobs.forEach { it.cancel() }
        decodeJobs.clear()
        viewModel.cleanup()
        viewScope.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && w != oldw) {
            val wallpapers = viewModel.wallpapers.value
            if (wallpapers.isNotEmpty()) {
                // Defer out of View.layout() so newly added CardViews are measured and laid out cleanly.
                post { displayWallpapers(wallpapers) }
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(calculateTotalWidth(), MeasureSpec.EXACTLY),
            heightMeasureSpec,
        )
    }

    private fun animateWidthTransition(newIndex: Int, firstItemWidth: Double, itemWidth: Double) {
        currentItemIndex = newIndex
        for (i in 0 until childCount) {
            (getChildAt(i) as? CardView)?.let { cardView ->
                val targetWidth = if (i == currentItemIndex) firstItemWidth.toInt() else itemWidth.toInt()
                if (cardView.layoutParams.width != targetWidth) {
                    ValueAnimator.ofInt(cardView.layoutParams.width, targetWidth).apply {
                        duration = 300L
                        addUpdateListener {
                            cardView.layoutParams.width = it.animatedValue as Int
                            cardView.requestLayout()
                        }
                        start()
                    }
                }
                if (i == currentItemIndex) addIconFrameToCenter(cardView)
            }
        }
    }
}
