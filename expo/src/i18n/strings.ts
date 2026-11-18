// From the Kotlin app's res/values/strings.xml; this is now the source for the Expo app's strings. Differences:
// the network lines (the Expo app goes online for app updates only).
// Placeholders follow Android: %s/%d, or %1$s/%2$d by position.

export const strings = {
  "app_name": "SuperChant",
  "notification_channel_name": "Chanting Session",
  "notification_channel_description": "Shows your active chanting session progress",
  "action_stop": "Stop",
  "action_pause": "Pause",
  "action_resume": "Resume",
  "settings_practice": "Practice",
  "daily_target_title": "Daily target",
  "daily_target_desc": "Rounds to aim for each day. Days that reach it build your streak.",
  "daily_target_off": "Off",
  "daily_target_decrease": "Lower daily target",
  "daily_target_increase": "Raise daily target",
  "insights_title": "Your practice",
  "insights_best_streak": "Best streak",
  "insights_rounds_today": "Rounds today",
  "insights_less": "Less",
  "insights_more": "More",
  "insights_mantras_title": "Lifetime by mantra",
  "insights_show_all": "Show all",
  "insights_show_less": "Show less",
  "reminder_setting_title": "Daily reminder",
  "reminder_setting_desc": "A gentle nudge at %1$s, skipped once today's practice is done.",
  "reminder_time": "Reminder time",
  "reminder_needs_notifications": "Allow notifications to get the reminder",
  "reminder_channel_name": "Daily reminder",
  "reminder_channel_description": "One gentle reminder a day, only if you haven't practiced yet",
  "reminder_title": "Time for your practice",
  "reminder_text": "A few quiet minutes with your mala.",
  "session_saved": "Session saved",
  "goals_create_first": "Create a goal",
  "shortcut_start_short": "Start counting",
  "shortcut_start_long": "Start a chanting session",
  "shortcut_start_goal": "Start %1$s",
  "tile_start": "Tap to start",
  "backup_title": "Your data",
  "backup_desc": "Everything stays on this phone. Export a backup file to keep your history, goals and settings safe or move them to a new phone. Importing adds to what is here and never deletes.",
  "backup_export": "Export",
  "backup_import": "Import",
  "backup_export_failed": "Couldn't save the backup",
  "backup_import_failed": "Couldn't read that file",
  "backup_not_valid": "That file isn't a SuperChant backup",
  "goals": "Goals",
  "history": "History",
  "settings": "Settings",
  "action_start": "Start",
  "cancel": "Cancel",
  "settings_counting": "Counting",
  "touch_counting": "Touch counting",
  "touch_counting_on": "On. Tap the counter to count.",
  "touch_counting_off": "Off",
  "touch_counting_hint": "Turn touch counting on or off here",
  "volume_buttons_title": "Volume buttons",
  "volume_buttons_desc": "During a session, volume up or down counts, in other apps and on the lock screen too. Pause the session to change the volume. A video player or the camera on screen keeps its buttons.",
  "ok": "OK",
  "status_paused": "Paused",
  "status_paused_hint": "Paused. Tap ▶ to continue.",
  "status_tap": "Tap anywhere here to count",
  "status_volume": "Press a volume button to count",
  "status_both": "Tap here or press a volume button to count",
  "status_touch_off": "Turn on touch counting to count",
  "status_goal_complete": "Goal complete. Tap Stop to save it.",
  "notification_paused": "Paused",
  "notification_goal_complete": "Goal Complete!",
  "vibration_strength_unsupported": "This phone can't vary vibration strength, so vibrations always use its fixed strength.",
  "notifications_off_hint": "Notifications are off, so the session runs without its notification. You can turn them on in app settings.",
  "count_description": "Count %1$d of %2$d. %3$s",
  "tap_to_count_action": "Count",
  "bead_click_title": "Soft bead click",
  "bead_click_desc": "A quiet click on each count. Follows the system sounds volume and silent mode.",
  "milestone_pulse_title": "Milestone pulse",
  "milestone_pulse_desc": "A fuller pulse every 27 beads (each quarter of a 108 mala).",
  "goal_mantra_position": "Mantra %1$d of %2$d",
  "goal_complete": "Complete",
  "goal_mantra_completed": "Completed",
  "goal_show_mantras": "Show all mantras",
  "goal_hide_mantras": "Hide mantras",
  "common_back": "Back",
  "common_edit": "Edit",
  "common_delete": "Delete",
  "common_save": "Save",
  "common_add": "Add",
  "theme_light": "Light theme",
  "theme_system": "System theme",
  "theme_dark": "Dark theme",
  "settings_appearance": "Appearance",
  "settings_theme": "Theme",
  "settings_theme_light": "Light",
  "settings_theme_dark": "Dark",
  "settings_theme_system": "System",
  "settings_vibration": "Vibration",
  "settings_vibrate_count_title": "Vibrate on each count",
  "settings_vibrate_count_desc": "Quick haptic feedback for every button press",
  "settings_count_intensity": "Count intensity",
  "settings_vibrate_round_title": "Vibrate on round complete",
  "settings_vibrate_round_desc": "Stronger vibration when you finish a round",
  "settings_round_intensity": "Round intensity",
  "settings_intensity_very_light": "Very Light",
  "settings_intensity_light": "Light",
  "settings_intensity_medium": "Medium",
  "settings_intensity_strong": "Strong",
  "settings_intensity_very_strong": "Very Strong",
  "settings_about": "About",
  "settings_about_tagline": "A distraction-free digital mala for your spiritual practice",
  "settings_version": "Version %1$s",
  "settings_version_update": "Version %1$s, update %2$s",
  "settings_channel": "Channel: %1$s",
  "early_updates_title": "Early updates",
  "early_updates_on_message": "Get updates from the preview channel before everyone else. They may have bugs. You can turn this off here.",
  "early_updates_off_message": "Go back to the updates everyone gets.",
  "early_updates_turn_on": "Turn on",
  "early_updates_turn_off": "Turn off",
  "early_updates_up_to_date": "Switched. You have the newest update.",
  "early_updates_offline": "Switched. The update downloads next time you open the app online.",
  "history_clear": "Clear history",
  "history_empty_title": "No sessions yet",
  "history_empty_message": "Your chanting history will appear here",
  "history_today": "Today",
  "history_this_week": "This Week",
  "history_this_month": "This Month",
  "history_older": "Older",
  "history_clear_title": "Clear History",
  "history_clear_message": "Are you sure you want to clear all session history? This cannot be undone.",
  "history_clear_confirm": "Clear",
  "history_all_time_stats": "All Time Stats",
  "history_stat_counts": "Counts",
  "history_stat_rounds": "Rounds",
  "history_stat_sessions": "Sessions",
  "history_total_time": "Total time: %1$s",
  "history_less_than_minute": "Less than a minute",
  "history_duration_hm": "%1$dh %2$dm",
  "history_duration_ms": "%1$dm %2$ds",
  "history_duration_s": "%1$ds",
  "goals_create_goal": "Create Goal",
  "goals_edit_goal": "Edit Goal",
  "goals_empty_title": "No goals yet",
  "goals_empty_message": "Create a goal to track multiple mantras\nin a single session",
  "goals_delete_title": "Delete Goal",
  "goals_delete_message": "Are you sure you want to delete \"%1$s\"? This action cannot be undone.",
  "goals_options": "Options",
  "goals_start_session": "Start Session",
  "goals_name_label": "Goal Name",
  "goals_name_placeholder": "e.g., Morning Practice",
  "goals_mantras": "Mantras",
  "goals_reorder_hint": "Long press to drag and reorder",
  "goals_no_mantras": "No mantras added",
  "goals_no_mantras_hint": "Add mantras to create your goal",
  "goals_total": "Total",
  "goals_discard_title": "Discard changes?",
  "goals_discard_message": "Your changes to this goal will be lost.",
  "goals_discard_confirm": "Discard",
  "goals_drag_to_reorder": "Drag to reorder",
  "goals_move_up": "Move up",
  "goals_move_down": "Move down",
  "goals_add_mantra": "Add Mantra",
  "goals_edit_mantra": "Edit Mantra",
  "goals_mantra_name_label": "Mantra Name",
  "goals_mantra_name_placeholder": "e.g., Om Namah Shivaya",
  "goals_target_rounds_label": "Target Rounds",
  "goals_round_is_108": "Each round = 108 counts"
} as const;

