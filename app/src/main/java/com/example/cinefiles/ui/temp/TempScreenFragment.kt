package com.example.cinefiles.ui.temp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.databinding.FragmentTempScreenBinding
import com.example.cinefiles.util.ARG_MOVIE_ID
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.openScreen

// Argument keys. They must match the <argument> names in res/navigation/nav_graph.xml.
private const val ARG_TITLE = "title"
private const val ARG_MESSAGE = "message"
private const val ARG_SHOW_BACK = "showBack"
private const val ARG_ACTION_TEXT = "actionText"
private const val ARG_ACTION_DESTINATION = "actionDestination"

// TEMP: one stand-in screen used for Discover, Collection, Profile, Mood and Details until their
// real screens exist. What it shows (title, message, back button, extra button, movie id) comes from
// the arguments in nav_graph.xml. Removed screen by screen in later batches (see TEMP.md).
class TempScreenFragment : Fragment() {

    private var _binding: FragmentTempScreenBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTempScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.tempRoot.applySystemBarsPadding()

        val args = requireArguments()
        binding.tempTitle.setText(args.getInt(ARG_TITLE))
        binding.tempMessage.setText(args.getInt(ARG_MESSAGE))

        // Back arrow: only on screens that are not tabs
        binding.tempBack.isVisible = args.getBoolean(ARG_SHOW_BACK, false)
        binding.tempBack.setOnClickListener { findNavController().navigateUp() }

        // Details: show which movie was tapped
        val movieId = args.getInt(ARG_MOVIE_ID, 0)
        binding.tempExtra.isVisible = movieId != 0
        if (movieId != 0) {
            binding.tempExtra.text = getString(R.string.temp_movie_id, movieId)
        }

        // Profile: an extra button
        val actionText = args.getInt(ARG_ACTION_TEXT, 0)
        val actionDestination = args.getInt(ARG_ACTION_DESTINATION, 0)
        binding.tempAction.isVisible = actionText != 0 && actionDestination != 0
        if (binding.tempAction.isVisible) {
            binding.tempAction.setText(actionText)
            binding.tempAction.setOnClickListener {
                findNavController().openScreen(actionDestination)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
