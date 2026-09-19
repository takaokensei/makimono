package zechs.drive.stream.ui.player

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import com.google.android.exoplayer2.text.Cue
import com.google.android.exoplayer2.ui.CaptionStyleCompat
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import zechs.drive.stream.R
import zechs.drive.stream.databinding.DialogSubtitleStyleBinding
import zechs.drive.stream.utils.SubtitleAppearance
import zechs.drive.stream.utils.SubtitleCueComposer
import zechs.drive.stream.utils.SubtitleFontFamily
import zechs.drive.stream.utils.SubtitleStyle

object SubtitleStyleDialog {

    fun show(
        context: Context,
        initial: SubtitleStyle,
        onStyleCommitted: (SubtitleStyle) -> Unit
    ) {
        val binding = DialogSubtitleStyleBinding.inflate(LayoutInflater.from(context))
        var working = initial

        fun refreshPreview() {
            SubtitleAppearance.applyToExo(binding.subtitlePreview, working)
            val previewCue = Cue.Builder()
                .setText("Vou apresentar. Ele também é\ndo Clube de Fotografia.")
                .build()
            binding.subtitlePreview.setCues(
                SubtitleCueComposer.compose(listOf(previewCue), working)
            )
        }

        fun updateColorButton(button: com.google.android.material.button.MaterialButton, color: Int) {
            button.setBackgroundColor(color)
            // Set contrasting border based on brightness
            val brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000
            val borderColor = if (brightness > 128) Color.BLACK else Color.WHITE
            button.strokeColor = android.content.res.ColorStateList.valueOf(borderColor)
            button.strokeWidth = 2
        }

        binding.chipFontSans.setOnClickListener { working = working.copy(fontFamily = SubtitleFontFamily.SANS); refreshPreview() }
        binding.chipFontSerif.setOnClickListener { working = working.copy(fontFamily = SubtitleFontFamily.SERIF); refreshPreview() }
        binding.chipFontMono.setOnClickListener { working = working.copy(fontFamily = SubtitleFontFamily.MONO); refreshPreview() }
        binding.chipFontRounded.setOnClickListener { working = working.copy(fontFamily = SubtitleFontFamily.ROUNDED); refreshPreview() }

        when (initial.fontFamily) {
            SubtitleFontFamily.SANS -> binding.chipFontSans.isChecked = true
            SubtitleFontFamily.SERIF -> binding.chipFontSerif.isChecked = true
            SubtitleFontFamily.MONO -> binding.chipFontMono.isChecked = true
            SubtitleFontFamily.ROUNDED -> binding.chipFontRounded.isChecked = true
        }

        binding.sliderSize.value = initial.sizeSp
        binding.tvSizeLabel.text = "Tamanho: ${initial.sizeSp.toInt()}sp"
        binding.sliderSize.addOnChangeListener { _: Slider, value, _ ->
            working = working.copy(sizeSp = value)
            binding.tvSizeLabel.text = "Tamanho: ${value.toInt()}sp"
            refreshPreview()
        }

        binding.switchBold.isChecked = initial.bold
        binding.switchBold.setOnCheckedChangeListener { _, checked ->
            working = working.copy(bold = checked)
            refreshPreview()
        }
        binding.switchItalic.isChecked = initial.italic
        binding.switchItalic.setOnCheckedChangeListener { _, checked ->
            working = working.copy(italic = checked)
            refreshPreview()
        }

        binding.switchMergeLines.isChecked = initial.mergeOverlappingBottomCues
        binding.switchMergeLines.setOnCheckedChangeListener { _, checked ->
            working = working.copy(mergeOverlappingBottomCues = checked)
            refreshPreview()
        }

        // Position slider
        binding.sliderPosition.value = initial.positionFraction
        binding.tvPositionLabel.text = positionLabel(initial.positionFraction)
        binding.sliderPosition.addOnChangeListener { _: Slider, value, _ ->
            working = working.copy(positionFraction = value)
            binding.tvPositionLabel.text = positionLabel(value)
            refreshPreview()
        }

        // Color buttons
        updateColorButton(binding.btnTextColor, initial.foregroundColor)
        updateColorButton(binding.btnEdgeColor, initial.edgeColor)
        updateColorButton(binding.btnBackgroundColor, initial.backgroundColor)

        binding.btnTextColor.setOnClickListener {
            showColorPicker(context, initial.foregroundColor) { color ->
                working = working.copy(foregroundColor = color)
                updateColorButton(binding.btnTextColor, color)
                refreshPreview()
            }
        }

        binding.btnEdgeColor.setOnClickListener {
            showColorPicker(context, initial.edgeColor) { color ->
                working = working.copy(edgeColor = color)
                updateColorButton(binding.btnEdgeColor, color)
                refreshPreview()
            }
        }

        binding.btnBackgroundColor.setOnClickListener {
            showColorPicker(context, initial.backgroundColor) { color ->
                working = working.copy(backgroundColor = color)
                updateColorButton(binding.btnBackgroundColor, color)
                refreshPreview()
            }
        }

        // Delay slider
        binding.sliderDelay.value = initial.subtitleDelayMs.toFloat()
        binding.tvDelayLabel.text = "Atraso: ${initial.subtitleDelayMs}ms"
        binding.sliderDelay.addOnChangeListener { _: Slider, value, _ ->
            working = working.copy(subtitleDelayMs = value.toLong())
            binding.tvDelayLabel.text = "Atraso: ${value.toLong()}ms"
            refreshPreview()
        }

        // Gap slider
        binding.sliderGap.value = initial.bridgeGapMs.toFloat()
        binding.tvGapLabel.text = "Preencher lacunas: ${initial.bridgeGapMs}ms"
        binding.sliderGap.addOnChangeListener { _: Slider, value, _ ->
            working = working.copy(bridgeGapMs = value.toLong())
            binding.tvGapLabel.text = "Preencher lacunas: ${value.toLong()}ms"
            refreshPreview()
        }

        fun selectEdge(chip: Chip, edgeType: Int, bg: Int = Color.TRANSPARENT) {
            binding.chipEdgeOutline.isChecked = false
            binding.chipEdgeShadow.isChecked = false
            binding.chipEdgeBox.isChecked = false
            binding.chipEdgeNone.isChecked = false
            chip.isChecked = true
            working = working.copy(edgeType = edgeType, backgroundColor = bg)
            refreshPreview()
        }

        binding.chipEdgeOutline.setOnClickListener {
            selectEdge(binding.chipEdgeOutline, CaptionStyleCompat.EDGE_TYPE_OUTLINE)
        }
        binding.chipEdgeShadow.setOnClickListener {
            selectEdge(binding.chipEdgeShadow, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW)
        }
        binding.chipEdgeBox.setOnClickListener {
            selectEdge(
                binding.chipEdgeBox,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.argb(180, 0, 0, 0)
            )
        }
        binding.chipEdgeNone.setOnClickListener {
            selectEdge(binding.chipEdgeNone, CaptionStyleCompat.EDGE_TYPE_NONE)
        }

        when (initial.edgeType) {
            CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW -> binding.chipEdgeShadow.isChecked = true
            CaptionStyleCompat.EDGE_TYPE_NONE -> binding.chipEdgeNone.isChecked = true
            else -> {
                if (initial.backgroundColor != Color.TRANSPARENT) binding.chipEdgeBox.isChecked = true
                else binding.chipEdgeOutline.isChecked = true
            }
        }

        refreshPreview()

        MaterialAlertDialogBuilder(context, R.style.ThemeOverlay_DriveStream_Dialog)
            .setTitle("Personalizar legendas")
            .setView(binding.root)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                onStyleCommitted(working.copy(
                    sizeSp = binding.sliderSize.value,
                    positionFraction = binding.sliderPosition.value,
                    subtitleDelayMs = binding.sliderDelay.value.toLong(),
                    bridgeGapMs = binding.sliderGap.value.toLong()
                ))
            }
            .show()
    }

    private fun positionLabel(fraction: Float): String {
        return when {
            fraction <= 0.75f -> "Superior"
            fraction <= 0.85f -> "Meio superior"
            fraction <= 0.92f -> "Meio inferior"
            else -> "Inferior"
        }
    }

    private fun showColorPicker(
        context: Context,
        initialColor: Int,
        onColorSelected: (Int) -> Unit
    ) {
        // Simple color picker with preset colors
        val colors = listOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.argb(180, 0, 0, 0), Color.TRANSPARENT
        )
        
        val colorNames = listOf(
            "Branco", "Preto", "Vermelho", "Verde", "Azul",
            "Amarelo", "Ciano", "Magenta",
            "Semi-transparente", "Transparente"
        )
        
        val items = colorNames.toTypedArray()
        val initialIndex = colors.indexOfFirst { it == initialColor }.coerceAtLeast(0)
        
        android.app.AlertDialog.Builder(context)
            .setTitle("Escolher cor")
            .setSingleChoiceItems(items, initialIndex) { dialog, which ->
                onColorSelected(colors[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
