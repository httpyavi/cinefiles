package com.example.cinefiles.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.data.CrewMember
import com.example.cinefiles.databinding.ItemCrewSheetHeaderBinding
import com.example.cinefiles.databinding.ItemCrewSheetPersonBinding
import com.example.cinefiles.databinding.SheetCrewBinding
import com.example.cinefiles.logic.CrewRole
import com.example.cinefiles.util.openPerson
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * "See all crew": a bottom sheet with every group of crew of the movie, the names tappable (they open the Person screen).
 * It reads the crew from the Details screen's own ViewModel (it is shown from that screen's child fragment manager),
 * so nothing is passed around. Open it with [show].
 */
class CrewSheet : BottomSheetDialogFragment() {

    private var _binding: SheetCrewBinding? = null
    private val binding get() = _binding!!

    private val detailViewModel: DetailViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetCrewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = CrewSheetAdapter { person ->
            val navController = findNavController()
            dismiss()
            navController.openPerson(person.id)
        }
        binding.crewSheetList.adapter = adapter
        detailViewModel.state.observe(viewLifecycleOwner) { state ->
            val content = state as? DetailState.Content
            if (content == null || content.fullCrew.isEmpty()) {
                dismiss() // the crew is gone (the Details screen was left): nothing to show
            } else {
                adapter.submitList(crewRows(content.fullCrew))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "CrewSheet"

        fun show(manager: FragmentManager) = CrewSheet().show(manager, TAG)
    }
}

/** One line of the sheet: the heading of a group, or a person. */
sealed class CrewSheetRow {
    data class Header(val role: CrewRole) : CrewSheetRow()
    data class Person(val role: CrewRole, val member: CrewMember) : CrewSheetRow()
}

/** The groups in the order of [CrewRole], each with its heading, then its people. */
fun crewRows(groups: Map<CrewRole, List<CrewMember>>): List<CrewSheetRow> =
    CrewRole.values().flatMap { role ->
        val people = groups[role].orEmpty()
        if (people.isEmpty()) emptyList() else listOf<CrewSheetRow>(CrewSheetRow.Header(role)) + people.map { CrewSheetRow.Person(role, it) }
    }

private class CrewSheetAdapter(
    private val onPersonClick: (CrewMember) -> Unit
) : ListAdapter<CrewSheetRow, RecyclerView.ViewHolder>(Diff) {

    private class HeaderHolder(val binding: ItemCrewSheetHeaderBinding) : RecyclerView.ViewHolder(binding.root)
    private class PersonHolder(val binding: ItemCrewSheetPersonBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int = if (getItem(position) is CrewSheetRow.Header) HEADER else PERSON

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == HEADER) {
            HeaderHolder(ItemCrewSheetHeaderBinding.inflate(inflater, parent, false))
        } else {
            PersonHolder(ItemCrewSheetPersonBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is CrewSheetRow.Header -> (holder as HeaderHolder).binding.root.setText(row.role.label)
            is CrewSheetRow.Person -> with((holder as PersonHolder).binding) {
                crewPersonName.text = row.member.name
                crewPersonJob.text = row.member.job
                root.setOnClickListener { onPersonClick(row.member) }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CrewSheetRow>() {
        override fun areItemsTheSame(old: CrewSheetRow, new: CrewSheetRow): Boolean = when {
            old is CrewSheetRow.Header && new is CrewSheetRow.Header -> old.role == new.role
            old is CrewSheetRow.Person && new is CrewSheetRow.Person -> old.role == new.role && old.member.id == new.member.id
            else -> false
        }

        override fun areContentsTheSame(old: CrewSheetRow, new: CrewSheetRow) = old == new
    }

    private companion object {
        const val HEADER = 0
        const val PERSON = 1
    }
}
