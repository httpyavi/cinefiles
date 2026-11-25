package com.example.cinefiles.ui.person

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentPersonBinding
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.ui.components.SortSheet
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show

/**
 * The Person screen for one actor, director or crew member: their photo, name, biography and their movies in the same
 * three-column poster grid as Discover. It only draws what PersonViewModel says and forwards taps.
 * The person's id arrives as the navigation argument "personId" (see openPerson in util/NavigationExtensions.kt).
 * Tapping a poster opens that movie's Details.
 */
class PersonFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentPersonBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PersonViewModel by viewModels()

    private lateinit var headerAdapter: PersonHeaderAdapter
    private lateinit var posterAdapter: PosterAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPersonBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The page starts below the status bar and ends above the gesture bar; the back button floats under the status bar
        binding.personList.applySystemBarsPadding()
        binding.personTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.personStates.applySystemBarsPadding()

        binding.personGlow.strength = GLOW_STRENGTH
        binding.personGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        binding.personBack.setOnClickListener { findNavController().popBackStack() }
        binding.personSortBar.applySystemBarsPadding(top = true, bottom = false)
        SortSheet.listen(childFragmentManager, viewLifecycleOwner) { viewModel.selectSort(it) }
        binding.stateError.errorRetry.setOnClickListener { viewModel.load() }

        setUpList()
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- Setup ----------

    /** One list: the header (full width) and then the posters, three to a row. */
    private fun setUpList() {
        headerAdapter = PersonHeaderAdapter(onTabSelected = viewModel::selectTab)
        posterAdapter = PosterAdapter(PosterStyle.GRID, showRatings = true, onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) })

        val layoutManager = GridLayoutManager(requireContext(), COLUMNS)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            // The header (the only item of the first adapter) takes the whole row
            override fun getSpanSize(position: Int): Int = if (position < headerAdapter.itemCount) COLUMNS else 1
        }
        binding.personList.layoutManager = layoutManager
        binding.personList.adapter = ConcatAdapter(headerAdapter, posterAdapter)
        binding.personList.itemAnimator = null // a tab change swaps the posters at once: no fancy animation needed
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: PersonState) {
        val content = state as? PersonState.Content

        binding.personList.isVisible = content != null
        binding.personStates.isVisible = content == null
        binding.stateLoading.root.isVisible = state is PersonState.Loading
        binding.stateError.root.isVisible = state is PersonState.Error
        if (state is PersonState.Error) binding.stateError.show(state.error)
        if (content == null) return

        headerAdapter.submit(content)
        posterAdapter.submitList(content.movies)
        showSortButton(content)
    }

    /** The Sort button floats at the top right, only when there is something to sort. It is red when the order is not the usual one. */
    private fun showSortButton(content: PersonState.Content) {
        binding.personSort.isVisible = content.movies.size > 1
        binding.personSort.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(requireContext(), if (content.sort != DEFAULT_SORT) R.color.accent else R.color.text_primary)
        )
        binding.personSort.setOnClickListener { SortSheet.show(childFragmentManager, content.sortFields, content.sort) }
    }

    private companion object {
        const val COLUMNS = 3
        const val GLOW_STRENGTH = 0.9f
    }
}
