package com.blacklab.buddybounce

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets
import kotlin.math.abs

/**
 * Host for the game loop. Rendering runs on its own thread against a hardware canvas; input
 * arrives on the UI thread, so both sides take the same lock around game state.
 */
class GameSurfaceView(context: Context, private val game: Game) : SurfaceView(context), SurfaceHolder.Callback {

    private var thread: RenderThread? = null
    private val lock = Any()
    private var activePointerId = -1

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
        keepScreenOn = true
    }

    // -------------------------------------------------------------------------------------
    // surface lifecycle
    // -------------------------------------------------------------------------------------

    override fun surfaceCreated(holder: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(lock) {
            game.onSurface(width, height)
            applyInsets()
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    fun startLoop() {
        if (thread?.isAlive == true) return
        val t = RenderThread()
        thread = t
        t.start()
    }

    fun stopLoop() {
        val t = thread ?: return
        t.running = false
        try {
            t.join(800)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        thread = null
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        synchronized(lock) { applyInsets(insets) }
        return super.onApplyWindowInsets(insets)
    }

    private fun applyInsets(insets: WindowInsets? = rootWindowInsets) {
        val i = insets ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bars = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            game.setInsets(bars.top, bars.bottom, bars.left, bars.right)
        } else {
            @Suppress("DEPRECATION")
            game.setInsets(i.systemWindowInsetTop, i.systemWindowInsetBottom, i.systemWindowInsetLeft, i.systemWindowInsetRight)
        }
    }

    // -------------------------------------------------------------------------------------
    // input
    // -------------------------------------------------------------------------------------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        synchronized(lock) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                    val index = event.actionIndex
                    if (activePointerId == -1) {
                        activePointerId = event.getPointerId(index)
                        game.onPointerDown(event.getX(index), event.getY(index))
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(activePointerId)
                    if (index >= 0) game.onPointerMove(event.getX(index), event.getY(index))
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                    val index = event.actionIndex
                    if (event.getPointerId(index) == activePointerId) {
                        game.onPointerUp(event.getX(index), event.getY(index))
                        activePointerId = -1
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    game.onPointerUp(event.x, event.y)
                    activePointerId = -1
                }
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(keyCode, true)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKey(keyCode, false)) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Every controller Android knows about reports through the same key codes, so there is no
     * per-brand mapping here and none is needed: an Xbox A, a PlayStation cross, a Switch B
     * (its physical bottom button) and a Retroid A all arrive as KEYCODE_BUTTON_A, and the
     * right-hand cancel button on all of them arrives as KEYCODE_BUTTON_B.
     *
     * In a menu the d-pad moves the focus ring; in a run the same keys steer Buddy. That split
     * is the only thing this has to decide.
     */
    private fun handleKey(keyCode: Int, down: Boolean): Boolean {
        synchronized(lock) {
            val inMenu = !game.padSteers()
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A -> {
                    if (inMenu) { if (down) game.onPadNav(-1, 0) } else game.onKeyLeft(down)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_D -> {
                    if (inMenu) { if (down) game.onPadNav(1, 0) } else game.onKeyRight(down)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_W -> {
                    if (inMenu && down) game.onPadNav(0, -1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_S -> {
                    if (inMenu && down) game.onPadNav(0, 1)
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (!down) game.onConfirmKey()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_ESCAPE -> {
                    if (!down) game.onBackPressed()
                    return true
                }
            }
        }
        return false
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            var axis = event.getAxisValue(MotionEvent.AXIS_X)
            if (abs(axis) < 0.08f) axis = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            var axisY = event.getAxisValue(MotionEvent.AXIS_Y)
            if (abs(axisY) < 0.08f) axisY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            synchronized(lock) {
                if (game.padSteers()) {
                    game.onPadAxis(axis)
                } else {
                    // A stick is analogue and a menu is not, so it is turned into discrete
                    // steps: one move per push past the threshold, nothing more until it comes
                    // back to centre. Without the latch a held stick scrolls the whole menu in
                    // a couple of frames.
                    game.onPadStick(axis, axisY)
                }
            }
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // -------------------------------------------------------------------------------------
    // render loop
    // -------------------------------------------------------------------------------------

    private inner class RenderThread : Thread("buddy-render") {
        @Volatile var running = true

        override fun run() {
            var last = System.nanoTime()
            while (running) {
                val now = System.nanoTime()
                var dt = (now - last) / 1_000_000_000f
                last = now
                if (dt > 0.05f) dt = 0.05f      // a stall must not teleport the simulation

                var canvas: Canvas? = null
                try {
                    val h = holder
                    if (!h.surface.isValid) {
                        Thread.sleep(8)
                        continue
                    }
                    canvas = h.lockHardwareCanvas() ?: h.lockCanvas()
                    if (canvas != null) {
                        synchronized(lock) {
                            game.update(dt)
                            game.draw(canvas)
                        }
                    }
                } catch (t: Throwable) {
                    // A surface can disappear mid-frame during rotation; just try again.
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (t: Throwable) {
                            // surface already gone
                        }
                    }
                }
            }
        }
    }
}
