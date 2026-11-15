# Faden audit checklist

Every finding from the 2026-11-15 audit (performance, Material 3, identity). Paths are relative to
`faden/app/src/main/java/io/github/radixtrator/resync/`. All findings were by inspection; nothing was profiled.
Expressive APIs are from material3 `1.5.0-alpha28` and are mostly `@ExperimentalMaterial3ExpressiveApi`.

Legend: `[ ]` open · `[x]` done · `[~]` done differently (see note) · `[-]` skipped (see note)

## Performance

- [x] **P1** *(59c1b7e: `ThemeSpec` + remembered scheme; root reads narrowed with `derivedStateOf`)* Theme: `remember` the `ColorScheme` keyed on theme fields; pass `FadenTheme` only the theme fields, not all of `Settings` (`ui/theme/Theme.kt:244`, `MainActivity.kt:138`). A new scheme instance invalidates everything under `MaterialTheme`.
- [x] **P2** *(77b9814: intensity and dark-overlay sliders commit on release; previews follow the drag locally)* Theme sliders keep a local value and commit in `onValueChangeFinished` (`ui/settings/ThemeManagementScreen.kt:176`, `:411`); today every drag tick writes the full Settings JSON.
- [x] **P3** *(a998977, 6d390c6: feed rows and comment rows read a small `FeedDisplay` instead of all of `Settings`; `ViewOptions.DEFAULT`)*
- [x] **P4** *(2403bef: `Post.previewText` built in the parser with a pure-Kotlin HTML→text approximation, not Android's parser — compare a few long text posts on device)* Compute the text-post preview in `Parser.post` (off main) instead of `AnnotatedString.fromHtml` on the whole body during scroll (`ui/common/PostLayouts.kt:1024`).
- [x] **P5** *(c57d9eb: new `faden_history` file for visited / post memory / thread visits / recents / explore; idempotent copy-then-remove migration; backup format unchanged, old backups and snapshots restore into the right file; writes coalesced ~750 ms and flushed when an activity stops — a change can be lost only if the process dies within that window)* Move high-churn stores (visited, post memory, thread visits, recent posts, explore) out of the single `resync` prefs file, with a one-time migration that keeps Backup working; debounce `markAll` on scroll (`AppContainer.kt:59`, `data/VisitedStore.kt:20`, `ui/feed/FeedScreen.kt:176`).
- [x] **P6** *(de16535: 2× screen decode; original loads on top past 2× zoom, read-only memory cache)* Image viewer: decode at ~2× screen size, full resolution only after zooming; don't write originals to the memory cache; fix the stale comment (`ui/media/ImageViewerScreen.kt:142`, `:160`).
- [x] **P7** *(ddc01dc: post toolbar icon checks known icons + 64-entry LRU; 1b85c4c: inbox count throttled to 60 s, forced after mark-read/reply)* Throttle `refreshInboxCount()` (~60 s) (`ui/feed/FeedViewModel.kt:51`, `AppContainer.kt:221`); use cached subreddit icons / a small LRU instead of fetching `/about` on every post open (`ui/post/PostViewModel.kt:307`).
- [x] **P8** *(79bc9d5: 2xx bodies decoded from the stream; me/user/mine/inbox/searchSubreddits/modListing parse off main)* Decode responses from the byte stream instead of `body.string()` (`data/RedditClient.kt:149`); run the parsers in `mine()`, `inbox`, `searchSubreddits`, `modListing`, `me`, `user` off main (`data/RedditApi.kt`).
- [x] **P9** *(d68e707, 0b884e1: header text, marks and OP pill remembered; unchanged comments keep their instance across reloads; ages tick with the shared minute clock)*
- [x] **P10** *(a998977: one network-callback `StateFlow`, carried in `FeedDisplay.metered`)* One app-level metered-network `StateFlow` (network callback) instead of an IPC call per card that never updates (`ui/common/PostParts.kt:61`).
- [x] **P11** *(0ea0cee: listener-driven state, position polled only while playing/visible/resumed, deferred reads; OkHttp + 100 MB disk cache via new `VideoCache.kt`)* Video: stop the 200 ms poll from recomposing the overlay (deferred reads, `Player.Listener`, pause polling when hidden/backgrounded); use the app's OkHttp client and a media cache (`ui/media/VideoPlayerScreen.kt:206`, `:258`).
- [~] **P12** *(f63722c: list remembered on its inputs instead of built lazily, since ProfileScreen also calls `ShellScaffold`; explore/watched collected only where used)* Build the quick-actions list lazily when the sheet opens; move `explore`/`watched` collects into the sheets (`ui/feed/FeedScreen.kt:153`, `:201`, `ui/home/HomeChrome.kt:186`, `:412`).
- [x] **P13** *(2279909, e72afa8: comment-card, inbox and profile bodies prepared off main; `CommentCard` meta remembered)*
- [x] **P14** *(c995e47: `buildReaderQueue` on Default)* Build the Read-aloud queue on `Dispatchers.Default` (`ui/post/CommentReader.kt:27`).
- [x] **P15** *(a998977: `FeedDisplay.moderated` lowercase set)* Hoist the moderated-subreddits collector to feed level as one lowercase `Set` (`ui/common/PostLayouts.kt:322`).
- [~] **P16** *(221d117: regex-free `fill()` with tests, hoisted regex, data-class cache key; the three caches hold different pipeline stages, so they were left separate)* Small allocations: placeholder fill without regex (`i18n/I18n.kt:77`); hoist the regex in `ui/common/CommentGifs.kt:88`; cheaper cache key in `ui/common/Html.kt:93`; collapse overlapping HTML caches (`Html.kt:90`, `:206`, `CommentGifs.kt:55`).
- [x] **P17** *(a998977: base font scale read from the container, with a fallback; 0ef2ba9: lazy OkHttp client)* Startup: read the base font scale from the container instead of decoding Settings twice (`MainActivity.kt:120`, `data/Settings.kt:452`); create the OkHttpClient lazily (`data/RedditClient.kt:39`).
- [x] **P18** *(2df6bc6: rows typed by `media::class`)* `contentType` by media kind so image/text/gallery cards reuse matching slots (`ui/common/PagedList.kt:80`, `:167`).
- [x] **P19** *(a998977: one shared minute clock, ticking only while started)* Bug: `PostMeta` remembers `timeAgo`, so "4h" never updates while a row stays composed (`ui/common/PostLayouts.kt:691`).

## Cleanup and structure

- [x] **C1** *(19f1f6e; `Post.editedUtc` is still used, kept)* Remove dead code: `Backup.apiClientId`, `WatchStore.isWatchingPost`, parsed-but-unread `Post.flairDarkText` and `Comment.editedUtc`; drop the unused `ui-tooling-preview` dependency.
- [x] **C2** *(a0be01c, 4743dfa: one `ui/common/Avatar.kt` for communities, users and the account button)*
- [~] **C3** *(68901cd: `Format.points/comments`, shared `HTML_TAG` + `plainText` in `data/HtmlText.kt` (a 4th copy found in Parser); `SwipeHeader` meta kept — it intentionally differs from `PostMeta`)* Dedupe points/comments label helpers (`SwipePage.kt:281`, `PostLayouts.kt:821`, `FeedScreen.kt:423`, `PostScreen.kt:794`); `SwipeHeader` meta reuses `PostMeta`; one shared `TAG` regex (`Wiki.kt:9`, `CommentGifs.kt:29`, `Html.kt:49`); one `plainText` (`PostScreen.kt:1145`, `InboxWorker.kt:91`).
- [x] **C4** *(0cbf058, 8c6aa58: PostScreen 1,354 → 570 + CommentRow 415 + PostSheets 345 + PostBars 158; PostLayouts 998 → 120 + Cards 162 + Rows 159 + Slides 192 + Text 245 + Preview 246; checked as a pure move)*
- [x] **C5** *(0144dd1: every icon on vendored Material Symbols; material-icons-core/extended dropped; debug APK 29.8 → 22.8 MB)*
- [x] **C6** The untracked `resync/` folder at the repo root is leftover from the rename (build output, `.idea`, `.gradle`, `local.properties`). Deleted with your OK.

## Material 3

- [x] **M1** *(a4fb58e: `MaterialExpressiveTheme` + `MotionScheme.expressive()`; enter/exit and settle springs from the motion scheme; page transitions stay tweens for predictive back)* `MaterialExpressiveTheme` with a `MotionScheme`; route `Motion.kt` easings, `Gestures.kt` `SettleSpec` and `Swipe.kt` springs through `MaterialTheme.motionScheme` (`ui/theme/Theme.kt:251`).
- [x] **M2** *(b65d9db: `LoadingIndicator` pages/sheets, `ContainedLoadingIndicator` over media, Expressive pull-to-refresh indicator; inline spinners kept)* `LoadingIndicator` for ~13 page-level spinners (one edit in `PagedList.kt` covers every list); `ContainedLoadingIndicator` over media (`MediaPeek.kt:133`, `ImageViewerScreen.kt:173`, `VideoPlayerScreen.kt:132`/`:294`); `PullToRefreshDefaults.LoadingIndicator` on the 5 pull-to-refresh sites. Keep small inline spinners.
- [x] **M3** *(313de52, b2f57df, 070b299: `FadenShapes` 6/10/16/24/32dp; corners routed through `MaterialTheme.shapes` across theme, header, settings, home, post and feed; `SyncCard` → `PostCard`)* A theme-wide `Shapes` scale; route hardcoded 6/8/10/12/14/16dp corners through `MaterialTheme.shapes`.
- [x] **M4** *(a0be01c: communities clipped to `MaterialShapes.Cookie9Sided`, users round)* The merged avatar (C2) clipped with a `MaterialShapes` shape.
- [x] **M5** *(d3d26e9: `ShortNavigationBar` 64dp and collapsed `WideNavigationRail` 96dp; padding constants recomputed; rail-right, drawer, scroll-away, reselect and badge kept by code reading)* `ShortNavigationBar` and `WideNavigationRail` in place of the 80dp bar / manual rail; recompute `NavBarHeight`/`RailWidth` consumers (`ui/home/HomeChrome.kt:145`, `:282`, `:544`).
- [~] **M6** *(0fa5de7: Reader and Sports bars on `HorizontalFloatingToolbar` with a label row above; prev/next + reply on `VerticalFloatingToolbar`)* Floating toolbars: `ReaderBar`/`SportsBar` → `HorizontalFloatingToolbar`; prev/next + reply → `VerticalFloatingToolbar` (`ui/post/PostScreen.kt:356`, `:1204`).
- [~] **M7** *(8721f1e: Settings and Theme management on `LargeFlexibleTopAppBar`; Customize view skipped — its LargeHeader chips and preview aren't bar content)* `LargeFlexibleTopAppBar` / `MediumFlexibleTopAppBar` for Settings, Theme management and Customize view.
- [x] **M8** *(c759b2a, 4079ab6: all deprecated `ListItem` sites migrated; one tap target per toggle/choice)* Migrate the 31 deprecated `ListItem(headlineContent=…)` calls; `Toggle` → `ListItem(checked, onCheckedChange)` (one tap target); `Choice` dialog rows → `ListItem(selected, onClick)` (`ui/settings/SettingsScreen.kt:559`, `ThemeManagementScreen.kt:191`).
- [x] **M9** *(be55b03)* Reply FAB → `FloatingActionButton` (`ui/post/PostScreen.kt:374`).
- [x] **M10** *(b68cf48: new `HeaderFilterChip` matching `HeaderChip`)* `FilterChip(selected=…)` for the Customize-view layout picker and "Only in r/x" (`CustomizeViewScreen.kt:96`, `SearchScreen.kt:280`).
- [~] **M11** *(6bff175: Automatic/Manual segmented; the Mode row stays chips because four German labels don't fit ~328dp)* `SingleChoiceSegmentedButtonRow` for Automatic/Manual and Mode in Theme management (`ThemeManagementScreen.kt:116`, `:145`); check German label widths.
- [x] **M12** *(2f6e120: OptionRow is selectable/toggleable/button by use; Include title is one toggle target; add-to-multi rows are checkbox toggles)* `OptionRow` exposes selection (`selectable`/`toggleable` or `ListItem(selected, onClick)`); fix the nested-Switch double target in "Include title" (`ui/common/Sheets.kt:119`, `PostItem.kt:217`).
- [x] **M13** *(9c2bafc: `Slider` with thin custom track/thumb, seek on release, fed by `snapshotFlow` so playback doesn't recompose it)* Video seek bar → `Slider` with custom thumb/track, seek on release (`ui/media/VideoPlayerScreen.kt:387`).
- [x] **M14** *(dab7807: shared `StateToggle` over `IconToggleButton` in the post header, comment bar, save, vote pill and swipe bar)* Vote/save buttons → `IconToggleButton` with checked colors (`PostScreen.kt:619`, `:887`, `PostLayouts.kt:306`, `:600`, `SwipePage.kt`).
- [x] **M15** *(d3cb361: toggle cells expose Switch role and state)* Quick-action toggles (Hide read, Dark mode, Dark overlay, Data saving, Sports mode, Read aloud) expose checked state, via toggle buttons (`ui/common/Sheets.kt:164`).
- [x] **M16** *(01877b7)* `CommentsPill` → `FilledTonalButton` (`ui/common/PostLayouts.kt:569`).
- [x] **M17** *(0a9a3d4, 7a35f6e: all four search fields labelled with their placeholder text; `InputField` not adopted)* Labelled search fields: `SearchBarDefaults.InputField` or a field with a real label (`ExploreSheet.kt:702`, `PostScreen.kt:1079`, `SearchScreen.kt:236`, `SettingsScreen.kt:183`).
- [x] **M18** *(e9723b8: radio-group semantics + color names on all three pickers; swatches still under 48dp)* Color-swatch pickers expose selection with `selectable(role = RadioButton)` + `selectableGroup()` (`SettingsScreen.kt:305`, `UserActions.kt:138`, `ThemeManagementScreen.kt:459`).
- [x] **M19** *(17216a4)* Sheet handles: consistent handle with a content description; `CommentSearchSheet` gets the shared one (`ui/common/Sheets.kt:104`, `PostScreen.kt:1074`).
- [x] **M20** *(2883092)* `SearchButton` → `FilledTonalButton` (`ExploreSheet.kt:738`).
- [~] **M21** *(2aee628: override removed; whether the row ⋮ really gets 48dp needs a device check)* `RowMenuButton` touch target back to 48dp (`ExploreSheet.kt:612`).
- [x] **M22** *(f4dc7e8: named fixed dark constants — media chrome stays dark in light mode)* Media chrome colors from theme roles instead of hex (`ui/media/MediaChrome.kt:46`).
- [x] **M23** *(c504d4c: drag up past 25% or fling dismisses — check on device that it doesn't fight taps/scroll inside)* (optional) Drag-to-dismiss on `TopSheet` (`ui/common/Sheets.kt:55`).
- Keep custom (no change): 3-stage `SwipeActions`, slide-back / media dismiss, `TopSheet` sort sheet, `LargeHeader` as list item 0, `MoreFab` (long-press), `VotePill`, `CommentActionBar`, flair/OP/New labels, dense Explore rows, `AccountCard`.

## Identity

Direction chosen: **A + C**. Faden means "thread", so the comment depth rails become the signature, with Faden's
own palette, accent, a thread launcher icon, Expressive motion and shapes, and Sync's density kept. Added to
that: an own font (Figtree, bundled under OFL) and Material Symbols Rounded icons. Default theme becomes Faden's
palette, and Sync's palette stays as a named built-in theme.

- [x] **I1** *(7b9cdc2: default = Faden red `#B4233C` palette; `ThemePalette.CLASSIC` keeps Sync's exact palette as "Classic"/"Klassisch"; optional `p` in theme codes, VERSION unchanged)* A new default palette of Faden's own; keep Sync's sampled palette available as a named theme; seedless saved themes and shared theme codes keep working.
- [x] **I2** *(3001147: `Tones.kt`, OKLCH palettes solved to exact WCAG luminance per tone; `ContrastTest` over every seed × light/dark/black × intensity 0–2 passes; d6946ec: light mode uses a tone-95 window with tone-99 cards)* Generate seeded schemes perceptually (tone-based) instead of plain HSL, so contrast holds for every accent (`ui/theme/Theme.kt:158`).
- [x] **I3** *(df03a07: rails `#E0465A` red thread, `#B87A12`, `#239379`, `#4F7FE0`, `#9B6BD6`; ≥3:1 on cards in both modes)* Comment depth-rail palette of Faden's own (`FadenColors.Depth`).
- [x] **I4** *(b9e2b7d: rust, ochre, moss, teal, blue, violet, plum)* Accent swatches (`THEME_SEEDS`) of Faden's own instead of Sync's rail colors.
- [x] **I5** *(c5dc0b8: Figtree is the default typeface with M3 letter spacing; Roboto etc. keep Sync spacing)* Typography: Faden's own type and spacing instead of Roboto + Sync's +1.5% letter spacing.
- [x] **I6** *(d3d26e9, 983b646: M3 nav label type and colors; header title `headlineLarge` semibold)*
- [x] **I7** *(983b646: chips use `shapes.small`; header title `headlineLarge` semibold)* `HeaderChip` styled from the theme (shape/height/label) instead of Sync's measurements (`ui/common/LargeHeader.kt:85`).
- [x] **I8** *(32df8eb, 378612f: home, post and search bars tint over scrolled content)* Top bars take M3's scrolled-container tint instead of a pinned single color (`HomeChrome.kt:231`, `PostScreen.kt:299`, `SearchScreen.kt:230`).
- [x] **I9** *(a2cc7a5, 0144dd1: Material Symbols Rounded, Apache-2.0 under `faden/third_party/material-symbols/`)*
- [x] **I10** *(c8e6258: red thread with one loop, running through the icon; launcher background color set with the theme)* Launcher icon that isn't a sync glyph (Faden = thread), and a launcher background that matches the palette (`res/drawable/ic_launcher_foreground.xml`, `res/values/*.xml`).
- [x] **I11** *(39ae824, d6946ec: window colors match the generated background; tested)* Startup window backgrounds match the theme, so there's no color flash at launch (`res/values/`, `res/values-night/`).
- [~] **I12** *(c12598b and per-agent edits: comments reworded where code no longer follows Sync; the many comments about behaviour that still follows Sync are kept on purpose)*

## Owed / not verified

- **Baseline profile** (`app/src/release/generated/baselineProfiles/`) still lists classes that were renamed, split or removed (e.g. `SyncCard`, material-icons). Harmless, but startup optimisation is weaker until it's regenerated with `./gradlew :app:generateBaselineProfile`. Not run here: it installs on *every* connected device, and your phone was connected over wireless ADB.
- **Seen on the emulator** (Pixel-class, API 37, light and dark): feed, post + comments, More-actions sheets, Settings, Theme management, launcher icon. **Not exercised by hand:** gestures (top-sheet drag-to-dismiss, the video seek slider vs. swipe-to-dismiss, the 3-stage swipe with the new settle spring), the floating reader/sports toolbars, the wide-screen rail, TalkBack output, pull-to-refresh motion.
- **New on-disk use:** video now streams through a 100 MB LRU cache in the app's cache dir (`cache/video`).
