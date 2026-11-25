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
    detail/            DetailFragment, DetailViewModel, DetailState (Movie Details), CastAdapter (the Cast row),
                       PosterViewerFragment (the full-screen poster)
    person/            PersonFragment, PersonViewModel, PersonState, PersonHeaderAdapter (an actor, director or crew member + their movies, one tab per role)
    diary/             DiaryListFragment + DiaryListViewModel + DiaryEntryAdapter (My Diary), DiaryEditorFragment + DiaryEditorViewModel +
                       DiaryBlockAdapter + DiaryHeaderAdapter (the article-style editor), RichText + RichEditText (bold, italic), DiaryStyleSheet,
                       DiaryStillsSheet, DiaryStyleExtensions (fonts and colors on views)
    profile/           (also) FavoritesAdapter (the 4 places, drag to reorder) and FavoritePickerSheet (watched movies only)
    components/        PosterAdapter (every poster row and grid), PosterView, PopcornRatingView (the half-popcorn rating row), SortSheet (the shared Sort bottom sheet), ZoomImageView
                       (pinch / drag / double-tap picture), StepIndicatorView, AmbientGlowView, AchievementBadgeView
  data/                Movie (also the Room table), MovieDao, DiaryEntry + DiaryDao (the diary table), AppDatabase + Migrations, Converters,
                       CollectionCounts, MovieRepository, MoviePage, TmdbApi (Retrofit), DiscoverQuery, ApiError, MovieDetails (cast, crew,
                       facts), PersonDetails, PicksPool, DiaryImageStore (private copies of gallery pictures), dto/ (TMDB's JSON shapes),
                       AchievementTracker and UserSettings (small SharedPreferences files)
  logic/               Plain Kotlin rules, no screens: Moods, MoodSelection, MoodShapes, Preferences, Discovery, SwipeDeck, SwipeGesture,
                       RecommendationEngine, Credits, PersonRoles, WatchRules, PopcornRating, MovieSorter, Languages, DiaryBlocks, DiaryStyles, CollectionRules,
                       DiscoverRules, Profile, Achievements, Genres, Featured, Greeting, PageCounter, Pagination, PosterColor
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
  - `openPerson(personId)`: open the Person screen (an actor, director or crew member). Works the same way: a different person stacks, the same one twice is ignored.
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
- **Preferences** (release period, minimum rating, length, language) are in `logic/Preferences.kt`. The first three are option lists (the screen builds two-line, equal-height cards from
  them). **The language is a search field with live suggestions, not four chips:** type "E" and English comes first; any language of `logic/Languages.kt` (about fifty, with TMDB's
  codes: Korean `ko`, Filipino `tl`, Mandarin `zh`, Cantonese `cn`...) works, aliases too ("Tagalog" finds Filipino), the x or an empty field means "Any language".
  `ui/mood/LanguageAdapter` filters with `Languages.search`. `Preferences.language` is the TMDB code (null = any).
- **These choices stay inside the flow.** They only shape the swipe deck and Your Picks. The Discover tab never reads them: `DiscoverViewModel` and `MovieRepository.browsePage`
  take no mood and no preferences (an earlier version copied them into Discover, so choosing Korean in "Let's watch something" changed the Discover lists; a test now guards it).
- **The swipe deck** is a MIX (`logic/Discovery.kt`): what fits your mood, top rated, hidden gems, trending, popular, taken in turns, each
  movie once, skipping every movie you already have a state for (liked, passed, on the watchlist or **watched**: `MovieDao.idsWithState`). If mood + preferences fit too few movies the search is loosened step by step
  (language, length, release period, rating, then genres) and those cards say "Something different". `SwipeViewModel` sends the questions to
  `MovieRepository.moviesFor` (TMDB `/discover`, `/trending`, `/popular`); `data/DiscoverQuery.kt` turns them into TMDB parameters. When 5 cards
  are left, more are fetched quietly. `logic/SwipeDeck.kt` is the plain-Kotlin deck (position, liked list and Undo are worked out from one
  history); `logic/SwipeGesture.kt` decides what counts as a swipe (35% of the width, or a flick faster than 600 dp/s). `SwipeDeckView` does the
  touch and animation with only `MotionEvent` and `ViewPropertyAnimator`. "Popcorn" (swipe right) and Pass are also offered as **accessibility actions** on the deck.
- **Swipe right is a popcorn, not a heart** (`ic_popcorn`): on the right-hand button, in the "Picks (n)" pill and in the big icon that grows on a card dragged
  to the right, so it is never mistaken for the Likes heart of Details and My Collection. What swiping right does has not changed.
- **The swipe deck is session-only:** swiping right or left only shapes this sitting's picks (`SwipeViewModel`); nothing is written to Room. A movie becomes
  Liked, a favorite or rated only from its Details (or My Collection), and only once it is watched (see the next section).

## The watch rule (like, favorite, rate) and the watchlist

- A movie can be on the watchlist, watched, liked, rated and a favorite. **A movie is on the watchlist OR watched, never both** (marking watched takes it
  off the watchlist; adding it to the watchlist un-watches it).
- **THE WATCH RULE (`logic/WatchRules.kt`): a movie can only be liked, put in the 4 Favorites, rated with popcorn or given a NEW diary page while it is WATCHED.** You cannot love,
  rank, score or review what you have not seen. The watchlist counts as not watched. (A diary page that was already written stays when the movie is un-watched: it is the user's own
  writing, and My Diary still lists it. On Details the diary panel is dimmed and says why; the diary list's "New page" and the editor check the saved copy too.)
  - `MovieRepository.like`, `toggleFavorite` / `addFavorite` and `setUserRating` check the SAVED copy of the movie (not the screen's copy) and refuse:
    `like` and `setUserRating` return `false`, the favorites return `FavoriteResult.NOT_WATCHED`. Refused actions change nothing. Clearing a rating is always allowed.
  - **Rating does not mark the movie watched for you** (we chose "block with a hint" over "do it silently", the same answer as Like and Favorite).
  - **Un-watching clears them:** `MovieDao.unmarkWatched` and `addToWatchlist` clear the like (a pass stays), the rating and the favorite in the SAME SQL statement
    (`CLEAR_WATCH_DEPENDENTS`), so the table can never hold a liked, rated or favorited unwatched movie. The Snackbar says what was cleared and **Undo**
    (`MovieRepository.restoreState`, which restores every state column, but never past the limit of 4 favorites) puts it all back.
  - **Feedback:** on Details the Like, Favorite and the popcorn row are dimmed on an unwatched movie. Tapping one shakes it (`View.shake()`) and a Snackbar
    says "Mark as watched first before liking" / "Watch this movie before adding it to Favorites" / "Mark as watched first before rating". My Collection's
    long-press menu only offers Like on a watched movie. The Profile's favorites picker only lists watched movies.
- `canLike` / `canFavorite` / `canRate` are plain Kotlin and tested in `WatchRulesTest`; the whole behaviour (including every pair and triple of actions) in `WatchRulesRepositoryTest`.

## Your Picks (the recommendation rules)

No AI, no server: a few small rules in `logic/RecommendationEngine.kt`, which knows nothing about TMDB or screens. Your **taste** is the moods you chose,
the movies you swiped right (a popcorn) and the movies you passed.

1. **Genre profile:** every genre of a chosen mood +1, of a swiped-right movie +2, of a passed movie −1. Negative totals count as 0, then everything is
   divided by the biggest.
2. **Score** of a candidate, from 0 to 1 (`ScoreBreakdown` shows the parts):
   `0.40 × genre match + 0.35 × quality + 0.15 × credibility + 0.10 × era and language − pass penalty`.
   - *Genre match*: cosine similarity between the candidate's genres and the profile (a neutral 0.5 when there is no profile).
   - *Quality*: IMDb's Bayesian weighted rating `(v / (v + m)) × R + (m / (v + m)) × C` (m = 500, C = 6.8, v = votes, R = rating), 5.0–9.0 mapped onto 0–1.
     A rating from few voters is pulled toward the average movie, so acclaimed, well-voted movies beat obscure or noisy ones.
   - *Credibility*: the number of votes on a log scale (20,000 votes = 1).
   - *Era and language*: the candidate's decade (same 1, next 0.5) and original language against your swiped-right movies; 0.5 when you swiped none.
   - *Pass penalty*: when you passed a genre at least twice more often than you liked it, movies whose FIRST genre is that one lose 0.10 to 0.20.
3. **The list:** best scores first, top 10, at most 3 whose first genre is the same. Match percent = score × 100. Equal scores are settled by
   rating, then movie id, so the order never changes by chance. Each pick says why: "Similar to <movie>" (TMDB listed it as similar, or it shares most genres),
   "Top-rated <mood> pick" or "Matches your <mood> mood".

**Candidates** (`MovieRepository.picksCandidates`, returns a `PicksPool`): TMDB `/discover` for the three best genres of the profile joined with `|` and your
preferences, asked twice (`vote_average.desc` and `popularity.desc`, pages 1 and 2) with a **quality floor** of a rating of 6.5 and 500 votes, plus
`/movie/{id}/similar` for up to 3 of your latest swiped-right movies (kept when rating ≥ 6.5, votes ≥ 300 and they fit your preferences). Movies that already have a
state in Room, movies you swiped in this sitting and movies without a poster are never candidates. Only when fewer than 10 are found does the floor drop to 6.0 and 200 votes
(Filipino keeps a tiny vote limit: few people vote on those), and if there are still fewer than 10, the preferences are forgotten too (the genres stay).
`WatchFlowViewModel.buildPicks` runs it, with a short "building your picks" moment (about 1 s, skipped when animations are off). The page: Top Match card,
"You liked", "More for you" (three columns, each poster with its match percent and a bookmark), and a quick-view sheet (story, reason, Details / Add to Watchlist /
Mark as watched). Picks live only while the flow is open.

## Discover

Search (2+ letters, 400 ms after the last key, `/search/movie`), the Popular / Trending / Top rated chips and the genre chips (`logic/Genres.kt`),
over a three-column poster grid with live status badges. One request runs at a time; a new search or chip cancels the old one; the next page loads
near the end of the list (`PaginationScrollListener`) until the last page, without repeats. With a genre and no search, `/discover` answers; while
searching, a genre chip only filters what has loaded (a small line says so). Rules: `logic/DiscoverRules.kt` and `ui/discover/DiscoverState.kt`.
Home's search icon opens the tab with the keyboard ready. **Discover is independent of "Let's watch something":** the mood, release period, rating, length and language chosen
there never filter these lists.

## My Collection and Movie Details

- **One table, `movies`** (Room): `data/Movie.kt` is both the class every screen uses and the table row. The user's state sits in the same row:
  `reaction`, `reactedAt`, `inWatchlist`, `watchlistAddedAt`, `isWatched`, `watchedAt`, the popcorn rating `userRating` (column `personalRating`) + `ratedAt`,
  and the favorite `isFavorite`, `favoritedAt`, `favoriteOrder` (the place 0..3, saved when the user drags the favorites).
- **THE rule of the DAO:** a movie is saved with `OnConflictStrategy.IGNORE`, never `REPLACE` (replacing a row would wipe the user's state). After
  that only small targeted `UPDATE` queries change the state. "Undo" everywhere is `restoreState`.
- **Collection:** `Liked · Watchlist · Watched` with live counts over a three-column grid. A poster wears a badge (`badgeStatusFor`) and, when the user rated it,
  a small popcorn rating badge in the bottom-left corner (`PosterView.rating`). Tap = Details, long-press = a menu that depends on the tab (`actionsFor`),
  and every action offers **Undo**.
- **Sort (My Collection and Person):** the round button next to the title (Collection) or at the top right (Person) opens `SortSheet`, ONE bottom sheet for both screens; the
  sorting is `logic/MovieSorter.kt` (tested). Fields: **When added** (Newest / Earliest: the time the movie got into the Liked / Watchlist / Watched tab on screen; Collection only),
  **Popularity** (TMDB's own order; Person only), **Film name** (A to Z / Z to A, ignoring a leading The / A / An), **Release date**, **Your rating** (Highest / Lowest, from `userRating`),
  **Film length** and **TMDB rating**. A movie without the value (unrated, runtime not known) is always last, ties are settled by name then id, and a field no movie in the list
  has a value for is not offered. The active sort has a red edge in the sheet and the button turns red when it is not the usual order; "Reset" goes back. A person's movies wear the
  user's saved state, so "Your rating" works there too. The sort applies to the tab on screen (and to the others after it).
- **Details:** backdrop, a **tappable poster**, rating · year · length, genres, "Directed by …", **Add to Watchlist**, Like, Watched and Favorite toggles, **Your rating**
  (the popcorn row), tagline and story, trailer (opens YouTube), the **Movie Diary** panel, the **Cast** row, the **Crew** table, a few facts (status, original language,
  country) and Similar movies. It shows what is known at once and works offline for a saved movie (`ui/detail/DetailState.kt`); the people and the facts only come with
  TMDB's details answer (`/movie/{id}?append_to_response=credits,videos`), so those sections appear when it arrives.
- **Your rating (`ui/components/PopcornRatingView`):** five popcorn buckets (`ic_popcorn_empty / half / full`), 0.5 to 5.0 in half steps, like Letterboxd. Tap the left half of a
  bucket for a half, the right half for the whole one; tap the rating you already have to clear it; drag across the row and it is set where the finger lifts (dragging off the left
  end clears it). The math is `logic/PopcornRating.kt` (tested). While the movie is not watched the row is locked and dimmed. Screen readers read "Your rating: 3.5 out of 5"
  and can raise or lower it by half a bucket. It is saved by `MovieRepository.setUserRating`.
- **The Crew table:** one row per key role (Director, Writers, Producers, Cinematography, Music: `logic/Credits.kt`, `Credits.preview`), at most 4 rows with 2 names each, every
  name tappable (opens Person); "+N more" and **See all** open `CrewSheet`, a bottom sheet with every group (also Editing, Production design, Costume design) and each person's job.
- **The poster** opens `PosterViewerFragment`, a full-screen dialog: dark backdrop, close button, the w780 poster (the list's small one shows at once while it loads),
  pinch to zoom (`ZoomImageView`, up to 4×), drag, double-tap to zoom, tap anywhere to close. It fades in and out unless the phone's animations are off.
- **Person** (`ui/person/`, `TmdbApi.person` = `/person/{id}?append_to_response=movie_credits`): tap a director, an actor or a crew name. Photo, name, what they are
  known for, biography (3 lines, "More") and their movies in the three-column poster grid, one poster per movie, most popular first. **One tab per kind of work the person
  really has**, with its count: `Actor (24)`, `Director (8)`, `Writer`, `Producer`, `Cinematographer`, `Composer`, `Editor` and `Crew` for every other job
  (`logic/PersonRoles.kt`, which reuses the job lists of `logic/Credits.kt`). Only roles with at least one movie get a tab; the person's primary role (TMDB's "known for
  department") comes first, then the most movies, Crew last; one role needs no tabs. Tapping a poster opens that movie's Details, and so on.

## Profile

The name, the 4 favorites, the numbers, your taste, quick links (Liked, Watchlist, Watched, Movie Diary), About and Clear my data.
- **Favorites:** four places (`FavoritesAdapter`, a RecyclerView). Tap an empty place to pick from the movies you have WATCHED (`FavoritePickerSheet`); tap the x to remove one
  (with Undo). **Press and hold a poster, then drag it** to another place: it lifts (a small scale and a haptic tick) and the new order is saved when it is dropped
  (`favoriteOrder`, one SQL statement: `MovieDao.setFavoriteOrder`), so it survives a restart. TalkBack gets "Move earlier / Move later" instead of the drag.
- **Name:** the avatar shows its first letter. **Edit** opens a small dialog; the name is saved in SharedPreferences (`data/UserSettings.kt`, rules in
  `logic/Profile.kt`: trimmed, one space between words, at most 20 letters, never blank) and shows in Home's greeting.
- **Your taste:** the genre most of your liked movies have and how many movies you like, read live from Room ("—" while nothing is liked).
  The three numbers above it (Discovered / Liked / Passed) are lifetime swipe counts from the achievements, so they can be bigger than the list.
- **Achievements:** nine badges (`logic/Achievements.kt`) kept by `data/AchievementTracker.kt`. A badge earned once stays earned.
- **About CineFiles:** the app version and the TMDB credit with the sentence TMDB's terms ask for.
- **Clear my data** (red, asks first): deletes every saved movie and state and every diary page (Room), the diary pictures kept in the app, the achievements and the name
  (both SharedPreferences files), ends any journey in progress and goes Home.

## Movie Diary

A private journal, only on the phone. Open it from Details ("Write in Diary" for a new page, "Open Diary" for that movie's pages) or from the Profile ("Movie Diary", every page).
- **Table `diary_entries`** (`data/DiaryEntry.kt`): `movieId`, `movieTitle`, `posterPath`, `createdAt`, `updatedAt`, `ratingSnapshot` (the popcorn rating when the page was saved),
  `fontStyle`, `backgroundTheme` and `blocksJson`.
- **A page is a list of blocks** (`logic/DiaryBlocks.kt`, Gson): a TEXT block (a run of paragraphs) or an IMAGE block. The JSON only holds words and picture ADDRESSES, never
  picture bytes: a TMDB picture is its TMDB path; a gallery picture is the NAME of a small JPEG copy in the app's private storage (`data/DiaryImageStore`, at most 1600 px, turned
  upright), so it never breaks when the gallery changes and no permission has to be kept. A text block also keeps `html` (it is rich text) and `align` (left / center / right);
  a note from an older version has neither and opens as plain left-aligned text.
- **The editor reads and writes like a Substack article, with NO boxes** (`DiaryEditorFragment`): the movie is the headline (`DiaryHeaderAdapter`), then paragraphs and pictures flow
  down one continuous page (`DiaryBlockAdapter`: paragraphs are bare text on the page, a picture only has a small quiet pill of tools in its corner). The blocks are still there underneath.
  - **Bold and italic** (`ui/diary/RichText.kt`, no library): `StyleSpan`s, saved as HTML with `HtmlCompat.toHtml` and read back with `HtmlCompat.fromHtml`; only bold and italic are ever
    written. With words selected the style is toggled on them; with only a cursor it is toggled for what is typed next.
  - **Left / center / right** alignment is per paragraph run. A strip with Bold, Italic and the three alignments shows above the buttons while a paragraph run has the cursor, and its
    buttons light up for what applies at the cursor.
  - **"Paragraph"** starts a new run at the end; **"Picture"** puts a picture where the cursor is (in the middle of a run it splits the run in two around the picture) and the page always
    ends with a paragraph run, so you can keep writing under a picture. A picture comes from the phone's photo picker (`ActivityResultContracts.PickVisualMedia`) or from the movie's official
    stills (`GET /movie/{id}/images`, `DiaryStillsSheet`). A picture can be dragged (press its handle), moved with its arrows, or deleted (Undo). An empty paragraph run is deleted with Backspace.
  - The page is saved when you leave it, when the app goes to the background and with Save; a NEW page with nothing on it is never saved, and pictures a page no longer uses are deleted.
- **Style** (`DiaryStyleSheet`): four fonts (Sans · Modern, Serif · Editorial, Typewriter, Bold Display) and five dark backgrounds (Midnight Obsidian, Cinema Crimson, Warm Parchment,
  Deep Slate, Vintage Sepia: `logic/DiaryStyles.kt`, colors in `values/colors.xml`). It changes the page live and is remembered per page.
- **My Diary** (`DiaryListFragment`): the pages as cards in their own theme and font, newest first, with the rating they were written with and a bin (asks first). Empty states say what to do.

## Performance, memory and accessibility

- Pictures: only `w185` (profile photos), `w300` (the Diary's still thumbnails), `w342` (rows and grids), `w500` (swipe card) and `w780` (backdrops and the full-screen poster), never `original`
  (`util/ImageUrls.kt`). Glide loads them; recycled list views cancel their old download.
- Every list uses `DiffUtil` (`ListAdapter`). Every Fragment clears its ViewBinding in `onDestroyView`.
- Room, TMDB and the settings files are never touched on the main thread: queries are `suspend`/`Flow`, and `CineFilesApp` makes the shared objects on a
  background thread at start. **StrictMode** is on in debug builds and writes to Logcat (tag `StrictMode`) for disk or network use on the main thread
  and for leaked cursors or Activities; search Logcat for it while using the app.
- Accessibility: every icon button and poster has a content description, tap targets are at least 48 dp, text uses `sp` and the layouts were checked at
  130% font size on a 360 × 640 dp screen, the swipe deck reads out the movie on top and offers "Like" and "Pass" as actions, the mood slider is a
  proper range control, and animations are skipped when the phone's "Remove animations" is on.
  At 130% text the buttons whose label has to fit a fixed width **shrink the text instead of cutting it** (`autoSizeTextType`, never below 9-10 sp): the
  Details actions (`Button.DetailAction`), the Preferences cards (`Button.PreferenceCard`), the Diary editor's "Paragraph" / "Picture" (`Button.Tonal.Fit`)
  and the role column of the Crew table.
- **The keyboard:** the window is edge-to-edge, so `adjustResize` alone does not move anything. `applySystemBarsPadding` (`util/ViewExtensions.kt`) pads the
  bottom by the bigger of the navigation bar and the keyboard, which is what keeps the Diary editor's formatting strip and "Start swiping" above the
  keyboard. Use it on the bottom bar of any new screen that has a text field.

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
| Rating | `PopcornRatingView` (`app:popcornSize`, `app:popcornGap`, `app:interactive="false"` for a read-only badge) |
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

- **No "Double Feature" badge:** the nine badges are about swiping; adding one needs an id and goal in `Achievements.all` and a counter in `AchievementStats`.
- **Database changes:** the database is at **version 3**. `MIGRATION_2_3` (`data/Migrations.kt`) added `movies.favoriteOrder`, replaced the old simple `diary_entries` table with the
  page table (every old note became one text block) and applied THE WATCH RULE to old data: a movie that was liked, rated or a favorite but not marked watched was
  marked **watched** (and left the watchlist), because that is the proof the user saw it. Nothing was deleted. If an entity ever changes again, raise `version` and add a real
  Migration; a database from an unknown version is started over (`fallbackToDestructiveMigration`), which deletes the saved movies, so never rely on it for a known version.
  The rating column keeps its old name `personalRating` (`@ColumnInfo` on `Movie.userRating`), so no rename was needed.
- **Your Picks are not saved:** they live in `WatchFlowViewModel` while the flow is open. The movies you act on (watchlist, watched) are saved as always.
- **"Trending" with a genre** means released since last year, most popular first (TMDB's trending list cannot be filtered by genre).
- **Portrait only** on purpose (the swipe deck, the mood slider and the tall posters are designed for a tall screen).
- **The token is inside the APK** (`BuildConfig`): use a throw-away token for any APK you hand out.
- **Library versions are not upgraded:** lint lists newer versions of AndroidX, Room, Material and the Android Gradle plugin. They were left alone on purpose
  to keep a tested set; upgrade them together and re-run the tests.
- **TMDB credit:** keep the About section's sentence if you ever show the app to anyone outside the project.

## Status

All eight batches are done: design system, tabs and Home, Mood and Preferences, the swipe deck, TMDB, Room + Collection + Details, the Like rule +
Your Picks + Discover, and the Profile, three-column grids, cleanup and polish of this last batch. After them: the cast / crew / Person screen (Part 1), and
Part 2: the watch rule, role tabs on Person, the Crew table, the popcorn rating, draggable favorites and the Movie Diary.
