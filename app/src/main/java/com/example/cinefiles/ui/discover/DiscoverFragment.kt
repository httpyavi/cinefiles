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
import com.example.cinefiles.ui.components.ShimmerDrawable
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.PaginationScrollListener
import com.example.cinefiles.util.addChoiceChip
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage
import com.google.android.material.chip.Chip

/**
 * Discover screen: Redesigned with ONE single horizontal scrollable filter row,
 * clearly separated movie categories (Popular, Trending, Top Rated) and genre browsing sections
 * with horizontally browsable movie posters.
 */
class DiscoverFragment : Fragment() {

    private var _binding: FragmentDiscoverBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DiscoverViewModel by activityViewModels()

    // Adapters for the category and genre horizontal rows
    private lateinit var popularAdapter: PosterAdapter
    private lateinit var trendingAdapter: PosterAdapter
    private lateinit var topRatedAdapter: PosterAdapter
    private lateinit var actionAdapter: PosterAdapter
    private lateinit var adventureAdapter: PosterAdapter
    private lateinit var animationAdapter: PosterAdapter
    private lateinit var comedyAdapter: PosterAdapter
    private lateinit var dramaAdapter: PosterAdapter
    private lateinit var horrorAdapter: PosterAdapter
    private lateinit var romanceAdapter: PosterAdapter
    private lateinit var scifiAdapter: PosterAdapter
    private lateinit var thrillerAdapter: PosterAdapter

    // Adapter for search results grid
    private lateinit var searchAdapter: PosterAdapter

    private var allChipId: Int = 0
    private val chipTags = HashMap<Int, String>()
    private val chipGenreIds = HashMap<Int, Int>()

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
        binding.sectionsScroll.applySystemBarsPadding(top = false, bottom = true)
        binding.discoverGrid.applySystemBarsPadding(top = false, bottom = true)

