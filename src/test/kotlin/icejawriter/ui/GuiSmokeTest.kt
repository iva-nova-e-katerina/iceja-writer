package icejawriter.ui

import icejawriter.config.AppSettings
import icejawriter.llm.SceneCardProposal
import icejawriter.project.ProjectIo
import icejawriter.project.TestProjects
import java.awt.GraphicsEnvironment
import java.nio.file.Path
import javax.swing.SwingUtilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * GUI smoke tests (НФР-12, smoke-тест GUI через xvfb): build the real main
 * frame, open a project through the same code path the menu uses, and run the
 * "Start writing" flow. The tests are skipped automatically when no display is
 * available; run them under `xvfb-run`.
 */
class GuiSmokeTest {

    @Test
    fun buildsFrameAndOpensProject(@TempDir dir: Path) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "no display available")

        val projectFile = dir.resolve("smoke.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject("\u0414\u044b\u043c\u043e\u0432\u043e\u0439 \u0440\u043e\u043c\u0430\u043d"))

        var frame: MainFrame? = null
        SwingUtilities.invokeAndWait { frame = MainFrame() }
        try {
            openProject(frame!!, projectFile)
            assertTrue(frame!!.title.contains("\u0414\u044b\u043c\u043e\u0432\u043e\u0439 \u0440\u043e\u043c\u0430\u043d"))

            // Open the first scene through the same path the tree uses.
            val sceneId = frame!!.session.document!!.scenes.first().card.id
            SwingUtilities.invokeAndWait { frame!!.openScene(sceneId) }

            closeFrame(frame!!)
        } finally {
            SwingUtilities.invokeAndWait { frame!!.dispose() }
        }
    }

    @Test
    fun startWritingAddsSceneInHumanMode(@TempDir dir: Path) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "no display available")

        val projectFile = dir.resolve("start.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject("\u0421\u0442\u0430\u0440\u0442"))

        var frame: MainFrame? = null
        SwingUtilities.invokeAndWait { frame = MainFrame() }
        try {
            openProject(frame!!, projectFile)

            // Human mode: "Start writing" appends a scene without any LLM call.
            SwingUtilities.invokeAndWait {
                frame!!.settings = frame!!.settings.copy(cardMode = AppSettings.CARD_MODE_HUMAN)
            }
            invokePrivate(frame!!, "startWriting")

            assertEquals(3, frame!!.session.document!!.scenes.size)
            assertEquals(3, frame!!.session.document!!.scenes.map { it.card.number }.max())

            closeFrame(frame!!)
        } finally {
            SwingUtilities.invokeAndWait { frame!!.dispose() }
        }
    }

    /**
     * Regression: an accepted AI card must stay in the document. Rebuilding the
     * tree fired a selection event that used to commit the stale (empty)
     * widget values back over the freshly applied proposal.
     */
    @Test
    fun acceptedAiCardIsShownInTheEditor(@TempDir dir: Path) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "no display available")

        val projectFile = dir.resolve("card.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject("\u041a\u0430\u0440\u0442\u043e\u0447\u043a\u0430"))

        var frame: MainFrame? = null
        SwingUtilities.invokeAndWait { frame = MainFrame() }
        try {
            openProject(frame!!, projectFile)

            val sceneId = frame!!.session.document!!.scenes.first().card.id
            SwingUtilities.invokeAndWait { frame!!.openScene(sceneId) }

            val proposal = SceneCardProposal(
                title = "\u0421\u0446\u0435\u043d\u0430 \u043e\u0442 \u0418\u0418",
                goal = "\u0446\u0435\u043b\u044c",
                conflict = "\u043a\u043e\u043d\u0444\u043b\u0438\u043a\u0442",
                twist = "\u043f\u043e\u0432\u043e\u0440\u043e\u0442",
                wordTargetMin = 800,
                wordTargetMax = 1500,
            )
            invokeApplyCard(frame!!, sceneId, proposal)

            val applied = frame!!.session.document!!.scenes.first { it.card.id == sceneId }
            assertEquals("\u0446\u0435\u043b\u044c", applied.card.goal)
            assertEquals("\u043a\u043e\u043d\u0444\u043b\u0438\u043a\u0442", applied.card.conflict)
            assertEquals("\u0421\u0446\u0435\u043d\u0430 \u043e\u0442 \u0418\u0418", applied.card.title)

            // Moving to another act renumbers the scene; the proposal must
            // survive and the moved scene must be found by its new id.
            val movedProposal = proposal.copy(act = 2, chapter = 1)
            invokeApplyCard(frame!!, sceneId, movedProposal)
            val moved = frame!!.session.document!!.scenes.first { it.card.goal == "\u0446\u0435\u043b\u044c" }
            assertEquals(2, moved.card.act)
            assertEquals("\u043a\u043e\u043d\u0444\u043b\u0438\u043a\u0442", moved.card.conflict)

            // Persist before closing so that no save prompt (modal dialog)
            // appears in the headless test environment.
            SwingUtilities.invokeAndWait { frame!!.session.save(0) }
            closeFrame(frame!!)
        } finally {
            SwingUtilities.invokeAndWait { frame!!.dispose() }
        }
    }

    /** Opens a project through the same path the menu uses. */
    private fun openProject(frame: MainFrame, projectFile: Path) {
        val method = MainFrame::class.java.getDeclaredMethod("openProject", Path::class.java)
        method.isAccessible = true
        SwingUtilities.invokeAndWait { method.invoke(frame, projectFile) }
    }

    /** Invokes a private no-argument method of the main frame on the EDT. */
    private fun invokePrivate(frame: MainFrame, name: String) {
        val method = MainFrame::class.java.getDeclaredMethod(name)
        method.isAccessible = true
        SwingUtilities.invokeAndWait { method.invoke(frame) }
    }

    /** Invokes the private AI card application on the EDT. */
    private fun invokeApplyCard(frame: MainFrame, sceneId: String, proposal: SceneCardProposal) {
        val method = MainFrame::class.java.getDeclaredMethod(
            "applyCardProposal",
            String::class.java,
            SceneCardProposal::class.java,
        )
        method.isAccessible = true
        SwingUtilities.invokeAndWait { method.invoke(frame, sceneId, proposal) }
    }

    /** Closes the project through the same path the menu uses. */
    private fun closeFrame(frame: MainFrame) {
        val close = MainFrame::class.java.getDeclaredMethod("closeProject")
        close.isAccessible = true
        SwingUtilities.invokeAndWait { close.invoke(frame) }
    }
}