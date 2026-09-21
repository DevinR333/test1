package com.blacklab.buddybounce

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.blacklab.buddybounce.audio.Audio
import com.blacklab.buddybounce.audio.Music
import com.blacklab.buddybounce.data.Save
import com.blacklab.buddybounce.ui.Theme

/**
 * The only activity. It owns the surface, the accelerometer, the orientation lock and the one
 * piece of real Android UI in the game: the name prompt (because nothing beats the system IME).
 */
class MainActivity : Activity(), SensorEventListener, Game.Host {

    private lateinit var save: Save
    private lateinit var audio: Audio
    private lateinit var game: Game
    private lateinit var surface: GameSurfaceView
    private lateinit var root: FrameLayout

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null

    private var nameOverlay: View? = null
    private var nameInput: EditText? = null
    private var nameTitle: TextView? = null
    private var nameCancel: Button? = null
    private lateinit var music: Music

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        save = Save(this)
        audio = Audio(this, save)
        music = Music(this, save)
        game = Game(save, audio, music, this)

        applyOrientation(save.landscape)

        root = FrameLayout(this)
        surface = GameSurfaceView(this, game)
        root.addView(surface, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        game.controls.tiltAvailable = accelerometer != null
    }

    // -------------------------------------------------------------------------------------
    // lifecycle
    // -------------------------------------------------------------------------------------

    override fun onResume() {
        super.onResume()
        goImmersive()
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        surface.startLoop()
        music.resume()
        surface.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
        game.onPauseApp()
        surface.stopLoop()
        music.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        audio.release()
        music.release()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goImmersive()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (nameOverlay?.visibility == View.VISIBLE) {
            if (save.hasName) hideNamePrompt()
            return
        }
        if (!game.onBackPressed()) super.onBackPressed()
    }

    private fun goImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        }
    }

    // -------------------------------------------------------------------------------------
    // tilt
    // -------------------------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        // Remap the device axes onto "screen right", then flip: tilting the right edge down
        // pushes the accelerometer negative along that axis, but should steer right.
        val steerAxis = when (currentRotation()) {
            Surface.ROTATION_90 -> y
            Surface.ROTATION_180 -> x
            Surface.ROTATION_270 -> -y
            else -> -x
        }
        game.controls.onTiltSample(steerAxis)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun currentRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    // -------------------------------------------------------------------------------------
    // Game.Host
    // -------------------------------------------------------------------------------------

    override fun setLandscape(landscape: Boolean) {
        runOnUiThread { applyOrientation(landscape) }
    }

    private fun applyOrientation(landscape: Boolean) {
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    override fun vibrateLater(delayMs: Long, ms: Long, amplitude: Int) {
        surface.postDelayed({ vibrate(ms, amplitude) }, delayMs)
    }

    override fun vibrate(ms: Long, amplitude: Int) {
        if (ms <= 0L) return
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val amp = if (amplitude <= 0) VibrationEffect.DEFAULT_AMPLITUDE else amplitude
        vibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
    }

    override fun promptName(current: String, title: String) {
        runOnUiThread { showNamePrompt(current, title) }
    }

    // -------------------------------------------------------------------------------------
    // name prompt
    // -------------------------------------------------------------------------------------

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
    ).toInt()

    private fun showNamePrompt(current: String, title: String) {
        if (nameOverlay == null) buildNamePrompt()
        nameTitle?.text = title
        nameInput?.setText(current)
        nameInput?.setSelection(current.length)
        nameCancel?.visibility = if (save.hasName) View.VISIBLE else View.GONE
        nameOverlay?.visibility = View.VISIBLE
        nameInput?.requestFocus()
        nameInput?.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
        }, 120L)
    }

    private fun hideNamePrompt() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(nameInput?.windowToken, 0)
        nameOverlay?.visibility = View.GONE
        surface.requestFocus()
        goImmersive()
    }

    private fun confirmName() {
        val entered = nameInput?.text?.toString().orEmpty()
        // Checked against the RAW text: the sanitiser strips the punctuation the code uses.
        if (game.isUnlockCode(entered)) {
            game.applyUnlockCode(entered)
            hideNamePrompt()
            return
        }
        val clean = Save.sanitizeName(entered)
        if (clean.isEmpty() && !save.hasName) return   // first launch needs a name
        if (clean.isNotEmpty()) game.onNameEntered(clean)
        hideNamePrompt()
    }

    private fun buildNamePrompt() {
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0xE60A0F1B.toInt())
        overlay.isClickable = true
        overlay.visibility = View.GONE

        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.gravity = Gravity.CENTER_HORIZONTAL
        card.setPadding(dp(24f), dp(28f), dp(24f), dp(20f))
        card.background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(Theme.PANEL_SOLID)
            setStroke(dp(1.5f), 0x33FFFFFF)
        }

        val heading = TextView(this)
        heading.text = getString(R.string.app_name)
        heading.setTextColor(Theme.ACCENT)
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        heading.letterSpacing = 0.22f
        heading.gravity = Gravity.CENTER
        card.addView(heading)

        val title = TextView(this)
        title.text = "What's your name?"
        title.setTextColor(Theme.TEXT)
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
        title.gravity = Gravity.CENTER
        title.setPadding(0, dp(6f), 0, dp(4f))
        card.addView(title)
        nameTitle = title

        // No explanation under the prompt. "What's your name?" is the whole question, and the
        // line that used to sit here read like a translation.
        val blurb = TextView(this)
        blurb.text = ""
        blurb.setTextColor(Theme.TEXT_DIM)
        blurb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        blurb.gravity = Gravity.CENTER
        blurb.setPadding(0, 0, 0, dp(10f))
        card.addView(blurb)

        val input = EditText(this)
        input.hint = getString(R.string.name_hint)
        input.setHintTextColor(0x66F3F6FB)
        input.setTextColor(Theme.TEXT)
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        input.gravity = Gravity.CENTER
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.imeOptions = EditorInfo.IME_ACTION_DONE
        input.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(12))
        input.background = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(Theme.PANEL_SUNK)
            setStroke(dp(1.5f), 0x33FFFFFF)
        }
        input.setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                confirmName(); true
            } else false
        }
        card.addView(input, LinearLayout.LayoutParams(dp(260f), LinearLayout.LayoutParams.WRAP_CONTENT))
        nameInput = input

        val go = Button(this)
        go.text = "LET'S BOUNCE"
        go.setTextColor(0xFF2A1D04.toInt())
        go.letterSpacing = 0.08f
        go.background = GradientDrawable().apply {
            cornerRadius = dp(26f).toFloat()
            setColor(Theme.ACCENT)
        }
        go.setOnClickListener { confirmName() }
        val goParams = LinearLayout.LayoutParams(dp(260f), dp(52f))
        goParams.topMargin = dp(18f)
        card.addView(go, goParams)

        val cancel = Button(this)
        cancel.text = "CANCEL"
        cancel.setTextColor(Theme.TEXT_DIM)
        cancel.setBackgroundColor(Color.TRANSPARENT)
        cancel.setOnClickListener { hideNamePrompt() }
        card.addView(cancel, LinearLayout.LayoutParams(dp(260f), dp(46f)))
        nameCancel = cancel

        val cardParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        )
        cardParams.gravity = Gravity.CENTER
        overlay.addView(card, cardParams)

        root.addView(overlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        nameOverlay = overlay
    }
}
