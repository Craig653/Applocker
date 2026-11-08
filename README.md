# KidLock

A PIN app locker for an Android tablet (built for a Samsung Galaxy Tab S6 Lite 2022).

- Pick which apps are locked from a list of every app on the tablet.
- Locked apps ask for a 4-digit parent PIN before they open.
- KidLock itself needs the PIN to open.
- KidLock can't be uninstalled (or switched off in Settings) without the PIN.

## Get the APK

Every push to `main` builds the app. Download `KidLock.apk` from the **Releases** page of this repo
(or from the latest run under **Actions → Build APK → Artifacts**).

## Install and set up on the tablet

1. Open the APK on the tablet. Allow "Install unknown apps" for your browser/Files app when asked.
2. Open KidLock and create your 4-digit PIN.
3. **Step 1 – App locking:** tap *Turn on* → Installed apps → KidLock → turn on.
   If Android says **Restricted setting**: tap *Open KidLock app info* → ⋮ (top right) →
   *Allow restricted settings* → go back and turn it on again.
4. **Step 2 – Uninstall protection:** tap *Turn on* → *Activate*.
5. Tick the apps to lock. Also tick **Settings** and **Play Store / Galaxy Store** (shown at the top) so kids
   can't turn KidLock off or remove apps.
6. Optional on Samsung: Settings → Battery → Background usage limits → **Never sleeping apps** → add KidLock.

## How it works

- `LockService` (an accessibility service) sees when an app opens and shows the PIN screen over locked apps.
  An unlocked app stays open until the child switches to a different app.
- It also shows the PIN screen on any Settings or uninstall screen that mentions KidLock.
- `AdminReceiver` makes KidLock a device admin, which Android blocks from being uninstalled.
- 5 wrong PINs triggers a 30 second wait.

## Limits

No app-only locker is unbreakable: a factory reset, or a computer with USB debugging, gets around it.
Keep Developer options off on the kid's tablet.

## Uninstalling KidLock (parent)

Open Settings (enter PIN) → Security and privacy → More security settings → Device admin apps → turn off KidLock,
then uninstall normally.
