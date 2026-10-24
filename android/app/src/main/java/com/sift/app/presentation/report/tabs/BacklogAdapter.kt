package com.sift.app.presentation.report.tabs

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemBacklogBinding
import com.sift.app.domain.model.BacklogItem
import com.sift.app.presentation.common.ItemEntrance
import com.sift.app.presentation.common.colorRes
import com.sift.app.presentation.common.iconRes
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.common.priorityColorRes

/** Ranked backlog items with a priority ring, type and effort. */
class BacklogAdapter(
    private val onClick: (BacklogItem) -> Unit
) : ListAdapter<BacklogItem, BacklogAdapter.ViewHolder>(Diff) {

    private val entrance = ItemEntrance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemBacklogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    inner class ViewHolder(private val binding: ItemBacklogBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: BacklogItem) {
            val context = binding.root.context
            binding.rank.text = context.getString(R.string.backlog_rank, item.rank)
            binding.title.text = item.title
            binding.score.setScore(item.priorityScore, ContextCompat.getColor(context, priorityColorRes(item.priorityScore)))

            binding.typeBadge.setText(item.type.labelRes)
            binding.typeBadge.setCompoundDrawablesRelativeWithIntrinsicBounds(item.type.iconRes, 0, 0, 0)
            val typeColor = ContextCompat.getColorStateList(context, item.type.colorRes)
            TextViewCompat.setCompoundDrawableTintList(binding.typeBadge, typeColor)
            binding.typeBadge.setTextColor(ContextCompat.getColor(context, item.type.colorRes))

            val effort = context.getString(item.effort.labelRes)
            binding.effortBadge.text = context.getString(R.string.backlog_effort, effort)
            binding.impact.text = item.userImpact
            binding.impact.isVisible = item.userImpact.isNotBlank()
            binding.themeLine.isVisible = !item.themeTitle.isNullOrBlank()
            binding.themeLine.text = item.themeTitle?.let { context.getString(R.string.backlog_theme, it) }

            binding.root.contentDescription = context.getString(
                R.string.cd_backlog_item,
                item.rank,
                item.title,
                context.getString(item.type.labelRes),
                item.priorityScore,
                effort
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<BacklogItem>() {
        override fun areItemsTheSame(oldItem: BacklogItem, newItem: BacklogItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: BacklogItem, newItem: BacklogItem) = oldItem == newItem
    }
}
