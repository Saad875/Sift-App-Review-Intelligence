package com.sift.app.presentation.setup

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.sift.app.R
import com.sift.app.databinding.ItemCountryBinding
import com.sift.app.presentation.common.Countries
import com.sift.app.presentation.common.Formatters

/** Country choices in the in-screen picker, sorted by localised name. */
class CountryAdapter(
    private val onSelected: (String) -> Unit
) : RecyclerView.Adapter<CountryAdapter.ViewHolder>() {

    private val countries = Countries.codes.sortedBy { Formatters.countryName(it) }
    private var selected: String = ""

    fun setSelected(code: String) {
        if (code == selected) return
        val previous = countries.indexOf(selected)
        selected = code
        if (previous >= 0) notifyItemChanged(previous)
        notifyItemChanged(countries.indexOf(code))
    }

    fun positionOf(code: String): Int = countries.indexOf(code).coerceAtLeast(0)

    override fun getItemCount(): Int = countries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemCountryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(countries[position])

    inner class ViewHolder(private val binding: ItemCountryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(code: String) {
            val name = Formatters.countryName(code)
            val isSelected = code == selected
            binding.flag.text = Formatters.countryFlag(code)
            binding.name.text = name
            binding.check.isInvisible = !isSelected
            binding.root.isSelected = isSelected
            binding.root.contentDescription = if (isSelected) {
                binding.root.context.getString(R.string.cd_country_selected, name)
            } else {
                name
            }
            binding.root.setOnClickListener { onSelected(code) }
        }
    }
}
