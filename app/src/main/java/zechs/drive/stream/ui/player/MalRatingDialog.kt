package zechs.drive.stream.ui.player

import android.app.Activity
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.R
import zechs.drive.stream.databinding.DialogMalRatingBinding

object MalRatingDialog {

    private fun getScoreLabel(score: Int): String {
        return when (score) {
            10 -> "Obra-prima"
            9 -> "Incrível"
            8 -> "Muito bom"
            7 -> "Bom"
            6 -> "Razoável"
            5 -> "Mediano"
            4 -> "Fraco"
            3 -> "Ruim"
            2 -> "Muito ruim"
            1 -> "Péssimo"
            else -> "Escolha uma nota"
        }
    }

    fun show(
        activity: Activity,
        animeTitle: String,
        totalEpisodes: Int,
        onSubmit: (score: Int) -> Unit,
        onSkip: () -> Unit = {},
        onDialogVisibilityChanged: (Boolean) -> Unit = { isVisible ->
            (activity as? PlayerModalHost)?.onPlayerModalVisibilityChanged(isVisible)
        }
    ): AlertDialog {
        val binding = DialogMalRatingBinding.inflate(activity.layoutInflater)

        binding.tvRatingAnimeTitle.text = animeTitle
        val epText = if (totalEpisodes > 0) "todos os $totalEpisodes episódios" else "a série"
        binding.tvRatingPrompt.text = "Você completou $epText! Como você avalia este anime no MyAnimeList?"

        var currentScore = 0
        binding.tvScoreNumber.text = "?"
        binding.tvScoreLabel.text = getScoreLabel(0)
        binding.seekBarScore.progress = 0
        binding.btnSubmitRating.isEnabled = false
        binding.btnSubmitRating.alpha = 0.42f

        binding.seekBarScore.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentScore = progress.coerceIn(0, 10)
                binding.tvScoreNumber.text = if (currentScore == 0) "—" else currentScore.toString()
                binding.tvScoreLabel.text = getScoreLabel(currentScore)
                binding.btnSubmitRating.isEnabled = currentScore > 0
                binding.btnSubmitRating.alpha = if (currentScore > 0) 1f else 0.42f
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_DriveStream_Dialog)
            .setView(binding.root)
            .setCancelable(true)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            dialog.window?.let { w ->
                zechs.drive.stream.utils.TvFocusRing.install(w.decorView)
            }
            binding.seekBarScore.requestFocus()
        }
        dialog.setOnDismissListener { onDialogVisibilityChanged(false) }
        dialog.setOnCancelListener { onSkip() }

        binding.btnSubmitRating.setOnClickListener {
            dialog.dismiss()
            onSubmit(currentScore)
        }

        binding.btnSkipRating.setOnClickListener {
            dialog.dismiss()
            onSkip()
        }

        onDialogVisibilityChanged(true)
        dialog.show()
        return dialog
    }
}
