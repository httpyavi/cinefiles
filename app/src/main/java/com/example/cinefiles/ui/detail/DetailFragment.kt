package com.example.cinefiles.ui.detail

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.cinefiles.R
import com.example.cinefiles.data.CrewMember
import com.example.cinefiles.data.Movie
import com.example.cinefiles.data.Reaction
import com.example.cinefiles.databinding.FragmentDetailBinding
import com.example.cinefiles.databinding.ItemCrewGroupBinding
import com.example.cinefiles.databinding.ItemInfoRowBinding
import com.example.cinefiles.logic.CrewRole
import com.example.cinefiles.logic.canLike
import com.example.cinefiles.logic.splitRuntime
import com.example.cinefiles.logic.watchlistButtonFor
import com.example.cinefiles.ui.components.PosterAdapter
import com.example.cinefiles.ui.components.PosterStyle
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.addPersonChip
import com.example.cinefiles.util.applySystemBarsPadding
import com.example.cinefiles.util.genresText
import com.example.cinefiles.util.languageName
import com.example.cinefiles.util.loadImage
import com.example.cinefiles.util.openMovieDetails
import com.example.cinefiles.util.openPerson
import com.example.cinefiles.util.ratingText
import com.example.cinefiles.util.shake
import com.example.cinefiles.util.show
import com.example.cinefiles.util.showRatingStar
import com.example.cinefiles.util.showMessage

/**
 * The Movie Details screen for one movie. It only draws what DetailViewModel says and forwards taps.
 * The movie's id arrives as the navigation argument "movieId" (see openMovieDetails in util/NavigationExtensions.kt).
 * The three buttons change the database; the screen follows the database, so it is always up to date.
 *
 * It also shows the people of the movie: the director(s) next to the title, the cast row and the crew chips. Every name
 * opens the Person screen (openPerson). The poster in the top corner opens the full-screen poster (PosterViewerFragment).
 */
class DetailFragment : Fragment() {

    // ViewBinding: only valid between onCreateView and onDestroyView
    private var _binding: FragmentDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DetailViewModel by viewModels()

    private lateinit var similarAdapter: PosterAdapter
    private lateinit var castAdapter: CastAdapter

    // Remembered between drawings, so a toggle does not redo work that did not change
    private var shownBackdropUrl: String? = null
    private var shownPosterPath: String? = null
    private var shownOverview: String? = null
    private var overviewExpanded = false
    private var trailerKey: String? = null
    private var posterTitle: String = ""
    private var shownDirectors: List<CrewMember>? = null
    private var shownCrew: Map<CrewRole, List<CrewMember>>? = null
    private var shownFacts: List<Pair<Int, String>>? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // The backdrop runs under the status bar, so only the floating back button and the states are moved down
        binding.detailTopBar.applySystemBarsPadding(top = true, bottom = false)
        binding.detailStates.applySystemBarsPadding()
        binding.detailScroll.applySystemBarsPadding(top = false, bottom = true)

        binding.detailBack.setOnClickListener { findNavController().popBackStack() }

        binding.watchlistButton.setOnClickListener { viewModel.toggleWatchlist() }
        binding.likeButton.setOnClickListener { viewModel.toggleLike() }
        binding.watchedButton.setOnClickListener { viewModel.toggleWatched() }
        binding.favoriteButton.setOnClickListener { viewModel.toggleFavorite() }
        binding.trailerButton.setOnClickListener { trailerKey?.let { openTrailer(it) } }
        binding.overviewToggle.setOnClickListener {
            overviewExpanded = !overviewExpanded
            applyOverviewLines()
        }

        // Similar movies: the same poster row as Home. Tapping one opens ITS details on top of this one.
        similarAdapter = PosterAdapter(PosterStyle.ROW, onMovieClick = { movie -> findNavController().openMovieDetails(movie.id) })
        binding.similarList.adapter = similarAdapter
        binding.similarList.setHasFixedSize(true)

        // The poster: tap it to see it full screen. (It is hidden while the movie has no poster.)
        binding.detailPoster.setOnClickListener {
            shownPosterPath?.let { PosterViewerFragment.show(childFragmentManager, it, posterTitle) }
        }

        // The cast: a row of round photos. Tapping an actor opens their Person screen.
        castAdapter = CastAdapter(onPersonClick = { member -> findNavController().openPerson(member.id) })
        binding.castList.adapter = castAdapter
        binding.castList.setHasFixedSize(true)

        binding.stateError.errorRetry.setOnClickListener { viewModel.load() }
        binding.loadErrorRetry.setOnClickListener { viewModel.load() }

