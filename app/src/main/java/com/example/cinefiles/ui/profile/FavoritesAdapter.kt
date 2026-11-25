package com.example.cinefiles.ui.profile

import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.databinding.ViewFavoriteSlotBinding
import com.example.cinefiles.logic.MAX_FAVORITES
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.animationsEnabled
import com.example.cinefiles.util.loadImage

/**
 * The four favorite places of the Profile: always four slots (a poster with a small x, or an empty "+" place), in the order the
 * user chose. It is NOT a ListAdapter on purpose: the places are fixed, and a drag moves a poster from one place to another
 * while the finger is still down, which a background diff would fight with.
 *
 *  - Tap a poster = its Details, tap an empty place = the picker ([onEmptyClick]), tap the x = [onRemoveClick].
 *  - Press and hold a poster, then drag it to another poster's place ([touchHelper]); the new order is reported with
 *    [onOrderChanged] when it is dropped, and the Profile saves it at once, so it survives a restart.
 *  - A screen reader gets "Move earlier" / "Move later" actions instead of the drag.
 */
class FavoritesAdapter(
    private val onMovieClick: (Movie) -> Unit,
    private val onEmptyClick: () -> Unit,
    private val onRemoveClick: (Movie) -> Unit,
    private val onOrderChanged: (List<Movie>) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.SlotHolder>() {

    class SlotHolder(val binding: ViewFavoriteSlotBinding) : RecyclerView.ViewHolder(binding.root)

    private var slots: List<Movie?> = List(MAX_FAVORITES) { null }
    private var orderAtDragStart: List<Int> = emptyList()

    /** The favorites in their places (the filled slots come first). */
    private val movies: List<Movie> get() = slots.filterNotNull()

    /** Shows [favorites] (up to four, first place first). Slots that did not change are not redrawn. */
    fun submit(favorites: List<Movie>) {
        val new = List(MAX_FAVORITES) { favorites.getOrNull(it) }
        if (new == slots) return
        val old = slots
        slots = new
        for (index in new.indices) if (old[index] != new[index]) notifyItemChanged(index)
    }

    fun isFilled(position: Int): Boolean = position in slots.indices && slots[position] != null

    /** Moves the poster at [from] to place [to]; the posters between them shift by one. Both places must be filled. */
    fun move(from: Int, to: Int): Boolean {
        if (!isFilled(from) || !isFilled(to) || from == to) return false
        val list = slots.toMutableList()
        list.add(to, list.removeAt(from))
        slots = list
        notifyItemMoved(from, to)
        return true
    }

    override fun getItemCount(): Int = slots.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotHolder =
        SlotHolder(ViewFavoriteSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: SlotHolder, position: Int) {
        val slot = holder.binding
        val movie = slots[position]
        val context = slot.root.context
        slot.posterImage.isVisible = movie != null
        slot.placeholderContainer.isVisible = movie == null
        slot.removeButton.isVisible = movie != null
        slot.movieTitle.isVisible = movie != null

        if (movie != null) {
            slot.posterImage.loadImage(ImageUrls.poster(movie.posterPath))
            slot.movieTitle.text = movie.title
            slot.slotContainer.contentDescription = context.getString(R.string.profile_favorite_slot_description, position + 1, movie.title)
            slot.slotContainer.setOnClickListener { onMovieClick(movie) }
            slot.removeButton.setOnClickListener { onRemoveClick(movie) }
            addMoveActions(slot.slotContainer, holder)
        } else {
            Glide.with(slot.root).clear(slot.posterImage)
            slot.slotContainer.contentDescription = context.getString(R.string.profile_favorite_empty_description, position + 1)
            slot.slotContainer.setOnClickListener { onEmptyClick() }
            slot.removeButton.setOnClickListener(null)
        }
    }

    /** "Move earlier" and "Move later": what a drag does, for people who cannot drag. */
    private fun addMoveActions(view: View, holder: SlotHolder) {
        val context = view.context
        for (actionId in listOf(R.id.action_favorite_earlier, R.id.action_favorite_later)) {
            ViewCompat.replaceAccessibilityAction(
                view,
                androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                    actionId,
                    context.getString(if (actionId == R.id.action_favorite_earlier) R.string.favorite_move_earlier else R.string.favorite_move_later)
                ),
                null
            ) { _, _ ->
                val from = holder.bindingAdapterPosition
                val to = if (actionId == R.id.action_favorite_earlier) from - 1 else from + 1
                if (move(from, to)) onOrderChanged(movies)
                true
            }
        }
    }

    override fun onViewRecycled(holder: SlotHolder) {
        Glide.with(holder.binding.root).clear(holder.binding.posterImage)
    }

    // ---------- Drag to reorder ----------

    /** Attach it to the list: `touchHelper.attachToRecyclerView(list)`. A long press lifts a poster; dropping saves the order. */
    val touchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (isFilled(viewHolder.bindingAdapterPosition)) {
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0)
            } else {
                0 // an empty place can neither be dragged nor swiped
            }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
            move(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

        // A poster can only be dropped on another poster's place: the empty places stay at the end
        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean =
            isFilled(target.bindingAdapterPosition)

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun isLongPressDragEnabled(): Boolean = true

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                orderAtDragStart = movies.map { it.id }
                lift(viewHolder.itemView, up = true)
                viewHolder.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            lift(viewHolder.itemView, up = false)
            if (orderAtDragStart.isNotEmpty() && movies.map { it.id } != orderAtDragStart) onOrderChanged(movies)
            orderAtDragStart = emptyList()
        }
    })

    /** A poster that is being dragged rises a little (a small scale), and settles back when it is dropped. */
    private fun lift(view: View, up: Boolean) {
        val scale = if (up) LIFT_SCALE else 1f
        val duration = if (view.context.animationsEnabled()) LIFT_MS else 0L
        view.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
    }

    private companion object {
        const val LIFT_SCALE = 1.08f
        const val LIFT_MS = 120L
    }
}
