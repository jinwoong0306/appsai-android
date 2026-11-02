# Native character rendering

Use `kr.example.haliltalk.character.CharacterView` in XML, or construct it with
`Context`. Set the current selection on the UI thread:

```java
characterView.setAppearance("chat", "straw_hat", "sunglasses", "coffee_cup");
```

`CharacterCatalog` provides parallel ID/name arrays for selection widgets.
Persist IDs, not array positions. Treat the public arrays as read-only.

| Slot | IDs |
| --- | --- |
| Character | `mail`, `video`, `music`, `photo`, `chat`, `map`, `calendar`, `reading`, `fitness` |
| Hat | `none` plus 10 head accessories |
| Face | `none` plus 6 face accessories |
| Body | `none` plus 6 neck/body accessories |
| Back | `none` plus 4 back accessories |
| Hand prop | `none` plus 4 hand accessories |

Every accessory array starts with `none` / `없음`. Public `validCharacter`,
`validHat`, `validFace`, `validBody`, `validBack`, and `validProp` reject null and unknown IDs. At the view
boundary, null or unknown IDs select `mail` for character and `none` for each
accessory independently. `nameFor` likewise defaults to the name of `mail`.
The caller owns saved selections; the view does not read or write storage.

## Asset and layer contract

`app/build.gradle` copies only the 27 production body/default-expression/hand
PNGs and 30 accessory PNGs into `app/build/generated/characterAssets`.
That directory is an assets source and `preBuild` depends on the Copy task.
Original assets are unchanged. Complete preview composites, expression variants,
costumes, scripts, references, and enlarged images are not packaged.
The catalog is the Java ID-to-asset-path mapping; source filenames are retained
inside Android assets, not `res/drawable`.

The catalog follows the IDs and paths in `캐릭터_에셋/에셋_목록.json` and
`캐릭터_에셋/07_액세서리_30종/액세서리_목록.json`. The source artwork is the
project's coordinate-based redraw described in those packages' READMEs.

All layers keep their full transparent 64 x 64 canvas and origin `(0, 0)`.
The source pivot is `(32, 56)`, head anchor `(32, 17)`, face anchor `(32, 30)`,
and hand anchors `(12, 43)` / `(52, 43)`; no per-character offsets or cropping
are applied. Layer order is back accessory, body, neck/body accessory, default
expression, face accessory, hands, hat, hand prop. Use the six-argument overload
`setAppearance(character, hat, face, body, back, prop)` for all five slots.
The old four-argument overload supplies `none` for body/back. The expression is
drawn before glasses, never repainted over them. All five slots combine.

Decoding uses `inScaled = false`, no bitmap density, and no filtering,
anti-aliasing, or dithering. Drawing fits the largest whole-number physical-pixel
scale in the padded view and centers the complete shared canvas at integer
coordinates. Allocate at least 64 physical pixels in each content dimension;
smaller bounds clip the 1x canvas rather than blur it with fractional shrinking.
Do not apply a fractional `scaleX`/`scaleY` to this view or a parent.

Missing or malformed packaged PNGs are build/integration defects and produce
an explicit exception rather than silently replacing a valid selection.
This module provides a static front pose and the default expression only.
It adds no dependencies and does not implement animation or a dressing screen.