        viewModel.state.observe(viewLifecycleOwner) { render(it) }
        viewModel.likeBlocked.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                // THE LIKE RULE: the button wiggles "no" and the Snackbar says what to do instead
                binding.likeButton.shake()
                showMessage(R.string.like_blocked_message)
            }
        }
        viewModel.favoriteBlocked.observe(viewLifecycleOwner) { event ->
            event.getIfNotHandled()?.let {
                binding.favoriteButton.shake()
                showMessage(R.string.favorite_limit_reached)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // The next view starts fresh, so everything must be drawn again
        shownBackdropUrl = null
        shownPosterPath = null
        shownOverview = null
        shownDirectors = null
        shownCrew = null
        shownFacts = null
        _binding = null
    }

    // ---------- Drawing what the ViewModel says ----------

    private fun render(state: DetailState) {
        val content = state as? DetailState.Content

        binding.detailScroll.isVisible = content != null
        binding.detailStates.isVisible = content == null
        binding.stateLoading.root.isVisible = state is DetailState.Loading
        binding.stateError.root.isVisible = state is DetailState.Error
        if (state is DetailState.Error) binding.stateError.show(state.error)
        if (content == null) return

        showHero(content.movie)
        showDirectors(content.directors)
        showButtons(content.movie)
        showTagline(content.tagline)
        showOverview(content.movie.overview)

        // The trailer button only exists when TMDB lists a YouTube trailer
        trailerKey = content.trailerKey
        binding.trailerButton.isVisible = content.trailerKey != null

        // TMDB could not be reached: say so, but keep showing what we have
        binding.detailLoadError.isVisible = content.loadError != null
        content.loadError?.let { binding.loadErrorText.setText(it.message) }

        // The people and the facts only come with TMDB's answer: each section shows once it has something
        binding.castSection.isVisible = content.cast.isNotEmpty()
        castAdapter.submitList(content.cast)
        showCrew(content.crew)
        showInfo(content)

        // Similar movies: placeholders while they load, the row when they are here, nothing if there are none
        val similarLoading = content.isLoadingMore && content.similar.isEmpty()
        binding.similarSection.isVisible = similarLoading || content.similar.isNotEmpty()
        binding.similarLoading.root.isVisible = similarLoading
        binding.similarList.isVisible = !similarLoading
        similarAdapter.submitList(content.similar)
    }

    /** The picture, title, "rating · year · length" and the genres. */
    private fun showHero(movie: Movie) {
        binding.detailTitle.text = movie.title

        // A movie without a backdrop uses its poster (cropped to the wide picture) instead of an empty block
        val url = ImageUrls.backdrop(movie.backdropPath ?: movie.posterPath)
        if (url != shownBackdropUrl) {
            shownBackdropUrl = url
            binding.detailBackdrop.loadImage(url)
        }

        // The poster in the corner opens the full-screen poster. A movie without a poster has no corner picture.
        posterTitle = movie.title
        binding.detailPosterWrap.isVisible = movie.posterPath != null
        binding.detailPoster.contentDescription = getString(R.string.detail_poster_description, movie.title)
        if (movie.posterPath != shownPosterPath) {
            shownPosterPath = movie.posterPath
            binding.detailPoster.imageView.loadImage(ImageUrls.poster(movie.posterPath))
        }

        val rating = movie.ratingText()
        val year = movie.releaseYear?.toString()
        val length = movie.runtimeMinutes?.let { runtimeText(it) }
        val meta = listOfNotNull(rating, year, length).joinToString(" · ")
        binding.detailMeta.text = meta
        binding.detailMeta.showRatingStar(rating != null)
        binding.detailMeta.isVisible = meta.isNotEmpty()
        // A screen reader says "Rated 8.4 out of 10" instead of "star 8.4"
        binding.detailMeta.contentDescription = rating?.let {
            listOfNotNull(getString(R.string.swipe_rating_description, it), year, length).joinToString(", ")
        }

        val genres = movie.genresText()
        binding.detailGenres.text = genres
        binding.detailGenres.isVisible = genres.isNotEmpty()
    }

    /** "Directed by Christopher Nolan": next to the title, every director's name opens their Person screen. */
    private fun showDirectors(directors: List<CrewMember>) {
        if (directors == shownDirectors) return
        shownDirectors = directors

        val view = binding.detailDirectors
        view.isVisible = directors.isNotEmpty()
        if (directors.isEmpty()) return

        val accent = ContextCompat.getColor(requireContext(), R.color.accent_soft)
        val text = SpannableStringBuilder(getString(R.string.detail_directed_by)).append(' ')
        directors.forEachIndexed { index, director ->
            if (index > 0) text.append(", ")
            val start = text.length
            text.append(director.name)
            text.setSpan(
                PersonSpan(accent) { findNavController().openPerson(director.id) },
                start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        view.text = text
        view.movementMethod = LinkMovementMethod.getInstance() // makes the names tappable
    }

    /** The crew: a small heading and tappable names for the writers, producers, cinematography and composers. */
    private fun showCrew(crew: Map<CrewRole, List<CrewMember>>) {
        if (crew == shownCrew) return
        shownCrew = crew

        binding.crewSection.isVisible = crew.isNotEmpty()
        binding.crewGroups.removeAllViews()
        for ((role, people) in crew) {
            val group = ItemCrewGroupBinding.inflate(layoutInflater, binding.crewGroups, false)
            group.crewRole.setText(role.label)
            for (person in people) group.crewChips.addPersonChip(person.name) { findNavController().openPerson(person.id) }
            binding.crewGroups.addView(group.root)
        }
    }

    /** A few facts: the release status, the original language and where it was made. Only the ones TMDB knows. */
    private fun showInfo(content: DetailState.Content) {
        val facts = listOfNotNull(
            content.status?.let { R.string.detail_info_status to it },
            languageName(content.movie.originalLanguage)?.let { R.string.detail_info_language to it },
            content.countries.takeIf { it.isNotEmpty() }?.let { R.string.detail_info_countries to it.joinToString(", ") }
        )
        if (facts == shownFacts) return
        shownFacts = facts

        binding.infoSection.isVisible = facts.isNotEmpty()
        binding.infoRows.removeAllViews()
        for ((label, value) in facts) {
            val row = ItemInfoRowBinding.inflate(layoutInflater, binding.infoRows, false)
            row.infoLabel.setText(label)
            row.infoValue.text = value
            binding.infoRows.addView(row.root)
        }
    }

    /** The tagline above the story, when TMDB has one. The story moves closer to it. */
    private fun showTagline(tagline: String?) {
        binding.detailTagline.isVisible = tagline != null
        binding.detailTagline.text = tagline
        binding.detailOverview.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = resources.getDimensionPixelSize(if (tagline != null) R.dimen.space_12 else R.dimen.space_24)
        }
    }

    /** The red watchlist button and the Like and Watched toggles show what is saved right now. */
    private fun showButtons(movie: Movie) {
        val watchlist = watchlistButtonFor(movie)
        binding.watchlistButton.setText(watchlist.label)
        binding.watchlistButton.setIconResource(
            if (watchlist.isOnList) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )

        val liked = movie.reaction == Reaction.LIKED
        binding.likeButton.setText(if (liked) R.string.status_liked else R.string.action_like)
        binding.likeButton.setIconResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        binding.likeButton.iconTint = tint(if (liked) R.color.like else R.color.text_primary)
        // On the watchlist, Like is unavailable (THE LIKE RULE): dimmed, but still tappable so that a tap can explain why
        binding.likeButton.alpha = if (liked || canLike(movie)) 1f else UNAVAILABLE_ALPHA

        val watched = movie.isWatched
        binding.watchedButton.setText(if (watched) R.string.status_watched else R.string.detail_mark_watched)
        binding.watchedButton.setIconResource(if (watched) R.drawable.ic_check_filled else R.drawable.ic_check)
        binding.watchedButton.iconTint = tint(if (watched) R.color.accent_soft else R.color.text_primary)

        val favorite = movie.isFavorite
        binding.favoriteButton.setText(if (favorite) R.string.detail_favorited else R.string.detail_favorite)
        binding.favoriteButton.setIconResource(R.drawable.ic_star_filled)
        binding.favoriteButton.iconTint = tint(if (favorite) R.color.accent else R.color.text_primary)
    }

    private fun tint(colorRes: Int): ColorStateList =
        ColorStateList.valueOf(ContextCompat.getColor(requireContext(), colorRes))

    /** The story, collapsed to three lines. "More" is only offered when there is more to show. */
    private fun showOverview(overview: String) {
        val hasText = overview.isNotBlank()
        binding.detailOverview.isVisible = hasText
        if (!hasText) {
            binding.overviewToggle.isVisible = false
            return
        }
        if (overview != shownOverview) {
            shownOverview = overview
            overviewExpanded = false
            binding.detailOverview.text = overview
            applyOverviewLines()
        }
    }

    private fun applyOverviewLines() {
        binding.detailOverview.maxLines = if (overviewExpanded) Int.MAX_VALUE else COLLAPSED_LINES
        binding.overviewToggle.setText(if (overviewExpanded) R.string.detail_less else R.string.detail_more)

        // Only after the text has been laid out can we know whether it was cut off
        binding.detailOverview.doOnPreDraw {
            val current = _binding ?: return@doOnPreDraw
            val layout = current.detailOverview.layout ?: return@doOnPreDraw
            val isCutOff = layout.lineCount > 0 && layout.getEllipsisCount(layout.lineCount - 1) > 0
            current.overviewToggle.isVisible = overviewExpanded || isCutOff
        }
    }

    private fun runtimeText(totalMinutes: Int): String {
        val (hours, minutes) = splitRuntime(totalMinutes)
        return when {
            hours > 0 && minutes > 0 -> getString(R.string.detail_runtime_hours_minutes, hours, minutes)
            hours > 0 -> getString(R.string.detail_runtime_hours, hours)
            else -> getString(R.string.detail_runtime_minutes, minutes)
        }
    }

    /** Opens the YouTube trailer: in the YouTube app if the phone has it, otherwise in the browser. */
    private fun openTrailer(key: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showMessage(R.string.detail_trailer_error)
        }
    }

    private companion object {
        const val COLLAPSED_LINES = 3
        const val UNAVAILABLE_ALPHA = 0.5f
    }
}

/** A name in a line of text that opens a screen when tapped: colored with the accent, not underlined. */
private class PersonSpan(private val color: Int, private val action: () -> Unit) : ClickableSpan() {
    override fun onClick(widget: View) = action()

    override fun updateDrawState(paint: TextPaint) {
        paint.color = color
        paint.isUnderlineText = false
        paint.isFakeBoldText = true
    }
}
