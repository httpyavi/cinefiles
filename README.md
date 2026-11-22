# CineFiles

A premium movie-discovery app for Android (student project).
Journey: Home → Mood Selection → Preferences → Swipe Movies → Your Picks → My Collection.

**Stack:** Kotlin, XML layouts + ViewBinding, single Activity + Navigation Component, TMDB API with Retrofit/Gson/OkHttp, Glide, Room (KSP), ViewModel + LiveData. No Compose, no DI framework.

## Run it

1. Open the project in Android Studio and press Run, or use `./gradlew assembleDebug`.
2. To load real movie data later, add your TMDB **read access token** to `local.properties` (next to `sdk.dir`):

   ```
   TMDB_READ_TOKEN=your_token_here
   ```

   `local.properties` is git-ignored, so the token is never committed. It is read in `app/build.gradle.kts` and available in code as `BuildConfig.TMDB_READ_TOKEN`. If it is missing the app still starts (it is just an empty string). Note that the token is packed inside the APK, so do not share your debug APK publicly.

(Batch 2's Home screen uses sample movies, so it works without a token. The sample posters are downloaded from TMDB's public image server, so the phone needs internet to show them.)

## Project layout

```
app/src/main/java/com/example/cinefiles/
  CineFilesApp.kt      Application class (creates the database + API once, in later batches)
  MainActivity.kt      The only Activity; hosts the Navigation Component and the bottom tab bar
  ui/                  Screens (Fragments) and reusable views
    home/              HomeFragment, HomeViewModel, HomeState, PosterRowAdapter
    mood/              MoodFragment, MoodFaceView (the animated face), MoodSliderView, MoodSpectrum, PreferencesFragment, WatchFlowViewModel (the shared flow state)
    swipe/             SwipeFragment, SwipeViewModel, SwipeDeckView (touch + animation), SwipeCardView (one card)
    profile/           ProfileFragment, ProfileViewModel, AchievementAdapter
    components/        PosterView, StepIndicatorView, AmbientGlowView (the colored light behind a screen), AchievementBadgeView
    temp/              TEMP stand-in screens
    gallery/           TEMP design gallery
  data/                Movie, AchievementTracker, MovieRepository, Retrofit API, Room database (later batches); TEMP SampleData
  logic/               Plain Kotlin rules (Greeting, Moods, MoodSelection, Preferences, SwipeDeck, SwipeGesture, Discovery, Achievements, PosterColor). Only resource ids, no screens.
  util/                Small helpers: ViewExtensions, NavigationExtensions, ImageExtensions, ImageUrls, PosterTint, Event, TestMode (TEMP)
app/src/main/res/
  values/              colors, dimens, styles, themes, strings
  drawable/            icons (ic_*), scrim, shapes
  layout/              screens and reusable layouts (view_*, item_*)
  menu/                bottom_nav.xml (the four tabs)
  navigation/          nav_graph.xml (the map of all screens)
  anim/ animator/      the fade-through screen change, and the "press" shrink
```

Fragments only show the UI and forward clicks. ViewModels hold screen state. One `MovieRepository` talks to TMDB and Room.

## How navigation works

- `res/navigation/nav_graph.xml` lists every screen. Home is the start.
- Four screens are **tabs**: `homeFragment`, `discoverFragment`, `collectionFragment`, `profileFragment`. Their ids must match the items in `res/menu/bottom_nav.xml`, and the list `TAB_SCREEN_IDS` in `MainActivity`. The bar slides away on every other screen.
- Never call `navigate(...)` directly. Use the helpers in `util/NavigationExtensions.kt`:
  - `findNavController().openTab(R.id.discoverFragment)` to jump to a tab (each tab keeps its state)
  - `findNavController().openScreen(R.id.moodFragment)` to open a normal screen on top (Back returns)
  - `findNavController().openMovieDetails(movieId)` to open Details
- To add a new screen: add a `<fragment>` to `nav_graph.xml`. To add a tab: also add a menu item and its id to `TAB_SCREEN_IDS`.
- A tab screen reserves room for the floating bar: `android:paddingBottom="@dimen/bottom_nav_height"` on its scrolling area (plus `clipToPadding="false"`).

## The "watch something" flow (Home → Mood → Preferences → Swipe → Picks)

