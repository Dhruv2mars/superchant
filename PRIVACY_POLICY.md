# Privacy Policy for SuperChant

**Last updated:** October 18, 2026

## Overview

SuperChant is a chanting/meditation counter app designed with privacy as a core principle. This policy explains how the app handles your data.

## Data Collection

**SuperChant does NOT collect, transmit, or share any personal data.**

SuperChant has no servers and no internet permission. The app itself never sends your data anywhere.
The only copy that can leave your device is Android's own device backup, described below.

## Data Stored Locally

The following data is stored on your device:

- **Chanting session history** - Your completed sessions (counts, rounds, duration, goal
  name and how many rounds of each of its mantras)
- **Goals** - Custom goals you create
- **Preferences** - Theme, vibration, counting, daily target and reminder settings, and whether you
  agreed to the accessibility disclosure
- **Active session** - The progress of a session that is running, so it can resume if Android closes
  the app. It is deleted when you stop the session.

This data is stored using Android's DataStore.

## Export and Import

You can export your history, goals and settings to a file. You choose where the file is saved with
Android's file picker (for example your phone's storage or a cloud drive app you use); SuperChant
itself never uploads it. Importing reads a file you pick and adds its contents to the app.

## Daily Reminder

If you turn on the optional daily reminder, Android's alarm service wakes SuperChant once a day at the
time you chose to show a local notification. It is computed on your device from your local history;
nothing is sent anywhere. SuperChant asks to start at boot (`RECEIVE_BOOT_COMPLETED`) only to set
this alarm again after your phone restarts.

## Device Backup

SuperChant allows Android's built-in backup so your history and goals come with you to a new phone.
If backup is turned on in your Android settings, the data above is included in your device backup:

- **Google backup** - Android uploads it, with the rest of your app data, to your Google account's
  backup storage, where it is handled under Google's privacy policy. The developer of SuperChant has
  no access to it.
- **Device-to-device transfer** - It is copied directly to your new phone when you set it up.

You can turn off backup for your device (or for SuperChant) in Android settings at any time.

## Accessibility Service

SuperChant uses Android's Accessibility Service **solely** to detect volume button presses during active chanting sessions. This allows you to count without looking at your screen.

Before you're sent to Android's accessibility settings, SuperChant shows this disclosure in the app and
asks you to agree.

**What the Accessibility Service does:**
- Detects when volume up/down buttons are pressed
- Only activates during an active chanting session
- Increments your count when a volume button is pressed

**What the Accessibility Service does NOT do:**
- Read any screen content
- Access any other apps
- Collect any information
- Transmit any data

The accessibility service has `canRetrieveWindowContent="false"` set, meaning it cannot read content from your screen or other apps.

## Third-Party Services

SuperChant uses **no third-party services**:
- No analytics
- No crash reporting
- No advertising
- No cloud storage of our own (only Android's device backup, if you have it turned on)
- No social features

## Data Sharing

We do not share any data because we do not collect any data.

## Data Deletion

- Clearing history in the app, or uninstalling the app, removes the data from your device
- You can clear app data from Android Settings at any time
- A copy in your Google backup is managed by Google; you can delete it from your Google account's backup settings

## Children's Privacy

SuperChant does not collect any personal information from anyone, including children.

## Changes to This Policy

If we update this privacy policy, the changes will be reflected in the "Last updated" date above.

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository:
https://github.com/dhruv2mars/superchant

---

*SuperChant is open source and you can verify these claims by reviewing the source code.*
