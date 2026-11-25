package com.example.cinefiles.ui.diary

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.ApiError
import com.example.cinefiles.databinding.ItemDiaryStillBinding
import com.example.cinefiles.databinding.SheetDiaryStillsBinding
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.show
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * The movie's own stills (TMDB: GET /movie/{id}/images) as a picture grid, to put one on the diary page. It shares the editor's
 * ViewModel (it is shown from the editor's child fragment manager), which asks TMDB and hands the chosen still to the page.
 * Open it with [show].
 */
class DiaryStillsSheet : BottomSheetDialogFragment() {

    private var _binding: SheetDiaryStillsBinding? = null
    private val binding get() = _binding!!

    private val editorViewModel: DiaryEditorViewModel by viewModels(ownerProducer = { requireParentFragment() })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SheetDiaryStillsBinding.inflate(inflater, container, false)
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

        val adapter = StillAdapter { path ->
            editorViewModel.addStill(path)
            dismiss()
        }
        binding.stillsList.adapter = adapter
        binding.stillsList.itemAnimator = null

        binding.stillsError.show(ApiError.NO_INTERNET)
        binding.stillsError.errorRetry.setOnClickListener { editorViewModel.loadStills(retry = true) }
        binding.stillsEmpty.show(
            icon = R.drawable.ic_image,
            title = R.string.diary_stills_title,
            message = R.string.diary_stills_empty
        )

        editorViewModel.stills.observe(viewLifecycleOwner) { state ->
            val ready = state as? StillsState.Ready
            binding.stillsLoading.root.isVisible = state is StillsState.Loading
            binding.stillsError.root.isVisible = state is StillsState.Error
            binding.stillsEmpty.root.isVisible = ready != null && ready.paths.isEmpty()
            binding.stillsList.isVisible = ready != null && ready.paths.isNotEmpty()
            if (ready != null) adapter.submitList(ready.paths)
        }
        editorViewModel.loadStills()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private class StillAdapter(private val onPick: (String) -> Unit) : ListAdapter<String, StillAdapter.StillHolder>(Diff) {

        class StillHolder(val binding: ItemDiaryStillBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            StillHolder(ItemDiaryStillBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: StillHolder, position: Int) {
            val path = getItem(position)
            holder.binding.root.loadImage(ImageUrls.stillThumbnail(path))
            holder.binding.root.setOnClickListener { onPick(path) }
        }

        override fun onViewRecycled(holder: StillHolder) {
            Glide.with(holder.binding.root).clear(holder.binding.root)
        }

        private object Diff : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(old: String, new: String) = old == new
            override fun areContentsTheSame(old: String, new: String) = old == new
        }
    }

    companion object {
        private const val TAG = "DiaryStillsSheet"
        private const val SHEET_HEIGHT_SHARE = 0.75f

        fun show(manager: FragmentManager) = DiaryStillsSheet().show(manager, TAG)
    }
}
