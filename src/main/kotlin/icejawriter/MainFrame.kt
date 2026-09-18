package icejawriter

import java.awt.Dimension
import java.util.Locale
import javax.swing.ButtonGroup
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JRadioButtonMenuItem

/**
 * The main application window, modeled after the Iceja Security dashboard:
 * a light themed frame with a menu bar (File / View / Help). View holds the
 * language submenu (Russian / English) and the zoom submenu with presets from
 * 75% up to 300%. The content is the manuscript editor in [WriterPanel].
 */
class MainFrame : JFrame() {

    private val writerPanel = WriterPanel()

    private val fileMenu = JMenu()
    private val exitItem = JMenuItem()

    private val viewMenu = JMenu()
    private val langMenu = JMenu()
    private val langRuItem = JMenuItem()
    private val langEnItem = JMenuItem()

    private val zoomMenu = JMenu()
    private val zoom75 = JRadioButtonMenuItem()
    private val zoom100 = JRadioButtonMenuItem()
    private val zoom125 = JRadioButtonMenuItem()
    private val zoom150 = JRadioButtonMenuItem()
    private val zoom200 = JRadioButtonMenuItem()
    private val zoom300 = JRadioButtonMenuItem()
    private val zoomGroup = ButtonGroup()

    private val helpMenu = JMenu()
    private val aboutItem = JMenuItem()

    init {
        defaultCloseOperation = EXIT_ON_CLOSE

        val menuBar = JMenuBar()
        menuBar.add(fileMenu)
        menuBar.add(viewMenu)
        menuBar.add(helpMenu)
        jMenuBar = menuBar

        exitItem.addActionListener { dispose() }
        langRuItem.addActionListener { switchLocale(Locale("ru")) }
        langEnItem.addActionListener { switchLocale(Locale.ENGLISH) }
        aboutItem.addActionListener { showAbout() }

        zoom75.addActionListener { applyZoom(0.75) }
        zoom100.addActionListener { applyZoom(1.00) }
        zoom125.addActionListener { applyZoom(1.25) }
        zoom150.addActionListener { applyZoom(1.50) }
        zoom200.addActionListener { applyZoom(2.00) }
        zoom300.addActionListener { applyZoom(3.00) }

        fileMenu.add(exitItem)

        viewMenu.add(langMenu)
        langMenu.add(langRuItem)
        langMenu.add(langEnItem)

        viewMenu.add(zoomMenu)
        listOf(zoom75, zoom100, zoom125, zoom150, zoom200, zoom300)
            .forEach { item ->
                zoomGroup.add(item)
                zoomMenu.add(item)
            }

        helpMenu.add(aboutItem)

        contentPane = writerPanel

        applyTexts()

        zoom100.isSelected = true
        Zoom.apply(1.00, this)
        setSize(1200, 800)
        minimumSize = Dimension(900, 600)
    }

    /**
     * Applies the requested zoom (clamped to 75% .. 300%) and refreshes the
     * visual state of the menu and the content panel.
     */
    private fun applyZoom(scale: Double) {
        Zoom.apply(scale, this)
        applyTexts()
        writerPanel.revalidate()
        writerPanel.repaint()
    }

    /**
     * Switches the UI locale and re-translates every menu item and the
     * content panel.
     */
    private fun switchLocale(locale: Locale) {
        I18n.setLocale(locale)
        applyTexts()
        writerPanel.revalidate()
        writerPanel.repaint()
    }

    /**
     * Re-translates the window title and every menu item.
     */
    private fun applyTexts() {
        title = I18n.t("app.title")

        fileMenu.text = I18n.t("menu.file")
        exitItem.text = I18n.t("menu.exit")

        viewMenu.text = I18n.t("menu.view")
        langMenu.text = I18n.t("menu.lang")
        zoomMenu.text = I18n.t("menu.zoom")
        langRuItem.text = I18n.t("lang.ru")
        langEnItem.text = I18n.t("lang.en")

        zoom75.text = I18n.t("zoom.75")
        zoom100.text = I18n.t("zoom.100")
        zoom125.text = I18n.t("zoom.125")
        zoom150.text = I18n.t("zoom.150")
        zoom200.text = I18n.t("zoom.200")
        zoom300.text = I18n.t("zoom.300")

        helpMenu.text = I18n.t("menu.help")
        aboutItem.text = I18n.t("menu.about")

        writerPanel.applyTexts()
    }

    /**
     * Shows the About dialog with the application description.
     */
    private fun showAbout() {
        JOptionPane.showMessageDialog(
            this,
            I18n.t("dialog.about.text"),
            I18n.t("dialog.about.title"),
            JOptionPane.INFORMATION_MESSAGE
        )
    }
}