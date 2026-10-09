package zechs.drive.stream.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluxo 4 da Seção 8.1:
 * Primeiro frame do player após escolher o episódio.
 */
@RunWith(AndroidJUnit4::class)
class PlayerBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun playerFirstFrameLaunch() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            pressHome()
            startActivityAndWait()

            // Abre o player se houver item ativo no Hero ou na estante
            val playAction = device.findObject(By.res(TARGET_PACKAGE, "btn_play"))
                ?: device.findObject(By.descContains("Reproduzir"))
                ?: device.findObject(By.descContains("Assistir"))

            playAction?.click()

            // Aguarda a superfície de vídeo ou o OSD do player aparecer
            device.wait(
                Until.hasObject(By.res(TARGET_PACKAGE, "player_view")),
                5000
            )

            // Sai do player
            device.pressBack()
            device.waitForIdle()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "zechs.drive.stream"
        const val DEFAULT_ITERATIONS = 5
    }
}
