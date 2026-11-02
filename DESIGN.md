# AppSai native UI

## 1. Reference and scope

Visual contract: `generated-images/ig_0b8170c09006a924016ae8a3dc75b487d08a794361e9652865.png`
(app selection) and `ig_0b8170c09006a924016ae8a4092a8087d0b8ba3dde989d1edb.png`
(character setup). The overview ending `388a45c87d089a93fa6e4c7a79f.png` connects both.
Cream paper, deep plum type, lavender surfaces, large friendly headings and real
pixel characters define this app. Keep the reference's character-led composition,
rounded list items and full-width purple action, not the old gray prototype.

## 2. Platform and boundaries

Java activities and XML layouts, platform widgets only. No web UI or added UI
framework. Home and chat extend the same visual language. Installed apps come
from the launcher query, not the mockup's fixed YouTube/Instagram list.
The independently produced character source directory remains read-only.

## 3. Tokens

Resource tokens are the source of truth. Canvas #FFF9F5, surface #FFFFFF,
ink #32134D, secondary #756580, primary #7842CB, primary pressed #6330B1,
lavender #F0E5FC, border #E4D8EA. White on primary is the principal action.
Essential field boundaries use #8D779D (dark #9C84AF); dark primary uses #8750CC
so selected small-label controls retain readable white-text contrast.
Dark native dialog text actions use #D3B0FF independently of filled buttons.
Dark equivalents are supplied in values-night; system bars follow the palette.
Spacing: 4, 8, 12, 16, 20, 24, 32 dp. Corners: 12 dp fields, 18 dp cards,
24 dp hero. Type: system sans-serif, bold 28 sp page title, bold 20 sp section,
17 sp body/actions, 15 sp secondary, 13 sp supporting package identifiers.

## 4. Layout

Phone: one column, 20 dp side gutters, 48 dp toolbar, 56 dp primary action.
App list owns its scrolling; search and the bottom selection action stay visible.
Character preview stays fixed outside the one settings ScrollView, below the
toolbar and connected-app label. Only name, character, accessory and personality
controls scroll; the create action stays fixed at the bottom. Horizontal galleries
scroll independently. The stage is 152 dp high. Larger widths keep the same reading
order. IME/system/cutout insets belong to the outer root.

Friends and app picker use a shared 64 dp bottom navigation with 24 dp icons,
13 sp labels, equal-width targets and a selected lavender background. Character
creation and chat are focused detail flows without this bar. Reselecting the
active destination is a no-op; switching destinations preserves app query/selection.
Future working tabs are registered in BottomNavigation.ITEMS and routed in
MainActivity.navigateTo. Shop, currency, ranking and an existing-friend editor are
future work, not disabled or fake destinations in this change.

v0.2.0: the existing-friend editor is now a real third destination. It keeps the
preview fixed, offers a friend chooser, five accessory slots and explicit
save/cancel. New friend and usage permission actions moved off home into a
top-right settings screen. Thirty compatible production accessories are available.
Currency/shop remains a subsequent demo increment; ranking is still future work.

v0.3.0: shop is the fourth real destination. The fixed preview uses the same
character renderer; a scrollable item list and slot filters show all 30 items.
A visible demo wallet, temporary prices, purchase confirmation and owned/insufficient
states distinguish this mock economy from real rewards/payments. Unowned wardrobe
choices open the shop and cannot be saved. Existing equipped items survive migration.

## 5. Reusable primitives and states

Primary button: purple fill, white medium type, pressed ripple, disabled muted
fill and readable text. Secondary action: lavender fill, plum type.
Field: labelled, rounded, neutral outline, purple focus outline.
App/friend/choice card: rounded surface, neutral fine outline; selected state
uses lavender fill plus an explicit check, not color alone.
Character stage: lavender gradient with real nearest-neighbor pixel composition.
Message bubbles: lavender friend bubble, purple own bubble; sender is labelled.
Back/search/check/settings/send are consistent native vector icons, not emoji.
The actual app list and setup galleries are the native primitive state harness.

## 6. Interaction and persistence

Select one app, confirm the selection, configure nickname/base/accessories/
personality, then explicitly create. Back does not create a friend. Existing
friend selection opens the existing conversation. Creation, appearance and first
message commit in one SQLite transaction. Re-creation restores each draft field
and selection. Names, character IDs and independent accessory slot IDs persist.
Changing selections updates the real preview immediately. Native ripples provide
press feedback; no ornamental perpetual motion.

## 7. Accessibility and adaptation

48 dp minimum targets, labelled fields and icon actions, checked semantics on
choices, visible empty search and empty home actions. Long names ellipsize only
in compact rows; headings and descriptions wrap. A scrollable form and inset-aware
footer remain reachable with large type and the keyboard. Light and dark use the
same hierarchy; dark is an extrapolation, not present in the supplied reference.

## 8. Accepted differences and evidence

Available 64 px production assets replace the conceptual mockup illustrations.
Accessories are limited to actual available head/face/hand assets, with "none"
per slot; no unimplemented animation, purchases or AI are implied.
The mockup phone frame, operating-system icons and fixed app brands are not app
content. QA must capture home, app search/selection/empty, character settings at
top and bottom with changed accessories, restored drafts and saved/reopened chat.
Build, regression, migration and emulator evidence belongs in docs/STATUS.md.
