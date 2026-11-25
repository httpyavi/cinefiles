package com.example.cinefiles.ui.diary

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.databinding.ItemDiaryHeaderBinding
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme

/**
 * The headline of a diary page: ONE item at the top of the page (the editor joins it with [DiaryBlockAdapter] in a ConcatAdapter, so the headline scrolls
 * away with the page like the title of an article). It shows the movie, the date and the rating the page was written with, in the page's font and colors.
 */
class DiaryHeaderAdapter : RecyclerView.Adapter<DiaryHeaderAdapter.HeaderHolder>() {

    class HeaderHolder(val binding: ItemDiaryHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private var title: String? = null
    private var meta: String = ""
    private var font = DiaryFont.SANS
    private var theme = DiaryTheme.MIDNIGHT

    override fun getItemCount(): Int = if (title != null) 1 else 0

    /** Shows the headline. The first call makes the item appear. */
    fun show(newTitle: String, newMeta: String) {
        val hadHeader = title != null
        title = newTitle
        meta = newMeta
        if (hadHeader) notifyItemChanged(0) else notifyItemInserted(0)
    }

    fun setStyle(newFont: DiaryFont, newTheme: DiaryTheme) {
        if (newFont == font && newTheme == theme) return
        font = newFont
        theme = newTheme
        if (title != null) notifyItemChanged(0)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder =
        HeaderHolder(ItemDiaryHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
        val binding = holder.binding
        val context = binding.root.context
        binding.headerTitle.text = title
        binding.headerTitle.applyDiaryText(font, theme, baseSp = TITLE_SP)
        binding.headerMeta.text = meta
        binding.headerMeta.applyDiaryText(font, theme, baseSp = META_SP, secondary = true)
        binding.headerRule.setBackgroundColor(context.themeColor(theme.accent))
    }

    private companion object {
        const val TITLE_SP = 30f
        const val META_SP = 14f
    }
}
