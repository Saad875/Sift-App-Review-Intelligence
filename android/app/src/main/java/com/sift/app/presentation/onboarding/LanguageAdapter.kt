package com.sift.app.presentation.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemLanguageBinding
import com.sift.app.domain.model.AppLanguage
import com.sift.app.presentation.common.ItemEntrance
import com.sift.app.presentation.common.LanguageLabels
import com.sift.app.presentation.common.Motion
import com.sift.app.presentation.common.hapticTap

/** One row per supported language; the selected row gets a green outline and check. */
class LanguageAdapter(
    private val onSelect: (AppLanguage) -> Unit
) : ListAdapter<AppLanguage, LanguageAdapter.Holder>(Diff) {

    private val entrance = ItemEntrance()
    var selected: AppLanguage? = null
        set(value) {
            val previous = field
            field = value
            listOfNotNull(previous, value).map(currentList::indexOf).filter { it >= 0 }.distinct()
                .forEach { notifyItemChanged(it, PAYLOAD_SELECTION) }
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) holder.bindSelection(getItem(position), animate = true)
        else onBindViewHolder(holder, position)
    }

    inner class Holder(private val binding: ItemLanguageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(language: AppLanguage) {
            val context = binding.root.context
            binding.mark.setText(LanguageLabels.mark(language))
            binding.nativeName.setText(LanguageLabels.nativeName(language))
            binding.localName.text = LanguageLabels.localizedName(context, language)
            binding.root.setOnClickListener {
                it.hapticTap()
                onSelect(language)
            }
            bindSelection(language, animate = false)
        }

        fun bindSelection(language: AppLanguage, animate: Boolean) {
            val context = binding.root.context
            val isSelected = language == selected
            val card = binding.root
            card.strokeColor = ContextCompat.getColor(context, if (isSelected) R.color.sift_primary else R.color.sift_glass_stroke)
            card.strokeWidth = context.resources.getDimensionPixelSize(
                if (isSelected) R.dimen.selection_stroke else R.dimen.stroke_hairline
            )
            card.isSelected = isSelected
            card.contentDescription = context.getString(
                if (isSelected) R.string.cd_language_selected else R.string.cd_language_option,
                context.getString(LanguageLabels.nativeName(language)),
                LanguageLabels.localizedName(context, language)
            )
            val target = if (isSelected) 1f else 0f
            binding.check.animate().cancel()
            if (animate && Motion.enabled(context)) {
                binding.check.animate().scaleX(target).scaleY(target).alpha(target)
                    .setDuration(Motion.duration(context, R.integer.motion_medium))
                    .setInterpolator(Motion.emphasizedDecelerate)
                    .start()
            } else {
                binding.check.scaleX = target
                binding.check.scaleY = target
                binding.check.alpha = target
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppLanguage>() {
        override fun areItemsTheSame(oldItem: AppLanguage, newItem: AppLanguage) = oldItem == newItem
        override fun areContentsTheSame(oldItem: AppLanguage, newItem: AppLanguage) = oldItem == newItem
    }

    private companion object {
        const val PAYLOAD_SELECTION = "selection"
    }
}
