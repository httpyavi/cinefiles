package com.example.cinefiles.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.FragmentHomeBinding
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openScreen
import com.example.cinefiles.util.openTab
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat

/**
 * The Home tab. This class only draws what HomeViewModel says and forwards taps.
 * The decisions (which greeting, what to load, which state) live in HomeViewModel.
 */
class HomeFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    // Shared with the Mood, Preferences and Swipe screens. Home only starts a fresh flow with it.
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private lateinit var trendingAdapter: PosterRowAdapter
    private lateinit var popularAdapter: PosterRowAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The header sits under the status bar; the scrolling area needs room above the tab bar
        binding.homeHeader.applySystemBarsPadding(top = true, bottom = false)
        binding.homeScroll.applySystemBarsPadding(top = false, bottom = true)

        setUpHeader()
        setUpHero()
        setUpPosterRows()
        setUpCollectionTiles()
        setUpStateButtons()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        // The app may have been open since the morning: check the time again
        viewModel.refreshGreeting()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ---------- Setup: wire up taps ----------

    private fun setUpHeader() {
        binding.userNameText.text = viewModel.userName
        binding.avatarButton.text = viewModel.avatarInitial

        // The search icon and the avatar jump to the matching tab
        binding.searchButton.setOnClickListener { findNavController().openTab(R.id.discoverFragment) }
        binding.avatarButton.setOnClickListener { findNavController().openTab(R.id.profileFragment) }

        // TEMP: long-press the name to step through the test modes (normal, loading, error, empty)
        binding.userNameText.setOnLongClickListener {
            val mode = viewModel.nextTestMode()
            Snackbar.make(binding.root, mode.label, Snackbar.LENGTH_SHORT).show()
            true
        }
    }

    private fun setUpHero() {
        // The gold button starts the journey: Home -> Mood. A new journey starts with nothing chosen.
        binding.heroButton.setOnClickListener {
            watchFlow.reset()
            findNavController().openScreen(R.id.moodFragment)
        }
    }

    private fun setUpPosterRows() {
        val openDetails = { movie: Movie -> findNavController().openMovieDetails(movie.id) }

        trendingAdapter = PosterRowAdapter(openDetails)
        popularAdapter = PosterRowAdapter(openDetails)

        for ((list, adapter) in listOf(
            binding.trendingList to trendingAdapter,
            binding.popularList to popularAdapter
        )) {
            list.adapter = adapter
            list.setHasFixedSize(true) // the row always has the same height
        }
    }

    private fun setUpCollectionTiles() {
        // Every tile opens the Collection tab
        val openCollection = View.OnClickListener { findNavController().openTab(R.id.collectionFragment) }
        binding.tileLiked.setOnClickListener(openCollection)
        binding.tileWatchlist.setOnClickListener(openCollection)
        binding.tileWatched.setOnClickListener(openCollection)
    }

    private fun setUpStateButtons() {
        with(binding.stateEmpty) {
            emptyIcon.setImageResource(R.drawable.ic_discover)
            emptyTitle.setText(R.string.home_empty_title)
            emptyMessage.setText(R.string.home_empty_message)
            emptyAction.setText(R.string.home_empty_action)
            emptyAction.isVisible = true
            emptyAction.setOnClickListener { viewModel.load() }
        }
        binding.stateError.errorRetry.setOnClickListener { viewModel.load() }
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.greeting.observe(viewLifecycleOwner) { binding.greetingText.setText(it) }
        viewModel.counts.observe(viewLifecycleOwner) { counts ->
            // Write the numbers the way the phone's language does
            val numbers = NumberFormat.getIntegerInstance()
            binding.likedCount.text = numbers.format(counts.liked)
            binding.watchlistCount.text = numbers.format(counts.watchlist)
            binding.watchedCount.text = numbers.format(counts.watched)
        }
    }

    private fun render(state: HomeState) {
        val isLoading = state is HomeState.Loading
        val content = state as? HomeState.Content

        // Loading and Content share the scrolling area (loading shows placeholders in it)
        binding.homeScroll.isVisible = isLoading || content != null
        binding.stateEmpty.root.isVisible = state is HomeState.Empty
        binding.stateError.root.isVisible = state is HomeState.Error

        // Hero: while loading you only see the empty tonal card
        val featured = content?.featured
        binding.heroCard.isVisible = isLoading || featured != null
        binding.heroFilled.isVisible = featured != null
        if (featured != null) {
            binding.heroTitle.text = featured.title
            binding.heroImage.loadImage(ImageUrls.backdrop(featured.backdropPath))
        }

        showRow(binding.trendingSection, binding.trendingList, binding.trendingLoading.root,
            trendingAdapter, isLoading, content?.trending)
        showRow(binding.popularSection, binding.popularList, binding.popularLoading.root,
            popularAdapter, isLoading, content?.popular)
    }

    /** Shows a poster row, its loading placeholders, or nothing (when there are no movies for it). */
    private fun showRow(
        section: View,
        list: RecyclerView,
        loadingPlaceholders: View,
        adapter: PosterRowAdapter,
        isLoading: Boolean,
        movies: List<Movie>?
    ) {
        section.isVisible = isLoading || !movies.isNullOrEmpty()
        loadingPlaceholders.isVisible = isLoading
        list.isVisible = !isLoading
        if (movies != null) adapter.submitList(movies)
    }
}
