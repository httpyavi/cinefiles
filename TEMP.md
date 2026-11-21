# TEMP.md: temporary things that must be replaced or removed

Everything below is marked `// TEMP:` (Kotlin) or `<!-- TEMP: -->` (XML) in the code, so you can also find it by searching the project for `TEMP:`.

## From Batch 1

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Design gallery screen (class) | `ui/gallery/DesignGalleryFragment.kt` | Lets you review the design system on a phone. It is still reachable: Profile tab -> "Open design gallery". | Removed in **Batch 8** |
| Design gallery layouts | `res/layout/fragment_design_gallery.xml`, `item_gallery_swatch.xml`, `item_gallery_icon.xml` | Same as above. | Removed in **Batch 8** |
| Design gallery texts | `res/values/temp_gallery_strings.xml` | Same as above. | Removed in **Batch 8** |
| Fake poster / backdrop pictures and swatch shape | `res/drawable/temp_sample_poster_1..4.xml`, `temp_sample_backdrop.xml`, `temp_bg_swatch.xml` | Stand-ins so the gallery has something to show without internet. | Removed in **Batch 8** |
| The gallery's entry in the navigation map | `res/navigation/nav_graph.xml` (`designGalleryFragment`) | Goes away with the gallery. | Removed in **Batch 8** |

## From Batch 2

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Stand-in screen for Discover, Collection and Details (and "Your Picks") | `ui/temp/TempScreenFragment.kt`, `res/layout/fragment_temp_screen.xml`, `res/values/temp_screen_strings.xml`, and the `<fragment>` entries that use `TempScreenFragment` in `res/navigation/nav_graph.xml` | These screens do not exist yet. Each one just shows "Coming soon", a title and a "coming in..." line so the navigation can be tested. (Mood arrived in Batch 3, and **Profile became a real screen in the design pass**: see the last section.) | One by one, when the real screen is built: in `nav_graph.xml` change that entry's `android:name` to the new Fragment and delete its `<argument>` lines. **Collection: Batch 6** (my reading of "counts are live in Batch 6"). **Discover, Details: batch not stated in any brief so far, please tell me** (their texts say "a later batch"). Delete `TempScreenFragment` and its layout + strings when the last one is gone. |
| "Open design gallery" button on the Profile screen | the `openGallery` button in `res/layout/fragment_profile.xml` and its click in `ui/profile/ProfileFragment.kt` (the `actionText` / `actionDestination` code in `TempScreenFragment` is now unused) | Keeps the Batch 1 design gallery reachable. | Removed in **Batch 8** with the gallery (also delete the unused `actionText` code in `TempScreenFragment`) |
| Sample movies for Home and the swipe deck | `data/SampleData.kt` (real TMDB image addresses, 20 movies, rough ratings / years / genres / runtimes) | There is no TMDB data yet. | Removed in **Batch 5** (Home and the deck then load from `MovieRepository`) |
| Pretend loading delay | `HomeViewModel.kt`: `SAMPLE_LOAD_TIME_MS` and the block inside `load()` marked `TEMP` | Makes the loading placeholders visible for a moment. | Replaced in **Batch 5** by the real network call |
| Home test modes (force loading / error / empty) | `util/TestMode.kt` (shared with the swipe screen since Batch 4), `HomeViewModel.kt` (`nextTestMode`, `failNextLoad`), the long-press on the name in `HomeFragment.kt`, and the `temp_test_*` strings in `temp_screen_strings.xml` | Lets you see the loading, error and empty states on a phone. **Long-press "Movie Lover" on Home** to step through: normal -> loading never ends -> error (Retry works) -> empty -> normal. | Removed in **Batch 5** (real loading and errors exist then) |
| Collection counts are always 0 | `HomeViewModel.kt` (`_counts`, marked `TEMP`) | The personal lists do not exist yet. | Live from Room in **Batch 6** |

## From Batch 3

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| ~~Stand-in for the swipe screen~~ | | **Done in Batch 4:** the real swipe screen replaced it, and the `temp_swipe_*` strings are gone. | (finished) |
| ~~No mood photos yet~~ | | **Gone in the design pass:** the Mood screen is an animated face and a slider, so there are no mood photos and `WatchFlowViewModel.moodImageUrls` was removed. | (finished) |

