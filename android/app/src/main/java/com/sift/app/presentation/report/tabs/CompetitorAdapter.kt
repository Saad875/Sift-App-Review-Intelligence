package com.sift.app.presentation.report.tabs

import android.text.SpannableString
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemBulletBinding
import com.sift.app.databinding.ItemCompetitorBinding
import com.sift.app.databinding.ItemCompetitorSectionBinding
import com.sift.app.databinding.ItemSourceBinding
import com.sift.app.domain.model.CompetitorInsight
import com.sift.app.domain.model.SourceLink
import com.sift.app.presentation.common.ItemEntrance

/** Competitor research cards: summary, strengths, weaknesses, opportunities and cited sources. */
class CompetitorAdapter(
    private val onSourceClick: (SourceLink) -> Unit
) : ListAdapter<CompetitorInsight, CompetitorAdapter.ViewHolder>(Diff) {

    private val entrance = ItemEntrance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCompetitorBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    inner class ViewHolder(private val binding: ItemCompetitorBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(insight: CompetitorInsight) {
            val context = binding.root.context
            val failed = insight.error.isNotBlank()
            binding.competitorName.text = insight.name
            binding.summary.isVisible = insight.summary.isNotBlank()
            binding.summary.text = insight.summary
            binding.researchError.isVisible = failed
            binding.researchError.text = context.getString(R.string.competitor_research_failed)

            val sections = binding.sections
            sections.removeAllViews()
            if (failed) return
            addBullets(R.string.competitor_strengths, insight.strengths)
            addBullets(R.string.competitor_weaknesses, insight.weaknesses)
            addBullets(R.string.competitor_opportunities, insight.opportunities)
            addSources(insight.sources)
        }

        private fun addBullets(@StringRes title: Int, values: List<String>) {
            if (values.isEmpty()) return
            val section = addSection(title)
            val inflater = LayoutInflater.from(section.root.context)
            values.forEach { value ->
                val row = ItemBulletBinding.inflate(inflater, section.sectionItems, false).root
                val text = row.context.getString(R.string.bullet_item, value)
                // Hang wrapped lines under the text, not under the bullet.
                val prefixEnd = if (text.endsWith(value)) text.length - value.length else 0
                val bulletWidth = row.paint.measureText(text, 0, prefixEnd).toInt()
                row.text = SpannableString(text).apply {
                    setSpan(LeadingMarginSpan.Standard(0, bulletWidth), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                section.sectionItems.addView(row)
            }
        }

        private fun addSources(sources: List<SourceLink>) {
            if (sources.isEmpty()) return
            val section = addSection(R.string.competitor_sources)
            val inflater = LayoutInflater.from(section.root.context)
            sources.forEach { source ->
                val row = ItemSourceBinding.inflate(inflater, section.sectionItems, false).root
                val label = source.title.ifBlank { source.url }
                row.text = label
                row.contentDescription = row.context.getString(R.string.cd_open_source, label)
                row.setOnClickListener { onSourceClick(source) }
                section.sectionItems.addView(row)
            }
        }

        private fun addSection(@StringRes title: Int): ItemCompetitorSectionBinding {
            val section = ItemCompetitorSectionBinding.inflate(
                LayoutInflater.from(binding.root.context), binding.sections, false
            )
            section.sectionTitle.setText(title)
            binding.sections.addView(section.root)
            return section
        }
    }

    private object Diff : DiffUtil.ItemCallback<CompetitorInsight>() {
        override fun areItemsTheSame(oldItem: CompetitorInsight, newItem: CompetitorInsight) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CompetitorInsight, newItem: CompetitorInsight) = oldItem == newItem
    }
}
