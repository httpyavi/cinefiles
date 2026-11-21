package com.example.cinefiles.ui.mood

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.databinding.ItemMoodCardBinding
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.util.loadImage

/**
 * The grid of mood cards. It only draws what it is given and reports taps; the selection rules
 * live in logic/MoodSelection.kt and the state in WatchFlowViewModel.
 *
 * @param onMoodClick  called with the tapped mood and its card view (the screen shakes the view
 *                     when the limit of three was reached)
 */
class MoodAdapter(
    private val onMoodClick: (Mood, View) -> Unit
) : ListAdapter<MoodCard, MoodAdapter.MoodViewHolder>(MoodCardDiffCallback) {

    class MoodViewHolder(val binding: ItemMoodCardBinding) : RecyclerView.ViewHolder(binding.root) {

        // Was this card drawn as selected the last time? null = never drawn yet.
        // It tells us whether to ANIMATE a change or just show the right look straight away.
        private var shownSelected: Boolean? = null

        fun bind(card: MoodCard) {
            val mood = card.mood
            binding.moodName.setText(mood.label)
            binding.moodHint.setText(mood.hint)

            // The fallback look, visible whenever there is no photo: a faint tint and an icon
            val tint = ContextCompat.getColor(binding.root.context, mood.tint)
            binding.moodTint.setBackgroundColor(tint)
            binding.moodTint.alpha = FALLBACK_TINT_ALPHA
            binding.moodIcon.setImageResource(mood.icon)
            binding.moodIcon.alpha = FALLBACK_ICON_ALPHA
            ImageViewCompat.setImageTintList(binding.moodIcon, ColorStateList.valueOf(tint))
            binding.moodIcon.isVisible = true

            // The photo, when there is one. It lies on top of the tint and covers it. The icon belongs to
            // the fallback look, so it goes away once a photo has really arrived (and stays if there is
            // no photo, or the download failed).
            binding.moodImage.loadImage(card.imageUrl, showSurfaceWhileMissing = false) { loaded ->
                binding.moodIcon.isVisible = !loaded
            }

            showSelected(card.isSelected)
        }

        private fun showSelected(selected: Boolean) {
            val card = binding.moodCard
            val animate = shownSelected != null && shownSelected != selected
            shownSelected = selected

            card.isChecked = selected // gold outline + check badge
            val scale = if (selected) SELECTED_SCALE else 1f // and a slight grow
            if (animate) {
                card.animate().scaleX(scale).scaleY(scale).setDuration(SCALE_DURATION_MS).start()
            } else {
                card.animate().cancel()
                card.scaleX = scale
                card.scaleY = scale
            }
        }

        fun clear() {
            Glide.with(binding.root).clear(binding.moodImage)
            binding.moodCard.animate().cancel()
            shownSelected = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val binding = ItemMoodCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = MoodViewHolder(binding)
        binding.moodCard.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                onMoodClick(getItem(position).mood, binding.moodCard)
            }
        }
        return holder
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: MoodViewHolder) {
        holder.clear()
    }

    /** Tells DiffUtil when two cards are the same mood, and when that card has changed. */
    private object MoodCardDiffCallback : DiffUtil.ItemCallback<MoodCard>() {
        override fun areItemsTheSame(oldItem: MoodCard, newItem: MoodCard) =
            oldItem.mood.id == newItem.mood.id

        override fun areContentsTheSame(oldItem: MoodCard, newItem: MoodCard) = oldItem == newItem
    }

    private companion object {
        const val SELECTED_SCALE = 1.03f   // "slightly bigger" when selected
        const val SCALE_DURATION_MS = 150L
        const val FALLBACK_TINT_ALPHA = 0.16f // how faint the tint wash is
        const val FALLBACK_ICON_ALPHA = 0.85f
    }
}
