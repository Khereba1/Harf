# Harf · حرف

A free, open-source text-on-photo editor for Android. It works fully offline: no ads, no tracking, no internet permission.

تطبيق مجاني ومفتوح المصدر للكتابة على الصور. بيشتغل من غير إنترنت، من غير إعلانات ولا تتبّع.

## Features (phase 1)
- Pick a photo (or start from a blank canvas: 1:1, 4:5, 9:16, 16:9)
- Any number of texts: drag, pinch to resize, twist to rotate, double tap to edit
- Fonts: Arabic and Latin (open-licensed fonts bundled at build time, plus your own .ttf/.otf)
- Color (palette + custom picker), bold / italic, alignment, line spacing, opacity
- Outline, shadow and background box for every text
- Layers order, duplicate, delete, unlimited undo/redo
- Autosave of the current project
- Export PNG or JPG to `Pictures/Harf`, or share directly
- Arabic and English interface (RTL supported)
- Open and edit photos from the Gallery or the Share sheet

## Build
Pushing to `main` runs GitHub Actions (`.github/workflows/build.yml`), which builds the APK and publishes it on the
[Releases](../../releases/tag/latest) page. Nothing needs to be built locally.

## Fonts
Fonts are **not** stored in this repository. The workflow downloads a set of fonts under the SIL Open Font License
from the [google/fonts](https://github.com/google/fonts) repository while building. Each font keeps its own license and copyright.

## License
MIT, see [LICENSE](LICENSE). This is an original work: it contains no code or assets from other apps.
