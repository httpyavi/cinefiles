package com.example.cinefiles.ui.components

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.LifecycleOwner
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ItemSortRowBinding
import com.example.cinefiles.databinding.SheetSortBinding
import com.example.cinefiles.logic.SortDirection
import com.example.cinefiles.logic.SortField
import com.example.cinefiles.logic.SortOption
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * THE Sort sheet, shared by My Collection and the Person screen: one block per field the list can be sorted on, each with its two directions
 * ("A to Z" / "Z to A", "Newest first" / "Earliest first"...). The sort that is active has a red edge. A tap picks it, sends it back to the
 * screen that opened the sheet and closes the sheet; "Reset" goes back to the screen's usual order. The sorting itself is logic/MovieSorter.kt.
 *
 * A screen opens it with [show] from its child fragment manager and listens for the answer with [listen] on the same manager.
 */
class SortSheet : BottomSheetDialogFragment() {

    private var _binding: SheetSortBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetSortBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val arguments = requireArguments()
        val fields = (arguments.getIntArray(ARG_FIELDS) ?: intArrayOf()).map { SortField.values()[it] }
        val current = SortOption(SortField.values()[arguments.getInt(ARG_FIELD)], SortDirection.values()[arguments.getInt(ARG_DIRECTION)])

        for (field in fields) {
            val row = ItemSortRowBinding.inflate(layoutInflater, binding.sortRows, false)
            row.sortFieldLabel.setText(field.label)
            val isActiveField = field == current.field
            row.sortFieldLabel.setTextColor(requireContext().getColor(if (isActiveField) R.color.accent_soft else R.color.text_primary))

            val first = field.firstDirection
            bindDirection(row.sortFirst, field, first, current)
            bindDirection(row.sortSecond, field, field.opposite(first), current)
            binding.sortRows.addView(row.root)
        }

        // "Reset": the first field of the list in the direction of its first wording, which is the screen's usual order
        binding.sortReset.setOnClickListener { fields.firstOrNull()?.let { pick(SortOption.firstOf(it)) } }
    }

    private fun bindDirection(button: com.google.android.material.button.MaterialButton, field: SortField, direction: SortDirection, current: SortOption) {
        val label = getString(field.labelOf(direction))
        button.text = label
        button.isCheckable = true
        button.isChecked = field == current.field && direction == current.direction
        button.contentDescription = getString(R.string.sort_field_description, getString(field.label), label)
        button.setOnClickListener { pick(SortOption(field, direction)) }
    }

    private fun pick(option: SortOption) {
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf(ARG_FIELD to option.field.ordinal, ARG_DIRECTION to option.direction.ordinal))
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "SortSheet"
        private const val RESULT_KEY = "sortSheetResult"
        private const val ARG_FIELDS = "fields"
        private const val ARG_FIELD = "field"
        private const val ARG_DIRECTION = "direction"

        /** Opens the sheet with the [fields] to offer (the first one is the screen's default) and the sort that is active now. */
        fun show(manager: FragmentManager, fields: List<SortField>, current: SortOption) {
            SortSheet().apply {
                arguments = bundleOf(
                    ARG_FIELDS to fields.map { it.ordinal }.toIntArray(),
                    ARG_FIELD to current.field.ordinal,
                    ARG_DIRECTION to current.direction.ordinal
                )
            }.show(manager, TAG)
        }

        /** Calls [onPicked] when the user picks a sort in a sheet opened on this [manager]. */
        fun listen(manager: FragmentManager, owner: LifecycleOwner, onPicked: (SortOption) -> Unit) {
            manager.setFragmentResultListener(RESULT_KEY, owner) { _, result ->
                onPicked(SortOption(SortField.values()[result.getInt(ARG_FIELD)], SortDirection.values()[result.getInt(ARG_DIRECTION)]))
            }
        }
    }
}
