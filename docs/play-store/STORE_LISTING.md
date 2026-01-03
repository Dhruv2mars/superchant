# SuperChant - Play Store Listing

## App Name
```
SuperChant
```

## Short Description (80 characters max)
```
Distraction-free chanting counter. Use volume buttons to count without looking.
```
(78 characters)

## Full Description (4000 characters max)
```
SuperChant is a minimalist digital mala designed for focused spiritual practice. Count your chants using your phone's volume buttons - no need to look at your screen.

DISTRACTION-FREE COUNTING
• Press volume up or down to increment your count
• Keep your phone in your pocket or face-down
• Stay focused on your practice, not your screen

TRADITIONAL MALA EXPERIENCE
• 108 counts per round (like a traditional mala)
• Haptic feedback on each count
• Stronger vibration when you complete a round
• Track total counts and completed rounds

GOALS & MULTIPLE MANTRAS
• Create custom goals with multiple mantras
• Set target rounds for each mantra
• Track progress through your daily practice
• Visual progress indicators

SESSION HISTORY
• View all your completed sessions
• See total counts, rounds, and duration
• Track your practice over time

THOUGHTFUL DESIGN
• Clean, minimal interface
• Light and dark themes
• Works in background while screen is off
• Pause and resume sessions anytime

PRIVACY FOCUSED
• All data stays on your device
• No accounts required
• No analytics or tracking
• No ads, ever

HOW IT WORKS
SuperChant uses Android's Accessibility Service to detect volume button presses. This allows the app to count your chants even when the screen is off or you're in another app. The accessibility service ONLY detects button presses and cannot read any content from your screen or other apps.

Perfect for:
• Japa meditation
• Mantra chanting
• Prayer counting
• Any repetitive spiritual practice

SuperChant is free and open source.
```

---

## Accessibility Declaration

When Google asks "Why does your app need Accessibility Service?", use this:

### Core Functionality
```
SuperChant is a chanting/meditation counter app. The Accessibility Service is used to detect physical volume button presses so users can count their chants without looking at their phone screen.
```

### Why Accessibility Service is Required
```
The app needs to detect volume button presses system-wide, including when:
- The screen is off
- The app is in the background
- The user is in another app

Android does not provide any other API to intercept hardware button presses system-wide. The Accessibility Service's key event filtering capability (FLAG_REQUEST_FILTER_KEY_EVENTS) is the only way to achieve this functionality.
```

### What the Service Does
```
1. Listens for KEYCODE_VOLUME_UP and KEYCODE_VOLUME_DOWN key events
2. When detected during an active chanting session, increments the counter
3. Consumes the key event to prevent volume changes during counting
```

### What the Service Does NOT Do
```
- canRetrieveWindowContent is set to FALSE
- Does not read any screen content
- Does not interact with any other apps
- Does not collect or transmit any data
- Only activates during user-initiated chanting sessions
```

### User Benefit
```
Users can keep their phone in their pocket, face-down, or with the screen off while chanting. They simply press a volume button to count each repetition, allowing them to maintain focus on their spiritual practice without visual distractions.
```

---

## Category
```
Health & Fitness
```
(Alternative: Lifestyle)

## Tags/Keywords
```
meditation, chanting, mala, japa, mantra, counter, prayer, spiritual, mindfulness, beads
```

## Content Rating
```
Everyone
```

## Contact Email
```
(Use your email)
```

## Privacy Policy URL
```
https://github.com/dhruv2mars/superchant/blob/main/PRIVACY_POLICY.md
```
(Update with your actual GitHub username if different)

---

## Screenshots Guidance

Take screenshots showing:

1. **Home Screen (Idle)** - Counter at 0, Start button visible
2. **Active Session** - Counter showing some counts (e.g., 42), rounds completed
3. **Goals Screen** (optional) - If you have goals set up
4. **History Screen** (optional) - Showing past sessions
