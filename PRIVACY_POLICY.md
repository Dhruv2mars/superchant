# Privacy Policy for SuperChant

**Last updated:** January 3, 2026

## Overview

SuperChant is a chanting/meditation counter app designed with privacy as a core principle. This policy explains how the app handles your data.

## Data Collection

**SuperChant does NOT collect, transmit, or share any personal data.**

All information stays on your device and is never sent to any server.

## Data Stored Locally

The following data is stored only on your device:

- **Chanting session history** - Your completed sessions (counts, rounds, duration)
- **Goals** - Custom goals you create
- **Preferences** - Theme setting, vibration preferences
- **Session state** - Current session progress (for resume functionality)

This data is stored using Android's DataStore and remains entirely on your device.

## Accessibility Service

SuperChant uses Android's Accessibility Service **solely** to detect volume button presses during active chanting sessions. This allows you to count without looking at your screen.

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
- No cloud storage
- No social features

## Data Sharing

We do not share any data because we do not collect any data.

## Data Deletion

Since all data is stored locally on your device:
- Uninstalling the app removes all data
- You can clear app data from Android Settings at any time

## Children's Privacy

SuperChant does not collect any personal information from anyone, including children.

## Changes to This Policy

If we update this privacy policy, the changes will be reflected in the "Last updated" date above.

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository:
https://github.com/dhruv2mars/superchant

---

*SuperChant is open source and you can verify these claims by reviewing the source code.*
