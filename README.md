# CineFiles

A premium movie-discovery app for Android (student project).
The signature journey: **Home → Mood → Preferences → Swipe movies → Your Picks → My Collection.**

**Stack:** Kotlin, XML layouts + ViewBinding, one Activity + the Navigation Component, TMDB through Retrofit / Gson / OkHttp,
Glide for pictures, Room (with KSP) for saved movies, ViewModel + LiveData for screen state.
**Not used on purpose:** Jetpack Compose, dependency injection, Paging 3, RxJava, Firebase, Lottie, image-color or swipe-card libraries,
any AI/ML, any backend, TMDB's `/recommendations` endpoint. `minSdk` is 24, so `java.time` is not used.

## Run it

1. Open the project in Android Studio and press Run, or use `./gradlew assembleDebug`. It needs a JDK 17 or newer
   (Android Studio's bundled JDK is fine) and an Android SDK; Android Studio writes `sdk.dir` into `local.properties` for you.
2. The app loads real movies from TMDB, so it needs your TMDB **API Read Access Token**: the long one that starts with `eyJ`,
   from your TMDB account's API settings (NOT the short "API Key"). Add it to `local.properties`, next to `sdk.dir`:

   ```
   TMDB_READ_TOKEN=your_token_here
   ```

   `local.properties` is git-ignored, so the token is never committed. `app/build.gradle.kts` reads it and makes it available
   in code as `BuildConfig.TMDB_READ_TOKEN`. Without it the app still starts: screens show a friendly "TMDB key missing"
   message with a Retry button. After changing the token, rebuild. The token is packed inside the APK, so do not share your
   debug APK publicly.
3. The phone needs internet for movie lists. Without it the screens say you are offline and Retry works once you are back online.
   My Collection, Details of a saved movie and the Profile work offline (they only read the database).

## Project layout

```
app/src/main/java/com/example/cinefiles/
  CineFilesApp.kt      Application class: makes the ONE database, repository, achievements and settings (on a background
                       thread at start); turns StrictMode on in debug builds
  MainActivity.kt      The only Activity: hosts the Navigation Component, the floating tab bar and the launch splash
  ui/                  Screens (Fragments + their ViewModels) and reusable views
    home/              HomeFragment, HomeViewModel, HomeState
    discover/          DiscoverFragment, DiscoverViewModel, DiscoverState (search, lists, genre chips, endless grid)
    collection/        CollectionFragment, CollectionViewModel (Liked / Watchlist / Watched)
    profile/           ProfileFragment, ProfileViewModel, AchievementAdapter (name, taste, achievements, About, Clear my data)
    mood/              MoodFragment, MoodFaceView, MoodSliderView, MoodSpectrum, PreferencesFragment, WatchFlowViewModel
    swipe/             SwipeFragment, SwipeViewModel, SwipeDeckView (touch + animation), SwipeCardView (one card)
    picks/             PicksFragment, PicksAdapter, QuickViewSheet, PicksState, PickTexts (Your Picks)
    detail/            DetailFragment, DetailViewModel, DetailState (Movie Details)
    components/        PosterAdapter (every poster row and grid), PosterView, StepIndicatorView, AmbientGlowView, AchievementBadgeView
  data/                Movie (also the Room table), MovieDao, AppDatabase, Converters, CollectionCounts, MovieRepository, MoviePage,
                       TmdbApi (Retrofit), DiscoverQuery, ApiError, MovieDetails, dto/ (TMDB's JSON shapes),
                       AchievementTracker and UserSettings (small SharedPreferences files)
  logic/               Plain Kotlin rules, no screens: Moods, MoodSelection, MoodShapes, Preferences, Discovery, SwipeDeck, SwipeGesture,
                       RecommendationEngine, CollectionRules, DiscoverRules, Profile, Achievements, Genres, Featured, Greeting,
                       PageCounter, Pagination, PosterColor
  util/                Small shared helpers: NavigationExtensions, ViewExtensions, StateViews, SnackbarExtensions, ChipExtensions,
                       MovieTexts, CurrentTime, AnimationSettings, ImageExtensions, ImageUrls, PosterTint, PaginationScrollListener, Event
app/src/main/res/
  values/              colors, dimens, styles, themes, strings, integers
  drawable/            icons (ic_*), scrims and glass shapes (bg_*, scrim_*), the logo
  layout/              screens (fragment_*), list items (item_*), shared pieces (view_*), sheet_quick_view, dialog_edit_name
  menu/                bottom_nav.xml (the four tabs)
  navigation/          nav_graph.xml (the map of all screens)
  anim/ animator/      screen changes (fade-through, slide) and the "press" shrink
app/src/test/          Unit tests that run on the computer (rules, ViewModel state builders, the network stack against a pretend TMDB)
app/src/androidTest/   Tests that need an emulator or phone (the real database, real touch gestures on the swipe deck)
```

Fragments only show the UI and forward clicks. ViewModels (`AndroidViewModel`, so no factories) hold screen state. One
`MovieRepository` talks to TMDB and Room. Rules live in plain Kotlin files in `logic/` and are unit-tested.

## How navigation works

- `res/navigation/nav_graph.xml` lists every screen. Home is the start.
- Four screens are **tabs**: `homeFragment`, `discoverFragment`, `collectionFragment`, `profileFragment`. Their ids must match the
  items in `res/menu/bottom_nav.xml` and the set `TAB_SCREEN_IDS` in `MainActivity`. The bar slides away on every other screen.
- Never call `navigate(...)` directly. Use the helpers in `util/NavigationExtensions.kt`:
  - `openTab(R.id.discoverFragment)`: jump to a tab (each tab keeps its state).
  - `openScreen(R.id.moodFragment)`: open a screen on top (Back returns). The steps of the watch flow pass `slide = true`.
  - `openMovieDetails(movieId)`: open Details. A different movie stacks on top of the current Details; the same movie twice is ignored.
- Motion: tabs and Details fade through, Mood → Preferences → Swipe → Picks slide (250 ms). With the phone's "Remove animations"
  on, every screen change is instant. Press animations (buttons, chips, posters) take 150 ms in and 200 ms out.
- A tab screen reserves room for the floating bar: `android:paddingBottom="@dimen/bottom_nav_height"` on its scrolling area
  plus `clipToPadding="false"`.

## The "watch something" flow

- **One shared state:** every screen of the flow gets the same `WatchFlowViewModel` (`by activityViewModels()`). It holds the picked moods,
  the preferences, the slider position and the built picks. Home calls `reset()` when the red button starts a new flow.
- **Moods** live in one file, `logic/Moods.kt`: nine moods, their TMDB genre ids and colors. Never copy genre ids elsewhere. The
  Mood screen is an animated face and a slider (`MoodFaceView`, `MoodSliderView`, shapes in `logic/MoodShapes.kt`); choosing rules
  (up to three, "Surprise Me" alone) are in `logic/MoodSelection.kt`.
- **Preferences** (release period, minimum rating, length, language) are the option lists in `logic/Preferences.kt`; the screen builds its chips from them.
- **The swipe deck** is a MIX (`logic/Discovery.kt`): what fits your mood, top rated, hidden gems, trending, popular, taken in turns, each
  movie once, skipping every movie you already have a state for. If mood + preferences fit too few movies the search is loosened step by step
  (language, length, release period, rating, then genres) and those cards say "Something different". `SwipeViewModel` sends the questions to
  `MovieRepository.moviesFor` (TMDB `/discover`, `/trending`, `/popular`); `data/DiscoverQuery.kt` turns them into TMDB parameters. When 5 cards
  are left, more are fetched quietly. `logic/SwipeDeck.kt` is the plain-Kotlin deck (position, liked list and Undo are worked out from one
  history); `logic/SwipeGesture.kt` decides what counts as a swipe (35% of the width, or a flick faster than 600 dp/s). `SwipeDeckView` does the
  touch and animation with only `MotionEvent` and `ViewPropertyAnimator`. Like and Pass are also offered as **accessibility actions** on the deck.
- **Saved at once:** every like and pass is written to Room; Undo takes it back.

## The rules of liking and the watchlist

- A movie can be liked, on the watchlist, and watched. **A movie is on the watchlist OR watched, never both** (marking watched takes it off
  the watchlist; adding it to the watchlist un-watches it). Liked is independent of both, **except for one rule:**
- **THE LIKE RULE: a movie that is on the watchlist cannot be liked.** Remove it from the watchlist, or mark it watched, and liking works
  again. A movie liked BEFORE it joined the watchlist keeps its like. The rule is `canLike` in `logic/CollectionRules.kt`;
  `MovieRepository.like(movie)` enforces it (it checks the SAVED copy) and returns `false` when it refuses. Details dims Like, shakes it and says why;
  the Watchlist tab's menu has no Like; a card swiped right on a watchlisted movie is not liked (a Snackbar says why) and is not counted in "Picks (n)".

## Your Picks (the recommendation rules)

No AI, no server: three small rules in `logic/RecommendationEngine.kt`, which knows nothing about TMDB or screens.

1. **Genre profile:** every genre of a chosen mood +1, of a liked movie +1.5, of a passed movie −0.5. Negative totals count as 0, then everything is
   divided by the biggest.
2. **Score** of a candidate = 0.6 × genre match + 0.3 × (rating / 10) + 0.1 × era match (how close its year is to the average year of your likes;
   0.5 when you liked nothing).
3. **The list:** best scores first, top 10, at most 4 whose first genre is the same. Match percent = score × 100. Equal scores are settled by
   rating, then movie id, so the order never changes by chance. Each pick says why: "Similar to <liked movie>" or "Matches your <mood> mood".

**Candidates** (`MovieRepository.picksCandidates`): TMDB `/discover` with the three best genres of the profile joined with `|`, your preferences and at
least 100 votes (pages 1 and 2). Movies that already have a state in Room and movies without a poster are never candidates. If fewer than 10 are
found, it asks once more WITHOUT the preferences. `WatchFlowViewModel.buildPicks` runs it, with a short "building your picks" moment (about 1 s,
skipped when animations are off). The page: Top Match card, "You liked", "More for you" (three columns, each poster with its match percent and a
bookmark), and a quick-view sheet (story, reason, Details / Add to Watchlist / Mark as watched). Picks live only while the flow is open.

## Discover

Search (2+ letters, 400 ms after the last key, `/search/movie`), the Popular / Trending / Top rated chips and the genre chips (`logic/Genres.kt`),
over a three-column poster grid with live status badges. One request runs at a time; a new search or chip cancels the old one; the next page loads
near the end of the list (`PaginationScrollListener`) until the last page, without repeats. With a genre and no search, `/discover` answers; while
searching, a genre chip only filters what has loaded (a small line says so). Rules: `logic/DiscoverRules.kt` and `ui/discover/DiscoverState.kt`.
Home's search icon opens the tab with the keyboard ready.

## My Collection and Movie Details

- **One table, `movies`** (Room): `data/Movie.kt` is both the class every screen uses and the table row. The user's state sits in the same row:
  `reaction`, `reactedAt`, `inWatchlist`, `watchlistAddedAt`, `isWatched`, `watchedAt`.
- **THE rule of the DAO:** a movie is saved with `OnConflictStrategy.IGNORE`, never `REPLACE` (replacing a row would wipe the user's state). After
  that only small targeted `UPDATE` queries change the state. "Undo" everywhere is `restoreState`.
- **Collection:** `Liked · Watchlist · Watched` with live counts over a three-column grid. A poster wears a badge (`badgeStatusFor`). Tap = Details,
  long-press = a menu that depends on the tab (`actionsFor`), and every action offers **Undo**.
- **Details:** backdrop, rating · year · length, genres, **Add to Watchlist**, Like and Watched toggles, story, trailer (opens YouTube) and Similar
  movies. It shows what is known at once and works offline for a saved movie (`ui/detail/DetailState.kt`).

## Profile

The name, the numbers, your taste, the achievements, About and Clear my data.
- **Name:** the avatar shows its first letter. **Edit** opens a small dialog; the name is saved in SharedPreferences (`data/UserSettings.kt`, rules in
  `logic/Profile.kt`: trimmed, one space between words, at most 20 letters, never blank) and shows in Home's greeting.
- **Your taste:** the genre most of your liked movies have and how many movies you like, read live from Room ("—" while nothing is liked).
  The three numbers above it (Discovered / Liked / Passed) are lifetime swipe counts from the achievements, so they can be bigger than the list.
- **Achievements:** nine badges (`logic/Achievements.kt`) kept by `data/AchievementTracker.kt`. A badge earned once stays earned.
- **About CineFiles:** the app version and the TMDB credit with the sentence TMDB's terms ask for.
- **Clear my data** (red, asks first): deletes every saved movie and state (Room), the achievements and the name (both SharedPreferences files),
  ends any journey in progress and goes Home.

## Performance, memory and accessibility

- Pictures: only `w342` (rows and grids), `w500` (swipe card) and `w780` (backdrops), never `original` (`util/ImageUrls.kt`). Glide loads them; recycled
  list views cancel their old download.
- Every list uses `DiffUtil` (`ListAdapter`). Every Fragment clears its ViewBinding in `onDestroyView`.
- Room, TMDB and the settings files are never touched on the main thread: queries are `suspend`/`Flow`, and `CineFilesApp` makes the shared objects on a
  background thread at start. **StrictMode** is on in debug builds and writes to Logcat (tag `StrictMode`) for disk or network use on the main thread
  and for leaked cursors or Activities; search Logcat for it while using the app.
- Accessibility: every icon button and poster has a content description, tap targets are at least 48 dp, text uses `sp` and the layouts were checked at
  130% font size on a 360 × 640 dp screen, the swipe deck reads out the movie on top and offers "Like" and "Pass" as actions, the mood slider is a
  proper range control, and animations are skipped when the phone's "Remove animations" is on.

## Design system cheat sheet

Always reuse these. Never type a color, size or text directly in a layout. Dark theme only; red means "primary action, selected or liked".

| Need | Use |
|---|---|
| Colors | `@color/background`, `surface`, `elevated`, `accent`, `accent_soft`, `accent_deep`, `on_accent`, `star`, `text_primary`, `text_secondary`, `like`, `pass`, and the glass fills `glass_fill`, `glass_fill_strong`, `glass_stroke` (see `values/colors.xml`) |
| Spacing | `@dimen/space_4 … space_40` (4 dp grid); screen sides `@dimen/screen_margin` (20 dp); smallest tap area `@dimen/touch_target` (48 dp) |
| Corner radius | `@dimen/radius_small/medium/large/xlarge/pill`. Images: `app:shapeAppearanceOverlay="@style/ShapeAppearance.CineFiles.Medium"` |
| Text | `@style/TextAppearance.CineFiles.Display`, `.LargeTitle`, `.Title`, `.Headline`, `.Body`, `.Callout`, `.Caption`, `.Eyebrow`, `.Label`, `.Button`, `.Pill` |
| Buttons | `Widget.CineFiles.Button.Primary` (red, one per screen), `.Tonal` (glass), `.Text`. Do not set `cornerRadius`. |
| Round icon button | `Widget.CineFiles.RoundActionButton` (+ `.Large`, `.Large.Like`) with `@dimen/round_button_size`, an `app:icon` and a `contentDescription` |
| Chip | `ChipGroup.addChoiceChip("label")` (util/ChipExtensions.kt) |
| Segmented control | `MaterialButtonToggleGroup` with `Widget.CineFiles.Button.Segment`; `android:saveEnabled="false"` when a ViewModel keeps the selection |
| Card | `Widget.CineFiles.Card`, `.Card.Glass` |
| Posters | `PosterView`; every row or grid uses `PosterAdapter(PosterStyle.ROW or GRID, …)` |
| Pictures | `imageView.loadImage(ImageUrls.poster(path))` |
| Screen states | `view_state_loading`, and `view_state_empty` / `view_state_error` filled with `binding.stateEmpty.show(...)` / `binding.stateError.show(error)` (util/StateViews.kt) |
| Messages | `showMessage`, `showUndo` (util/SnackbarExtensions.kt): same look everywhere, above the tab bar on tab screens |
| Texts about a movie | `movie.ratingText()`, `genresText()`, `yearAndGenres()`, `Int.formatted()` (util/MovieTexts.kt) |
| Edge-to-edge | `view.applySystemBarsPadding(top, bottom)` |

### Changing the font

The font is named in only two styles in `values/styles.xml`: `TextAppearance.CineFiles.Regular` and `.Medium`. Today they use Android's Roboto. To
use another font (Inter, for example): put the font files in `res/font/` and change the two `android:fontFamily` lines to point at them.

### Logo and icons

- The CineFiles logo is `res/drawable/cinefiles_logo.xml`. It is used in the Home header and on the red launch screen; `ic_logo.xml` wraps it for the
  system splash screen and the adaptive launcher icon (`res/mipmap-anydpi-v26/`). Android 7.x (API 24-25) still shows the older
  `res/mipmap-*/ic_launcher*.webp` files: replace those to change the icon there.
- TMDB: the Profile's About section shows a small badge in TMDB's colors (`bg_tmdb_badge.xml`) next to the sentence TMDB's terms ask for. To show
  TMDB's official logo instead, download it from TMDB's brand page, save it as `res/drawable/tmdb_logo.xml` (or a PNG) and replace the `tmdbBadge`
  TextView in `layout/fragment_profile.xml` with an ImageView that uses it.

## Tests

- Unit tests (rules, state builders, ViewModel helpers, the whole network stack against a pretend TMDB made from a plain `ServerSocket`):
  `./gradlew testDebugUnitTest`. No emulator needed.
- Lint: `./gradlew lint` (report in `app/build/reports/lint-results-debug.html`).
- On an emulator or phone: `app/src/androidTest/` tests the REAL database (`MovieDaoTest`, in memory, never touches your saved movies) and sends real
  touch gestures and accessibility actions to the swipe deck (`SwipeDeckViewTest`). `./gradlew connectedDebugAndroidTest` runs them, but Gradle
  uninstalls the app afterwards, which also deletes its saved data. To keep the data, build with `./gradlew assembleDebug assembleDebugAndroidTest`, install
  both APKs with `adb install -t -r`, and run
  `adb shell am instrument -w -e class com.example.cinefiles.data.MovieDaoTest com.example.cinefiles.test/androidx.test.runner.AndroidJUnitRunner`.

## Good to know / postponed

- **Cast on Details** is not shown yet. The data is already loaded (`MovieDetails.cast`, `directors`).
- **No "Double Feature" badge:** the nine badges are about swiping; adding one needs an id and goal in `Achievements.all` and a counter in `AchievementStats`.
- **Database changes:** the table is at version 1. If `Movie` ever changes, raise `version` in `AppDatabase` and add a real Migration; without one Room starts
  over with an empty table (`fallbackToDestructiveMigration`), which deletes the saved movies.
- **Your Picks are not saved:** they live in `WatchFlowViewModel` while the flow is open. The movies you act on (watchlist, watched) are saved as always.
- **"Trending" with a genre** means released since last year, most popular first (TMDB's trending list cannot be filtered by genre).
- **Portrait only** on purpose (the swipe deck, the mood slider and the tall posters are designed for a tall screen).
- **The token is inside the APK** (`BuildConfig`): use a throw-away token for any APK you hand out.
- **Library versions are not upgraded:** lint lists newer versions of AndroidX, Room, Material and the Android Gradle plugin. They were left alone on purpose
  to keep a tested set; upgrade them together and re-run the tests.
- **TMDB credit:** keep the About section's sentence if you ever show the app to anyone outside the project.

## Status

All eight batches are done: design system, tabs and Home, Mood and Preferences, the swipe deck, TMDB, Room + Collection + Details, the Like rule +
Your Picks + Discover, and the Profile, three-column grids, cleanup and polish of this last batch.
