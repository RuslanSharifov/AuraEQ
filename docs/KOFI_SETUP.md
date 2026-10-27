# Ko-fi support link setup

The Settings screen includes an animated support card. It does not process payments or collect donations within the app; it opens an external browser on explicit tap.

Before publishing, set `KOFI_PROFILE_URL` in `app/src/main/java/com/example/ui/components/KoFiSupportCard.kt` to your **exact** profile URL such as `https://ko-fi.com/YOUR_ACCOUNT`.

The card's action is intentionally disabled while the constant is blank, to avoid sending users to an unrelated account. Do not put account passwords, access tokens or other payment credentials into the repository.

The animation respects AuraEQ's existing **Smooth animations** setting. Ko-fi's own site controls any information provided there.
