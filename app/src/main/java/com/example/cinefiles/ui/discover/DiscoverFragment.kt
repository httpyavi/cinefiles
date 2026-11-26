package com.example.cinefiles.ui.discover

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
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
import com.example.cinefiles.ui.components.ShimmerDrawable
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.PaginationScrollListener
import com.example.cinefiles.util.addChoiceChip
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage

/**
 * Discover screen: ONE single horizontally scrollable filter row with subtle separator
 * (All · Popular · Trending · Top Rated | All Genres · Action · Adventure · Animation · ...),
 * with TWO independent selections (Discovery Type and Genre) that actually filter results,
 * and displays movies in ALL languages by default.
 */
class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiscoverViewModel by activityViewModels()

    // Single 3-column grid adapter for all Discover results
    private lateinit var discoverAdapter: PosterAdapter

    private var allCategoryChipId: Int = 0
    private var allGenreChipId: Int = 0

    private val categoryChipIds = HashMap<BrowseCategory, Int>()
    private val genreChipIds = HashMap<Int, Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiscoverBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.discoverHeader.applySystemBarsPadding(top = true, bottom = false)
        binding.discoverGrid.applySystemBarsPadding(top = false, bottom = true)

        binding.discoverGlow.strength = GLOW_STRENGTH
        binding.discoverGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        setUpAdapter()
        setUpFilterChips()
        setUpSearch()
        setUpStateButtons()
        setUpLoadingSkeletons()

        viewModel.start()
        observeViewModel()
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        categoryChipIds.clear()
        genreChipIds.clear()
        _binding = null
    }

    // ---------- Adapter & Setup ----------

    private fun setUpAdapter() {
        val openDetails = { movieId: Int -> findNavController().openMovieDetails(movieId) }
        discoverAdapter = PosterAdapter(PosterStyle.GRID, showBadges = true, onMovieClick = { openDetails(it.id) })

        binding.discoverGrid.adapter = discoverAdapter
        binding.discoverGrid.itemAnimator = null

        binding.discoverGrid.addOnScrollListener(PaginationScrollListener { viewModel.loadNextPage() })
        binding.discoverGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
    }

    /**
     * Builds all filters in ONE single horizontally scrollable row:
     * Curated Discovery Type (All · Popular · Trending · Top Rated) | Genres (All Genres · Action · Adventure · ...).
     * The two groups operate completely independently: selecting one does NOT reset the other.
     */
    private fun setUpFilterChips() {
        val curatedGroup = binding.filterChipsCurated
        val genreGroup = binding.filterChipsGenres

        // 1. Discovery Type filters: All · Popular · Trending · Top Rated
        val allChip = curatedGroup.addChoiceChip(getString(R.string.discover_all))
        allCategoryChipId = allChip.id
        categoryChipIds[BrowseCategory.ALL] = allChip.id
        allChip.setOnClickListener { selectCategory(BrowseCategory.ALL) }

        val popularChip = curatedGroup.addChoiceChip(getString(R.string.discover_popular))
        categoryChipIds[BrowseCategory.POPULAR] = popularChip.id
        popularChip.setOnClickListener {
            val current = viewModel.filters.value?.category ?: BrowseCategory.ALL
            selectCategory(if (current == BrowseCategory.POPULAR) BrowseCategory.ALL else BrowseCategory.POPULAR)
        }

        val trendingChip = curatedGroup.addChoiceChip(getString(R.string.discover_trending))
        categoryChipIds[BrowseCategory.TRENDING] = trendingChip.id
        trendingChip.setOnClickListener {
            val current = viewModel.filters.value?.category ?: BrowseCategory.ALL
            selectCategory(if (current == BrowseCategory.TRENDING) BrowseCategory.ALL else BrowseCategory.TRENDING)
        }

        val topRatedChip = curatedGroup.addChoiceChip(getString(R.string.discover_top_rated))
        categoryChipIds[BrowseCategory.TOP_RATED] = topRatedChip.id
        topRatedChip.setOnClickListener {
            val current = viewModel.filters.value?.category ?: BrowseCategory.ALL
            selectCategory(if (current == BrowseCategory.TOP_RATED) BrowseCategory.ALL else BrowseCategory.TOP_RATED)
        }

        // 2. Genre filters: All Genres (default) · Action · Adventure · Animation · ...
        val allGenresChip = genreGroup.addChoiceChip(getString(R.string.discover_all_genres))
        allGenreChipId = allGenresChip.id
        allGenresChip.setOnClickListener { selectGenre(null) }

        for ((genreId, name) in Genres.all) {
            val chip = genreGroup.addChoiceChip(name)
            genreChipIds[genreId] = chip.id
            chip.setOnClickListener {
                val current = viewModel.filters.value?.genreId
                selectGenre(if (current == genreId) null else genreId)
            }
        }

        // Default initial selections
        curatedGroup.check(allCategoryChipId)
        genreGroup.check(allGenreChipId)
    }

    private fun selectCategory(category: BrowseCategory) {
        val chipId = categoryChipIds[category] ?: allCategoryChipId
        binding.filterChipsCurated.check(chipId)
        viewModel.selectCategory(category)
        scrollToChip(chipId)
    }

    private fun selectGenre(genreId: Int?) {
        val chipId = if (genreId != null) genreChipIds[genreId] ?: allGenreChipId else allGenreChipId
        binding.filterChipsGenres.check(chipId)
        viewModel.selectGenre(genreId)
        scrollToChip(chipId)
    }

    private fun scrollToChip(chipId: Int) {
        val chip = binding.filterScroll.findViewById<View>(chipId) ?: return
        binding.filterScroll.smoothScrollTo((chip.left - binding.filterScroll.paddingStart).coerceAtLeast(0), 0)
    }

    private fun setUpSearch() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString().orEmpty()
                binding.searchClear.isVisible = text.isNotEmpty()
                viewModel.setQuery(text)
            }
        })

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchNow()
                hideKeyboard()
                true
            } else {
                false
            }
        }

        binding.searchClear.setOnClickListener {
            binding.searchInput.setText("")
        }
    }

    private fun setUpStateButtons() {
        binding.stateEmpty.show(
            icon = R.drawable.ic_search,
            title = R.string.discover_empty_title,
            message = R.string.discover_empty_message,
            action = EmptyAction(R.string.discover_clear_filters) {
                viewModel.clearFilters()
                selectCategory(BrowseCategory.ALL)
                selectGenre(null)
            }
        )
        binding.stateError.errorRetry.setOnClickListener { viewModel.retry() }
    }

    private fun setUpLoadingSkeletons() {
        val posterRadius = resources.getDimension(R.dimen.radius_medium)
        val textRadius = resources.getDimension(R.dimen.radius_small)

        val loading = binding.stateLoading
        loading.loadingPosterOne.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingPosterTwo.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingPosterThree.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingPosterFour.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingPosterFive.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingPosterSix.background = ShimmerDrawable(posterRadius, startImmediately = true)
        loading.loadingHeading.background = ShimmerDrawable(textRadius, startImmediately = true)
        loading.loadingLineOne.background = ShimmerDrawable(textRadius, startImmediately = true)
        loading.loadingLineTwo.background = ShimmerDrawable(textRadius, startImmediately = true)
    }

    private fun observeViewModel() {
        viewModel.filters.observe(viewLifecycleOwner) { filters ->
            if (binding.searchInput.text.toString() != filters.query) {
                binding.searchInput.setText(filters.query)
                binding.searchInput.setSelection(binding.searchInput.length())
            }
            binding.searchClear.isVisible = filters.query.isNotEmpty()
            binding.searchNote.isVisible = filters.isSearching

            // Keep chips synchronized with active filters
            val targetCategoryChipId = categoryChipIds[filters.category] ?: allCategoryChipId
            if (binding.filterChipsCurated.checkedChipId != targetCategoryChipId) {
                binding.filterChipsCurated.check(targetCategoryChipId)
            }

            val targetGenreChipId = if (filters.genreId != null) genreChipIds[filters.genreId] ?: allGenreChipId else allGenreChipId
            if (binding.filterChipsGenres.checkedChipId != targetGenreChipId) {
                binding.filterChipsGenres.check(targetGenreChipId)
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            renderState(state)
        }

        viewModel.focusSearch.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { focusSearchField() }
        }

        viewModel.loadMoreFailed.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                showMessage(R.string.discover_load_more_failed, R.string.state_retry, aboveTabBar = true) {
                    viewModel.loadNextPage()
                }
            }
        }
    }

    private fun renderState(state: DiscoverState) {
        val content = state as? DiscoverState.Content
        binding.stateLoading.root.isVisible = state is DiscoverState.Loading
        binding.stateError.root.isVisible = state is DiscoverState.Error
        binding.stateEmpty.root.isVisible = state is DiscoverState.Empty
        binding.discoverGrid.isVisible = content != null
        binding.loadMoreBar.isVisible = content?.isLoadingMore == true

        when (state) {
            is DiscoverState.Error -> binding.stateError.show(state.error)
            is DiscoverState.Empty -> binding.stateEmpty.emptyAction.isVisible = state.hasFilters
            is DiscoverState.Content -> discoverAdapter.submit(null, state.movies)
            else -> discoverAdapter.submit(null, emptyList())
        }
    }

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
    }
}
