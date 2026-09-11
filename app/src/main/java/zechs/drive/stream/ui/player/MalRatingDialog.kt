package zechs.drive.stream.ui.player

import android.app.Activity
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.databinding.DialogMalRatingBinding

object MalRatingDialog {

    private fun getScoreLabel(score: Int): String {
        return when (score) {
            10 -> "★ 10 - Obra-prima (Masterpiece)"
            9 -> "★ 9 - Incrível (Great)"
            8 -> "★ 8 - Muito Bom (Very Good)"
            7 -> "★ 7 - Bom (Good)"
            6 -> "★ 6 - Razoável (Fine)"
            5 -> "★ 5 - Médio (Average)"
            4 -> "★ 4 - Ruim (Bad)"
            3 -> "★ 3 - Muito Ruim (Very Bad)"
            2 -> "★ 2 - Horrível (Horrible)"
            1 -> "★ 1 - Pavoroso (Appalling)"
            else -> "★ $score"
        }
    }

    fun show(
        activity: Activity,
        animeTitle: String,
        totalEpisodes: Int,
        onSubmit: (score: Int) -> Unit,
        onSkip: () -> Unit = {}
    ): AlertDialog {
        val binding = DialogMalRatingBinding.inflate(activity.layoutInflater)

        binding.tvRatingAnimeTitle.text = animeTitle
        val epText = if (totalEpisodes > 0) "todos os $totalEpisodes episódios" else "a série"
        binding.tvRatingPrompt.text = "Você completou $epText! Como você avalia este anime no MyAnimeList?"

        var currentScore = 10
        binding.tvScoreNumber.text = "10"
        binding.tvScoreLabel.text = getScoreLabel(10)
        binding.seekBarScore.progress = 10

        binding.seekBarScore.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentScore = if (progress < 1) 1 else progress
                binding.tvScoreNumber.text = currentScore.toString()
                binding.tvScoreLabel.text = getScoreLabel(currentScore)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        val dialog = MaterialAlertDialogBuilder(activity)
            .setView(binding.root)
            .setCancelable(false)
            .create()

        binding.btnSubmitRating.setOnClickListener {
            dialog.dismiss()
            onSubmit(currentScore)
        }

        binding.btnSkipRating.setOnClickListener {
            dialog.dismiss()
            onSkip()
        }

        dialog.show()
        return dialog
    }
}
