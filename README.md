> **This is a personal fork of [axel358/smartdock](https://github.com/axel358/smartdock).**
> It adds the following on top of the original:
>
> - **Snap Left/Right fixed:** in the dock app menu, Snap → Left/Right now snaps the window. Before, it moved the app's dock icon instead.
> - **Keyboard snapping without root:** the snap keyboard shortcuts now use Shizuku, so they work on devices that aren't rooted.
> - **Half-screen launch modes:** new Launch mode options (*Half screen (centre)*, *Half screen (left)* and *Half screen (right)*) open apps at half the screen width and full height.
> - **Swipe to snap:** swipe left or right on the dock to snap the active window to that half of the screen. This replaces the swipe-left-for-Back gesture while a window is open.
> - **Swipe up for full screen:** swipe up on the dock to make the active window full screen.
> - **Swipe on app icons:** swipe left, right or up on a dock icon to snap *that* app left, right or full screen. It opens the app in that position if it isn't running yet. The icon row no longer scrolls sideways.
> - **Work profile support:** work-profile windows show in the dock, and all snapping and swipe actions work on work-profile apps (e.g. Teams).
> - **Snapping full-screen apps:** swiping or keyboard snapping also works on full-screen apps, which become floating windows.
> - **Fixed signing key:** debug builds are signed with a key committed to this repo (`app/debug.keystore`), so new builds install over old ones.
>
> Builds: run **Actions → Build debug apk** and download the `apk-debug` artifact.

<div align="center">
  <h1>Smart Dock</h1>
  A user-friendly desktop mode launcher that offers a modern and customizable user interface
</div>

![Screenshot](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)

## Main features
- Very customizable, icons, colors, shapes, sounds
- Multi window support
- Keyboard shortcuts
- Support for both desktop and tablet layouts
- Compatible with Android 10 and above, no root required

## Install

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/cu.axel.smartdock)

You can grab the latest release from F-Droid

Note: The application should be installed as a system app in order to obtain the right permissions.
Alternatively you can use [Shizuku](https://shizuku.rikka.app).

## Usage

### Grant restricted permissions: 
On some devices Accessibility and Notification permissions might not be available. To solve this go to:  System Settings > Apps > Smartdock > 3 Dot menu (Top right corner) > Allow restricted permissions

### Secure settings
To grant secure settings permissions run the following command on an adb or root shell:
```
pm grant cu.axel.smartdock android.permission.WRITE_SECURE_SETTINGS
```

### Hide navigation bar
You might also want to hide the Android navigation bar.
[See hiding navigation bar](HideNav.md)

## Get help and ask questions 

Telegram support group: https://t.me/smartdock358

## Support the project

If you find this project useful or it has helped you in any way, consider showing your appreciation and supporting its ongoing development. 

Bitcoin Address:
```
bc1qzc0jnpzk4g838t56jshvt4j5a2a5dgfx5w9k5a
```

Thank you for your support!

## Contributors

[See contributors](Contributors.md)