export const plurals = {
  "today_rounds": {
    "one": "Today: %d round",
    "other": "Today: %d rounds"
  },
  "today_rounds_of_target": {
    "one": "Today: %1$d of %2$d round",
    "other": "Today: %1$d of %2$d rounds"
  },
  "streak_days": {
    "one": "%d-day streak",
    "other": "%d-day streak"
  },
  "insights_streak_days": {
    "one": "Day streak",
    "other": "Day streak"
  },
  "insights_heatmap_summary": {
    "one": "Practiced %1$d of the last %2$d day",
    "other": "Practiced %1$d of the last %2$d days"
  },
  "insights_counts": {
    "one": "%,d count",
    "other": "%,d counts"
  },
  "reminder_text_streak": {
    "one": "Keep your %d-day streak going.",
    "other": "Keep your %d-day streak going."
  },
  "reminder_text_target": {
    "one": "%1$d of %2$d round so far today.",
    "other": "%1$d of %2$d rounds so far today."
  },
  "minutes_short": {
    "one": "%d min",
    "other": "%d min"
  },
  "early_updates_taps_away": {
    "one": "%d more tap for early updates",
    "other": "%d more taps for early updates"
  },
  "backup_exported": {
    "one": "Exported %1$d session and %2$d goals",
    "other": "Exported %1$d sessions and %2$d goals"
  },
  "backup_imported": {
    "one": "Imported %1$d new session and %2$d goals",
    "other": "Imported %1$d new sessions and %2$d goals"
  },
  "rounds_count": {
    "one": "%d round",
    "other": "%d rounds"
  },
  "goal_mantra_progress": {
    "one": "%1$d / %2$d round",
    "other": "%1$d / %2$d rounds"
  },
  "goal_all_rounds_done": {
    "one": "All %d round completed",
    "other": "All %d rounds completed"
  },
  "rounds_completed": {
    "one": "%d round completed",
    "other": "%d rounds completed"
  },
  "rounds_total": {
    "one": "%d round total",
    "other": "%d rounds total"
  },
  "mantras_count": {
    "one": "%d mantra",
    "other": "%d mantras"
  },
  "minutes_count": {
    "one": "%d minute",
    "other": "%d minutes"
  },
  "notification_goal_complete_text": {
    "one": "%1$s - %2$d round completed",
    "other": "%1$s - All %2$d rounds completed"
  },
  "rounds_label": {
    "one": "round",
    "other": "rounds"
  },
  "counts_label": {
    "one": "count",
    "other": "counts"
  },
  "goals_more_mantras": {
    "one": "+%d more",
    "other": "+%d more"
  }
} as const;
