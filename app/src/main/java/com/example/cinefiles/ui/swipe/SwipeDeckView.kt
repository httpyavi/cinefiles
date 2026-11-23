package com.example.cinefiles.ui.swipe

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.SwipeDirection
import com.example.cinefiles.logic.SwipeGesture
import kotlin.math.abs
import kotlin.math.max

/**
 * The pile of swipe cards, and everything that moves them: the finger, the fling, the animations.
 * No library: only MotionEvent, VelocityTracker and ViewPropertyAnimator.
 *
 * HOW IT WORKS
 *  - Only THREE card views exist. They are reused all the time: the card on top, the one behind it,
 *    and one more behind that (invisible, but already loading its poster so it is ready).
 *  - The deck does not know which movies are in the deck. The screen tells it what to show with
 *    [setCards]. After a swipe the deck calls [Listener.onSwiped]; the ViewModel updates itself and the
 *    screen calls [setCards] again with the new top three. Cards that already show the right movie
 *    are kept as they are, so nothing flickers.
 *  - While an animation runs ([isBusy]) all touches and button taps are ignored.
 *  - Only translation, rotation, scale and alpha are animated (they are cheap: no re-layout).
 *
 * "Remove animations" in the phone's settings: Android then makes every animation finish at once, so
 * the cards simply jump to where they should be. Nothing in here depends on an animation taking time.
 */
