package icejawriter

import java.util.Locale
import javax.swing.SwingUtilities
import javax.swing.UIManager

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater {
            // Prefer the system look and feel for a native light theme.
            runCatching {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            }

            // Allow forcing a language through -Dapp.lang=ru|en.
            System.getProperty("app.lang")?.lowercase()?.let { lang ->
                when (lang) {
                    "ru" -> I18n.setLocale(Locale("ru"))
                    "en" -> I18n.setLocale(Locale.ENGLISH)
                }
            }

            // Capture base Look & Feel fonts before the frame applies any zoom.
            Zoom.captureBaseFonts()

            val frame = MainFrame()
            frame.isVisible = true
        }
    }
}