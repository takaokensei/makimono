package zechs.drive.stream.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.databinding.DialogPlayerGlassMenuBinding
import zechs.drive.stream.databinding.ItemPlayerGlassMenuBinding
import zechs.drive.stream.utils.SubtitleLanguage

data class GlassMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val isSelected: Boolean = false,
    val tag: Any? = null,
    val isInteractive: Boolean = true
)

internal object GlassMenuFocusPolicy {
    fun initialInteractiveIndex(items: List<GlassMenuItem>): Int? {
        val selected = items.indexOfFirst { it.isInteractive && it.isSelected }
        if (selected >= 0) return selected
        return items.indexOfFirst(GlassMenuItem::isInteractive).takeIf { it >= 0 }
    }
}

class PlayerGlassMenuDialog(
    private val context: Context,
    private val title: String,
    private var items: List<GlassMenuItem>,
    private val onItemSelected: (GlassMenuItem) -> Unit
) {

    private var subtitleText: String? = null

    private var actionButtonText: String? = null
    private var onActionClicked: ((PlayerGlassMenuDialog) -> Unit)? = null

    private var footerTertiaryText: String? = null
    private var onFooterTertiaryClicked: (() -> Unit)? = null

    private var footerSecondaryText: String? = null
    private var onFooterSecondaryClicked: (() -> Unit)? = null

    private var footerPrimaryText: String? = null
    private var onFooterPrimaryClicked: (() -> Unit)? = null

    private var selectedLanguage: SubtitleLanguage? = null
    private var onLanguageChanged: ((PlayerGlassMenuDialog, SubtitleLanguage) -> Unit)? = null

    private var dialog: AlertDialog? = null
    private var binding: DialogPlayerGlassMenuBinding? = null
    private var adapter: GlassMenuAdapter? = null
    private var onDismiss: (() -> Unit)? = null
    private var accentColorResolved: Int = Color.parseColor("#22D3EE")

    fun setOnDismiss(action: () -> Unit) = apply { onDismiss = action }

    fun setSubtitle(text: String?) = apply {
        this.subtitleText = text
    }

    fun setActionButton(text: String, onClick: (PlayerGlassMenuDialog) -> Unit) = apply {
        this.actionButtonText = text
        this.onActionClicked = onClick
    }

    fun setFooterTertiary(text: String, onClick: () -> Unit) = apply {
        this.footerTertiaryText = text
        this.onFooterTertiaryClicked = onClick
    }

    fun setFooterSecondary(text: String, onClick: () -> Unit) = apply {
        this.footerSecondaryText = text
        this.onFooterSecondaryClicked = onClick
    }

    fun setFooterPrimary(text: String, onClick: () -> Unit) = apply {
        this.footerPrimaryText = text
        this.onFooterPrimaryClicked = onClick
    }

    fun setLanguageSelector(
        initialLanguage: SubtitleLanguage,
        onLanguageSelected: (PlayerGlassMenuDialog, SubtitleLanguage) -> Unit
    ) = apply {
        this.selectedLanguage = initialLanguage
        this.onLanguageChanged = onLanguageSelected
    }

    fun show(): PlayerGlassMenuDialog {
        val previousFocus = (context as? android.app.Activity)?.currentFocus
        val inflater = LayoutInflater.from(context)
        val dialogBinding = DialogPlayerGlassMenuBinding.inflate(inflater)
        binding = dialogBinding

        dialogBinding.tvDialogTitle.text = title
        if (!subtitleText.isNullOrBlank()) {
            dialogBinding.tvDialogSubtitle.text = subtitleText
            dialogBinding.tvDialogSubtitle.visibility = View.VISIBLE
        } else {
            dialogBinding.tvDialogSubtitle.visibility = View.GONE
        }

        dialogBinding.btnDialogClose.setOnClickListener {
            dismiss()
        }

        // Resolve accent color
        val typedValue = TypedValue()
        accentColorResolved = if (context.theme.resolveAttribute(R.attr.colorAccentPrimary, typedValue, true)) {
            typedValue.data
        } else {
            Color.parseColor("#22D3EE")
        }

        // Language selector pills (Apple TV+ grade)
        if (selectedLanguage != null && onLanguageChanged != null) {
            dialogBinding.scrollLanguagePills.visibility = View.VISIBLE
            renderLanguagePills(dialogBinding.layoutLanguagePills)
        } else {
            dialogBinding.scrollLanguagePills.visibility = View.GONE
        }

        // Action button
        if (!actionButtonText.isNullOrBlank()) {
            dialogBinding.btnDialogAction.text = actionButtonText
            dialogBinding.btnDialogAction.visibility = View.VISIBLE
            dialogBinding.btnDialogAction.setOnClickListener {
                onActionClicked?.invoke(this)
            }
        } else {
            dialogBinding.btnDialogAction.visibility = View.GONE
        }

        // Footer buttons
        val hasFooter = !footerPrimaryText.isNullOrBlank() ||
            !footerSecondaryText.isNullOrBlank() ||
            !footerTertiaryText.isNullOrBlank()
        if (hasFooter) {
            dialogBinding.layoutFooterActions.visibility = View.VISIBLE
            if (!footerTertiaryText.isNullOrBlank()) {
                dialogBinding.btnFooterTertiary.text = footerTertiaryText
                dialogBinding.btnFooterTertiary.visibility = View.VISIBLE
                dialogBinding.btnFooterTertiary.setOnClickListener {
                    dismiss()
                    onFooterTertiaryClicked?.invoke()
                }
            } else {
                dialogBinding.btnFooterTertiary.visibility = View.GONE
            }

            if (!footerSecondaryText.isNullOrBlank()) {
                dialogBinding.btnFooterSecondary.text = footerSecondaryText
                dialogBinding.btnFooterSecondary.visibility = View.VISIBLE
                dialogBinding.btnFooterSecondary.setOnClickListener {
                    dismiss()
                    onFooterSecondaryClicked?.invoke()
                }
            } else {
                dialogBinding.btnFooterSecondary.visibility = View.GONE
            }

            if (!footerPrimaryText.isNullOrBlank()) {
                dialogBinding.btnFooterPrimary.text = footerPrimaryText
                dialogBinding.btnFooterPrimary.visibility = View.VISIBLE
                dialogBinding.btnFooterPrimary.setOnClickListener {
                    dismiss()
                    onFooterPrimaryClicked?.invoke()
                }
            } else {
                dialogBinding.btnFooterPrimary.visibility = View.GONE
            }
        } else {
            dialogBinding.layoutFooterActions.visibility = View.GONE
        }

        adapter = GlassMenuAdapter(items.toMutableList(), accentColorResolved) { selectedItem ->
            onItemSelected(selectedItem)
            dismiss()
        }

        dialogBinding.rvDialogItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlayerGlassMenuDialog.adapter
            setHasFixedSize(false)
        }

        val selectedIndex = items.indexOfFirst { it.isInteractive && it.isSelected }
        val initialFocusIndex = GlassMenuFocusPolicy.initialInteractiveIndex(items)
        val initialVisibleIndex = selectedIndex.takeIf { it >= 0 } ?: initialFocusIndex
        if (initialVisibleIndex != null) {
            dialogBinding.rvDialogItems.scrollToPosition(initialVisibleIndex)
        }

        dialog = AlertDialog.Builder(context, R.style.ThemeOverlay_Makimono_Glass)
            .setView(dialogBinding.root)
            .create()

        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }

        zechs.drive.stream.utils.TvFocusRing.install(dialogBinding.root)
        dialog?.setOnDismissListener {
            (context as? PlayerModalHost)?.onPlayerModalVisibilityChanged(false)
            onDismiss?.invoke() ?: previousFocus?.takeIf { it.isAttachedToWindow && it.isShown }?.requestFocus()
        }
        (context as? PlayerModalHost)?.onPlayerModalVisibilityChanged(true)
        dialog?.show()
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = minOf(
                (540 * context.resources.displayMetrics.density).toInt(),
                (context.resources.displayMetrics.widthPixels * 0.92f).toInt()
            )
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            dialogBinding.root.setCardBackgroundColor(Color.TRANSPARENT)
            zechs.drive.stream.utils.FrostedWindow.apply(this)
        }
        dialogBinding.rvDialogItems.post {
            if (initialFocusIndex == null) {
                if (dialogBinding.btnDialogAction.visibility == View.VISIBLE) {
                    dialogBinding.btnDialogAction.requestFocus()
                } else {
                    dialogBinding.btnDialogClose.requestFocus()
                }
            } else {
                requestInitialItemFocus(dialogBinding, initialFocusIndex, attemptsRemaining = 5)
            }
        }
        return this
    }

    private fun stylePillView(pill: TextView, isSelected: Boolean) {
        val density = context.resources.displayMetrics.density
        val cornerRadius = 999f * density
        pill.typeface = Typeface.create("sans-serif-medium", if (isSelected) Typeface.BOLD else Typeface.NORMAL)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            this.cornerRadius = cornerRadius
            if (isSelected) {
                setColor(Color.argb(60, Color.red(accentColorResolved), Color.green(accentColorResolved), Color.blue(accentColorResolved)))
                setStroke((1.5f * density).toInt(), accentColorResolved)
            } else {
                setColor(Color.parseColor("#1E293B"))
                setStroke((1f * density).toInt(), Color.parseColor("#334155"))
            }
        }
        pill.background = bg
        pill.setTextColor(if (isSelected) Color.WHITE else Color.parseColor("#CBD5E1"))
    }

    private fun renderLanguagePills(container: LinearLayout) {
        val density = context.resources.displayMetrics.density
        val padH = (12 * density).toInt()
        val padV = (6 * density).toInt()
        val marginEnd = (8 * density).toInt()
        val languages = SubtitleLanguage.values()

        if (container.childCount == languages.size) {
            languages.forEachIndexed { idx, lang ->
                val pill = container.getChildAt(idx) as? TextView ?: return@forEachIndexed
                stylePillView(pill, lang == selectedLanguage)
            }
            return
        }

        container.removeAllViews()
        languages.forEach { lang ->
            val pill = TextView(context).apply {
                text = "${lang.flag} ${lang.shortLabel}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
                gravity = Gravity.CENTER
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                stylePillView(this, lang == selectedLanguage)

                setOnClickListener {
                    if (selectedLanguage != lang) {
                        selectedLanguage = lang
                        renderLanguagePills(container)
                        onLanguageChanged?.invoke(this@PlayerGlassMenuDialog, lang)
                    }
                }
            }

            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                this.marginEnd = marginEnd
            }
            container.addView(pill, lp)
        }
    }

    private fun requestInitialItemFocus(
        dialogBinding: DialogPlayerGlassMenuBinding,
        position: Int,
        attemptsRemaining: Int
    ) {
        val activeDialog = dialog ?: return
        if (!activeDialog.isShowing) return

        val list = dialogBinding.rvDialogItems
        val itemCount = adapter?.itemCount ?: 0
        if (itemCount == 0) {
            dialogBinding.btnDialogClose.requestFocus()
            return
        }

        val targetPosition = position.coerceIn(0, itemCount - 1)
        val target = list.findViewHolderForAdapterPosition(targetPosition)?.itemView
        if (target?.isShown == true && (target.requestFocus() || target.requestFocusFromTouch())) return

        if (attemptsRemaining > 0) {
            list.scrollToPosition(targetPosition)
            zechs.drive.stream.utils.LifecycleDelays.postDelayed(list, 32L) {
                requestInitialItemFocus(dialogBinding, targetPosition, attemptsRemaining - 1)
            }
        } else {
            dialogBinding.btnDialogClose.requestFocus()
        }
    }

    fun showLoading(show: Boolean) {
        binding?.let { b ->
            b.dialogProgressBar.visibility = if (show) View.VISIBLE else View.GONE
            b.btnDialogAction.isEnabled = !show
        }
    }

    fun updateSubtitle(text: String?) {
        subtitleText = text
        binding?.let { b ->
            if (!text.isNullOrBlank()) {
                b.tvDialogSubtitle.text = text
                b.tvDialogSubtitle.visibility = View.VISIBLE
            } else {
                b.tvDialogSubtitle.visibility = View.GONE
            }
        }
    }

    fun updateActionButtonText(text: String) {
        actionButtonText = text
        binding?.btnDialogAction?.text = text
    }

    fun updateItems(newItems: List<GlassMenuItem>) {
        items = newItems
        adapter?.updateList(newItems)
        val selectedIndex = newItems.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            binding?.rvDialogItems?.scrollToPosition(selectedIndex)
        }
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
        binding = null
    }

    private class GlassMenuAdapter(
        private val itemList: MutableList<GlassMenuItem>,
        private val accentColor: Int,
        private val onClick: (GlassMenuItem) -> Unit
    ) : RecyclerView.Adapter<GlassMenuAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemPlayerGlassMenuBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPlayerGlassMenuBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = itemList[position]
            val b = holder.binding

            b.tvItemTitle.text = item.title

            if (!item.subtitle.isNullOrBlank()) {
                b.tvItemSubtitle.text = item.subtitle
                b.tvItemSubtitle.maxLines = 3
                b.tvItemSubtitle.visibility = View.VISIBLE
            } else {
                b.tvItemSubtitle.visibility = View.GONE
            }

            if (item.isSelected) {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_checked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(accentColor)
                b.tvItemTitle.setTextColor(Color.parseColor("#FFFFFF"))
            } else if (!item.isInteractive) {
                b.ivItemCheck.setImageResource(R.drawable.ic_info_24)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(Color.parseColor("#94A3B8"))
                b.tvItemTitle.setTextColor(Color.parseColor("#E2E8F0"))
            } else {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_unchecked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
                b.tvItemTitle.setTextColor(Color.parseColor("#E2E8F0"))
            }

            b.itemRoot.apply {
                isClickable = item.isInteractive
                isFocusable = item.isInteractive
                setOnClickListener(if (item.isInteractive) View.OnClickListener { onClick(item) } else null)
            }
        }

        override fun getItemCount(): Int = itemList.size

        fun updateList(newList: List<GlassMenuItem>) {
            itemList.clear()
            itemList.addAll(newList)
            notifyDataSetChanged()
        }
    }
}
