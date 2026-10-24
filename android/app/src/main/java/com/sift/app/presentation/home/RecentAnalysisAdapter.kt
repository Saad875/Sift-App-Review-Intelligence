package com.sift.app.presentation.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemRecentAnalysisBinding
import com.sift.app.domain.model.Analysis
import com.sift.app.domain.model.AnalysisSource
import com.sift.app.domain.model.AnalysisStatus
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.ItemEntrance
import com.sift.app.presentation.common.labelRes
import com.sift.app.presentation.common.loadAppIcon

/** Recent analyses on the Home screen. */
class RecentAnalysisAdapter(
    private val onClick: (Analysis) -> Unit
) : ListAdapter<Analysis, RecentAnalysisAdapter.ViewHolder>(Diff) {

    private val entrance = ItemEntrance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemRecentAnalysisBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    inner class ViewHolder(private val binding: ItemRecentAnalysisBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Analysis) {
            val context = binding.root.context
            binding.appName.text = item.appName
            binding.appIcon.loadAppIcon(item.iconUrl.takeIf { item.source == AnalysisSource.APP_STORE || item.isDemo })

            val parts = mutableListOf(context.getString(item.source.labelRes))
            if (item.source == AnalysisSource.APP_STORE && item.country.isNotBlank()) {
                parts += Formatters.countryName(item.country)
            }
            Formatters.date(item.createdAtMillis)?.let { parts += it }
            binding.meta.text = parts.joinToString(context.getString(R.string.meta_separator))

            val (label, color) = when {
                item.isDemo -> context.getString(R.string.sample_data_label) to R.color.sift_neutral
                item.status == AnalysisStatus.COMPLETED -> context.resources.getQuantityString(
                    R.plurals.review_count, item.stats.totalReviews, Formatters.count(item.stats.totalReviews)
                ) to R.color.sift_text_secondary
                item.status == AnalysisStatus.FAILED -> context.getString(R.string.status_failed) to R.color.sift_negative
                else -> context.getString(R.string.status_in_progress, item.progress) to R.color.sift_primary_text
            }
            binding.statusBadge.text = label
            binding.statusBadge.setTextColor(ContextCompat.getColor(context, color))
            binding.root.contentDescription = context.getString(
                R.string.cd_recent_analysis, item.appName, binding.meta.text, label
            )
            binding.root.setOnClickListener { onClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Analysis>() {
        override fun areItemsTheSame(oldItem: Analysis, newItem: Analysis) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Analysis, newItem: Analysis) = oldItem == newItem
    }
}
