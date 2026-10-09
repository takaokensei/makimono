package zechs.drive.stream.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fluxos da Seção 8.1:
 * - Fluxo 2: Abertura de uma série com metadados já em cache.
 * - Fluxo 3: Abertura de uma série sem cache.
 * - Fluxo 5: Navegação entre abas do rail (ida e volta).
 * - Fluxo 6: Rolagem de uma estante (frames com jank).
 */
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    /**
     * Fluxo 5: Navegação entre abas do rail (ida e volta).
     */
    @Test
    fun railNavigationTrip() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            pressHome()
            startActivityAndWait()

            // Espera a Home carregar
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)

            // Navega para aba do catálogo / arquivos via D-pad ou toque
            val railCatalog = device.findObject(By.res(TARGET_PACKAGE, "nav_files"))
                ?: device.findObject(By.descContains("Arquivos"))
            railCatalog?.click()
            device.waitForIdle()

            // Retorna para a aba Home
            val railHome = device.findObject(By.res(TARGET_PACKAGE, "nav_home"))
                ?: device.findObject(By.descContains("Início"))
            railHome?.click()
            device.waitForIdle()
        }
    }

    /**
     * Fluxo 6: Rolagem de uma estante com múltiplos itens (medição de jank de quadros).
     */
    @Test
    fun shelfScrollJank() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            pressHome()
            startActivityAndWait()

            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)

            // Localiza a lista horizontal da estante
            val shelfRecycler = device.findObject(By.scrollable(true))
            shelfRecycler?.setGestureMargin(device.displayWidth / 5)
            shelfRecycler?.fling(Direction.RIGHT)
            device.waitForIdle()
            shelfRecycler?.fling(Direction.LEFT)
            device.waitForIdle()
        }
    }

    /**
     * Fluxo 2: Abertura de uma série com metadados em cache.
     */
    @Test
    fun seriesDetailOpenCached() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.DEFAULT,
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.WARM
        ) {
            pressHome()
            startActivityAndWait()

            val mediaCard = device.findObject(By.res(TARGET_PACKAGE, "card_media"))
                ?: device.findObject(By.clickable(true))
            mediaCard?.click()
            device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "btn_play")), 3000)
            device.pressBack()
        }
    }

    /**
     * Fluxo 3: Abertura de navegação sem cache de sessão.
     */
    @Test
    fun seriesDetailOpenClean() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            iterations = DEFAULT_ITERATIONS,
            startupMode = StartupMode.COLD
        ) {
            pressHome()
            startActivityAndWait()

            val mediaCard = device.findObject(By.res(TARGET_PACKAGE, "card_media"))
                ?: device.findObject(By.clickable(true))
            mediaCard?.click()
            device.waitForIdle()
            device.pressBack()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "zechs.drive.stream"
        const val DEFAULT_ITERATIONS = 5
    }
}
