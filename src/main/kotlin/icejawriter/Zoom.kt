package icejawriter

import java.awt.Font
import java.util.ConcurrentModificationException
import javax.swing.JFrame
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.UIDefaults
import javax.swing.plaf.FontUIResource

/**
 * Applies a global UI zoom by scaling every font registered in the
 * UIManager defaults. Base (zoom = 100%) fonts are captured once after the
 * Look & Feel is installed; every later scale factor is derived from them, so
 * repeated zoom changes never drift. Mirrors the Iceja Security dashboard.
 */
object Zoom {

    /** Zoom is allowed in the range 75% .. 300%. */
    const val MIN_SCALE: Double = 0.75
    const val MAX_SCALE: Double = 3.00

    private val lock = Any()

    private var baseFonts: Map<Any, FontUIResource> = emptyMap()
    var scale: Double = 1.0
        private set

    /** Must be called on the EDT after the Look & Feel is installed. */
    fun captureBaseFonts() = synchronized(lock) {
        val defaults = UIManager.getDefaults()
        baseFonts = snapshotFontsWithRetry(defaults)
    }

    /**
     * Re-scales every UIManager font by [scale] (clamped to 75% .. 300%),
     * then forces the whole [frame] component tree to refresh its fonts.
     */
    fun apply(scale: Double, frame: JFrame) = synchronized(lock) {
        if (baseFonts.isEmpty()) {
            captureBaseFonts()
        }
        this.scale = scale.coerceIn(MIN_SCALE, MAX_SCALE)

        val defaults = UIManager.getDefaults()
        for ((key, base) in baseFonts) {
            val newSize = (base.size2D * this.scale).toFloat()
            defaults[key] = FontUIResource(base.deriveFont(newSize))
        }

        SwingUtilities.updateComponentTreeUI(frame)
        frame.pack()
        frame.setLocationRelativeTo(null)
    }

    /**
     * Snapshots the Look & Feel fonts. The defaults table may be populated
     * lazily while we iterate, so the snapshot is retried on concurrent
     * modification like the dashboard implementation does.
     */
    private fun snapshotFontsWithRetry(defaults: UIDefaults): Map<Any, FontUIResource> {
        repeat(10) {
            try {
                val keys = mutableListOf<Any>()
                val iterator = defaults.keys()
                while (iterator.hasMoreElements()) {
                    keys += iterator.nextElement()
                }

                val snapshot = LinkedHashMap<Any, FontUIResource>()
                for (key in keys) {
                    when (val value = defaults[key]) {
                        is FontUIResource -> snapshot[key] = value
                        is Font -> snapshot[key] = FontUIResource(value)
                    }
                }
                return snapshot
            } catch (_: ConcurrentModificationException) {
                // The Look & Feel is lazily populating defaults; retry.
            }
        }
        return emptyMap()
    }
}