package com.example.cinefiles.ui.gallery

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentDesignGalleryBinding
import com.example.cinefiles.databinding.ItemGalleryIconBinding
import com.example.cinefiles.databinding.ItemGallerySwatchBinding
import com.example.cinefiles.ui.components.PosterBadge
import com.example.cinefiles.util.applySystemBarsPadding
import com.google.android.material.snackbar.Snackbar
import java.util.Locale

// TEMP: Design gallery. Shows every component so the design system can be reviewed on a phone.
// Nothing here is real app logic. Removed in Batch 8 (see TEMP.md).
class DesignGalleryFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentDesignGalleryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDesignGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.galleryScroll.applySystemBarsPadding()

        addColorSwatches()
        addRadiusSwatches()
        addIcons()
        setUpButtons()
        setUpActionButtons()
        setUpPosters()
        setUpStateLayouts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---- Colors, radii and icons are drawn from lists, so adding one is a one-line change ----

    private fun addColorSwatches() {
        val colors = listOf(
            R.color.background, R.color.surface, R.color.elevated,
            R.color.accent, R.color.on_accent, R.color.text_primary,
            R.color.text_secondary, R.color.like, R.color.pass
        )
        for (colorRes in colors) {
            val color = ContextCompat.getColor(requireContext(), colorRes)
            val item = ItemGallerySwatchBinding.inflate(layoutInflater, binding.colorGrid, false)
            (item.swatchColor.background.mutate() as GradientDrawable).setColor(color)
            item.swatchName.text = resources.getResourceEntryName(colorRes)
            item.swatchValue.text = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)
            binding.colorGrid.addView(item.root)
        }
    }

    private fun addRadiusSwatches() {
        val radii = listOf(
            R.dimen.radius_small, R.dimen.radius_medium, R.dimen.radius_large,
            R.dimen.radius_xlarge, R.dimen.radius_pill
        )
        val fill = ContextCompat.getColor(requireContext(), R.color.elevated)
        for (radiusRes in radii) {
            val radius = resources.getDimension(radiusRes)
            val item = ItemGallerySwatchBinding.inflate(layoutInflater, binding.radiiGrid, false)
            val shape = item.swatchColor.background.mutate() as GradientDrawable
            shape.setColor(fill)
            shape.cornerRadius = radius // a radius bigger than half the height becomes a pill
            item.swatchName.text = resources.getResourceEntryName(radiusRes).removePrefix("radius_")
            item.swatchValue.text = getString(R.string.gallery_dp, (radius / resources.displayMetrics.density).toInt())
            binding.radiiGrid.addView(item.root)
        }
    }

    private fun addIcons() {
        val icons = listOf(
            R.drawable.ic_home, R.drawable.ic_home_filled,
            R.drawable.ic_discover, R.drawable.ic_discover_filled,
            R.drawable.ic_bookmark, R.drawable.ic_bookmark_filled,
            R.drawable.ic_profile, R.drawable.ic_profile_filled,
            R.drawable.ic_heart, R.drawable.ic_heart_filled,
            R.drawable.ic_check, R.drawable.ic_check_filled,
            R.drawable.ic_star, R.drawable.ic_star_filled,
            R.drawable.ic_search, R.drawable.ic_close,
            R.drawable.ic_back, R.drawable.ic_undo,
            R.drawable.ic_info, R.drawable.ic_play,
            R.drawable.ic_filter
        )
        for (iconRes in icons) {
            val item = ItemGalleryIconBinding.inflate(layoutInflater, binding.iconGrid, false)
            item.iconImage.setImageResource(iconRes)
            item.iconName.text = resources.getResourceEntryName(iconRes).removePrefix("ic_")
            binding.iconGrid.addView(item.root)
        }
    }

    // ---- Click handling: every control answers with a small message ----

    private fun setUpButtons() {
        binding.buttonPrimary.setOnClickListener { showTapped(R.string.gallery_button_primary) }
        binding.buttonTonal.setOnClickListener { showTapped(R.string.gallery_button_tonal) }
        binding.buttonText.setOnClickListener { showTapped(R.string.gallery_button_text) }
        // The disabled button has no listener on purpose: tapping it does nothing.
    }

    private fun setUpActionButtons() {
        binding.actionPass.setOnClickListener { showTapped(R.string.gallery_action_pass) }
        binding.actionUndo.setOnClickListener { showTapped(R.string.gallery_action_undo) }
        binding.actionInfo.setOnClickListener { showTapped(R.string.gallery_action_info) }
        binding.actionLike.setOnClickListener { showTapped(R.string.gallery_action_like) }
    }

    private fun setUpPosters() {
        val posters = listOf(
            binding.posterOne to R.drawable.temp_sample_poster_1,
            binding.posterTwo to R.drawable.temp_sample_poster_2,
            binding.posterThree to R.drawable.temp_sample_poster_3,
            binding.posterFour to R.drawable.temp_sample_poster_4
        )
        posters.forEachIndexed { index, (poster, picture) ->
            poster.imageView.setImageResource(picture)
            poster.contentDescription = getString(R.string.gallery_poster_description, index + 1)
            // Tapping a poster steps through: no badge -> liked -> watchlist -> watched -> no badge
            poster.setOnClickListener { poster.badge = nextBadge(poster.badge) }
        }

        // Start with one poster per badge so all of them are visible right away
        binding.posterTwo.badge = PosterBadge.LIKED
        binding.posterThree.badge = PosterBadge.WATCHLIST
        binding.posterFour.badge = PosterBadge.WATCHED
    }

    private fun nextBadge(current: PosterBadge?): PosterBadge? {
        val all = PosterBadge.values()
        return if (current == null) all.first() else all.getOrNull(current.ordinal + 1)
    }

    private fun setUpStateLayouts() {
        // Empty state: a screen sets its own icon, texts and button
        with(binding.stateEmpty) {
            emptyIcon.setImageResource(R.drawable.ic_bookmark)
            emptyTitle.setText(R.string.gallery_empty_title)
            emptyMessage.setText(R.string.gallery_empty_message)
            emptyAction.setText(R.string.gallery_empty_action)
            emptyAction.isVisible = true
            emptyAction.setOnClickListener { showTapped(R.string.gallery_empty_action) }
        }
        // Error state: the screen decides what Retry does
        binding.stateError.errorRetry.setOnClickListener { showTapped(R.string.state_retry) }
    }

    private fun showTapped(@StringRes label: Int) {
        val message = getString(R.string.gallery_tapped, getString(label))
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
}
