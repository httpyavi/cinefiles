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
| Stand-in screen for Discover, Collection, Profile and Details | `ui/temp/TempScreenFragment.kt`, `res/layout/fragment_temp_screen.xml`, `res/values/temp_screen_strings.xml`, and the four `<fragment>` entries that use `TempScreenFragment` in `res/navigation/nav_graph.xml` | These screens do not exist yet. Each one just shows a title and a "coming in..." line so the navigation can be tested. (Mood is no longer one of them: the real Mood screen arrived in Batch 3.) | One by one, when the real screen is built: in `nav_graph.xml` change that entry's `android:name` to the new Fragment and delete its `<argument>` lines. **Collection: Batch 6** (my reading of "counts are live in Batch 6"). **Discover, Profile, Details: batch not stated in any brief so far, please tell me** (their texts say "a later batch"). Delete `TempScreenFragment` and its layout + strings when the last one is gone. |
| "Open design gallery" button on the Profile stand-in | the `actionText` / `actionDestination` arguments of `profileFragment` in `nav_graph.xml`, and the button code in `TempScreenFragment` | Keeps the Batch 1 design gallery reachable. | Removed in **Batch 8** with the gallery |
| Sample movies for Home | `data/SampleData.kt` (real TMDB image addresses, fake lists) | There is no TMDB data yet. | Removed in **Batch 5** (Home then loads from `MovieRepository`) |
| Pretend loading delay | `HomeViewModel.kt`: `SAMPLE_LOAD_TIME_MS` and the block inside `load()` marked `TEMP` | Makes the loading placeholders visible for a moment. | Replaced in **Batch 5** by the real network call |
| Home test modes (force loading / error / empty) | `util/TestMode.kt` (shared with the swipe screen since Batch 4), `HomeViewModel.kt` (`nextTestMode`, `failNextLoad`), the long-press on the name in `HomeFragment.kt`, and the `temp_test_*` strings in `temp_screen_strings.xml` | Lets you see the loading, error and empty states on a phone. **Long-press "Movie Lover" on Home** to step through: normal -> loading never ends -> error (Retry works) -> empty -> normal. | Removed in **Batch 5** (real loading and errors exist then) |
| Collection counts are always 0 | `HomeViewModel.kt` (`_counts`, marked `TEMP`) | The personal lists do not exist yet. | Live from Room in **Batch 6** |

## From Batch 3

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| ~~Stand-in for the swipe screen~~ | | **Done in Batch 4:** the real swipe screen replaced it, and the `temp_swipe_*` strings are gone. | (finished) |
| No mood photos yet | `ui/mood/WatchFlowViewModel.kt`: `moodImageUrls` is an empty map (marked `TEMP`) | There is no TMDB data yet, so every mood card shows its tinted fallback (faint color + icon). | **Batch 5** fills `moodImageUrls` (mood id -> photo web address). Nothing else needs to change: the cards already load a photo when they get a URL, hide the icon once it arrives, and keep the fallback if it fails. (I checked this with a real and a broken URL, then took the test URLs out.) |

## From Batch 4

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Sample swipe cards | `data/SampleData.kt` (`swipePage`, the 20 movies with their rating / year / genres) | There is no TMDB data yet, so the deck is the same 20 sample movies. "Keep swiping" / "Try new cards" gives the same 20 in a new order (`swipePage(page)`). Your mood and preferences do **not** change which cards you get yet. | **Batch 5** (the deck is loaded through `MovieRepository` using the mood genre ids and the preferences, and "Keep swiping" loads the next real page). Delete `swipePage` then. |
| Pretend loading delay, sample loading and the "next page" | `SwipeViewModel.kt`: the blocks marked `TEMP` in `load()` and `keepSwiping()` | Shows the loading card for a moment; stands in for the network. | **Batch 5** |
| Swipe test modes (force loading / error / empty) | `ui/swipe/SwipeViewModel.kt` (`nextTestMode`, `failNextLoad`), the long-press on the moods line in `SwipeFragment.kt`. Uses `util/TestMode.kt` and the `temp_test_*` strings | Lets you see the loading, error and empty states on a phone. **Long-press the moods line** (e.g. "Happy · Romantic") to step: loading never ends -> error (Retry works) -> empty -> back to normal. | **Batch 5** (with the Home test modes) |
| Stand-in for "Your Picks" | the `picksFragment` entry (a `TempScreenFragment`) in `res/navigation/nav_graph.xml`, and `temp_picks_*` in `temp_screen_strings.xml` | Opens from the "Picks (n)" pill and from "Show my picks". The real screen is Batch 7. | **Batch 7** (change that entry's `android:name`, delete its `<argument>` lines and the two strings) |
| Likes and passes are only kept while the screen lives | `SwipeViewModel` / `logic/SwipeDeck.kt` (the history list) | Nothing is saved yet. Leaving the swipe screen (the X, or Back to Home) forgets the deck. Rotating the phone, switching apps or opening Details does **not** lose it. | **Batch 6** (Room) |
| Details is a stand-in | `openMovieDetails(...)` from a card tap / the Info button goes to the `TempScreenFragment` entry for Details | Same stand-in as Batch 2. | See the Batch 2 table: Details batch still to be confirmed |

## Not temporary, but good to know

- **Launcher icon on Android 7.x (API 24-25):** the old template icon files (`res/mipmap-*/ic_launcher*.webp`) are still there. Android 8+ already shows the new CineFiles logo. Swap the old ones during final polish (Batch 8).
- **`logic/` folder:** `Greeting.kt` (Batch 2), `Moods.kt`, `MoodSelection.kt`, `Preferences.kt` (Batch 3) and `SwipeDeck.kt`, `SwipeGesture.kt` (Batch 4). **`data/` folder:** `Movie.kt` and the TEMP `SampleData.kt`.
- **Preferences are only stored so far.** `logic/Preferences.kt` has the option lists and labels. Turning "7+" or "Under 2 hrs" into TMDB filter values needs real TMDB data, so it is part of Batch 5. The swipe screen does not use them yet.
- **Tests that need a phone or emulator:** `app/src/androidTest/.../SwipeDeckViewTest.kt` plays real touch gestures into the card deck. Run it with `./gradlew connectedDebugAndroidTest` while an emulator or phone is connected. The normal `./gradlew testDebugUnitTest` does not need one.
- **TMDB credit:** TMDB's terms ask apps that use its data and images to show its logo and the sentence "This product uses the TMDB API but is not endorsed or certified by TMDB." Add this (for example on the Profile screen) before the app is shown to anyone outside the project.
- **Tab screens and the bottom bar:** the bar floats above the screen. A screen that is a tab must keep `android:paddingBottom="@dimen/bottom_nav_height"` on its scrolling area (see `fragment_home.xml`).
