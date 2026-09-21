package zechs.drive.stream.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import zechs.drive.stream.R
import zechs.drive.stream.databinding.DialogPlayerGlassMenuBinding
import zechs.drive.stream.databinding.ItemPlayerGlassMenuBinding

data class GlassMenuItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val isSelected: Boolean = false,
    val tag: Any? = null
)

class PlayerGlassMenuDialog(
    private val context: Context,
    private val title: String,
    private var items: List<GlassMenuItem>,
    private val onItemSelected: (GlassMenuItem) -> Unit
) {

    private var actionButtonText: String? = null
    private var onActionClicked: ((PlayerGlassMenuDialog) -> Unit)? = null

    private var footerSecondaryText: String? = null
    private var onFooterSecondaryClicked: (() -> Unit)? = null

    private var footerPrimaryText: String? = null
    private var onFooterPrimaryClicked: (() -> Unit)? = null

    private var dialog: AlertDialog? = null
    private var binding: DialogPlayerGlassMenuBinding? = null
    private var adapter: GlassMenuAdapter? = null
    private var onDismiss: (() -> Unit)? = null

    fun setOnDismiss(action: () -> Unit) = apply { onDismiss = action }

    fun setActionButton(text: String, onClick: (PlayerGlassMenuDialog) -> Unit) = apply {
        this.actionButtonText = text
        this.onActionClicked = onClick
    }

    fun setFooterSecondary(text: String, onClick: () -> Unit) = apply {
        this.footerSecondaryText = text
        this.onFooterSecondaryClicked = onClick
    }

    fun setFooterPrimary(text: String, onClick: () -> Unit) = apply {
        this.footerPrimaryText = text
        this.onFooterPrimaryClicked = onClick
    }

    fun show(): PlayerGlassMenuDialog {
        val previousFocus = (context as? android.app.Activity)?.currentFocus
        val inflater = LayoutInflater.from(context)
        val dialogBinding = DialogPlayerGlassMenuBinding.inflate(inflater)
        binding = dialogBinding

        dialogBinding.tvDialogTitle.text = title
        dialogBinding.btnDialogClose.setOnClickListener {
            dismiss()
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
        val hasFooter = !footerPrimaryText.isNullOrBlank() || !footerSecondaryText.isNullOrBlank()
        if (hasFooter) {
            dialogBinding.layoutFooterActions.visibility = View.VISIBLE
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

        // Resolve accent color
        val typedValue = TypedValue()
        val accentColor = if (context.theme.resolveAttribute(R.attr.colorAccentPrimary, typedValue, true)) {
            typedValue.data
        } else {
            Color.parseColor("#22D3EE")
        }

        adapter = GlassMenuAdapter(items.toMutableList(), accentColor) { selectedItem ->
            onItemSelected(selectedItem)
            dismiss()
        }

        dialogBinding.rvDialogItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@PlayerGlassMenuDialog.adapter
            setHasFixedSize(false)
        }

        val selectedIndex = items.indexOfFirst { it.isSelected }
        if (selectedIndex >= 0) {
            dialogBinding.rvDialogItems.scrollToPosition(selectedIndex)
        }

        dialog = AlertDialog.Builder(context, R.style.ThemeOverlay_Makimono_Glass)
            .setView(dialogBinding.root)
            .create()

        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setDimAmount(0.65f)
        }

        dialog?.show()
        zechs.drive.stream.utils.TvFocusRing.install(dialogBinding.root)
        dialog?.setOnDismissListener {
            onDismiss?.invoke() ?: previousFocus?.takeIf { it.isAttachedToWindow && it.isShown }?.requestFocus()
        }
        dialog?.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = minOf((520 * context.resources.displayMetrics.density).toInt(),
                (context.resources.displayMetrics.widthPixels * 0.92f).toInt())
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            dialogBinding.root.setCardBackgroundColor(Color.TRANSPARENT)
            zechs.drive.stream.utils.FrostedWindow.apply(this)
        }
        dialogBinding.rvDialogItems.post {
            val targetPos = if (selectedIndex >= 0) selectedIndex else 0
            val targetHolder = dialogBinding.rvDialogItems.findViewHolderForAdapterPosition(targetPos)
            targetHolder?.itemView?.requestFocus() ?: dialogBinding.rvDialogItems.requestFocus()
        }
        return this
    }

    fun showLoading(show: Boolean) {
        binding?.let { b ->
            b.dialogProgressBar.visibility = if (show) View.VISIBLE else View.GONE
            b.btnDialogAction.isEnabled = !show
        }
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
                b.tvItemSubtitle.visibility = View.VISIBLE
            } else {
                b.tvItemSubtitle.visibility = View.GONE
            }

            if (item.isSelected) {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_checked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(accentColor)
                b.tvItemTitle.setTextColor(Color.parseColor("#FFFFFF"))
            } else {
                b.ivItemCheck.setImageResource(R.drawable.ic_radio_unchecked)
                b.ivItemCheck.imageTintList = ColorStateList.valueOf(Color.parseColor("#64748B"))
                b.tvItemTitle.setTextColor(Color.parseColor("#E2E8F0"))
            }

            b.itemRoot.setOnClickListener {
                onClick(item)
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
