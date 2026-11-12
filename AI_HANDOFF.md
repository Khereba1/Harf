# Harf: handoff notes for an AI assistant

Paste this file together with the project zip when asking an AI to change the app.
The family-wide rules are in STANDARD.md (ask the owner to attach it if missing).

## What this is
Harf (حرف): a free, open-source, offline text-on-photo editor. Add styled text (fonts, colors, outline, shadow, background box) on top of a photo or a blank canvas, then export PNG/JPG.
Android app, Kotlin + Jetpack Compose Material3. Source package `com.khereba1.app`, applicationId `com.khereba1.harf`.
minSdk 29, compileSdk 35. UI languages: Arabic + English (RTL supported). Developer name: Khereba1.
No INTERNET permission unless the owner asked for it.

## How it is built (IMPORTANT)
The owner has only a phone. There is NO local build. Code is pushed from Termux to GitHub and GitHub Actions
(.github/workflows/build.yml) builds the APK with Gradle 8.10.2 (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.10.01).
You cannot compile: be careful and conservative. A mistake shows up only as a failed Actions run.
- Avoid Kotlin JVM signature clashes (`var x` with `private set` plus `fun setX(...)` breaks the build).
- Every new string needs an entry in every language file the app has (values/strings.xml, and values-ar/strings.xml if Arabic exists).
- Do not bump library versions without checking that the exact version exists.

## Files (app/src/main/java/com/khereba1/app)
- App.kt: Application (starts AppLog + FontStore), AppLog (file error log), LocaleHelper (in-app language).
- MainActivity.kt: activity (receives photos shared from other apps), AppRoot (Scaffold, snackbar, start/editor switch).
- Model.kt: `Layer` (ONE data class for every layer: `type` TYPE_TEXT or TYPE_SHAPE; emoji are text layers) and `Project` (immutable document, layers bottom to top in `items`) + JSON. Sizes of outline/shadow/box are RELATIVE to `size`. Old saved projects without `type` load as text.
- Shapes.kt: 20 original vector shapes built as android.graphics.Path (`Shapes.build(kind, w, h, corner)`).
- Renderer.kt: draws a Project with android.graphics (StaticLayout text with stroke pass / shadow layer / background box, shape paths, layer alpha). The SAME code paints the preview and the exported image. Also `boxOf`, hit-testing (skips hidden and locked layers) and `fitDoc` (doc px <-> view px).
- HarfViewModel.kt: state holder (survives language change), undo/redo as snapshots with coalescing, debounced autosave, export, layer operations (add text/shape/emoji, lock, hide, reorder, delete, duplicate).
- Storage.kt: `Session` (autosave of the current project into filesDir/session), `ImageIO` (ImageDecoder decode with EXIF + downscale to 2560 px, save to Pictures/Harf via MediaStore).
- FontStore.kt: fonts = system families, assets/fonts (downloaded by the workflow, gitignored), user-imported files.
- EditorScreen.kt: editor layout (top bar, canvas, layer action row, tool chips Text/Shapes/Emoji/Layers, fixed-height panel) + the gesture canvas (drag / pinch / twist / double-tap). Keep the bottom area a fixed height so the canvas never resizes under a finger.
- Panels.kt: SubTabs, Font/Color/Layout/Outline/Shadow/Box panels (generic over Layer), ShapeAdd/ShapeFill, Emoji, Layers panels. Dialogs.kt: color picker, text, export, blank canvas, settings. StartScreen.kt, Theme.kt.
Document coordinates = pixels of the photo (or of the blank canvas). Editing goes through `vm.editItem(key, fresh) { it.copy(...) }`.

## Roadmap (phase 1 and edit2 are done)
edit2: extra image layers (from gallery) and freehand drawing (pen/eraser as a layer).
edit3: saved-projects list with thumbnails, background tool (color / gradient / photo adjustments).
edit4: text gradient / texture fill, curved text, letter spacing, filters.
Optional later: background removal, perspective, 3D text.
Do NOT copy any code, assets, names or data formats from other apps (this project is an original MIT-licensed work).

## REQUIRED answer format (the owner has only a phone + Termux)
1. Briefly say what you changed and why (plain language; the owner writes Egyptian Arabic mixed with English terms, reply in the same).
2. Deliver ONE zip containing only the changed/new files, complete (never snippets or diffs), with paths relative to the project root.
   Name it `editN.zip` (edit2, edit2, ...). If you cannot create a zip, give each file in full in its own code block with its path, and say so.
3. Raise `versionCode` (+1) and `versionName` in `app/build.gradle.kts` for every functional change, and include that file.
4. Give the install command, ready to copy, with the real folder name:
   `cd ~/harf && unzip -o "$(ls -t ~/storage/downloads/editN*.zip | head -1)" -d . && git add . && git commit -m "short message" && git push`
   (GitHub repo: Khereba1/Harf, APK: github.com/Khereba1/Harf/releases/tag/latest)
5. Say honestly that nothing was compiled or tested, and what to check first on the phone.
6. If the build fails, the owner sends lines containing `e: ` from the Actions log; fix only those.

## Signing and sharing
The stable signing key lives in GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD).
Never print, commit or ask for them, and never ask for the backup folder.
`bash share_zip.sh` builds a clean `<folder>-for-AI.zip` (no keys, env files, build output or APKs) for sharing.


## edit2 (design pass)
Always-dark theme (Theme.kt), stroke vector icons in res/drawable/ic_*.xml, shared components in Ui.kt (HIcon, IconAction, ToolItem, SectionLabel), floating action pill over canvas, rounded bottom sheet with icon tool bar, underline SubTabs, restyled dialogs/start screen. versionCode 4 / 1.3.