## From Batch 4

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Sample swipe cards | `data/SampleData.kt` (`discover(request)`) | There is no TMDB data yet, so the deck is chosen from the same 20 sample movies. Mood, preferences, mixing and "never twice" are real (`logic/Discovery.kt`), but 20 movies cannot make a varied deck, and the sample has no real hidden gems. | **Batch 5**: `SwipeViewModel.nextPicks` sends the same `DiscoveryRequest`s to `MovieRepository` (TMDB /discover, /trending, /movie/popular) instead of `SampleData.discover`. Delete `discover` then. |
| Pretend loading delay | `SwipeViewModel.kt`: `SAMPLE_LOAD_TIME_MS` and the block in `load()` marked `TEMP` | Shows the loading card for a moment; stands in for the network. | **Batch 5** |
| Swipe test modes (force loading / error / empty) | `ui/swipe/SwipeViewModel.kt` (`nextTestMode`, `failNextLoad`), the long-press on the moods line in `SwipeFragment.kt`. Uses `util/TestMode.kt` and the `temp_test_*` strings | Lets you see the loading, error and empty states on a phone. **Long-press the moods line** (e.g. "Happy · Romantic") to step: loading never ends -> error (Retry works) -> empty -> back to normal. | **Batch 5** (with the Home test modes) |
| Stand-in for "Your Picks" | the `picksFragment` entry (a `TempScreenFragment`) in `res/navigation/nav_graph.xml`, and `temp_picks_*` in `temp_screen_strings.xml` | Opens from the "Picks (n)" pill and from "Show my picks". The real screen is Batch 7. | **Batch 7** (change that entry's `android:name`, delete its `<argument>` lines and the two strings) |
| Likes and passes are only kept while the screen lives | `SwipeViewModel` / `logic/SwipeDeck.kt` (the history list) | Nothing is saved yet. Leaving the swipe screen (the X, or Back to Home) forgets the deck and the "already swiped" list, so the same movies can come back in the next session. Rotating the phone, switching apps or opening Details does **not** lose it. | **Batch 6** (Room: also lets the deck skip movies swiped in earlier sessions) |
| Details is a stand-in | `openMovieDetails(...)` from a card tap / the Info button goes to the `TempScreenFragment` entry for Details | Same stand-in as Batch 2. | See the Batch 2 table: Details batch still to be confirmed |

## From the design pass (between Batch 4 and Batch 5)

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Achievements are stored in SharedPreferences | `data/AchievementTracker.kt`: `read()` and `write()` | Room does not exist yet, but achievements would vanish at every app start without some storage. The rules (`logic/Achievements.kt`) only see a plain `AchievementStats`, so only these two functions change. | **Batch 6** (move into Room) |
| "Double Feature" (add two movies to your watchlist) is missing | `logic/Achievements.kt` (not added) | There is no watchlist yet. | **Batch 6/7**: add the badge, a `watchlisted` counter and its string |
| Profile has no settings, only numbers and achievements | `ui/profile/` | The Profile batch was never stated; this is the minimum that the achievements section needs. | When the real Profile is planned |
| The design gallery does not show the new glass tokens or the new components | `ui/gallery/` | It is removed in Batch 8 anyway. | **Batch 8** |

## Not temporary, but good to know

- **Glass is faked.** Android below API 31 cannot blur what is behind a view without a library, so "glass" here is a faint white fill with a light edge (`bg_glass_*`, `glass_*` colors). It looks best over pictures and the colored light (`AmbientGlowView`); never stack glass on glass.
- **Contrast of the red button:** white text on `#FF3449` is about 3.6:1. That is fine for large or bold text but below WCAG AA (4.5:1) for 16 sp medium text. If that matters, make the button label bigger or bolder, or use a darker red for filled buttons.
- **Slow emulators:** the animated face redraws about 60 times a second while the Mood screen shows. It stops by itself when the screen is hidden or the phone's "Remove animations" is on.

- **Launcher icon on Android 7.x (API 24-25):** the old template icon files (`res/mipmap-*/ic_launcher*.webp`) are still there. Android 8+ already shows the new CineFiles logo. Swap the old ones during final polish (Batch 8).
- **`logic/` folder:** `Greeting.kt` (Batch 2), `Moods.kt`, `MoodSelection.kt`, `Preferences.kt` (Batch 3), `SwipeDeck.kt`, `SwipeGesture.kt` (Batch 4), `Discovery.kt`, `Achievements.kt`, `PosterColor.kt` (design pass). **`data/` folder:** `Movie.kt`, `AchievementTracker.kt` and the TEMP `SampleData.kt`.
- **Preferences are used by the deck now.** `logic/Discovery.kt` turns them into limits (`DiscoveryRequest`). The sample data applies them itself; in Batch 5 the same limits become TMDB /discover parameters (release dates, `vote_average.gte`, `with_runtime`, `with_original_language`).
- **Tests that need a phone or emulator:** `app/src/androidTest/.../SwipeDeckViewTest.kt` plays real touch gestures into the card deck. Run it with `./gradlew connectedDebugAndroidTest` while an emulator or phone is connected. The normal `./gradlew testDebugUnitTest` does not need one.
- **TMDB credit:** TMDB's terms ask apps that use its data and images to show its logo and the sentence "This product uses the TMDB API but is not endorsed or certified by TMDB." Add this (for example on the Profile screen) before the app is shown to anyone outside the project.
- **Tab screens and the bottom bar:** the bar floats above the screen. A screen that is a tab must keep `android:paddingBottom="@dimen/bottom_nav_height"` on its scrolling area (see `fragment_home.xml`).
