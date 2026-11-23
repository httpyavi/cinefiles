package com.example.cinefiles.ui.picks

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.SheetQuickViewBinding
import com.example.cinefiles.ui.mood.WatchFlowViewModel
import com.example.cinefiles.util.openMovieDetails
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The quick view of one pick: a bottom sheet with a short description, why it was picked, and three actions
 * (Details, Add to Watchlist, Mark as watched). It reads the pick from the shared WatchFlowViewModel and
 * sends the actions back to it. Open it with [show].
 */
class QuickViewSheet : BottomSheetDialogFragment() {

    private var _binding: SheetQuickViewBinding? = null
    private val binding get() = _binding!!

    private val watchFlow: WatchFlowViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetQuickViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val movieId = requireArguments().getInt(ARG_MOVIE_ID)
        val ready = watchFlow.picks.value as? PicksState.Ready
        val pick = ready?.let { (listOf(it.top) + it.more).firstOrNull { pick -> pick.movie.id == movieId } }
        if (pick == null) {
            dismiss() // the picks were rebuilt while the sheet was away: there is nothing to show
            return
        }

        val movie = pick.movie
        binding.quickTitle.text = movie.title
        binding.quickMatch.text = getString(R.string.picks_match_percent, pick.matchPercent)
        binding.quickMeta.text = pickMeta(movie)
        binding.quickReason.text = requireContext().reasonText(pick.reason)
        binding.quickDescription.text = movie.overview
        binding.quickDescription.visibility = if (movie.overview.isBlank()) View.GONE else View.VISIBLE

        // The watchlist button says what a tap will do, and follows the database
        watchFlow.watchlistIds.observe(viewLifecycleOwner) { ids ->
            val onList = movie.id in ids
            binding.quickAddWatchlist.setText(if (onList) R.string.detail_in_watchlist else R.string.action_add_watchlist)
            binding.quickAddWatchlist.setIconResource(if (onList) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        }

        binding.quickDetails.setOnClickListener {
            val navController = findNavController()
            dismiss()
            navController.openMovieDetails(movie.id)
        }
        binding.quickAddWatchlist.setOnClickListener {
            watchFlow.toggleWatchlist(movie)
            dismiss()
        }
        binding.quickMarkWatched.setOnClickListener {
            watchFlow.markWatched(movie)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "QuickViewSheet"
        private const val ARG_MOVIE_ID = "movieId"

        fun show(manager: FragmentManager, movieId: Int) {
            QuickViewSheet().apply { arguments = bundleOf(ARG_MOVIE_ID to movieId) }.show(manager, TAG)
        }
    }
}
