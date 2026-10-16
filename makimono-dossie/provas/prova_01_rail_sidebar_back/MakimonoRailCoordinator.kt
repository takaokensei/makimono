package makimono.dossie.provas.rail

/**
 * Contrato de navegação do Menu Lateral (Navigation Rail) e política de Back para Android TV.
 * Extraído e especificado com base na observação da arquitetura de TV (Clean-Room).
 */
data class RailDimensions(
    val collapsedWidthDp: Int = 80,
    val expandedWidthDp: Int = 264
)

sealed interface RailFocusState {
    object CollapsedInContent : RailFocusState
    data class ExpandedInRail(val selectedIndex: Int) : RailFocusState
    object ExitConfirmationDialogVisible : RailFocusState
}

sealed interface BackPressResult {
    object ConsumedAnchorToRail : BackPressResult
    object ConsumedShowExitDialog : BackPressResult
    object ConsumedDismissExitDialog : BackPressResult
    object UnhandledExitApplication : BackPressResult
}

class MakimonoRailCoordinator(
    val dimensions: RailDimensions = RailDimensions()
) {
    var state: RailFocusState = RailFocusState.CollapsedInContent
        private set

    private var lastRailIndex: Int = 0

    fun onRailFocused(index: Int = 0): Int {
        lastRailIndex = index.coerceAtLeast(0)
        state = RailFocusState.ExpandedInRail(lastRailIndex)
        return dimensions.expandedWidthDp
    }

    fun onContentFocused(): Int {
        state = RailFocusState.CollapsedInContent
        return dimensions.collapsedWidthDp
    }

    /**
     * Política de manipulação de Back Press na TV:
     * 1. Se o foco está no conteúdo interno -> Ancorar foco de volta ao Rail no item ativo.
     * 2. Se o foco está no Rail -> Abrir Diálogo de Confirmação de Saída, preservando o índice ativo.
     * 3. Se o Diálogo já está visível -> Fechar o diálogo e restaurar foco no Rail no índice anterior.
     */
    fun handleBackPress(): BackPressResult {
        return when (val current = state) {
            is RailFocusState.CollapsedInContent -> {
                state = RailFocusState.ExpandedInRail(lastRailIndex)
                BackPressResult.ConsumedAnchorToRail
            }
            is RailFocusState.ExpandedInRail -> {
                lastRailIndex = current.selectedIndex
                state = RailFocusState.ExitConfirmationDialogVisible
                BackPressResult.ConsumedShowExitDialog
            }
            is RailFocusState.ExitConfirmationDialogVisible -> {
                state = RailFocusState.ExpandedInRail(lastRailIndex)
                BackPressResult.ConsumedDismissExitDialog
            }
        }
    }

    fun confirmExit(): BackPressResult {
        return BackPressResult.UnhandledExitApplication
    }
}
