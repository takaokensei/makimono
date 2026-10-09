package zechs.drive.stream.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.BeforeClass
import org.junit.Test

/**
 * Testes arquiteturais automatizados usando ArchUnit para garantir a integridade das camadas
 * conforme a especificação de arquitetura (Seção 5.1).
 */
class DomainRulesArchitectureTest {

    companion object {
        private lateinit var importedClasses: JavaClasses

        @BeforeClass
        @JvmStatic
        fun loadClasses() {
            importedClasses = ClassFileImporter()
                .withImportOption(ImportOption.DoNotIncludeTests())
                .importPackages("zechs.drive.stream")
        }
    }

    /**
     * Regra 1: A camada de domínio deve ser puramente Kotlin e não pode importar
     * classes de frameworks Android/AndroidX ou camadas externas (UI, Data, Playback, Platform).
     */
    @Test
    fun domainLayerMustNotDependOnAndroidOrOuterLayers() {
        val rule = noClasses()
            .that().resideInAPackage("..zechs.drive.stream.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "android..",
                "androidx..",
                "..zechs.drive.stream.ui..",
                "..zechs.drive.stream.data..",
                "..zechs.drive.stream.playback..",
                "..zechs.drive.stream.platform.."
            )

        rule.check(importedClasses)
    }

    /**
     * Regra 4: A camada de dados (Data) não pode depender da camada de apresentação (UI).
     */
    @Test
    fun dataLayerMustNotDependOnUiLayer() {
        val rule = noClasses()
            .that().resideInAPackage("..zechs.drive.stream.data..")
            .should().dependOnClassesThat().resideInAPackage(
                "..zechs.drive.stream.ui.."
            )

        rule.check(importedClasses)
    }
}
