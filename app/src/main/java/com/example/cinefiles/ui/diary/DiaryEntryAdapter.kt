package com.example.cinefiles.ui.diary

import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.DiaryEntry
import com.example.cinefiles.databinding.ItemDiaryEntryBinding
import com.example.cinefiles.logic.DiaryBlocks
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage
import java.util.Date

/**
 * The list of diary pages. Each card wears the page's own theme (background and text colors) and its own font for the
 * first words, so the list already shows how each page looks. Tap = open the page in the editor; the bin = ask to delete it.
 */
class DiaryEntryAdapter(
    private val onOpen: (DiaryEntry) -> Unit,
    private val onDelete: (DiaryEntry) -> Unit
) : ListAdapter<DiaryEntry, DiaryEntryAdapter.EntryHolder>(Diff) {

    class EntryHolder(val binding: ItemDiaryEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder =
        EntryHolder(ItemDiaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: EntryHolder, position: Int) {
        val entry = getItem(position)
        val item = holder.binding
        val context = item.root.context
        val theme = DiaryTheme.of(entry.backgroundTheme)
        val font = DiaryFont.of(entry.fontStyle)

        item.entryCard.setCardBackgroundColor(context.themeColor(theme.card))
        item.entryPoster.loadImage(ImageUrls.poster(entry.posterPath))
        item.entryTitle.text = entry.movieTitle
        item.entryTitle.setTextColor(context.themeColor(theme.text))
        item.entryDate.text = DateFormat.getMediumDateFormat(context).format(Date(entry.updatedAt))
        item.entryDate.setTextColor(context.themeColor(theme.secondaryText))

        item.entryRating.isVisible = entry.ratingSnapshot != null
        item.entryRating.rating = entry.ratingSnapshot

        val preview = DiaryBlocks.preview(DiaryBlocks.fromJson(entry.blocksJson))
        item.entryPreview.text = preview.ifEmpty { context.getString(R.string.diary_no_words) }
        item.entryPreview.applyDiaryText(font, theme, baseSp = PREVIEW_SP, secondary = preview.isEmpty())

        item.entryCard.contentDescription = context.getString(R.string.diary_page_description, entry.movieTitle)
        item.entryCard.setOnClickListener { onOpen(entry) }
        item.entryDelete.setOnClickListener { onDelete(entry) }
    }

    override fun onViewRecycled(holder: EntryHolder) {
        Glide.with(holder.binding.root).clear(holder.binding.entryPoster)
    }

    private object Diff : DiffUtil.ItemCallback<DiaryEntry>() {
        override fun areItemsTheSame(old: DiaryEntry, new: DiaryEntry) = old.id == new.id
        override fun areContentsTheSame(old: DiaryEntry, new: DiaryEntry) = old == new
    }

    private companion object {
        const val PREVIEW_SP = 15f
    }
}
