package com.example.cinefiles.ui.swipe

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cinefiles.MainActivity
import com.example.cinefiles.R
import com.example.cinefiles.data.Movie
import com.example.cinefiles.logic.SwipeDirection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Plays REAL touch gestures into the swipe deck, with exact timing, so that things a person does
 * with a finger (a fast flick, a slow drag, a tap, tapping while a card flies) can be checked
 * without a person. It puts a SwipeDeckView on top of MainActivity and sends MotionEvents to it.
 */
@RunWith(AndroidJUnit4::class)
class SwipeDeckViewTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var deck: SwipeDeckView

    private val swipes = ArrayList<SwipeDirection>()
    private var taps = 0
    // Sixteen different made-up movies (no poster: these tests are about fingers, not pictures)
    private val movies = (1..16).map { Movie(id = it, title = "Test movie $it", posterPath = null, backdropPath = null) }

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity ->
            deck = SwipeDeckView(activity)
            deck.listener = object : SwipeDeckView.Listener {
                override fun onSwiped(direction: SwipeDirection) {
                    swipes.add(direction)
                }

                override fun onCardTapped() {
                    taps++
                }
            }
            activity.addContentView(
                deck,
                ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
        // Let the deck get its size, then show three cards
        waitUntil { deck.width > 0 && deck.height > 0 }
        onMain { deck.setCards(movies[0], movies[1], movies[2]) }
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    // ---------- gestures ----------

    @Test
    fun fastShortFlickToTheLeftPassesTheCard() {
        // Only 150 px, but in 40 ms: that is a flick
        drag(fromX = 600f, toX = 450f, durationMs = 40, steps = 5)
        waitUntil { swipes.isNotEmpty() }
        assertEquals(listOf(SwipeDirection.PASS), swipes)
    }

    @Test
    fun fastShortFlickToTheRightLikesTheCard() {
        drag(fromX = 400f, toX = 550f, durationMs = 40, steps = 5)
        waitUntil { swipes.isNotEmpty() }
        assertEquals(listOf(SwipeDirection.LIKE), swipes)
    }

    @Test
    fun slowShortDragSpringsBack() {
        // Same 150 px, but over 800 ms and with the finger resting at the end
        drag(fromX = 600f, toX = 450f, durationMs = 800, steps = 40, restMs = 200)
        waitUntil { !deck.isBusy }
        assertTrue("no swipe expected", swipes.isEmpty())
        assertEquals(0f, topCard().translationX, 0.5f)
        assertEquals(0f, topCard().rotation, 0.5f)
    }

    @Test
    fun slowLongDragPastTheLineLikesTheCard() {
        val distance = deck.width * 0.5f // well past 35% of the width
        drag(fromX = 100f, toX = 100f + distance, durationMs = 900, steps = 45, restMs = 200)
        waitUntil { swipes.isNotEmpty() }
        assertEquals(listOf(SwipeDirection.LIKE), swipes)
    }

    @Test
    fun tapWithoutDraggingOpensDetails() {
        drag(fromX = 500f, toX = 503f, durationMs = 60, steps = 3)
        assertEquals(1, taps)
        assertTrue(swipes.isEmpty())
    }

    @Test
    fun touchesAreIgnoredWhileACardIsFlying() {
        var started = false
        onMain { started = deck.swipeTop(SwipeDirection.LIKE) }
        assertTrue(started)

        var secondStarted = true
        var touchAccepted = true
        onMain {
            secondStarted = deck.swipeTop(SwipeDirection.PASS)
            touchAccepted = deck.dispatchTouchEvent(motion(SystemClock.uptimeMillis(), 0L, MotionEvent.ACTION_DOWN, 500f))
        }
        assertFalse("a second button tap must be ignored", secondStarted)
        assertFalse("a touch must be ignored", touchAccepted)

        waitUntil { swipes.isNotEmpty() }
        Thread.sleep(400)
        assertEquals("only ONE swipe must be reported", listOf(SwipeDirection.LIKE), swipes)
    }

    @Test
    fun undoBringsTheCardBackFromTheSideItLeftTo() {
        onMain { deck.swipeTop(SwipeDirection.PASS) }
        waitUntil { swipes.isNotEmpty() }
        // The screen would now show the next three cards
        onMain { deck.setCards(movies[1], movies[2], movies[3]) }

        var undone = false
        var started = false
        onMain { started = deck.bringBack(movies[0], SwipeDirection.PASS) { undone = true } }
        assertTrue(started)
        waitUntil { undone }
        onMain { deck.setCards(movies[0], movies[1], movies[2]) }
        assertFalse(deck.isBusy)
        assertEquals(0f, topCard().translationX, 0.5f)
        assertEquals(1f, topCard().scaleX, 0.001f)
    }

    @Test
    fun manyButtonSwipesInARowAllArriveInOrder() {
        // Like the real screen: after each swipe it shows the next three cards
        val seen = ArrayList<SwipeDirection>()
        deck.listener = object : SwipeDeckView.Listener {
            override fun onSwiped(direction: SwipeDirection) {
                seen.add(direction)
                val n = seen.size
                deck.setCards(movies[n], movies[n + 1], movies.getOrNull(n + 2))
            }

            override fun onCardTapped() = Unit
        }
        val wanted = listOf(
            SwipeDirection.LIKE, SwipeDirection.PASS, SwipeDirection.PASS, SwipeDirection.LIKE, SwipeDirection.LIKE
        )
        for (direction in wanted) {
            // Tap "as fast as possible": taps that arrive while busy are simply refused
            var started = false
            val deadline = SystemClock.uptimeMillis() + 3000
            while (!started && SystemClock.uptimeMillis() < deadline) {
                onMain { started = deck.swipeTop(direction) }
                if (!started) Thread.sleep(10)
            }
            assertTrue("swipe did not start", started)
        }
        waitUntil { seen.size == wanted.size }
        assertEquals(wanted, seen)
    }

    // ---------- screen readers ----------

    @Test
    fun theDeckReadsOutTheMovieOnTop() {
        var spoken: CharSequence? = null
        onMain { spoken = deck.contentDescription }
        assertEquals("Test movie 1", spoken)
    }

    @Test
    fun likeAndPassAreOfferedAsAccessibilityActionsAndSwipeTheTopCard() {
        // TalkBack lists these in its actions menu, so a card can be swiped without a finger gesture
        var likeId = 0
        var passId = 0
        onMain {
            val actions = deck.createAccessibilityNodeInfo().actionList
            val like = deck.context.getString(R.string.swipe_popcorn)
            val pass = deck.context.getString(R.string.swipe_pass)
            likeId = actions.first { it.label == like }.id
            passId = actions.first { it.label == pass }.id
        }

        var accepted = false
        onMain { accepted = deck.performAccessibilityAction(likeId, null) }
        assertTrue("the Popcorn (swipe right) action must be accepted", accepted)
        waitUntil { swipes.size == 1 }

        onMain { deck.setCards(movies[1], movies[2], movies[3]) } // the screen shows the next cards after a swipe
        waitUntil { !deck.isBusy }
        onMain { accepted = deck.performAccessibilityAction(passId, null) }
        assertTrue("the Pass action must be accepted", accepted)
        waitUntil { swipes.size == 2 }

        assertEquals(listOf(SwipeDirection.LIKE, SwipeDirection.PASS), swipes)
    }

    // ---------- helpers ----------

    private fun topCard(): SwipeCardView {
        // The card drawn last is the top card
        return deck.getChildAt(deck.childCount - 1) as SwipeCardView
    }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun waitUntil(timeoutMs: Long = 4000, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(20)
        }
        assertTrue("timed out waiting", condition())
    }

    private fun motion(downTime: Long, offsetMs: Long, action: Int, x: Float): MotionEvent =
        MotionEvent.obtain(downTime, downTime + offsetMs, action, x, 800f, 0)

    /**
     * Sends DOWN, [steps] MOVEs spread evenly over [durationMs], then UP. The timestamps inside the events are
     * exact (we do not wait between them), which is what VelocityTracker reads. [restMs] = the finger rests
     * at the end for that long before it lifts.
     */
    private fun drag(fromX: Float, toX: Float, durationMs: Long, steps: Int, restMs: Long = 0) {
        val downTime = SystemClock.uptimeMillis()
        onMain {
            deck.dispatchTouchEvent(motion(downTime, 0, MotionEvent.ACTION_DOWN, fromX))
            for (i in 1..steps) {
                val t = durationMs * i / steps
                val x = fromX + (toX - fromX) * i / steps
                deck.dispatchTouchEvent(motion(downTime, t, MotionEvent.ACTION_MOVE, x))
            }
            deck.dispatchTouchEvent(motion(downTime, durationMs + restMs, MotionEvent.ACTION_UP, toX))
        }
    }
}