- **One shared state:** every screen of the flow gets the same `WatchFlowViewModel` with `private val watchFlow: WatchFlowViewModel by activityViewModels()`. It holds the picked moods (`selection`), the `preferences` and where the mood slider is. Home calls `watchFlow.reset()` when the red button starts a new flow.
- **Moods live in one file:** `logic/Moods.kt` has the nine moods, their TMDB genre ids and colors. Never copy genre ids anywhere else; call `Moods.genreIdsFor(selection.ids)`. `Moods.spectrum` is the order on the Mood screen's slider. To add or change a mood, edit that file, its strings (`mood_<id>`, `_short`, `_hint`, `_line`), its color (`mood_tint_<id>`), its body shape (`logic/MoodShapes.kt`) and its face pose (`MoodFaceView.poses`).
- **The Mood screen:** drag the slider (`MoodSliderView`) and the face (`MoodFaceView`) changes with it; the light behind (`AmbientGlowView`) takes the mood's color. Tap the face, or the pill under the name, to add the mood to your mix. The character is drawn with flat, solid Canvas shapes (no outlines): each mood has its own body silhouette (`MoodShapes`: 72 points) and one list of face numbers (a "pose": eye shape, brows, mouth, blush, tears...). Both are blended between two moods while you drag, so the body flows from one shape into the next and nothing is swapped.
- **Selection rules** (max three, "Surprise Me" stands alone) are in `logic/MoodSelection.kt` and are unit-tested. The ViewModel only calls them.
- **Preferences** are the option lists in `logic/Preferences.kt`; the screen builds its chips from them.

## The swipe deck (Batch 4, and the design pass)

- **Who does what:** `SwipeFragment` only shows things and forwards clicks. `SwipeViewModel` holds the state (a `SwipeDeck`). `logic/SwipeDeck.kt` is the plain-Kotlin deck: cards plus a history of swipes, and the position, the liked list and Undo are all worked out from that history, so they can never disagree. `logic/SwipeGesture.kt` is the rule for "does this drag count as a swipe?" (past 35% of the card width, or a flick faster than 600 dp/s). Both are unit-tested.
- **Which movies come up:** `logic/Discovery.kt`. The deck is a MIX of five kinds (what fits your mood, top rated, hidden gems, trending, popular), taken in turns, each movie once, skipping movies already swiped. If your mood and preferences fit too few movies, the search is loosened one step at a time (language, length, release period, rating, genres) and those cards are labelled "Something different". Today the questions are answered by `SampleData.discover`; in Batch 5 `SwipeViewModel.nextPicks` sends the same `DiscoveryRequest`s to `MovieRepository`.
- **`SwipeDeckView`** owns three reusable `SwipeCardView`s (top, next, and one hidden behind them) and does the touch handling and the animations with only `MotionEvent`, `VelocityTracker` and `ViewPropertyAnimator`. It never knows the movies: the screen calls `setCards(top, second, third)` after every change.
- **The light behind the cards:** `AmbientGlowView` takes the color of the poster on top (`util/PosterTint.kt` + `logic/PosterColor.kt`: a tiny copy of the poster is loaded through Glide and its pixels are averaged; no library), leans red while a card is dragged toward "like" and cool slate toward "pass".
- **Like / pass feedback:** while a card is dragged, `SwipeCardView.setFeedback(like, pass)` fades in a see-through red (right) or slate (left) veil and grows a heart or X in the middle, both following the distance; at rest the poster is completely clear.
- **The buttons and the finger do the same thing:** `swipeTop(direction)` flies the top card away; when it lands the deck calls `listener.onSwiped(...)`, the ViewModel updates, and the screen calls `setCards` again. Undo is `bringBack(...)`.
- **While a card is moving, touches and taps are ignored** (`isBusy`), so quick tapping can never skip or double-count a card.
- **Tuning the feel:** the numbers (scale of the card behind, flight time, overshoot, rotation) are constants at the bottom of `SwipeDeckView.kt`; the fling speed and card offset are in `dimens.xml` (`swipe_*`).
- **Testing gestures:** `SwipeDeckViewTest` (in `androidTest`) sends real `MotionEvent`s with exact timestamps. `adb shell input swipe` cannot be used for flicks: on emulators it gives too few samples for a speed.

## Achievements

- **The rules** are in `logic/Achievements.kt` (nine badges, each with a goal) and work from one summary, `AchievementStats`. `data/AchievementTracker.kt` updates it after every swipe (and every Undo, so liking, undoing and liking again does not count twice), remembers which badges are earned (they are never taken back) and says which ones a swipe just earned. There is one tracker for the app: `(application as CineFilesApp).achievements`.
- **Where you see them:** a slim glass banner slides in on the swipe screen for a moment when a badge is earned (tap it to send it away), and the Profile tab shows all badges with progress rings, a "Next up" panel, and a shine animation the first time you look at a newly earned badge.
- **Adding a badge:** add an id and a goal in `Achievements.all`, a counter in `AchievementStats` if it needs one (and in `AchievementTracker.read/write`), its name and description strings, and an icon in `res/drawable`.

## Design system cheat sheet

Always reuse these. Never type a color, size or text directly in a layout.

