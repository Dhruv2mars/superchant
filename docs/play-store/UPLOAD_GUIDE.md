# Play Console Upload Guide - SuperChant

## Prerequisites
- [ ] Google Play Developer Account ($25 one-time fee - you have this)
- [ ] AAB file: `SuperChant-v1.1.0.aab`
- [ ] App Icon: `docs/play-store/icon-512.png`
- [ ] 2 Screenshots (you'll take these)
- [ ] Privacy Policy pushed to GitHub (so the URL works)

---

## Step 1: Push to GitHub First

Before uploading to Play Console, push the code to GitHub so the Privacy Policy URL works:

```bash
git add .
git commit -m "Prepare for Play Store: add privacy policy, store listing assets"
git push origin main
```

Your Privacy Policy URL will be:
```
https://github.com/YOUR_USERNAME/superchant/blob/main/PRIVACY_POLICY.md
```

---

## Step 2: Create App in Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Click **"Create app"**
3. Fill in:
   - **App name**: `SuperChant`
   - **Default language**: English (United States)
   - **App or game**: App
   - **Free or paid**: Free
4. Check the declarations and click **"Create app"**

---

## Step 3: Set Up Store Listing

Navigate to: **Grow > Store presence > Main store listing**

### App Details
- **App name**: `SuperChant`
- **Short description**: 
  ```
  Distraction-free chanting counter. Use volume buttons to count without looking.
  ```
- **Full description**: Copy from `docs/play-store/STORE_LISTING.md`

### Graphics
- **App icon**: Upload `docs/play-store/icon-512.png`
- **Feature graphic**: Skip (optional)
- **Screenshots**: Upload 2 phone screenshots you take

Click **Save**

---

## Step 4: App Content Setup

Navigate to: **Policy > App content**

Complete each section:

### Privacy Policy
- **Privacy policy URL**: `https://github.com/YOUR_USERNAME/superchant/blob/main/PRIVACY_POLICY.md`

### Ads
- Select: **No, my app does not contain ads**

### App Access
- Select: **All functionality is available without special access**

### Content Rating
- Complete the questionnaire (app has no violence, no user content, etc.)
- You'll get an **Everyone** rating

### Target Audience
- Select: **18 and over** (simplest, avoids children's policy requirements)

### News App
- Select: **No**

### Data Safety
Fill out the form:
- **Does your app collect or share any user data?** → No
- The app stores data locally only (session history, preferences)
- No data is transmitted off device

### Government Apps
- Select: **No**

---

## Step 5: Accessibility Service Declaration

Navigate to: **Policy > App content > Sensitive app permissions**

Google will ask about your Accessibility Service. Use these answers:

**Why does your app need this permission?**
```
SuperChant is a chanting/meditation counter app. The Accessibility Service detects physical volume button presses so users can count their chants without looking at their phone screen, even when the screen is off or the app is in the background.
```

**What does the service do?**
```
1. Listens for volume button key events (KEYCODE_VOLUME_UP/DOWN)
2. Increments the chant counter when detected during an active session
3. Prevents volume changes during counting by consuming the key event
```

**Why can't you achieve this without Accessibility Service?**
```
Android does not provide any other API to intercept hardware button presses system-wide. The Accessibility Service's FLAG_REQUEST_FILTER_KEY_EVENTS is the only way to detect volume buttons when the screen is off or the app is in the background.
```

**Confirm these:**
- canRetrieveWindowContent = false
- Does NOT read screen content
- Does NOT interact with other apps
- Does NOT collect/transmit data

---

## Step 6: Create Internal Testing Release

Navigate to: **Release > Testing > Internal testing**

1. Click **"Create new release"**

2. **App signing**: If prompted, opt in to Play App Signing (recommended)

3. **Upload AAB**: Drag and drop `SuperChant-v1.1.0.aab`

4. **Release name**: `1.1.0`

5. **Release notes**:
   ```
   Initial internal testing release.
   
   Features:
   - Count chants using volume buttons
   - 108 counts per round
   - Haptic feedback
   - Session history
   - Custom goals with multiple mantras
   - Light and dark themes
   ```

6. Click **"Save"** then **"Review release"** then **"Start rollout to Internal testing"**

---

## Step 7: Add Testers

Navigate to: **Release > Testing > Internal testing > Testers**

1. Click **"Create email list"**
2. Name it (e.g., "Alpha Testers")
3. Add your Gmail address
4. **Save changes**

5. Copy the **opt-in URL** (looks like: `https://play.google.com/apps/internaltest/...`)

---

## Step 8: Install the App

1. Open the opt-in URL on your phone (or send to yourself)
2. Click **"Become a tester"**
3. Click **"Download it on Google Play"**
4. Install from Play Store like any other app
5. Enable Accessibility Service - **it should work without restrictions!**

---

## Troubleshooting

### "App not available"
- Wait 5-10 minutes after release creation
- Make sure you accepted the tester invite
- Clear Play Store cache

### Accessibility Declaration rejected
- Make sure canRetrieveWindowContent is false in your XML
- Emphasize the app ONLY detects volume buttons
- Mention it cannot read screen content

### AAB upload fails
- Check signing config is correct
- Ensure versionCode is higher than any previous upload

---

## Files Reference

| File | Location | Purpose |
|------|----------|---------|
| AAB | `SuperChant-v1.1.0.aab` | Upload to Play Console |
| Icon | `docs/play-store/icon-512.png` | Store listing icon |
| Privacy Policy | `PRIVACY_POLICY.md` | Link in store listing |
| Store Copy | `docs/play-store/STORE_LISTING.md` | Copy descriptions from here |

---

## Future Updates

When you make changes:

1. Increment `versionCode` in `app/build.gradle.kts`
2. Update `versionName` if desired
3. Build: `./gradlew buildReleaseBundle`
4. Upload new AAB to Internal Testing
5. No review required - available immediately!
