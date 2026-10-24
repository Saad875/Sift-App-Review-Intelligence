package com.sift.app.presentation.reviews

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemReviewBinding
import com.sift.app.domain.model.Review
import com.sift.app.presentation.common.Formatters
import com.sift.app.presentation.common.ItemEntrance
import com.sift.app.presentation.common.colorRes
import com.sift.app.presentation.common.containerColorRes
import com.sift.app.presentation.common.labelRes

/** Review cards with rating, sentiment, category and a text preview. */
class ReviewAdapter(
    private val onClick: (Review) -> Unit
) : ListAdapter<Review, ReviewAdapter.ViewHolder>(Diff) {

    private val entrance = ItemEntrance()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemReviewBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
        entrance.animate(holder.itemView, position)
    }

    /** Call before submitting a list for a new filter so the new items animate in. */
    fun resetEntrance() = entrance.reset()

    inner class ViewHolder(private val binding: ItemReviewBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(review: Review) {
            val context = binding.root.context
            binding.stars.isVisible = review.rating != null
            binding.stars.text = review.rating?.let { ReviewText.stars(context, it) }

            val sentiment = review.sentiment
            binding.sentimentBadge.isVisible = sentiment != null
            if (sentiment != null) {
                binding.sentimentBadge.setText(sentiment.labelRes)
                binding.sentimentBadge.setTextColor(ContextCompat.getColor(context, sentiment.colorRes))
                ViewCompat.setBackgroundTintList(
                    binding.sentimentBadge, ContextCompat.getColorStateList(context, sentiment.containerColorRes)
                )
            }
            binding.categoryBadge.isVisible = review.category != null
            review.category?.let { binding.categoryBadge.setText(it.labelRes) }
            binding.date.text = Formatters.date(review.dateMillis).orEmpty()

            binding.reviewTitle.isVisible = review.title.isNotBlank()
            binding.reviewTitle.text = review.title
            binding.reviewText.text = review.text
            val byline = ReviewText.byline(context, review)
            binding.author.isVisible = byline.isNotEmpty()
            binding.author.text = byline

            binding.root.contentDescription = ReviewText.description(context, review)
            binding.root.setOnClickListener { onClick(review) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<Review>() {
        override fun areItemsTheSame(oldItem: Review, newItem: Review) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Review, newItem: Review) = oldItem == newItem
    }
}

/** Shared text for review rows and the review detail screen. */
object ReviewText {
    private const val MAX_STARS = 5

    /** Five star glyphs, filled up to [rating]. */
    fun stars(context: Context, rating: Int): String {
        val filled = rating.coerceIn(0, MAX_STARS)
        return context.getString(R.string.star_filled).repeat(filled) +
            context.getString(R.string.star_empty).repeat(MAX_STARS - filled)
    }

    /** Author and app version, for example "maria_k · v4.2.1". */
    fun byline(context: Context, review: Review): String = listOfNotNull(
        review.author.takeIf { it.isNotBlank() },
        review.appVersion.takeIf { it.isNotBlank() }?.let { context.getString(R.string.review_version, it) }
    ).joinToString(context.getString(R.string.meta_separator))

    /** Spoken summary for TalkBack. */
    fun description(context: Context, review: Review): String = listOfNotNull(
        review.rating?.let { context.resources.getQuantityString(R.plurals.cd_star_rating, it, it) },
        review.sentiment?.let { context.getString(it.labelRes) },
        review.category?.let { context.getString(it.labelRes) },
        review.title.takeIf { it.isNotBlank() },
        review.text,
        Formatters.date(review.dateMillis)
    ).joinToString(context.getString(R.string.list_separator))
}