        binding.discoverGlow.strength = GLOW_STRENGTH
        binding.discoverGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        setUpAdapters()
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
        chipTags.clear()
        chipGenreIds.clear()
        _binding = null
    }

    // ---------- Adapters & Setup ----------

    private fun setUpAdapters() {
        val openDetails = { movieId: Int -> findNavController().openMovieDetails(movieId) }

        popularAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        trendingAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        topRatedAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })

        actionAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        adventureAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        animationAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        comedyAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        dramaAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        horrorAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        romanceAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        scifiAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })
        thrillerAdapter = PosterAdapter(PosterStyle.ROW, showBadges = true, onMovieClick = { openDetails(it.id) })

        binding.popularList.adapter = popularAdapter
        binding.trendingList.adapter = trendingAdapter
        binding.topRatedList.adapter = topRatedAdapter
        binding.actionList.adapter = actionAdapter
        binding.adventureList.adapter = adventureAdapter
        binding.animationList.adapter = animationAdapter
        binding.comedyList.adapter = comedyAdapter
        binding.dramaList.adapter = dramaAdapter
        binding.horrorList.adapter = horrorAdapter
        binding.romanceList.adapter = romanceAdapter
        binding.scifiList.adapter = scifiAdapter
        binding.thrillerList.adapter = thrillerAdapter

        // Search Grid Adapter
        searchAdapter = PosterAdapter(PosterStyle.GRID, showBadges = true, onMovieClick = { openDetails(it.id) })
        binding.discoverGrid.adapter = searchAdapter
        binding.discoverGrid.itemAnimator = null

        binding.discoverGrid.addOnScrollListener(PaginationScrollListener { viewModel.loadNextPage() })
        binding.discoverGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })
    }

    /**
     * Builds all filter chips in ONE single horizontal row.
     * Order: All, Popular, Trending, Top Rated, then Genres.
     */
    private fun setUpFilterChips() {
        val group = binding.filterChips

        val allChip = group.addChoiceChip(getString(R.string.discover_all))
        allChipId = allChip.id
        chipTags[allChip.id] = "all"

        val popularChip = group.addChoiceChip(getString(R.string.discover_popular))
        chipTags[popularChip.id] = "popular"

        val trendingChip = group.addChoiceChip(getString(R.string.discover_trending))
        chipTags[trendingChip.id] = "trending"

        val topRatedChip = group.addChoiceChip(getString(R.string.discover_top_rated))
        chipTags[topRatedChip.id] = "top_rated"

        val majorGenres = listOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            18 to "Drama",
            27 to "Horror",
            10749 to "Romance",
            878 to "Sci-Fi",
            53 to "Thriller"
        )
        for ((genreId, name) in majorGenres) {
            val chip = group.addChoiceChip(name)
            val tag = name.lowercase().replace("-", "")
            chipTags[chip.id] = tag
            chipGenreIds[chip.id] = genreId
        }

        // Remaining genres
        val majorIds = majorGenres.map { it.first }.toSet()
        for ((genreId, name) in Genres.all) {
            if (genreId !in majorIds) {
                val chip = group.addChoiceChip(name)
                chipTags[chip.id] = "genre_$genreId"
                chipGenreIds[chip.id] = genreId
            }
        }

        group.check(allChipId)

        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: allChipId
            onFilterChipSelected(checkedId)
        }
    }

    private fun onFilterChipSelected(chipId: Int) {
        val tag = chipTags[chipId] ?: "all"
        val isSearching = viewModel.filters.value?.isSearching == true

        if (isSearching) {
            val genreId = chipGenreIds[chipId]
            viewModel.selectGenre(genreId)
            return
        }

        // Browsing mode: smoothly scroll the sections container to the selected section
        when (tag) {
            "all" -> binding.sectionsScroll.smoothScrollTo(0, 0)
            "popular" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionPopular.top)
            "trending" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionTrending.top)
            "top_rated" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionTopRated.top)
            "action" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionAction.top)
            "adventure" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionAdventure.top)
            "animation" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionAnimation.top)
            "comedy" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionComedy.top)
            "drama" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionDrama.top)
            "horror" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionHorror.top)
            "romance" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionRomance.top)
            "scifi" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionScifi.top)
            "thriller" -> binding.sectionsScroll.smoothScrollTo(0, binding.sectionThriller.top)
            else -> Unit
        }

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
                binding.filterChips.check(allChipId)
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
        viewModel.sections.observe(viewLifecycleOwner) { sections ->
            popularAdapter.submit(null, sections.popular)
            trendingAdapter.submit(null, sections.trending)
            topRatedAdapter.submit(null, sections.topRated)

            actionAdapter.submit(null, sections.action)
            adventureAdapter.submit(null, sections.adventure)
            animationAdapter.submit(null, sections.animation)
            comedyAdapter.submit(null, sections.comedy)
            dramaAdapter.submit(null, sections.drama)
            horrorAdapter.submit(null, sections.horror)
            romanceAdapter.submit(null, sections.romance)
            scifiAdapter.submit(null, sections.scifi)
            thrillerAdapter.submit(null, sections.thriller)

            val isSearching = viewModel.filters.value?.isSearching == true
            if (!isSearching && sections.popular.isNotEmpty()) {
                binding.stateLoading.root.isVisible = false
                binding.sectionsScroll.isVisible = true
            }
        }

        viewModel.filters.observe(viewLifecycleOwner) { filters ->
            if (binding.searchInput.text.toString() != filters.query) {
                binding.searchInput.setText(filters.query)
                binding.searchInput.setSelection(binding.searchInput.length())
            }
            binding.searchClear.isVisible = filters.query.isNotEmpty()
            binding.searchNote.isVisible = filters.isSearching

            val isSearching = filters.isSearching
            binding.sectionsScroll.isVisible = !isSearching
            binding.discoverGrid.isVisible = isSearching
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
        val isSearching = viewModel.filters.value?.isSearching == true

        if (isSearching) {
            val content = state as? DiscoverState.Content
            binding.stateLoading.root.isVisible = state is DiscoverState.Loading
            binding.stateError.root.isVisible = state is DiscoverState.Error
            binding.stateEmpty.root.isVisible = state is DiscoverState.Empty
            binding.discoverGrid.isVisible = content != null
            binding.sectionsScroll.isVisible = false
            binding.loadMoreBar.isVisible = content?.isLoadingMore == true

            when (state) {
                is DiscoverState.Error -> binding.stateError.show(state.error)
                is DiscoverState.Empty -> binding.stateEmpty.emptyAction.isVisible = state.hasFilters
                is DiscoverState.Content -> searchAdapter.submit(null, state.movies)
                else -> searchAdapter.submit(null, emptyList())
            }
        } else {
            // In browsing mode, show skeleton loading until popular section has items
            val isLoadingSections = popularAdapter.itemCount == 0 && state !is DiscoverState.Error
            binding.stateLoading.root.isVisible = isLoadingSections
            binding.stateError.root.isVisible = state is DiscoverState.Error && (popularAdapter.itemCount == 0)
            binding.stateEmpty.root.isVisible = false
            binding.sectionsScroll.isVisible = !isLoadingSections
            binding.discoverGrid.isVisible = false
            binding.loadMoreBar.isVisible = false
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
