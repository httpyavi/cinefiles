package com.example.cinefiles.ui.discover

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentDiscoverBinding
import com.example.cinefiles.logic.BrowseCategory
import com.example.cinefiles.logic.DiscoverFilters
import com.example.cinefiles.logic.Genres
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.PaginationScrollListener
import com.example.cinefiles.util.addChoiceChip
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage

/**
 * The Discover tab: search, the Popular / Trending / Top rated lists and the genre chips, as a three-column poster grid.
 * This class only draws what DiscoverViewModel says and forwards taps. The rules (when a search starts, what a genre
 * chip does) are in logic/DiscoverRules.kt and ui/discover/DiscoverViewModel.kt.
 */
class DiscoverFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    // Shared by the whole app window: Home's search icon asks it for the keyboard, and your search survives tab changes
    private val viewModel: DiscoverViewModel by activityViewModels()

    private lateinit var adapter: PosterAdapter

    private val categoryOfChip = HashMap<Int, BrowseCategory>() // chip view id -> category
    private val genreOfChip = HashMap<Int, Int>()               // chip view id -> TMDB genre id
    private var shownFilters: DiscoverFilters? = null           // the filters the grid is showing movies for

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The title starts below the status bar; the grid needs room above the floating tab bar
        binding.discoverHeader.applySystemBarsPadding(top = true, bottom = false)
        binding.discoverGrid.applySystemBarsPadding(top = false, bottom = true)

        binding.discoverGlow.strength = GLOW_STRENGTH
        binding.discoverGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        setUpGrid()
        setUpSearch()
        setUpChips()
        setUpStateButtons()

        viewModel.start() // the first call loads the first list; later calls do nothing
        viewModel.filters.observe(viewLifecycleOwner) { showFilters(it) }
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.focusSearch.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { focusSearchField() }
        }
        viewModel.loadMoreFailed.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                showMessage(R.string.discover_load_more_failed, R.string.state_retry, aboveTabBar = true) { viewModel.loadNextPage() }
            }
        }
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        shownFilters = null
        categoryOfChip.clear()
        genreOfChip.clear()
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpGrid() {
        adapter = PosterAdapter(
            style = PosterStyle.GRID,
            showBadges = true,
            onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) }
        )
        binding.discoverGrid.adapter = adapter
        binding.discoverGrid.itemAnimator = null // posters change badges or arrive in pages: no fancy animation needed

        // Near the end of the list the next page is asked for (the ViewModel ignores it when there is none)
        binding.discoverGrid.addOnScrollListener(PaginationScrollListener { viewModel.loadNextPage() })
        // Scrolling the posters puts the keyboard away
        binding.discoverGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
    }

    private fun setUpSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.searchClear.isVisible = text.isNotEmpty()
                viewModel.setQuery(text) // the ViewModel waits 400 ms for a pause in typing
            }
        })
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchNow() // the search key: no need to wait for the pause
                hideKeyboard()
                true
            } else {
                false
            }
        }
        binding.searchClear.setOnClickListener { binding.searchInput.setText("") }
    }

    /** The category chips and the genre chips are built once from the lists in logic/. */
    private fun setUpChips() {
        for (category in BrowseCategory.values()) {
            val chip = binding.categoryChips.addChoiceChip(getString(category.label))
            categoryOfChip[chip.id] = category
        }
        for ((genreId, name) in Genres.all) {
            val chip = binding.genreChips.addChoiceChip(name)
            genreOfChip[chip.id] = genreId
        }

        // Set after the chips exist and the first check below, so building them does not count as a pick
        binding.categoryChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val category = checkedIds.firstOrNull()?.let { categoryOfChip[it] } ?: return@setOnCheckedStateChangeListener
            viewModel.selectCategory(category)
        }
        binding.genreChips.setOnCheckedStateChangeListener { _, checkedIds ->
            viewModel.selectGenre(checkedIds.firstOrNull()?.let { genreOfChip[it] }) // none checked: no genre
        }
    }

    private fun setUpStateButtons() {
        binding.stateEmpty.show(
            icon = R.drawable.ic_search,
            title = R.string.discover_empty_title,
            message = R.string.discover_empty_message,
            action = EmptyAction(R.string.discover_clear_filters) { viewModel.clearFilters() }
        )
        binding.stateError.errorRetry.setOnClickListener { viewModel.retry() }
    }

    // ---------- Drawing what the ViewModel says ----------

    /** The search field and the chips show the chosen filters (they change from outside too: "Clear filters"). */
    private fun showFilters(filters: DiscoverFilters) {
        if (binding.searchInput.text.toString() != filters.query) {
            binding.searchInput.setText(filters.query)
            binding.searchInput.setSelection(binding.searchInput.length())
        }
        binding.searchClear.isVisible = filters.query.isNotEmpty()

        val categoryChip = categoryOfChip.entries.first { it.value == filters.category }.key
        if (binding.categoryChips.checkedChipId != categoryChip) binding.categoryChips.check(categoryChip)

        val genreChip = genreOfChip.entries.firstOrNull { it.value == filters.genreId }?.key
        if (genreChip == null) {
            binding.genreChips.clearCheck()
        } else if (binding.genreChips.checkedChipId != genreChip) {
            binding.genreChips.check(genreChip)
            binding.genreScroll.post { _binding?.let { scrollToChip(it.genreScroll, genreChip) } }
        }

        // While a search is on, the lists do not apply and the genre chips only filter what has loaded
        binding.categoryScroll.alpha = if (filters.isSearching) INACTIVE_ALPHA else 1f
        binding.searchNote.isVisible = filters.isSearching
    }

    private fun scrollToChip(scroll: HorizontalScrollView, chipId: Int) {
        val chip = scroll.findViewById<View>(chipId) ?: return
        scroll.smoothScrollTo((chip.left - scroll.paddingStart).coerceAtLeast(0), 0)
    }

    private fun render(state: DiscoverState) {
        val content = state as? DiscoverState.Content

        binding.stateLoading.root.isVisible = state is DiscoverState.Loading
        binding.stateError.root.isVisible = state is DiscoverState.Error
        binding.stateEmpty.root.isVisible = state is DiscoverState.Empty
        binding.discoverGrid.isVisible = content != null
        binding.loadMoreBar.isVisible = content?.isLoadingMore == true

        when (state) {
            is DiscoverState.Error -> binding.stateError.show(state.error)
            // "Clear filters" only makes sense when something is chosen
            is DiscoverState.Empty -> binding.stateEmpty.emptyAction.isVisible = state.hasFilters
            else -> Unit
        }

        if (content == null) {
            adapter.submit(null, emptyList()) // the next list starts at the top
            shownFilters = null
            return
        }

        adapter.submit(null, content.movies)
        val filters = viewModel.filters.value
        if (filters != null && filters != shownFilters) {
            // A new search, list or genre: it starts at the top
            shownFilters = filters
            binding.discoverGrid.post { _binding?.discoverGrid?.scrollToPosition(0) }
        }
    }

    // ---------- The keyboard ----------

    /** Home's search icon: the field gets the cursor and the keyboard comes up. */
    private fun focusSearchField() {
        val input = binding.searchInput
        input.requestFocus()
        input.post {
            val field = _binding?.searchInput ?: return@post
            context?.getSystemService<InputMethodManager>()?.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard() {
        val input = _binding?.searchInput ?: return
        input.clearFocus()
        context?.getSystemService<InputMethodManager>()?.hideSoftInputFromWindow(input.windowToken, 0)
    }

    private companion object {
        const val GLOW_STRENGTH = 0.4f
        const val INACTIVE_ALPHA = 0.45f
    }
}
