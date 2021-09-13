package openfoodfacts.github.scrachx.openfood.features.simplescan

import android.animation.LayoutTransition
import android.content.Context
import android.util.AttributeSet
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import openfoodfacts.github.scrachx.openfood.R
import java.util.concurrent.atomic.AtomicBoolean


class SimpleScanDialogView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    companion object {
        private const val ANIM_DURATION = 500L
    }

    private val isHidden = AtomicBoolean(true)

    init {
        inflate(context, R.layout.view_simple_scan_dialog, this)
        layoutTransition = LayoutTransition()
        isVisible = false
    }

    fun close() {
        if (!isHidden.getAndSet(true)) {
            hide()
        }
    }

    fun showLoadingState() {

    }

    fun showManualInputState() {

    }

    fun showErrorState() {

    }

    private fun show() {
        isVisible = true
        val animation = TranslateAnimation(0f, 0f, height.toFloat(), 0f).apply {
            duration = ANIM_DURATION
            fillAfter = true
        }
        startAnimation(animation)
    }

    private fun hide() {
        val animation = TranslateAnimation(0f, 0f, 0f, height.toFloat()).apply {
            duration = ANIM_DURATION
            fillAfter = true
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    isVisible = false
                }

                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        startAnimation(animation)
    }
}
