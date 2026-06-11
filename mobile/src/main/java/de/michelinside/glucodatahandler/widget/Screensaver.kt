package de.michelinside.glucodatahandler.widget

import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.View
import android.widget.ImageView
import de.michelinside.glucodatahandler.R
import de.michelinside.glucodatahandler.common.utils.Log

class Screensaver: DreamService() {
    private val LOG_ID = "GDH.Screensaver"
    private lateinit var imageView: ImageView
    private lateinit var container: View
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var screensaverHandler: ScreensaverHandler
    private var firstRun = true

    // Settings for movement
    private val moveInterval = 90000L // Move every x seconds (if there was no update)
    private val animationDuration = 8000L // Duration for the glide effect in ms

    override fun onAttachedToWindow() {
        Log.i(LOG_ID, "Starting screensaver")
        super.onAttachedToWindow()

        isFullscreen = true
        isInteractive = false
        setContentView(R.layout.screensaver)

        imageView = findViewById(R.id.dream_image_view)
        container = findViewById(R.id.dream_container)

        screensaverHandler = ScreensaverHandler(this)
        screensaverHandler.setOnUpdateListener {
            Log.d(LOG_ID, "Update triggered by handler")
            updateBitmap()
        }
        screensaverHandler.create()
        screensaverHandler.resume()

        updateBitmap()
        startBouncing()
    }

    private fun updateBitmap() {
        handler.post {
            try {
                Log.d(LOG_ID, "updateBitmap called")
                val bitmap = screensaverHandler.getUpdatedBitmap()
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    moveElement()
                }
            } catch (ex: Exception) {
                Log.e(LOG_ID, "updateBitmap exception: " + ex.message)
            }
        }
    }

    private fun startBouncing() {
        Log.d(LOG_ID, "startBouncing called")
        val runnable = object : Runnable {
            override fun run() {
                moveElement()
                handler.postDelayed(this, moveInterval)
            }
        }
        handler.post(runnable)
    }

    private fun moveElement() {
        // Use container.post to ensure layout is finished (fixes orientation lag)
        container.post {
            // Calculate available space (Screen - Bitmap size)
            val deltaX = container.width - imageView.width
            val deltaY = container.height - imageView.height

            Log.d(LOG_ID, "moveElement: container=${container.width}x${container.height}, image=${imageView.width}x${imageView.height}, delta=${deltaX}x${deltaY}")

            if (deltaX > 0 && deltaY > 0) {
                val isLandscape = container.width > container.height
                val margin = 100f

                if (firstRun) {
                    // Set initial position within safe margins
                    imageView.x = (Math.random().toFloat() * (deltaX - 2 * margin) + margin)
                    imageView.y = (Math.random().toFloat() * (deltaY - 2 * margin) + margin)
                    Log.d(LOG_ID, "Initial position set to x=${imageView.x}, y=${imageView.y} (isLandscape=$isLandscape)")
                    firstRun = false
                }

                // Adjust step size based on orientation
                val maxStepX = if (isLandscape) container.width * 0.3f else container.width * 0.5f
                val maxStepY = if (isLandscape) container.height * 0.5f else container.height * 0.3f

                var nextX = imageView.x + (Math.random().toFloat() * 2f - 1f) * maxStepX
                var nextY = imageView.y + (Math.random().toFloat() * 2f - 1f) * maxStepY

                // Constrain to safe area
                nextX = nextX.coerceIn(margin, maxOf(margin, deltaX - margin))
                nextY = nextY.coerceIn(margin, maxOf(margin, deltaY - margin))

                Log.d(LOG_ID, "Animating to x=$nextX, y=$nextY (steps: ${maxStepX}x${maxStepY})")

                imageView.animate()
                    .x(nextX)
                    .y(nextY)
                    .setDuration(animationDuration)
                    .start()
            } else {
                Log.w(LOG_ID, "deltaX or deltaY <= 0, skipping move")
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(LOG_ID, "onConfigurationChanged called: $newConfig")
        firstRun = true // Recalculate position on orientation change
        updateBitmap()
    }

    override fun onDetachedFromWindow() {
        Log.i(LOG_ID, "Stopping screensaver")
        super.onDetachedFromWindow()
        screensaverHandler.pause()
        screensaverHandler.destroy()
        handler.removeCallbacksAndMessages(null)
    }
}
