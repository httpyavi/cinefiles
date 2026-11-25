package com.example.cinefiles.ui.detail

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentPosterViewerBinding
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.animationsEnabled
import com.example.cinefiles.util.applySystemBarsPadding

/**
 * The poster of a movie, full screen, as sharp as TMDB's w780 size (never "original"): a dark backdrop, a close button,
 * pinch to zoom and drag (ZoomImageView), a double tap to zoom in, and a tap anywhere to close.
 * The small poster of the list is shown at once while the big one loads. It fades in and out, unless the phone's
 * animations are off. Open it with [show].
 */
class PosterViewerFragment : DialogFragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentPosterViewerBinding? = null
    private val binding get() = _binding!!

    private val posterPath: String? get() = arguments?.getString(ARG_POSTER_PATH)
    private val movieTitle: String get() = arguments?.getString(ARG_TITLE).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.Theme_CineFiles_PosterViewer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPosterViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Edge to edge, like the rest of the app: the picture runs under the bars, the close button stays clear of them
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setWindowAnimations(if (requireContext().animationsEnabled()) R.style.Animation_CineFiles_PosterViewer else 0)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewerTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.viewerClose.setOnClickListener { dismiss() }
        binding.viewerImage.setOnClickListener { dismiss() } // a tap anywhere closes it
        binding.viewerImage.contentDescription = getString(R.string.poster_viewer_description, movieTitle)
        loadPoster()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadPoster() {
        val path = posterPath ?: return showFailure()

        binding.viewerLoading.isVisible = true
        Glide.with(this)
            .load(ImageUrls.hdPoster(path))
            // The small poster is most likely in Glide's cache already (the list showed it): it fills the screen at once
            .thumbnail(Glide.with(this).load(ImageUrls.poster(path)).override(Target.SIZE_ORIGINAL).dontTransform())
            .override(Target.SIZE_ORIGINAL) // never shrunk to the view: zooming in needs every pixel
            .dontTransform()                // no crop: ZoomImageView fits the whole poster itself
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean): Boolean {
                    if (_binding != null && binding.viewerImage.drawable == null) showFailure()
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?, dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    _binding?.let {
                        it.viewerLoading.isVisible = false
                        it.viewerError.isVisible = false
                    }
                    return false
                }
            })
            .into(binding.viewerImage)
    }

    private fun showFailure() {
        binding.viewerLoading.isVisible = false
        binding.viewerError.isVisible = true
    }

    companion object {
        private const val TAG = "PosterViewer"
        private const val ARG_POSTER_PATH = "posterPath"
        private const val ARG_TITLE = "title"

        /** Opens the viewer on top of the screen of [manager]. Opening it twice (a double tap) does nothing. */
        fun show(manager: FragmentManager, posterPath: String, title: String) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            PosterViewerFragment()
                .apply { arguments = bundleOf(ARG_POSTER_PATH to posterPath, ARG_TITLE to title) }
                .showNow(manager, TAG)
        }
    }
}
