package com.example.cinefiles.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.SheetFavoritePickerBinding
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.util.EmptyAction
import com.example.cinefiles.util.openTab
import com.example.cinefiles.util.show
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The favorites picker: the movies the user has WATCHED, as a poster grid, to fill an empty favorite place.
 * (THE WATCH RULE: an unwatched movie cannot be a favorite, so it is not offered. With nothing watched yet the sheet says
 * how to get started.) It reads its movies from the Profile's own ViewModel, because it is shown from that screen's child
 * fragment manager. Open it with [show].
 */
class FavoritePickerSheet : BottomSheetDialogFragment() {

    private var _binding: SheetFavoritePickerBinding? = null
    private val binding get() = _binding!!

    private val profileViewModel: ProfileViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetFavoritePickerBinding.inflate(inflater, container, false)
        // Three quarters of the screen: tall enough for a grid, and it still looks like a sheet
        // The sheet has no parent while it is inflated, so the view has no layout params yet: give it a full-width, fixed-height set
        val height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_SHARE).toInt()
        binding.root.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }

        val adapter = PosterAdapter(PosterStyle.GRID, onMovieClick = { movie ->
            profileViewModel.addFavorite(movie)
            dismiss()
        })
        binding.pickerList.adapter = adapter
        binding.pickerList.itemAnimator = null

        binding.pickerEmpty.show(
            icon = R.drawable.ic_check,
            title = R.string.favorites_picker_empty_title,
            message = R.string.favorites_picker_empty_message,
            action = EmptyAction(R.string.favorites_picker_empty_action) {
                val navController = findNavController()
                dismiss()
                navController.openTab(R.id.discoverFragment)
            }
        )

        profileViewModel.pickable.observe(viewLifecycleOwner) { movies ->
            adapter.submitList(movies)
            binding.pickerList.isVisible = movies.isNotEmpty()
            binding.pickerEmpty.root.isVisible = movies.isEmpty()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "FavoritePickerSheet"
        private const val SHEET_HEIGHT_SHARE = 0.75f

        fun show(manager: FragmentManager) = FavoritePickerSheet().show(manager, TAG)
    }
}
