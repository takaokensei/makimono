package zechs.drive.stream.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Gerador de Baseline Profile para os fluxos críticos da Seção 8.2:
 * - Fluxo 1: Inicialização da aplicação até a Home.
 * - Fluxo 2: Abertura de detalhes da série / mídia.
 * - Fluxo 4: Entrada e inicialização do reprodutor.
 * - Fluxo 5: Navegação no Rail lateral.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        baselineRule.collect(
            packageName = TARGET_PACKAGE,
            maxIterations = 5
        ) {
            // Fluxo 1: Startup
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5000)

            // Fluxo 5: Rail Navigation
            val railFiles = device.findObject(By.res(TARGET_PACKAGE, "nav_files"))
                ?: device.findObject(By.descContains("Arquivos"))
            railFiles?.click()
            device.waitForIdle()

            val railHome = device.findObject(By.res(TARGET_PACKAGE, "nav_home"))
                ?: device.findObject(By.descContains("Início"))
            railHome?.click()
            device.waitForIdle()

            // Fluxo 2: Mídia / Detalhes
            val mediaCard = device.findObject(By.res(TARGET_PACKAGE, "card_media"))
                ?: device.findObject(By.clickable(true))
            mediaCard?.click()
            device.waitForIdle()

            // Fluxo 4: Player launch
            val playAction = device.findObject(By.res(TARGET_PACKAGE, "btn_play"))
            playAction?.click()
            device.waitForIdle()
            device.pressBack()
            device.pressBack()
        }
    }

    companion object {
        const val TARGET_PACKAGE = "zechs.drive.stream"
    }
}
