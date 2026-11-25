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
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryText
import com.example.cinefiles.logic.DiaryTextSize
import com.example.cinefiles.logic.DiaryTheme
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage
import java.util.Date

/**
 * The list of diary pages. Each card wears the page's own canvas theme (background and text colors) and its own font for the headline and
 * the first words, so the list already shows how each page looks. Tap = open the page in the editor; the movie's name or poster = open that
 * movie's Details; the bin = ask to delete the page.
 */
class DiaryEntryAdapter(
    private val onOpen: (DiaryEntry) -> Unit,
    private val onOpenMovie: (DiaryEntry) -> Unit,
    private val onDelete: (DiaryEntry) -> Unit
) : ListAdapter<DiaryEntry, DiaryEntryAdapter.EntryHolder>(Diff) {

    class EntryHolder(val binding: ItemDiaryEntryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryHolder =
        EntryHolder(ItemDiaryEntryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: EntryHolder, position: Int) {
        val entry = getItem(position)
        val item = holder.binding
        val context = item.root.context
        val theme = DiaryTheme.of(entry.canvasTheme)
        val font = DiaryFont.of(entry.fontStyle)
        val size = DiaryTextSize.of(entry.textSize)

        item.entryCard.setCardBackgroundColor(context.themeColor(theme.card))
        item.entryPoster.loadImage(ImageUrls.poster(entry.posterPath))
        item.entryTitle.text = entry.movieTitle
        item.entryTitle.setTextColor(context.themeColor(theme.accent))
        item.entryTitle.compoundDrawablesRelative[2]?.setTint(context.themeColor(theme.accent))
        item.entryTitle.contentDescription = context.getString(R.string.diary_open_movie_description, entry.movieTitle)
        item.entryTitle.setOnClickListener { onOpenMovie(entry) }
        item.entryPoster.setOnClickListener { onOpenMovie(entry) }
        item.entryPoster.contentDescription = context.getString(R.string.diary_open_movie_description, entry.movieTitle)
        item.entryDate.text = DateFormat.getMediumDateFormat(context).format(Date(entry.updatedAt))
        item.entryDate.setTextColor(context.themeColor(theme.secondaryText))

        item.entryRating.isVisible = entry.ratingSnapshot != null
        item.entryRating.rating = entry.ratingSnapshot

        item.entryHeadline.isVisible = entry.headline.isNotBlank()
        item.entryHeadline.text = entry.headline
        item.entryHeadline.applyDiaryText(font, theme, baseSp = HEADLINE_SP)
        item.entryHeadline.paint.isFakeBoldText = true

        val preview = DiaryText.preview(entry.bodyHtml)
        item.entryPreview.text = preview.ifEmpty { context.getString(R.string.diary_no_words) }
        item.entryPreview.applyDiaryText(font, theme, baseSp = size.bodySp.coerceAtMost(PREVIEW_MAX_SP), secondary = preview.isEmpty())

        item.entryCard.contentDescription = context.getString(R.string.diary_page_description, entry.headline.ifBlank { entry.movieTitle })
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
        const val HEADLINE_SP = 17f
        const val PREVIEW_MAX_SP = 16f // a page's own text size shows in the list, but never so big that a card grows too tall
    }
}
