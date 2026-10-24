package com.sift.app.presentation.onboarding

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemWelcomePageBinding
import com.sift.app.presentation.common.startFloat
import com.sift.app.presentation.common.startPulse

/** The three welcome pages: collect, understand, act. */
enum class WelcomePage(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
    @DrawableRes val primaryChipIcon: Int,
    @StringRes val primaryChip: Int,
    @DrawableRes val secondaryChipIcon: Int,
    @StringRes val secondaryChip: Int
) {
    COLLECT(
        R.drawable.ic_search, R.string.welcome_collect_title, R.string.welcome_collect_body,
        R.drawable.ic_star, R.string.welcome_collect_chip_primary,
        R.drawable.ic_upload_file, R.string.welcome_collect_chip_secondary
    ),
    UNDERSTAND(
        R.drawable.ic_auto_awesome, R.string.welcome_understand_title, R.string.welcome_understand_body,
        R.drawable.ic_bug, R.string.welcome_understand_chip_primary,
        R.drawable.ic_trending_up, R.string.welcome_understand_chip_secondary
    ),
    ACT(
        R.drawable.ic_trending_up, R.string.welcome_act_title, R.string.welcome_act_body,
        R.drawable.ic_lightbulb, R.string.welcome_act_chip_primary,
        R.drawable.ic_reply, R.string.welcome_act_chip_secondary
    )
}

class WelcomePagerAdapter : RecyclerView.Adapter<WelcomePagerAdapter.Holder>() {

    private val pages = WelcomePage.entries

    override fun getItemCount() = pages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemWelcomePageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(pages[position])

    class Holder(val binding: ItemWelcomePageBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(page: WelcomePage) {
            binding.icon.setImageResource(page.icon)
            binding.title.setText(page.title)
            binding.body.setText(page.body)
            binding.chipPrimaryText.setChip(page.primaryChipIcon, page.primaryChip)
            binding.chipSecondaryText.setChip(page.secondaryChipIcon, page.secondaryChip)

            val distance = binding.root.resources.getDimension(R.dimen.welcome_float_distance)
            binding.chipPrimary.startFloat(distance)
            binding.chipSecondary.startFloat(distance, startFraction = HALF_CYCLE)
            binding.orbGlow.startPulse()
        }

        private fun TextView.setChip(@DrawableRes icon: Int, @StringRes text: Int) {
            setText(text)
            val size = resources.getDimensionPixelSize(R.dimen.icon_xs)
            val drawable = AppCompatResources.getDrawable(context, icon)?.mutate()?.apply { setBounds(0, 0, size, size) }
            setCompoundDrawablesRelative(drawable, null, null, null)
            TextViewCompat.setCompoundDrawableTintList(
                this, ColorStateList.valueOf(ContextCompat.getColor(context, R.color.sift_primary_text))
            )
        }
    }

    private companion object {
        const val HALF_CYCLE = 0.5f
    }
}
