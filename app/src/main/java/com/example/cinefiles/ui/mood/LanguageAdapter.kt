package com.example.cinefiles.ui.mood

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.example.cinefiles.R
import com.example.cinefiles.logic.LanguageOption
import com.example.cinefiles.logic.Languages

/**
 * The suggestions under the language search field of "Make it yours". As the user types, [Languages.search] decides which languages are
 * suggested and in which order (typing "E" gives English first). Blank text suggests every language, so a tap on the empty field shows the
 * whole list. It filters with that rule instead of the adapter's own (which only matches the start of the shown name and knows no aliases,
 * so "Tagalog" would find nothing).
 */
class LanguageAdapter(context: Context) : ArrayAdapter<LanguageOption>(context, R.layout.item_language_suggestion, Languages.all.toMutableList()) {

    private var shown: List<LanguageOption> = Languages.all

    /** Back to the whole list (after a language was picked, a tap on the field offers every language again, not just the last matches). */
    fun showAll() {
        shown = Languages.all
        notifyDataSetChanged()
    }

    override fun getCount(): Int = shown.size

    override fun getItem(position: Int): LanguageOption? = shown.getOrNull(position)

    override fun getFilter(): Filter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults =
            FilterResults().also {
                val found = Languages.search(constraint?.toString().orEmpty())
                it.values = found
                it.count = found.size
            }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults) {
            shown = results.values as? List<LanguageOption> ?: emptyList()
            notifyDataSetChanged()
        }

        // What the field shows after a suggestion is tapped: its name
        override fun convertResultToString(resultValue: Any?): CharSequence = (resultValue as? LanguageOption)?.name.orEmpty()
    }
}
