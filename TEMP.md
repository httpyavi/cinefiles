# TEMP.md: temporary things that must be replaced or removed

Everything below is marked `// TEMP:` (Kotlin) or `<!-- TEMP: -->` (XML) in the code, so you can also find it by searching the project for `TEMP:`.

## From Batch 1

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Design gallery screen (class) | `ui/gallery/DesignGalleryFragment.kt` | Lets you review the design system on a phone. It is still reachable: Profile tab -> "Open design gallery". | Removed in **Batch 8** |
| Design gallery layouts | `res/layout/fragment_design_gallery.xml`, `item_gallery_swatch.xml`, `item_gallery_icon.xml` | Same as above. | Removed in **Batch 8** |
| Design gallery texts | `res/values/temp_gallery_strings.xml` | Same as above. | Removed in **Batch 8** |
| Fake poster / backdrop pictures and swatch shape | `res/drawable/temp_sample_poster_1..4.xml`, `temp_sample_backdrop.xml`, `temp_bg_swatch.xml` | Stand-ins so the gallery has something to show without internet. | Removed in **Batch 8** |
| The gallery's entry in the navigation map | `res/navigation/nav_graph.xml` (`designGalleryFragment`) | Goes away with the gallery. (It is no longer the start screen: Home is.) | Removed in **Batch 8** |

## From Batch 2

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Stand-in screen for Discover, Collection, Profile, Mood and Details | `ui/temp/TempScreenFragment.kt`, `res/layout/fragment_temp_screen.xml`, `res/values/temp_screen_strings.xml`, and the five `<fragment>` entries with `TempScreenFragment` in `res/navigation/nav_graph.xml` | These screens do not exist yet. Each one just shows a title and a "coming in..." line so the navigation can be tested. | One by one, when the real screen is built: in `nav_graph.xml` change that entry's `android:name` to the new Fragment and delete its `<argument>` lines. **Mood: Batch 3** (from your brief). **Collection: Batch 6** (my reading of "counts are live in Batch 6"). **Discover, Profile, Details: batch not stated in the brief, please tell me** (their texts currently say "a later batch"). Delete `TempScreenFragment` and its layout + strings when the last one is gone. |
| "Open design gallery" button on the Profile stand-in | the `actionText` / `actionDestination` arguments of `profileFragment` in `nav_graph.xml`, and the button code in `TempScreenFragment` | Keeps the Batch 1 design gallery reachable. | Removed in **Batch 8** with the gallery |
| Sample movies for Home | `data/SampleData.kt` (real TMDB image addresses, fake lists) | There is no TMDB data yet. | Removed in **Batch 5** (Home then loads from `MovieRepository`) |
| Pretend loading delay | `HomeViewModel.kt`: `SAMPLE_LOAD_TIME_MS` and the block inside `load()` marked `TEMP` | Makes the loading placeholders visible for a moment. | Replaced in **Batch 5** by the real network call |
| Home test modes (force loading / error / empty) | `HomeViewModel.kt` (`HomeTestMode`, `nextTestMode`, `failNextLoad`), the long-press on the name in `HomeFragment.kt`, and the `temp_test_*` strings in `temp_screen_strings.xml` | Lets you see the loading, error and empty states on a phone. **Long-press "Movie Lover" on Home** to step through: normal -> loading never ends -> error (Retry works) -> empty -> normal. | Removed in **Batch 5** (real loading and errors exist then) |
| Collection counts are always 0 | `HomeViewModel.kt` (`_counts`, marked `TEMP`) | The personal lists do not exist yet. | Live from Room in **Batch 6** |

## Not temporary, but good to know

- **Launcher icon on Android 7.x (API 24-25):** the old template icon files (`res/mipmap-*/ic_launcher*.webp`) are still there. Android 8+ already shows the new CineFiles logo. Swap the old ones during final polish (Batch 8).
- **`logic/` folder:** now holds `Greeting.kt`. **`data/` folder:** holds `Movie.kt` and the TEMP `SampleData.kt`.
- **TMDB credit:** TMDB's terms ask apps that use its data and images to show its logo and the sentence "This product uses the TMDB API but is not endorsed or certified by TMDB." Add this (for example on the Profile screen) before the app is shown to anyone outside the project.
- **Tab screens and the bottom bar:** the bar floats above the screen. A screen that is a tab must keep `android:paddingBottom="@dimen/bottom_nav_height"` on its scrolling area (see `fragment_home.xml`).