class SwipeDeckView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        /** A card finished flying away (by finger or by button). */
        fun onSwiped(direction: SwipeDirection)

        /** The top card was tapped, without dragging. */
        fun onCardTapped()

        /**
         * The card is being dragged. [progress] is -1f (far left, a pass is certain) ... 0f ... +1f (far right,
         * a like is certain). The screen uses it to turn its background light up and down.
         */
        fun onDrag(progress: Float) = Unit

        /** The finger let go and the card is going back to the middle (no swipe). */
        fun onDragCancelled() = Unit

        /** The card has started to fly away (by finger or by button), BEFORE it has landed. */
        fun onCardLeaving(direction: SwipeDirection) = Unit
    }

    var listener: Listener? = null

    /** Gives the small line shown above a card's title ("Top rated"...). The screen sets it once. */
    var eyebrowFor: (Movie) -> String? = { null }

    /** True while a card is flying away, springing back or coming back. Everything is ignored then. */
    var isBusy: Boolean = false
        private set

    // The three reusable cards, in order: [0] top, [1] next, [2] the one after (hidden)
    private val cards = ArrayList<SwipeCardView>(CARD_COUNT)

    // ---- Numbers worked out ONCE here (in pixels), so the touch handler never converts or allocates ----
    private val viewConfiguration = ViewConfiguration.get(context)
    private val touchSlop = viewConfiguration.scaledTouchSlop.toFloat()
    private val flingVelocity = max(
        viewConfiguration.scaledMinimumFlingVelocity.toFloat(),
        resources.getDimension(R.dimen.swipe_fling_velocity)
    )
    private val nextCardOffset = resources.getDimension(R.dimen.swipe_next_card_offset)
    private val topCardElevation = resources.getDimension(R.dimen.swipe_card_elevation)

    // The interpolators are made once too
    private val flyInterpolator = AccelerateInterpolator(FLY_ACCELERATION)
    private val growInterpolator = DecelerateInterpolator()
    private val springInterpolator = OvershootInterpolator(SPRING_OVERSHOOT)
    private val comeBackInterpolator = DecelerateInterpolator(COME_BACK_DECELERATION)

    // ---- The finger ----
    private var activePointerId = INVALID_POINTER
    private var downX = 0f
    private var downY = 0f
    private var lastDx = 0f
    private var dragging = false
    private var velocityTracker: VelocityTracker? = null

    // What to report when the running animation ends (null = nothing is waiting)
    private var pendingDirection: SwipeDirection? = null
    private var pendingUndo: (() -> Unit)? = null

    init {
        // The cards fly out past the edges of this view and cast a shadow, so nothing may clip them
        clipChildren = false
        clipToPadding = false

        repeat(CARD_COUNT) {
            val card = SwipeCardView(context)
            card.visibility = INVISIBLE
            addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            cards.add(card)
        }
        for (role in 0 until CARD_COUNT) placeAtRest(cards[role], role)

        // Screen readers: the deck reads out the movie on top and offers "Like" and "Pass" as actions, so swiping
        // is possible without a finger gesture (the same animated swipe as the buttons)
        isFocusable = true
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.action_like)) { _, _ -> swipeTop(SwipeDirection.LIKE) }
        ViewCompat.addAccessibilityAction(this, context.getString(R.string.swipe_pass)) { _, _ -> swipeTop(SwipeDirection.PASS) }
    }

    // =====================================================================================
    //  What the screen can ask for
    // =====================================================================================

    /**
     * Shows these three movies as the top card, the next card and the one after (null = no card there).
     * No animation: use it to show the deck when the screen appears and after every change.
     */
    fun setCards(top: Movie?, second: Movie?, third: Movie?) {
        val wanted = arrayOf(top, second, third)
        val order = arrayOfNulls<SwipeCardView>(CARD_COUNT)
        val unused = ArrayList(cards)

        // 1) A card that already shows the wanted movie keeps it (no flicker, no reload).
        for (role in 0 until CARD_COUNT) {
            val movie = wanted[role] ?: continue
            val match = unused.firstOrNull { it.movieId == movie.id } ?: continue
            order[role] = match
            unused.remove(match)
        }
        // 2) The other places take the cards that are left over and show their movie.
        for (role in 0 until CARD_COUNT) {
            if (order[role] != null) continue
            val card = unused.removeAt(0)
            val movie = wanted[role]
            if (movie != null) card.bind(movie, eyebrowFor(movie)) else card.clear()
            order[role] = card
        }
        applyOrder(order.requireNoNulls().asList())
        contentDescription = cards[TOP].contentDescription // what a screen reader says about the card on top
    }

    /** The buttons use this: flies the top card away exactly like a swipe. False if it could not start. */
    fun swipeTop(direction: SwipeDirection): Boolean {
        if (isBusy || cards[TOP].movieId == null) return false
        flyOff(direction)
        return true
    }

    /**
     * Undo: brings [movie] (the card that was swiped away) back from the side it left to, on top of the pile.
     * [onDone] is called when it has arrived. False if it could not start.
     */
    fun bringBack(movie: Movie, from: SwipeDirection, onDone: () -> Unit): Boolean {
        if (isBusy) return false
        isBusy = true
        pendingUndo = onDone

        val oldTop = cards[TOP]
        val oldNext = cards[NEXT]
        val incoming = cards[THIRD] // the hidden card is free: it becomes the returning card

        val sign = if (from == SwipeDirection.LIKE) 1f else -1f
        val offscreen = sign * (width + incoming.width)

        // The returning card starts off-screen, on the side where it left, still showing its like / pass feedback
        incoming.animate().cancel()
        incoming.cancelChildAnimations()
        incoming.bind(movie, eyebrowFor(movie))
        incoming.setCover(0f) // it comes back as the top card: poster fully visible
        incoming.alpha = 1f
        incoming.scaleX = 1f
        incoming.scaleY = 1f
        incoming.translationY = 0f
        incoming.translationX = offscreen
        incoming.rotation = rotationFor(offscreen)
        incoming.elevation = topCardElevation
        incoming.setFeedback(
            like = if (from == SwipeDirection.LIKE) 1f else 0f,
            pass = if (from == SwipeDirection.PASS) 1f else 0f
        )
        incoming.bringToFront()
        incoming.fadeOutFeedback(COME_BACK_MS)

        // The cards that were in front step back one place
        if (oldTop.movieId != null) {
            oldTop.elevation = 0f
            oldTop.animateCover(WAITING_CARD_COVER, COME_BACK_MS)
            oldTop.animate().scaleX(NEXT_CARD_SCALE).scaleY(NEXT_CARD_SCALE)
                .translationY(nextCardOffset).setDuration(COME_BACK_MS).setInterpolator(growInterpolator)
        }
        if (oldNext.movieId != null) {
            oldNext.animate().alpha(0f).setDuration(COME_BACK_MS)
        }

        // The returning card is started LAST: when its animation ends we are done with all of them
        incoming.animate().translationX(0f).rotation(0f)
            .setDuration(COME_BACK_MS).setInterpolator(comeBackInterpolator)
            .withEndAction { finishBringBack(incoming, oldTop, oldNext) }
        return true
    }

    // =====================================================================================
    //  The finger
    // =====================================================================================

    // performClick() is called from finishTracking when the touch turns out to be a tap, not from here
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return startTracking(event)
            MotionEvent.ACTION_MOVE -> {
                followFinger(event)
                return activePointerId != INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // A second finger came and went: ignore it. If it was OUR finger that lifted, we are done.
                if (event.getPointerId(event.actionIndex) == activePointerId) finishTracking(event, cancelled = false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                finishTracking(event, cancelled = false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                finishTracking(event, cancelled = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // Needed so that screen readers and keyboards can "click" the deck
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun startTracking(event: MotionEvent): Boolean {
        // Ignore the touch while an animation runs, or when there is no card
        if (isBusy || cards[TOP].movieId == null) return false

        activePointerId = event.getPointerId(0)
        downX = event.x
        downY = event.y
        lastDx = 0f
        dragging = false

        velocityTracker?.recycle()
        velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }

        // We want the whole gesture, even if the finger drifts onto something else
        parent?.requestDisallowInterceptTouchEvent(true)
        return true
    }

    // Called many times per second while the finger moves. It must stay light: no new objects in here.
    private fun followFinger(event: MotionEvent) {
        if (activePointerId == INVALID_POINTER) return
        velocityTracker?.addMovement(event)

        val index = event.findPointerIndex(activePointerId)
        if (index < 0) return
        val dx = event.getX(index) - downX
        val dy = event.getY(index) - downY

        // Small wobbles of a resting finger are not a drag (touch slop)
        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
        if (dragging) dragTop(dx, dy)
    }

    /** Moves the top card with the finger, and lets everything else react to how far it has gone. */
    private fun dragTop(dx: Float, dy: Float) {
        lastDx = dx
        val top = cards[TOP]
        val next = cards[NEXT]
        val cardWidth = width.toFloat()

        top.translationX = dx
        top.translationY = dy
        top.rotation = rotationFor(dx)

        listener?.onDrag((dx / (cardWidth * SwipeGesture.COMMIT_FRACTION)).coerceIn(-1f, 1f))

        // A red veil and a heart (right) or a slate veil and an X (left) grow with the distance, up to the "swipe" line
        val feedback = (abs(dx) / (cardWidth * SwipeGesture.COMMIT_FRACTION)).coerceAtMost(1f)
        if (dx > 0f) top.setFeedback(feedback, 0f) else top.setFeedback(0f, feedback)

        // The card behind grows toward full size, and rises, as the top card approaches the "point of no return"
        val progress = (abs(dx) / (cardWidth * SwipeGesture.COMMIT_FRACTION)).coerceAtMost(1f)
        val scale = NEXT_CARD_SCALE + (1f - NEXT_CARD_SCALE) * progress
        next.scaleX = scale
        next.scaleY = scale
        next.translationY = nextCardOffset * (1f - progress)
        next.setCover(WAITING_CARD_COVER * (1f - progress)) // its poster is revealed as it comes forward
    }

    private fun finishTracking(event: MotionEvent, cancelled: Boolean) {
        if (activePointerId == INVALID_POINTER) return

        val tracker = velocityTracker
        tracker?.addMovement(event)
        tracker?.computeCurrentVelocity(1000) // pixels per second
        val velocityX = tracker?.xVelocity ?: 0f
        tracker?.recycle()
        velocityTracker = null
        val finishedPointerId = activePointerId
        activePointerId = INVALID_POINTER
        parent?.requestDisallowInterceptTouchEvent(false)

        val wasDragging = dragging
        dragging = false

        // Judge by where the finger was when it LIFTED (the last move may be a moment older)
        val upIndex = event.findPointerIndex(finishedPointerId)
        if (wasDragging && !cancelled && upIndex >= 0) lastDx = event.getX(upIndex) - downX

        when {
            !wasDragging -> {
                // The finger never really moved: that is a tap
                if (!cancelled) {
                    performClick()
                    listener?.onCardTapped()
                }
            }
            cancelled -> springBack()
            else -> {
                // Far enough, or flicked fast enough = it flies away; otherwise it springs back
                val direction = SwipeGesture.decide(lastDx, velocityX, width.toFloat(), flingVelocity)
                if (direction != null) flyOff(direction) else springBack()
            }
        }
    }

    // =====================================================================================
    //  The animations
    // =====================================================================================

    /** The top card flies off the screen (to the right = LIKE, to the left = PASS). */
    private fun flyOff(direction: SwipeDirection) {
        val top = cards[TOP]
        val next = cards[NEXT]
        val third = cards[THIRD]

        isBusy = true
        pendingDirection = direction
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) // a light tick

        val sign = if (direction == SwipeDirection.LIKE) 1f else -1f
        val distance = sign * (width + top.width) // far enough to be completely off the screen

        // The feedback grows to full while the card flies (a swipe by a button had none before)
        top.cancelChildAnimations()
        top.showFullFeedback(like = sign > 0f, durationMs = FLY_OFF_MS)
        listener?.onCardLeaving(direction)

        // The card behind grows to full size and shows its poster, and the hidden one behind it fades in
        next.animateCover(0f, FLY_OFF_MS)
        next.animate().scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(FLY_OFF_MS).setInterpolator(growInterpolator)
        third.animate().alpha(1f).setDuration(FLY_OFF_MS)

        // The flying card is started LAST. When its animation ends we are finished with all of them.
        top.animate().translationX(distance).rotation(rotationFor(distance))
            .setDuration(FLY_OFF_MS).setInterpolator(flyInterpolator)
            .withEndAction { finishFlyOff() }
    }

    private fun finishFlyOff() {
        val direction = pendingDirection ?: return // this view went away while the card was flying
        pendingDirection = null
        // The ViewModel updates and the screen calls setCards(...) with the new top three, right here
        listener?.onSwiped(direction)
        isBusy = false
    }

    /** Not far enough: the card swings back to the middle, with a little overshoot. */
    private fun springBack() {
        val top = cards[TOP]
        val next = cards[NEXT]
        isBusy = true
        listener?.onDragCancelled()

        top.fadeOutFeedback(SPRING_BACK_MS)
        next.animateCover(WAITING_CARD_COVER, SPRING_BACK_MS)
        next.animate().scaleX(NEXT_CARD_SCALE).scaleY(NEXT_CARD_SCALE).translationY(nextCardOffset)
            .setDuration(SPRING_BACK_MS)

        top.animate().translationX(0f).translationY(0f).rotation(0f)
            .setDuration(SPRING_BACK_MS).setInterpolator(springInterpolator)
            .withEndAction { isBusy = false }
    }

    private fun finishBringBack(incoming: SwipeCardView, oldTop: SwipeCardView, oldNext: SwipeCardView) {
        val done = pendingUndo ?: return
        pendingUndo = null
        // The three cards have new places now. Everything is put exactly at rest.
        applyOrder(listOf(incoming, oldTop, oldNext))
        // The ViewModel updates; the screen then calls setCards(...) with the same three movies: no change
        done()
        isBusy = false
    }

    // =====================================================================================
    //  Places and looks
    // =====================================================================================

    /** The card turns by 12 degrees for every card-width it is moved sideways. */
    private fun rotationFor(dx: Float): Float {
        val degrees = MAX_ROTATION_DEGREES * dx / max(1, width)
        return degrees.coerceIn(-ROTATION_LIMIT_DEGREES, ROTATION_LIMIT_DEGREES)
    }

    /** Gives the cards their new order (top, next, third), puts the top one in front, and resets them. */
    private fun applyOrder(order: List<SwipeCardView>) {
        cards.clear()
        cards.addAll(order)
        // The card drawn last is on top, so bring the back ones to the front first
        for (role in CARD_COUNT - 1 downTo 0) cards[role].bringToFront()
        for (role in 0 until CARD_COUNT) placeAtRest(cards[role], role)
    }

    /** Puts a card exactly where its place needs it, with no animation. */
    private fun placeAtRest(card: SwipeCardView, role: Int) {
        card.animate().cancel()
        card.cancelChildAnimations()
        card.setFeedback(0f, 0f)
        card.setCover(if (role == TOP) 0f else WAITING_CARD_COVER)
        card.translationX = 0f
        card.rotation = 0f

        if (role == TOP) {
            card.scaleX = 1f
            card.scaleY = 1f
            card.translationY = 0f
            card.alpha = 1f
            card.elevation = topCardElevation // only the top card has a shadow
            card.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        } else {
            // A little smaller and lower, so the top card sits in front of it.
            // The third card is invisible (but loads its poster) until it becomes the second.
            card.scaleX = NEXT_CARD_SCALE
            card.scaleY = NEXT_CARD_SCALE
            card.translationY = nextCardOffset
            card.alpha = if (role == NEXT) 1f else 0f
            card.elevation = 0f
            card.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    override fun onDetachedFromWindow() {
        // The screen is going away: stop everything. Whatever was running is forgotten, not reported.
        pendingDirection = null
        pendingUndo = null
        for (card in cards) {
            card.animate().cancel()
            card.cancelChildAnimations()
        }
        velocityTracker?.recycle()
        velocityTracker = null
        activePointerId = INVALID_POINTER
        dragging = false
        isBusy = false
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CARD_COUNT = 3
        const val TOP = 0
        const val NEXT = 1
        const val THIRD = 2
        const val INVALID_POINTER = -1

        // How the cards look and behave. These are the numbers to change to tune the feel.
        const val NEXT_CARD_SCALE = 0.94f           // the card behind is a bit smaller
        const val WAITING_CARD_COVER = 1f           // a waiting card is fully covered by a flat surface-colored sheet
        const val MAX_ROTATION_DEGREES = 12f        // turn at one card-width of movement
        const val ROTATION_LIMIT_DEGREES = 40f

        const val FLY_OFF_MS = 220L
        const val SPRING_BACK_MS = 250L
        const val COME_BACK_MS = 250L
        const val FLY_ACCELERATION = 1.1f
        const val SPRING_OVERSHOOT = 1.2f           // a gentle overshoot
        const val COME_BACK_DECELERATION = 1.5f
    }
}
