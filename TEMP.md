# TEMP.md: temporary things that must be replaced or removed

Everything below is marked `// TEMP:` (Kotlin) or `<!-- TEMP: -->` (XML) in the code, so you can also find it by searching the project for `TEMP:`.

| What | Where | Why it is temporary | Replaced / removed in |
|---|---|---|---|
| Design gallery screen (class) | `ui/gallery/DesignGalleryFragment.kt` | Lets you review the design system on a phone. | Removed in **Batch 8** |
| Design gallery layouts | `res/layout/fragment_design_gallery.xml`, `item_gallery_swatch.xml`, `item_gallery_icon.xml` | Same as above. | Removed in **Batch 8** |
| Design gallery texts | `res/values/temp_gallery_strings.xml` | Same as above. | Removed in **Batch 8** |
| Fake poster / backdrop pictures and swatch shape | `res/drawable/temp_sample_poster_1..4.xml`, `temp_sample_backdrop.xml`, `temp_bg_swatch.xml` | Stand-ins so the gallery has something to show without internet. | Removed in **Batch 8** |
| Gallery as the start screen | `res/navigation/nav_graph.xml` (`startDestination` and the `designGalleryFragment` entry) | There is no Home screen yet. | The start destination changes in the batch that builds **Home** (the batch number is not in this brief, so I will confirm it when you share the plan). The gallery entry is deleted in **Batch 8**. |

## Not temporary, but left for later

- **Launcher icon on Android 7.x (API 24-25):** the old template icon files (`res/mipmap-*/ic_launcher*.webp`) are still there. Android 8+ already shows the new CineFiles logo. Swap the old ones during final polish (Batch 8).
- **`data/` and `logic/` folders** only contain an empty `.gitkeep` file until the batches that need them.
