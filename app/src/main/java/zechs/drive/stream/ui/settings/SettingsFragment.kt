package zechs.drive.stream.ui.settings

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.graphics.Color
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import zechs.drive.stream.databinding.ItemThemePreviewBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import zechs.drive.stream.R
import zechs.drive.stream.databinding.FragmentSettingsBinding
import zechs.drive.stream.ui.BaseFragment
import zechs.drive.stream.ui.main.MainViewModel
import zechs.drive.stream.utils.AppTheme
import zechs.drive.stream.utils.VideoPlayer
import zechs.drive.stream.utils.state.Resource
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import zechs.drive.stream.data.repository.MalRepository
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.util.Constants

@AndroidEntryPoint
class SettingsFragment : BaseFragment() {

    companion object {
        const val TAG = "SettingsFragment"
    }

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel by activityViewModels<MainViewModel>()

    @Inject
    lateinit var malRepository: MalRepository

    @Inject
    lateinit var malSessionManager: MalSessionManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(
            inflater, container, /* attachToParent */false
        )
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSettingsBinding.bind(view)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        setupThemeMenu()
        setupDefaultPlayerMenu()
        setupCheckForUpdates()
        setupMalIntegration()
        setupPlaybackExperience()
    }

    private fun setupThemeMenu() {
        binding.settingSelectTheme.setOnClickListener {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_theme_selector, null)
            val container = dialogView.findViewById<LinearLayout>(R.id.llThemeContainer)

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create()

            AppTheme.entries.forEach { theme ->
                val itemBinding = ItemThemePreviewBinding.inflate(LayoutInflater.from(requireContext()), container, false)
                itemBinding.apply {
                    tvThemeName.text = theme.displayName
                    tvThemeSubtitle.text = theme.subtitle

                    swatchBase.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgBaseHex))
                    }
                    swatchSurface.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.bgSurfaceHex))
                    }
                    swatchAccent.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentPrimaryHex))
                    }
                    swatchSecondary.background = ContextCompat.getDrawable(requireContext(), R.drawable.glass_circle_button_bg)?.mutate()?.apply {
                        setTint(Color.parseColor(theme.accentSecondaryHex))
                    }

                    ivSelected.isVisible = (mainViewModel.currentThemeIndex == theme.value)

                    cardTheme.setOnClickListener {
                        mainViewModel.setTheme(theme)
                        dialog.dismiss()
                    }
                }
                container.addView(itemBinding.root)
            }

            dialog.show()
        }
    }

    private fun setupDefaultPlayerMenu() {
        val players = listOf(
            getString(R.string.exoplayer),
            getString(R.string.mpv)
        )
        binding.settingDefaultPlayer.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).apply {
                setTitle(getString(R.string.default_player))
                setSingleChoiceItems(
                    players.toTypedArray(),
                    mainViewModel.currentPlayerIndex.value
                ) { dialog, item ->
                    val player = when (item) {
                        VideoPlayer.EXO_PLAYER.value -> VideoPlayer.EXO_PLAYER
                        VideoPlayer.MPV.value -> VideoPlayer.MPV
                        else -> throw IllegalArgumentException("Unknown default player")
                    }
                    mainViewModel.setPlayer(player)
                    dialog.dismiss()
                }
            }.also { it.show() }
        }
    }

    private fun setupCheckForUpdates() {

        var isUserClick = false
        binding.settingCheckForUpdate.setOnClickListener {
            if (!mainViewModel.isChecking) {
                mainViewModel.getLatestRelease()
                isUserClick = true
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                mainViewModel.lastUpdated.collect {
                    if (it != null) {
                        val last = "Last checked: $it"
                        TransitionManager.beginDelayedTransition(
                            binding.settingCheckForUpdate,
                        )
                        binding.lastCheckedLabel.text = last
                    }
                }
            }
        }

        fun isChecking(bool: Boolean) {
            binding.progressBarChecking.isInvisible = !bool
        }

        mainViewModel.latest.observe(viewLifecycleOwner) { state ->
            when (state) {
                is Resource.Loading -> isChecking(true)

                is Resource.Error -> {
                    isChecking(false)
                    showSnackBar("Unable to check for updates")
                }

                is Resource.Success -> {
                    isChecking(false)
                    val release = state.data!!
                    if (release.isLatest() && isUserClick) {
                        showSnackBar("You are already on the latest version")
                    }
                }
            }
        }

    }

    private fun setupMalIntegration() {
        fun updateMalUi() {
            val isLoggedIn = malSessionManager.isLoggedIn()
            val username = malSessionManager.getUsername()
            if (isLoggedIn) {
                binding.tvMalTitle.text = "MyAnimeList"
                binding.tvMalSubtitle.text = "Conectado como: ${username ?: "Usuário"} • Toque para desconectar"
                binding.ivMalIcon.setImageResource(R.drawable.ic_unlock_24)
            } else {
                binding.tvMalTitle.text = "MyAnimeList"
                binding.tvMalSubtitle.text = "Conectar conta para scrobble automático"
                binding.ivMalIcon.setImageResource(R.drawable.ic_lock_24)
            }
            binding.switchMalSync.isChecked = malSessionManager.isSyncEnabled()
            binding.settingMalSyncToggle.isVisible = isLoggedIn
            binding.settingMalTransferToTv.isVisible = isLoggedIn
        }

        updateMalUi()

        binding.switchMalSync.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setSyncEnabled(isChecked)
            showSnackBar(if (isChecked) "Sincronização com o MyAnimeList ativada" else "Sincronização com o MyAnimeList pausada")
        }

        binding.settingMalTransferToTv.setOnClickListener {
            val inputLayout = com.google.android.material.textfield.TextInputLayout(requireContext()).apply {
                boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
                hint = "IP da TV (ex: 192.168.1.150)"
                setPadding(48, 24, 48, 12)
            }
            val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_PHONE or android.text.InputType.TYPE_TEXT_VARIATION_URI
                setText("192.168.")
                setSelection(text?.length ?: 0)
            }
            inputLayout.addView(editText)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Transferir Login para Android TV")
                .setMessage("Abra a tela de conexão do MAL na sua TV e digite o IP exibido no QR Code:")
                .setView(inputLayout)
                .setPositiveButton("Transferir") { dialog, _ ->
                    val ip = editText.text?.toString()?.trim()
                    if (!ip.isNullOrBlank()) {
                        transferMalSessionToTv(ip)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                .show()
        }

        binding.settingMalAccount.setOnClickListener {
            if (malSessionManager.isLoggedIn()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Desconectar do MyAnimeList?")
                    .setMessage("O scrobble automático de episódios será desativado.")
                    .setPositiveButton("Desconectar") { dialog, _ ->
                        malSessionManager.clearSession()
                        updateMalUi()
                        dialog.dismiss()
                        showSnackBar("Conta do MyAnimeList desconectada")
                    }
                    .setNegativeButton("Cancelar") { dialog, _ -> dialog.dismiss() }
                    .show()
            } else {
                val authDialog = MalAuthDialog(
                    onCodeReceived = { code, verifier ->
                        viewLifecycleOwner.lifecycleScope.launch {
                            showSnackBar("Autenticando com MyAnimeList...")
                            val res = malRepository.exchangeToken(code, verifier)
                            if (res is Resource.Success) {
                                updateMalUi()
                                showSnackBar("MyAnimeList conectado com sucesso!")
                            } else {
                                showSnackBar(res.message ?: "Falha ao conectar MyAnimeList")
                            }
                        }
                    },
                    onTokenReceived = { token, username ->
                        malSessionManager.saveTokens(token)
                        if (!username.isNullOrBlank()) {
                            malSessionManager.saveUserProfile(zechs.drive.stream.data.model.MalUserProfile(0, username, null))
                        }
                        updateMalUi()
                        showSnackBar("MyAnimeList conectado com sucesso via celular!")
                    }
                )
                authDialog.show(parentFragmentManager, MalAuthDialog.TAG)
            }
        }
    }

    private fun transferMalSessionToTv(rawIp: String) {
        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanHost = rawIp.removePrefix("http://").removePrefix("https://").substringBefore(":").substringBefore("/")
                val targetUrl = "http://$cleanHost:${Constants.MAL_REDIRECT_PORT}/api/mal-token"
                val payload = org.json.JSONObject().apply {
                    put("accessToken", malSessionManager.getAccessToken() ?: "")
                    put("refreshToken", malSessionManager.getRefreshToken() ?: "")
                    put("expiresIn", (malSessionManager.getExpiresAt() - System.currentTimeMillis()) / 1000L)
                    put("username", malSessionManager.getUsername() ?: "")
                }.toString()

                val connection = java.net.URL(targetUrl).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { os ->
                    os.write(payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8))
                }

                val responseCode = connection.responseCode
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (responseCode in 200..299) {
                        showSnackBar("🎉 Login do MyAnimeList transferido para a TV com sucesso!")
                    } else {
                        showSnackBar("Falha ao comunicar com a TV (código HTTP $responseCode)")
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    showSnackBar("Erro ao transferir: ${e.message}")
                }
            }
        }
    }

    private fun setupPlaybackExperience() {
        binding.switchAutoSkip.isChecked = malSessionManager.isAutoSkipEnabled()
        binding.settingAutoSkipToggle.setOnClickListener {
            binding.switchAutoSkip.toggle()
        }
        binding.switchAutoSkip.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setAutoSkipEnabled(isChecked)
            showSnackBar(if (isChecked) "Pulo de aberturas automático ativado" else "Pulo de aberturas automático desativado")
        }

        binding.switchGestures.isChecked = malSessionManager.isGesturesEnabled()
        binding.settingGesturesToggle.setOnClickListener {
            binding.switchGestures.toggle()
        }
        binding.switchGestures.setOnCheckedChangeListener { _, isChecked ->
            malSessionManager.setGesturesEnabled(isChecked)
            showSnackBar(if (isChecked) "Gestos touch ativados" else "Gestos touch desativados")
        }
    }

    private fun showSnackBar(message: String) {
        Snackbar.make(
            binding.root,
            message,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

}