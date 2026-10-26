# Privacy Policy for SuperChant

**Last updated:** October 27, 2026

## Overview

SuperChant is a chanting/meditation counter app for Android and iPhone, designed with privacy as a core
principle. This policy explains how the app handles your data.

## Data Collection

**SuperChant does NOT collect, transmit, or share any personal data.**

SuperChant has no servers of its own and never sends your practice data (sessions, goals, settings) anywhere.
The only copy that can leave your device is your phone's own device backup, described below.

## App Updates

The app goes online for one thing only: to check for and download updates to the app itself, from Expo's
update service (EAS Update, run by Expo, 650 Industries, Inc.). When the app starts, it asks that service
whether a newer version of the app's code exists and, if so, downloads it. Each check sends:

- a random identifier created for this installation of the app (not linked to you, your device's hardware or any
  account; it lets Expo count how many installations received updates),
- the platform (Android or iOS), the app's version and the update currently installed.

It sends nothing about your practice and no error reports. Like any internet request, the service sees your
IP address. On Android, the app is built so it cannot connect to any other server. Earlier Android versions
of SuperChant (before the Expo-based app) had no internet access at all.

## Data Stored Locally

The following data is stored on your device:

- **Chanting session history** - Your completed sessions (counts, rounds, duration, goal
  name and how many rounds of each of its mantras)
- **Goals** - Custom goals you create
- **Preferences** - Theme, vibration, counting, daily target and reminder settings, and whether you
  agreed to the accessibility disclosure
- **Active session** - The progress of a session that is running, so it can resume if Android closes
  the app. It is deleted when you stop the session.

This data is stored on your device (Android's DataStore, or iOS's app storage). On Android, your goal names
also appear as launcher shortcuts (long-press the app icon), which Android keeps on your device. On iPhone, a
running session shows its count on the Lock Screen and Dynamic Island as a Live Activity, which stays on your
device.

## Export and Import

You can export your history, goals and settings to a file. You choose where the file is saved with the
system file picker (for example your phone's storage, the Files app, or a cloud drive app you use); SuperChant
itself never uploads it. Importing reads a file you pick and adds its contents to the app.

## Daily Reminder

If you turn on the optional daily reminder, SuperChant shows a local notification once a day at the time
you chose (on Android through the alarm service, on iPhone as a scheduled local notification). It is
computed on your device from your local history; nothing is sent anywhere. On Android, SuperChant asks to
start at boot (`RECEIVE_BOOT_COMPLETED`) only to set this alarm again after your phone restarts.

## Device Backup

SuperChant allows the phone's built-in backup so your history and goals come with you to a new phone.
On iPhone, the data is part of your iCloud or computer backup if you have one turned on. On Android, if
backup is turned on in your Android settings, the data above is included in your device backup:

- **Google backup** - Android uploads it, with the rest of your app data, to your Google account's
  backup storage, where it is handled under Google's privacy policy. The developer of SuperChant has
  no access to it.
- **Device-to-device transfer** - It is copied directly to your new phone when you set it up.

You can turn off backup for your device (or for SuperChant) in Android settings at any time.

## Accessibility Service

On Android, SuperChant uses Android's Accessibility Service **solely** to count volume button presses during a chanting
session, so you can count without looking at your screen, including on the lock screen or while another app
is open.

Before you're sent to Android's accessibility settings, SuperChant shows this disclosure in the app and
asks you to agree.

**What the Accessibility Service does:**
- Detects when volume up/down buttons are pressed
- Counts a press, instead of changing the volume, only while a chanting session is running and not paused
- At all other times, and for every other key, lets the press through untouched without keeping it

**What the Accessibility Service does NOT do:**
- Read any screen content
- Access any other apps
- Collect any information
- Transmit any data

The accessibility service has `canRetrieveWindowContent="false"` set, meaning it cannot read content from your screen or other apps.

## Third-Party Services

SuperChant uses **no third-party services** other than the update check described above:
- No analytics
- No crash reporting
- No advertising
- No cloud storage of our own (only Android's device backup, if you have it turned on)
- No social features

## Data Sharing

We do not share any data. The update check's random installation identifier goes only to Expo, to deliver
updates.

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
