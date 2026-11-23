package com.example.cinefiles.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.FragmentCollectionBinding
import com.example.cinefiles.logic.CollectionTab
import com.example.cinefiles.logic.actionsFor
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openScreen
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showMessage
import com.example.cinefiles.util.showUndo

/**
 * The My Collection tab: your Liked movies, your Watchlist and the movies you Watched, as a poster grid.
 * This class only draws what CollectionViewModel says and forwards taps. The rules (which badge, which menu
 * entries) are in logic/CollectionRules.kt.
 */
class CollectionFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentCollectionBinding? = null
    private val binding get() = _binding!!

    // Shared with Home (its three tiles pick the tab) and kept for the whole app window
    private val viewModel: CollectionViewModel by activityViewModels()

    // Used by the empty Liked tab, whose button starts a new "watch something" journey (like Home's red button)
    private val watchFlow: WatchFlowViewModel by activityViewModels()

    private lateinit var adapter: PosterAdapter

    private var shownTab: CollectionTab? = null // the tab whose posters the grid is showing now

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The title starts below the status bar; the grid needs room above the floating tab bar
        binding.collectionHeader.applySystemBarsPadding(top = true, bottom = false)
        binding.collectionGrid.applySystemBarsPadding(top = false, bottom = true)

        binding.collectionGlow.strength = GLOW_STRENGTH
        binding.collectionGlow.setTint(ContextCompat.getColor(requireContext(), R.color.accent))

        adapter = PosterAdapter(
            style = PosterStyle.GRID,
            showBadges = true,
            onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) },
            onMovieLongClick = { movie, anchor -> showMenu(movie, anchor) }
        )
        binding.collectionGrid.adapter = adapter
        binding.collectionGrid.itemAnimator = null // posters change badges or leave: no fancy animation needed
        binding.collectionGrid.setHasFixedSize(true)

        setUpTabs()
        binding.stateError.show(ApiError.UNKNOWN)
        binding.stateError.errorRetry.setOnClickListener { viewModel.retry() }

        render(CollectionState.Loading) // until the database answers (a few milliseconds)
        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.counts.observe(viewLifecycleOwner) { counts ->
            binding.tabLiked.text = getString(R.string.collection_tab_liked, counts.liked)
            binding.tabWatchlist.text = getString(R.string.collection_tab_watchlist, counts.watchlist)
            binding.tabWatched.text = getString(R.string.collection_tab_watched, counts.watched)
        }
        viewModel.likeBlocked.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { showMessage(R.string.like_blocked_message, aboveTabBar = true) }
        }
        viewModel.undo.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let { info -> showUndo(info, aboveTabBar = true) { viewModel.undo(it) } }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        shownTab = null
        _binding = null
    }

    // ---------- Setup ----------

    private fun setUpTabs() {
        binding.collectionTabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) viewModel.selectTab(tabOfButton(checkedId))
        }
        // The selected tab can also change from outside (Home's tiles): the control follows
        viewModel.tab.observe(viewLifecycleOwner) { binding.collectionTabs.check(buttonOfTab(it)) }
    }

    private fun tabOfButton(buttonId: Int): CollectionTab = when (buttonId) {
        R.id.tabWatchlist -> CollectionTab.WATCHLIST
        R.id.tabWatched -> CollectionTab.WATCHED
        else -> CollectionTab.LIKED
    }

    private fun buttonOfTab(tab: CollectionTab): Int = when (tab) {
        CollectionTab.LIKED -> R.id.tabLiked
        CollectionTab.WATCHLIST -> R.id.tabWatchlist
        CollectionTab.WATCHED -> R.id.tabWatched
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: CollectionState) {
        val content = state as? CollectionState.Content
        val isEmpty = content != null && content.movies.isEmpty()

        binding.stateLoading.root.isVisible = state is CollectionState.Loading
        binding.stateError.root.isVisible = state is CollectionState.Error
        binding.stateEmpty.root.isVisible = isEmpty
        binding.collectionGrid.isVisible = content != null && !isEmpty

        if (content == null) return
        adapter.submit(content.tab, content.movies)
        if (shownTab != content.tab) {
            shownTab = content.tab
            binding.collectionGrid.scrollToPosition(0) // a different tab starts at its top
        }
        if (isEmpty) showEmptyState(content.tab)
    }

    /** Each tab has its own calm empty state, with a clear next step where there is one. */
    private fun showEmptyState(tab: CollectionTab) {
        val empty = binding.stateEmpty
        when (tab) {
            CollectionTab.LIKED -> empty.show(
                icon = R.drawable.ic_heart,
                title = R.string.collection_empty_liked_title,
                message = R.string.collection_empty_liked_message,
                action = EmptyAction(R.string.home_hero_button) { // "Let's watch something!", like Home's red button
                    watchFlow.reset() // a new journey starts with nothing chosen
                    findNavController().openScreen(R.id.moodFragment)
                }
            )
            CollectionTab.WATCHLIST -> empty.show(
                icon = R.drawable.ic_bookmark,
                title = R.string.collection_empty_watchlist_title,
                message = R.string.collection_empty_watchlist_message,
                action = EmptyAction(R.string.collection_empty_watchlist_action) { // "Browse movies"
                    findNavController().openTab(R.id.discoverFragment)
                }
            )
            // Nothing to do from here: you watch movies, then mark them
            CollectionTab.WATCHED -> empty.show(
                icon = R.drawable.ic_check,
                title = R.string.collection_empty_watched_title,
                message = R.string.collection_empty_watched_message
            )
        }
    }

    // ---------- The long-press menu ----------

    /** The small menu of a poster. Its entries depend on the tab and the movie (logic/CollectionRules.kt). */
    private fun showMenu(movie: Movie, anchor: View) {
        val tab = viewModel.tab.value ?: return
        val actions = actionsFor(tab, movie)

        val menu = PopupMenu(requireContext(), anchor)
        actions.forEachIndexed { index, action -> menu.menu.add(Menu.NONE, index, index, action.label) }
        menu.setOnMenuItemClickListener { item ->
            viewModel.perform(actions[item.itemId], movie)
            true
        }
        menu.show()
    }

    private companion object {
        const val GLOW_STRENGTH = 0.4f
    }
}
