package com.example.cinefiles.ui.person

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ItemPersonHeaderBinding
import com.example.cinefiles.logic.PersonRole
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.addChoiceChip
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.show

/**
 * The header of the Person screen: ONE item at the top of the poster grid (the screen joins it with the poster
 * adapter in a ConcatAdapter). It shows the photo, the name, the biography and, when the person has more than one kind of
 * work, the role tabs ("Actor (24)", "Director (8)"...). Everything it needs is in the [PersonState.Content] it is given with [submit].
 */
class PersonHeaderAdapter(
    private val onTabSelected: (PersonRole) -> Unit
) : RecyclerView.Adapter<PersonHeaderAdapter.HeaderViewHolder>() {

    class HeaderViewHolder(val binding: ItemPersonHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private var content: PersonState.Content? = null
    private var bioExpanded = false
    private var bindingNow = false // true while the tabs are being set from code: that is not a tap
    private var shownTabs: List<PersonTab>? = null // the tabs the chips were made for, so a tap does not rebuild them

    override fun getItemCount(): Int = if (content != null) 1 else 0

    fun submit(newContent: PersonState.Content) {
        val hadHeader = content != null
        if (content?.person?.id != newContent.person.id) bioExpanded = false
        content = newContent
        if (hadHeader) notifyItemChanged(0) else notifyItemInserted(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        val binding = ItemPersonHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        binding.stateEmpty.show(
            icon = R.drawable.ic_film,
            title = R.string.person_empty_title,
            message = R.string.person_empty_message
        )
        binding.personTabs.setOnCheckedStateChangeListener { group, checkedIds ->
            val role = checkedIds.firstOrNull()?.let { group.findViewById<View>(it)?.tag as? PersonRole }
            if (role != null && !bindingNow) onTabSelected(role)
        }
        binding.personBioToggle.setOnClickListener {
            bioExpanded = !bioExpanded
            applyBioLines(binding)
        }
        return HeaderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        val content = content ?: return
        val person = content.person
        val binding = holder.binding

        binding.personName.text = person.name
        binding.personKnownFor.text = person.knownFor?.let { binding.root.context.getString(R.string.person_known_for, it) }
        binding.personKnownFor.isVisible = person.knownFor != null
        // The profile icon behind stays visible while the photo loads, and when there is no photo at all
        binding.personPhoto.loadImage(ImageUrls.profile(person.profilePath), showSurfaceWhileMissing = false)

        val hasBio = person.biography.isNotBlank()
        binding.personBio.isVisible = hasBio
        if (hasBio) {
            binding.personBio.text = person.biography
            applyBioLines(binding)
        } else {
            binding.personBioToggle.isVisible = false
        }

        showTabs(binding, content)

        binding.stateEmpty.root.isVisible = content.movies.isEmpty()
    }

    /**
     * One chip per role ("Actor (24)"). The chips are only made again when the roles or their counts change, so tapping
     * one does not throw the row away (it would jump back to the start); a tap only moves the check mark.
     */
    private fun showTabs(binding: ItemPersonHeaderBinding, content: PersonState.Content) {
        binding.personTabsScroll.isVisible = content.showTabs
        if (!content.showTabs) return

        bindingNow = true
        if (shownTabs != content.tabs) {
            shownTabs = content.tabs
            binding.personTabs.removeAllViews()
            val context = binding.root.context
            for (tab in content.tabs) {
                val chip = binding.personTabs.addChoiceChip(context.getString(R.string.person_tab_label, context.getString(tab.role.label), tab.count))
                chip.tag = tab.role
            }
        }
        for (index in 0 until binding.personTabs.childCount) {
            val chip = binding.personTabs.getChildAt(index)
            val isSelected = chip.tag == content.role
            if (isSelected) binding.personTabs.check(chip.id)
        }
        bindingNow = false
    }

    override fun onViewRecycled(holder: HeaderViewHolder) {
        Glide.with(holder.binding.root).clear(holder.binding.personPhoto)
    }

    /** The story, collapsed to three lines. "More" is only offered when there is more to show. */
    private fun applyBioLines(binding: ItemPersonHeaderBinding) {
        binding.personBio.maxLines = if (bioExpanded) Int.MAX_VALUE else COLLAPSED_LINES
        binding.personBioToggle.setText(if (bioExpanded) R.string.detail_less else R.string.detail_more)

        // Only after the text has been laid out can we know whether it was cut off
        binding.personBio.doOnPreDraw {
            val layout = binding.personBio.layout ?: return@doOnPreDraw
            val isCutOff = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
            binding.personBioToggle.isVisible = bioExpanded || isCutOff
        }
    }

    private companion object {
        const val COLLAPSED_LINES = 3
    }
}