| Need | Use |
|---|---|
| Colors | `@color/background`, `surface`, `elevated`, `gold`, `on_gold`, `text_primary`, `text_secondary`, `like_green`, `pass_red` (see `values/colors.xml`) |
| Spacing | `@dimen/space_4 … space_48` (4 dp grid), screen sides: `@dimen/screen_margin` (20 dp) |
| Corner radius | `@dimen/radius_small/medium/large/xlarge/pill` = 8 / 12 / 16 / 24 dp / pill. Images: `app:shapeAppearanceOverlay="@style/ShapeAppearance.CineFiles.Medium"` (or `.Large`, `.Small`) |
| Text | `@style/TextAppearance.CineFiles.Display` (40), `.LargeTitle` (32), `.Title` (24), `.Headline` (18), `.Body` (16), `.Callout` (14, gray), `.Caption` (12, gray), `.Eyebrow` (11, spaced capitals: put it above a title). Controls: `.Button` (16), `.Label` (14) |
| Buttons | `@style/Widget.CineFiles.Button.Primary` (red with a soft glow, one per screen), `.Tonal` (glass), `.Text`. Do not set `cornerRadius`: Material buttons are already pills, and an absolute radius turns the glow into a rectangle. A container of a glowing button needs `clipToPadding="false"`. |
| Round icon button | `@style/Widget.CineFiles.RoundActionButton` (glass) + `layout_width/height=@dimen/round_button_size`, `app:icon`, `app:iconTint`, **and a `contentDescription`**. Variants: `.Large.Like` (red), `.Large.Pass` (slate icon) |
| Chip | `@style/Widget.CineFiles.Chip` (inside a `ChipGroup`): glass, with a red edge when selected |
| Card / tile | `@style/Widget.CineFiles.Card` (a quiet surface block, use sparingly); `.Card.Glass`. Change the corner with `app:cardCornerRadius`. |
| Step indicator | `<com.example.cinefiles.ui.components.StepIndicatorView app:currentStep="1" />` |
| "No" wiggle | `view.shake()` (from `util/ViewExtensions.kt`) |
| Poster | `<com.example.cinefiles.ui.components.PosterView>` ; set `badge = PosterBadge.LIKED / WATCHLIST / WATCHED` or `null`. In a horizontal row use `PosterRowAdapter` (`item_poster_row.xml`). |
| Loading a picture | `imageView.loadImage(ImageUrls.poster(path))` (or `ImageUrls.backdrop(path)`): placeholder, fade-in and size are handled |
| Image scrim | `@drawable/scrim_bottom` in a view stacked above the image (same corner shape) |
| Icons | `@drawable/ic_home`, `ic_discover`, `ic_bookmark`, `ic_profile`, `ic_heart`, `ic_check`, `ic_star` (+ `_filled` versions for selected states), `ic_search`, `ic_close`, `ic_back`, `ic_add`, `ic_undo`, `ic_info`, `ic_play`, `ic_filter`, and the badge icons `ic_film`, `ic_moon`, `ic_layers`, `ic_sparkle`, `ic_drop`, `ic_heart_broken`. Tint with `app:tint`. |
| Screen states | `view_state_empty` (change icon / texts / button from code), `view_state_error` (set what Retry does), `view_state_loading` (full-screen placeholders), `view_row_loading` (placeholders for a poster row) |
| Press animation | `android:stateListAnimator="@animator/press_scale"` (already built into buttons, chips, tiles and `PosterView`) |
| Edge-to-edge | call `rootView.applySystemBarsPadding()` (from `util/ViewExtensions.kt`) on a screen's root |

Red means "primary action, selected or liked": use it for the one thing that matters on a screen. Depth comes from pictures, light (`AmbientGlowView`) and glass, not from borders and boxes.

### Changing the font

The font is named in only two styles in `values/styles.xml`: `TextAppearance.CineFiles.Regular` and `.Medium`. Today they use Android's built-in Roboto. To use Inter: put the Inter files in `res/font/` and change the two `android:fontFamily` lines to point at them (for example `@font/inter_regular`, `@font/inter_medium`).

## Status

- **Batch 1 (done, approved):** project setup, design system, design gallery (temporary).
- **Batch 2 (done, approved):** bottom navigation, Home screen, stand-in screens.
- **Batch 3 (done, approved):** Mood screen, Preferences screen, shared `WatchFlowViewModel`.
- **Batch 4 (done, committed):** Tinder-style swipe deck (gestures, buttons, Undo, end-of-deck states) on sample cards.
- **Design pass (built, waiting for review):** new look (red accent, glass, light), animated mood face + slider, discovery mix, poster-colored light, achievements and a real Profile. Batch 5 (TMDB) has not started.
- See `TEMP.md` for everything that is temporary.
