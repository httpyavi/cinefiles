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
    components/        PosterView (poster + status badge)
    temp/              TEMP stand-in screens
    gallery/           TEMP design gallery
  data/                Movie, MovieRepository, Retrofit API, Room database (later batches); TEMP SampleData
  logic/               Plain Kotlin rule logic, no Android code (Greeting.kt)
  util/                Small helpers: ViewExtensions, NavigationExtensions, ImageExtensions, ImageUrls
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

## Design system cheat sheet

Always reuse these. Never type a color, size or text directly in a layout.

| Need | Use |
|---|---|
| Colors | `@color/background`, `surface`, `elevated`, `gold`, `on_gold`, `text_primary`, `text_secondary`, `like_green`, `pass_red` (see `values/colors.xml`) |
| Spacing | `@dimen/space_4 … space_48` (4 dp grid), screen sides: `@dimen/screen_margin` (20 dp) |
| Corner radius | `@dimen/radius_small/medium/large/xlarge/pill` = 8 / 12 / 16 / 24 dp / pill. Images: `app:shapeAppearanceOverlay="@style/ShapeAppearance.CineFiles.Medium"` (or `.Large`, `.Small`) |
| Text | `@style/TextAppearance.CineFiles.LargeTitle` (32), `.Title` (24), `.Headline` (18), `.Body` (16), `.Callout` (14, gray), `.Caption` (12, gray). Controls: `.Button` (16), `.Label` (14) |
| Buttons | `@style/Widget.CineFiles.Button.Primary` (gold, one per screen), `.Tonal`, `.Text` |
| Round icon button | `@style/Widget.CineFiles.RoundActionButton` + `layout_width/height=@dimen/round_button_size`, `app:icon`, `app:iconTint`, **and a `contentDescription`** |
| Chip | `@style/Widget.CineFiles.Chip` (inside a `ChipGroup`) |
| Card / tile | `@style/Widget.CineFiles.Card` (tonal block); `Widget.CineFiles.Card.Tile` is the tappable version. Change the corner with `app:cardCornerRadius`. |
| Poster | `<com.example.cinefiles.ui.components.PosterView>` ; set `badge = PosterBadge.LIKED / WATCHLIST / WATCHED` or `null`. In a horizontal row use `PosterRowAdapter` (`item_poster_row.xml`). |
| Loading a picture | `imageView.loadImage(ImageUrls.poster(path))` (or `ImageUrls.backdrop(path)`): placeholder, fade-in and size are handled |
| Image scrim | `@drawable/scrim_bottom` in a view stacked above the image (same corner shape) |
| Icons | `@drawable/ic_home`, `ic_discover`, `ic_bookmark`, `ic_profile`, `ic_heart`, `ic_check`, `ic_star` (+ `_filled` versions for selected states), `ic_search`, `ic_close`, `ic_back`, `ic_undo`, `ic_info`, `ic_play`, `ic_filter`. Tint with `app:tint`. |
| Screen states | `view_state_empty` (change icon / texts / button from code), `view_state_error` (set what Retry does), `view_state_loading` (full-screen placeholders), `view_row_loading` (placeholders for a poster row) |
| Press animation | `android:stateListAnimator="@animator/press_scale"` (already built into buttons, chips, tiles and `PosterView`) |
| Edge-to-edge | call `rootView.applySystemBarsPadding()` (from `util/ViewExtensions.kt`) on a screen's root |

Gold means "primary action or selected". Depth comes from the tonal surfaces (`background` → `surface` → `elevated`), not from borders or shadows.

### Changing the font

The font is named in only two styles in `values/styles.xml`: `TextAppearance.CineFiles.Regular` and `.Medium`. Today they use Android's built-in Roboto. To use Inter: put the Inter files in `res/font/` and change the two `android:fontFamily` lines to point at them (for example `@font/inter_regular`, `@font/inter_medium`).

## Status

- **Batch 1 (done, approved):** project setup, design system, design gallery (temporary).
- **Batch 2 (built, waiting for review):** bottom navigation, Home screen, stand-in screens.
- See `TEMP.md` for everything that is temporary.
