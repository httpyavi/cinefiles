package com.example.cinefiles.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ItemDiaryStyleOptionBinding
import com.example.cinefiles.databinding.SheetDiaryStyleBinding
import com.example.cinefiles.logic.DiaryFont
import com.example.cinefiles.logic.DiaryTheme
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The style of a diary page: four fonts and five background themes as small cards. A tap changes the page behind the sheet at
 * once (it talks to the editor's own ViewModel, because it is shown from the editor's child fragment manager). The chosen font
 * and theme have a red edge. Open it with [show].
 */
class DiaryStyleSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDiaryStyleBinding? = null
    private val binding get() = _binding!!

    private val editorViewModel: DiaryEditorViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private val fontCards = HashMap<DiaryFont, ItemDiaryStyleOptionBinding>()
    private val themeCards = HashMap<DiaryTheme, ItemDiaryStyleOptionBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDiaryStyleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()
        for (font in DiaryFont.values()) {
            val card = addCard(binding.fontOptions, font.label)
            card.optionSample.typeface = font.typeface(context)
            card.optionCard.setOnClickListener { editorViewModel.setFont(font) }
            fontCards[font] = card
        }
        for (theme in DiaryTheme.values()) {
            val card = addCard(binding.themeOptions, theme.label)
            // The card shows the theme itself: its page color and its words
            card.optionCard.setCardBackgroundColor(context.themeColor(theme.background))
            card.optionSample.setTextColor(context.themeColor(theme.text))
            card.optionLabel.setTextColor(context.themeColor(theme.secondaryText))
            card.optionCard.setOnClickListener { editorViewModel.setTheme(theme) }
            themeCards[theme] = card
        }

        editorViewModel.font.observe(viewLifecycleOwner) { selected ->
            for ((font, card) in fontCards) mark(card, font == selected)
        }
        editorViewModel.theme.observe(viewLifecycleOwner) { selected ->
            for ((theme, card) in themeCards) mark(card, theme == selected)
        }
    }

    private fun addCard(row: LinearLayout, label: Int): ItemDiaryStyleOptionBinding {
        val card = ItemDiaryStyleOptionBinding.inflate(layoutInflater, row, false)
        card.optionLabel.setText(label)
        card.optionCard.contentDescription = getString(label)
        row.addView(card.root)
        return card
    }

    /** The chosen card gets a red edge. */
    private fun mark(card: ItemDiaryStyleOptionBinding, selected: Boolean) {
        val context = requireContext()
        card.optionCard.strokeColor = context.themeColor(if (selected) R.color.accent else R.color.glass_stroke)
        card.optionCard.strokeWidth =
            resources.getDimensionPixelSize(if (selected) R.dimen.diary_option_selected_stroke else R.dimen.glass_stroke_width)
        card.optionCard.isSelected = selected
    }

    override fun onDestroyView() {
        super.onDestroyView()
        fontCards.clear()
        themeCards.clear()
        _binding = null
    }

    companion object {
        private const val TAG = "DiaryStyleSheet"

        fun show(manager: FragmentManager) = DiaryStyleSheet().show(manager, TAG)
    }
}
