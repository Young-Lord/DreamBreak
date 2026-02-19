package moe.lyniko.dreambreak.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import moe.lyniko.dreambreak.R
import moe.lyniko.dreambreak.core.BreakPhase
import moe.lyniko.dreambreak.core.BreakState
import moe.lyniko.dreambreak.core.SessionMode

class BreakOverlayController(
    private val context: Context,
    private val onInterruptBreak: () -> Unit,
    private val onPostponeBreak: (Int) -> Unit,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var promptView: TextView? = null
    private var fullScreenView: FrameLayout? = null
    private var backgroundImageView: ImageView? = null
    private var dimLayerView: View? = null
    private var fullTypeView: TextView? = null
    private var fullRemainingView: TextView? = null
    private var fullExitButton: Button? = null
    private var postponeOptionsPanel: LinearLayout? = null
    private var lastBackgroundUri: String = ""

    fun render(
        state: BreakState,
        appEnabled: Boolean,
        overlayBackgroundUri: String,
        overlayTransparencyPercent: Int,
    ) {
        if (!Settings.canDrawOverlays(context)) {
            hidePromptOverlay()
            hideFullScreenOverlay()
            return
        }

        if (!appEnabled) {
            hidePromptOverlay()
            hideFullScreenOverlay()
            return
        }

        val showPrompt = state.mode == SessionMode.BREAK && state.phase == BreakPhase.PROMPT
        val showFullScreen = state.mode == SessionMode.BREAK &&
            (state.phase == BreakPhase.FULL_SCREEN || state.phase == BreakPhase.POST)

        if (showPrompt) {
            showPromptOverlay()
        } else {
            hidePromptOverlay()
        }

        if (showFullScreen) {
            showFullScreenOverlay(state, overlayBackgroundUri, overlayTransparencyPercent)
        } else {
            hideFullScreenOverlay()
        }
    }

    fun release() {
        hidePromptOverlay()
        hideFullScreenOverlay()
    }

    private fun showPromptOverlay() {
        if (promptView != null) {
            return
        }

        val view = TextView(context).apply {
            text = context.getString(R.string.prompt_banner)
            setBackgroundColor(0xFFE57373.toInt())
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            textSize = 14f
            val animation = AlphaAnimation(0.35f, 1f).apply {
                duration = 450
                repeatMode = Animation.REVERSE
                repeatCount = Animation.INFINITE
            }
            startAnimation(animation)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            dp(42),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP
            applyEdgeToEdge(this)
        }

        windowManager.addView(view, params)
        promptView = view
    }

    private fun hidePromptOverlay() {
        val view = promptView ?: return
        runCatching {
            view.clearAnimation()
            windowManager.removeView(view)
        }
        promptView = null
    }

    private fun showFullScreenOverlay(
        state: BreakState,
        overlayBackgroundUri: String,
        overlayTransparencyPercent: Int,
    ) {
        if (fullScreenView == null) {
            val root = FrameLayout(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                setOnTouchListener { _, _ -> true }
            }

            val backgroundImage = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = View.GONE
            }
            root.addView(
                backgroundImage,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )

            val dimLayer = View(context)
            root.addView(
                dimLayer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            )

            val container = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(32), dp(24), dp(24))
            }

            val typeView = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 44f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }

            val remainingView = TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 96f
                typeface = Typeface.MONOSPACE
                gravity = Gravity.CENTER
            }

            val actionRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            val exitButton = createActionButton(
                text = context.getString(R.string.action_exit),
                backgroundColor = 0xFF5E4F54.toInt(),
                onClick = {
                    onInterruptBreak()
                    postponeOptionsPanel?.visibility = View.GONE
                },
            )

            val postponeButton = createActionButton(
                text = context.getString(R.string.action_postpone),
                backgroundColor = 0xFF4E6377.toInt(),
                onClick = {
                    postponeOptionsPanel?.visibility = if (postponeOptionsPanel?.visibility == View.VISIBLE) {
                        View.GONE
                    } else {
                        View.VISIBLE
                    }
                },
            )

            actionRow.addView(
                exitButton,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    rightMargin = dp(6)
                }
            )
            actionRow.addView(
                postponeButton,
                LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    leftMargin = dp(6)
                }
            )

            val optionsPanel = buildPostponePanel()

            container.addView(typeView)
            container.addView(remainingView)

            val containerParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            )
            root.addView(container, containerParams)

            val actionParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(52)
                leftMargin = dp(64)
                rightMargin = dp(64)
            }
            root.addView(actionRow, actionParams)

            val optionsParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).apply {
                bottomMargin = dp(112)
                leftMargin = dp(28)
                rightMargin = dp(28)
            }
            root.addView(optionsPanel, optionsParams)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                applyEdgeToEdge(this)
            }

            windowManager.addView(root, params)
            fullScreenView = root
            backgroundImageView = backgroundImage
            dimLayerView = dimLayer
            fullTypeView = typeView
            fullRemainingView = remainingView
            fullExitButton = exitButton
            postponeOptionsPanel = optionsPanel
        }

        applyOverlayBackground(overlayBackgroundUri, overlayTransparencyPercent)

        val breakTypeRes = if (state.isBigBreak) R.string.break_type_big else R.string.break_type_small
        fullTypeView?.text = context.getString(breakTypeRes)
        fullRemainingView?.text = formatSeconds(state.breakSecondsRemaining)
        fullExitButton?.isEnabled = state.phase == BreakPhase.FULL_SCREEN || state.phase == BreakPhase.POST
    }

    private fun applyOverlayBackground(overlayBackgroundUri: String, overlayTransparencyPercent: Int) {
        val imageView = backgroundImageView
        val dimLayer = dimLayerView
        if (imageView == null || dimLayer == null) {
            return
        }

        val normalizedUri = overlayBackgroundUri.trim()
        if (normalizedUri.isBlank()) {
            imageView.visibility = View.GONE
            imageView.setImageDrawable(null)
            lastBackgroundUri = ""
        } else {
            if (lastBackgroundUri != normalizedUri) {
                runCatching {
                    imageView.setImageURI(Uri.parse(normalizedUri))
                    lastBackgroundUri = normalizedUri
                    imageView.visibility = View.VISIBLE
                }.onFailure {
                    imageView.setImageDrawable(null)
                    imageView.visibility = View.GONE
                    lastBackgroundUri = ""
                }
            } else {
                imageView.visibility = View.VISIBLE
            }
        }

        val dimAlpha = ((100 - overlayTransparencyPercent.coerceIn(0, 100)) / 100f)
            .coerceIn(0f, 1f)
        dimLayer.setBackgroundColor(Color.BLACK)
        dimLayer.alpha = dimAlpha
    }

    private fun buildPostponePanel(): LinearLayout {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xEE1A1A1A.toInt())
                setStroke(dp(1), 0xFF3A3A3A.toInt())
            }
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.postpone_choose_title)
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }

        val grid = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }

        val options = listOf(
            60 to R.string.postpone_option_1m,
            5 * 60 to R.string.postpone_option_5m,
            15 * 60 to R.string.postpone_option_15m,
            30 * 60 to R.string.postpone_option_30m,
        )
        options.chunked(2).forEach { chunk ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

            chunk.forEachIndexed { index, (seconds, labelRes) ->
                row.addView(
                    createActionButton(
                        text = context.getString(labelRes),
                        backgroundColor = 0xFF3A3A3A.toInt(),
                        onClick = {
                            onPostponeBreak(seconds)
                            panel.visibility = View.GONE
                        },
                    ),
                    LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                        if (index == 0) rightMargin = dp(6) else leftMargin = dp(6)
                    }
                )
            }

            grid.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (grid.childCount > 0) topMargin = dp(8)
                }
            )
        }

        panel.addView(title)
        panel.addView(grid)
        return panel
    }

    private fun createActionButton(
        text: String,
        backgroundColor: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(context).apply {
            this.text = text
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12).toFloat()
                setColor(backgroundColor)
            }
            elevation = dp(1).toFloat()
            setOnClickListener { onClick() }
        }
    }

    private fun hideFullScreenOverlay() {
        val view = fullScreenView ?: return
        runCatching { windowManager.removeView(view) }
        fullScreenView = null
        backgroundImageView = null
        dimLayerView = null
        fullTypeView = null
        fullRemainingView = null
        fullExitButton = null
        postponeOptionsPanel = null
        lastBackgroundUri = ""
    }

    @Suppress("DEPRECATION")
    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun dp(value: Int): Int {
        val density = context.resources.displayMetrics.density
        return (value * density).toInt()
    }

    private fun applyEdgeToEdge(params: WindowManager.LayoutParams) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.setFitInsetsTypes(0)
            params.setFitInsetsSides(0)
            params.isFitInsetsIgnoringVisibility = true
        }
    }

    private fun formatSeconds(rawSeconds: Int): String {
        val safe = rawSeconds.coerceAtLeast(0)
        val minutePart = safe / 60
        val secondPart = safe % 60
        return "%02d:%02d".format(minutePart, secondPart)
    }
}
