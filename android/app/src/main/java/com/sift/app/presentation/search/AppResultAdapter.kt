package com.sift.app.presentation.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemAppResultBinding
import com.sift.app.domain.model.AppSummary
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.ItemEntrance
import com.sift.app.presentation.common.loadAppIcon

/** App Store search results. */
class AppResultAdapter(
    private val onClick: (AppSummary) -> Unit
) : ListAdapter<AppSummary, AppResultAdapter.ViewHolder>(Diff) {

    private val entrance = ItemEntrance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAppResultBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    /** New results stagger in again. */
    fun submitResults(items: List<AppSummary>) {
        if (items != currentList) entrance.reset()
        submitList(items)
    }

    inner class ViewHolder(private val binding: ItemAppResultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(app: AppSummary) {
            val context = binding.root.context
            binding.appName.text = app.name
            binding.developer.text = app.developer
            binding.appIcon.loadAppIcon(app.iconUrl)
            val parts = mutableListOf<String>()
            app.rating?.let { parts += Formatters.rating(context, it) }
            app.ratingCount?.let {
                parts += context.resources.getQuantityString(R.plurals.rating_count, it, Formatters.count(it))
            }
            if (app.genre.isNotBlank()) parts += app.genre
            binding.rating.text = parts.joinToString(context.getString(R.string.meta_separator))
            binding.root.contentDescription = context.getString(
                R.string.cd_app_result, app.name, app.developer, binding.rating.text
            )
            binding.root.setOnClickListener { onClick(app) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<AppSummary>() {
        override fun areItemsTheSame(oldItem: AppSummary, newItem: AppSummary) = oldItem.appId == newItem.appId
        override fun areContentsTheSame(oldItem: AppSummary, newItem: AppSummary) = oldItem == newItem
    }
}
