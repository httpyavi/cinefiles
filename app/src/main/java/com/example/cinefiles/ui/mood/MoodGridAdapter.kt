package com.example.cinefiles.ui.mood

import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ItemMoodTileBinding
import com.example.cinefiles.logic.Mood
import com.example.cinefiles.logic.MoodSelection

/**
 * Adapter for the 2-column mood selection grid (10 total mood choices).
 * Renders each mood with its illustration, exact name, exact description,
 * and clear interactive selected states.
 */
class MoodGridAdapter(
    private val moods: List<Mood>,
    private var selection: MoodSelection,
    private val onMoodClicked: (Mood, View) -> Unit
) : RecyclerView.Adapter<MoodGridAdapter.MoodViewHolder>() {

    fun updateSelection(newSelection: MoodSelection) {
        val oldSelection = selection
        selection = newSelection
        // Rebind affected items
        for (i in moods.indices) {
            val mood = moods[i]
            if (oldSelection.isSelected(mood) != newSelection.isSelected(mood)) {
                notifyItemChanged(i)
            }
        }
    }

    override fun getItemCount(): Int = moods.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val binding = ItemMoodTileBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return MoodViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        holder.bind(moods[position])
    }

    inner class MoodViewHolder(
        private val binding: ItemMoodTileBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(mood: Mood) {
            val context = itemView.context
            val isSelected = selection.isSelected(mood)

            binding.moodTitle.setText(mood.label)
            binding.moodDescription.setText(mood.line)

            // 1. Illustration
            if (mood.illustrationRes != null) {
                binding.moodIllustration.visibility = View.VISIBLE
                binding.moodIllustration.setImageResource(mood.illustrationRes)
            } else {
                // If illustration is missing, do not substitute another emotion's artwork
                binding.moodIllustration.setImageDrawable(null)
                binding.moodIllustration.visibility = View.INVISIBLE
            }

            // 2. Selected visual state
            val accentColor = ContextCompat.getColor(context, R.color.accent)
            val glassStroke = ContextCompat.getColor(context, R.color.glass_stroke)
            val surfaceColor = ContextCompat.getColor(context, R.color.surface)

            if (isSelected) {
                val strokeWidthPx = dpToPx(context, 2f)
                binding.moodTileCard.strokeWidth = strokeWidthPx
                binding.moodTileCard.strokeColor = accentColor
                binding.moodTileCard.setCardBackgroundColor(
                    ColorUtils.setAlphaComponent(accentColor, ACTIVE_FILL_ALPHA)
                )
                binding.moodCheckBadge.visibility = View.VISIBLE
            } else {
                val strokeWidthPx = dpToPx(context, 1f)
                binding.moodTileCard.strokeWidth = strokeWidthPx
                binding.moodTileCard.strokeColor = glassStroke
                binding.moodTileCard.setCardBackgroundColor(surfaceColor)
                binding.moodCheckBadge.visibility = View.GONE
            }

            // 3. Selection interaction and animation
            binding.root.setOnClickListener {
                binding.root.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(70L)
                    .withEndAction {
                        binding.root.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(90L)
                            .start()
                    }
                    .start()
                onMoodClicked(mood, binding.root)
            }
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Float): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics
        ).toInt()

    private companion object {
        const val ACTIVE_FILL_ALPHA = 46 // ~18% subtle red wash
    }
}
