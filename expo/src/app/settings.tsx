/** Settings (SettingsScreen.kt, ReminderControls.kt, BackupControls.kt). */
import { useEffect, useState } from 'react';
import { AppState, Platform, ScrollView, StyleSheet, View } from 'react-native';
import { getCalendars } from 'expo-localization';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Native from 'superchant-native';
import { t, tp } from '@/i18n';
import { platform } from '@/native/platform';
import { BackupOutcome, defaultSettings, ThemeMode } from '@/native/types';
import { useSettings } from '@/state/store';
import { useTheme } from '@/theme';
import { AccessibilitySetupDialog } from '@/ui/AccessibilitySetup';
import { AppTopBar, BackButton } from '@/ui/chrome';
import { Button, Divider, IconButton, PressScale } from '@/ui/controls';
import { Icon, IconName } from '@/ui/Icon';
import { InlineTimePicker, TimePicker } from '@/ui/native-controls';
import { IntensityRow, SettingsItem, SettingsSection, SwitchRow } from '@/ui/settingsRows';
import { useSnackbar } from '@/ui/Snackbar';
import { Text } from '@/ui/Text';

const ios = Platform.OS === 'ios';
const DAILY_TARGET_STEPS = [...Array.from({ length: 16 }, (_, i) => i + 1), 20, 24, 27, 32, 48, 54, 64, 108];

export default function Settings() {
  const s = useSettings() ?? defaultSettings;
  const insets = useSafeAreaInsets();
  const snackbar = useSnackbar();
  const [serviceEnabled, setServiceEnabled] = useState(() => platform.volume?.isServiceEnabled() ?? false);
  const [showSetup, setShowSetup] = useState(false);
  // Re-checked on return, e.g. from the accessibility settings
  useEffect(() => {
    const sub = AppState.addEventListener('change', (st) => st === 'active' && setServiceEnabled(platform.volume?.isServiceEnabled() ?? false));
    return () => sub.remove();
  }, []);
  const volumeReady = serviceEnabled && s.accessibilityConsent;
  const amplitude = platform.hasAmplitudeControl;

  return (
    <View style={{ flex: 1 }}>
      <AppTopBar title={t('settings')} navigation={<BackButton />} />
      <ScrollView contentContainerStyle={{ padding: 16, gap: 16, paddingBottom: 16 + insets.bottom }}>
        <SettingsSection title={t('settings_counting')}>
          {platform.volume ? (
            <>
              <SettingsItem
                title={t('volume_buttons_title')}
                subtitle={t(volumeReady ? 'volume_buttons_on' : 'volume_buttons_off')}
                trailing={!volumeReady ? <Button variant="text" label={t('volume_buttons_set_up')} onPress={() => setShowSetup(true)} /> : null}
              />
              <Divider style={{ marginVertical: 12 }} />
            </>
          ) : null}
          <SwitchRow title={t('bead_click_title')} subtitle={t('bead_click_desc')} value={s.beadClick} onChange={platform.setBeadClick} />
        </SettingsSection>

        <SettingsSection title={t('settings_practice')}>
          <DailyTarget target={s.dailyTarget} onChange={platform.setDailyTarget} />
          <Divider style={{ marginVertical: 12 }} />
          <ReminderControls
            enabled={s.reminderEnabled}
            minuteOfDay={s.reminderMinuteOfDay}
            onChange={async (enabled, minute) => {
              if (!(await platform.setReminder(enabled, minute))) snackbar(t('reminder_needs_notifications'));
            }}
          />
        </SettingsSection>

        <SettingsSection title={t('settings_appearance')}>
          <SettingsItem
            title={t('settings_theme')}
            subtitle={t(s.themeMode === 'LIGHT' ? 'settings_theme_light' : s.themeMode === 'DARK' ? 'settings_theme_dark' : 'settings_theme_system')}
            trailing={<ThemeToggle mode={s.themeMode} onChange={platform.setThemeMode} />}
          />
        </SettingsSection>

        <SettingsSection title={t('settings_vibration')}>
          <SwitchRow title={t('settings_vibrate_count_title')} subtitle={t('settings_vibrate_count_desc')} value={s.countVibration} onChange={platform.setCountVibration} />
          {amplitude ? (
            <IntensityRow title={t('settings_count_intensity')} value={s.countIntensity} enabled={s.countVibration} onDone={platform.setCountIntensity} />
          ) : null}
          <View style={{ height: 8 }} />
          <SwitchRow
            title={t('milestone_pulse_title')}
            subtitle={t('milestone_pulse_desc')}
            value={s.milestonePulse && s.countVibration}
            enabled={s.countVibration}
            onChange={platform.setMilestonePulse}
          />
          <Divider style={{ marginVertical: 12 }} />
          <SwitchRow title={t('settings_vibrate_round_title')} subtitle={t('settings_vibrate_round_desc')} value={s.roundVibration} onChange={platform.setRoundVibration} />
          {amplitude ? (
            <IntensityRow title={t('settings_round_intensity')} value={s.roundIntensity} enabled={s.roundVibration} onDone={platform.setRoundIntensity} />
          ) : (
            <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 12 }}>
              {t('vibration_strength_unsupported')}
            </Text>
          )}
        </SettingsSection>

        <SettingsSection title={t('backup_title')}>
          <BackupControls report={snackbar} />
        </SettingsSection>

        <SettingsSection title={t('settings_about')}>
          <View style={{ paddingVertical: 8, gap: 8 }}>
            <Text variant="titleMedium">{t('app_name')}</Text>
            <Text variant="bodyMedium" color="onSurfaceVariant">
              {t('settings_about_tagline')}
            </Text>
            <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 4 }}>
              {t('settings_version', Native.versionName)}
            </Text>
          </View>
        </SettingsSection>
      </ScrollView>
      <AccessibilitySetupDialog
        visible={showSetup}
        consented={s.accessibilityConsent}
        onConsent={() => {
          platform.setAccessibilityConsent(true);
          if (serviceEnabled) setShowSetup(false);
        }}
        onDismiss={() => setShowSetup(false)}
        onOpenSettings={() => {
          setShowSetup(false);
          platform.volume?.openAccessibilitySettings();
        }}
      />
    </View>
  );
}

/** Rounds per day, stepped through common values; 0 is off. The stepper shares the title's line. */
function DailyTarget({ target, onChange }: { target: number; onChange: (rounds: number) => void }) {
  const down = [...DAILY_TARGET_STEPS].reverse().find((v) => v < target) ?? 0;
  const up = DAILY_TARGET_STEPS.find((v) => v > target) ?? target;
  return (
    <View>
      <View style={styles.rowCenter}>
        <Text variant="bodyLarge" style={{ flex: 1 }}>
          {t('daily_target_title')}
        </Text>
        <IconButton name="remove" tonal label={t('daily_target_decrease')} disabled={target <= 0} onPress={() => onChange(down)} />
        <Text variant="titleSmall" color="primary" align="center" style={{ minWidth: 84 }} accessibilityLiveRegion="polite">
          {target > 0 ? tp('rounds_count', target) : t('daily_target_off')}
        </Text>
        <IconButton name="add" tonal label={t('daily_target_increase')} disabled={target >= DAILY_TARGET_STEPS[DAILY_TARGET_STEPS.length - 1]} onPress={() => onChange(up)} />
      </View>
      <Text variant="bodySmall" color="onSurfaceVariant">
        {t('daily_target_desc')}
      </Text>
    </View>
  );
}

function is24Hour(): boolean {
  return getCalendars()[0]?.uses24hourClock ?? false;
}

function formatMinute(minute: number): string {
  const d = new Date(2000, 0, 1, Math.floor(minute / 60), minute % 60);
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit', hour12: !is24Hour() }).format(d);
}

/** The optional daily reminder: a switch and a time (a dialog on Android, the inline picker on iOS). */
function ReminderControls({ enabled, minuteOfDay, onChange }: { enabled: boolean; minuteOfDay: number; onChange: (enabled: boolean, minute: number) => void }) {
  const [picking, setPicking] = useState(false);
  const timeText = formatMinute(minuteOfDay);
  return (
    <View>
      <SwitchRow title={t('reminder_setting_title')} subtitle={t('reminder_setting_desc', timeText)} value={enabled} onChange={(on) => onChange(on, minuteOfDay)} />
      {enabled ? (
        <View style={[styles.rowCenter, { marginTop: 4, minHeight: 48 }]}>
          <Text variant="bodyMedium" style={{ flex: 1 }}>
            {t('reminder_time')}
          </Text>
          {ios ? (
            <InlineTimePicker minuteOfDay={minuteOfDay} onPick={(m) => onChange(true, m)} />
          ) : (
            <PressScale onPress={() => setPicking(true)} accessibilityRole="button" accessibilityLabel={`${t('reminder_time')}, ${timeText}`} style={styles.timeButton}>
              <Text variant="titleMedium" color="primary">
                {timeText}
              </Text>
            </PressScale>
          )}
        </View>
      ) : null}
      <TimePicker
        visible={picking}
        minuteOfDay={minuteOfDay}
        is24Hour={is24Hour()}
        confirmLabel={t('ok')}
        cancelLabel={t('cancel')}
        onPick={(m) => {
          setPicking(false);
          onChange(true, m);
        }}
        onDismiss={() => setPicking(false)}
      />
    </View>
  );
}

const THEME_OPTIONS: { mode: ThemeMode; icon: IconName; label: 'theme_light' | 'theme_system' | 'theme_dark' }[] = [
  { mode: 'LIGHT', icon: 'lightMode', label: 'theme_light' },
  { mode: 'SYSTEM', icon: 'systemMode', label: 'theme_system' },
  { mode: 'DARK', icon: 'darkMode', label: 'theme_dark' },
];

/** Three round options in a pill (ThemeToggle.kt), read as a radio group. */
function ThemeToggle({ mode, onChange }: { mode: ThemeMode; onChange: (m: ThemeMode) => void }) {
  const { c } = useTheme();
  return (
    <View style={[styles.themePill, { backgroundColor: c.surfaceVariant }]} accessibilityRole="radiogroup">
      {THEME_OPTIONS.map((o, i) => {
        const selected = o.mode === mode;
        return (
          <PressScale
            key={o.mode}
            onPress={() => onChange(o.mode)}
            borderless
            accessibilityRole="radio"
            accessibilityLabel={t(o.label)}
            accessibilityState={{ selected }}
            accessibilityValue={{ text: `${i + 1} / 3` }}
            hitSlop={4}
            style={[styles.themeOption, { backgroundColor: selected ? c.primaryContainer : c.surfaceVariant }]}
          >
            <Icon name={o.icon} size={20} color={selected ? 'onPrimaryContainer' : 'onSurfaceVariant'} />
          </PressScale>
        );
      })}
    </View>
  );
}

/** Export and import of all data as a JSON file the user picks, with no account or network. */
function BackupControls({ report }: { report: (message: string) => void }) {
  const [busy, setBusy] = useState(false);
  const run = async (action: () => Promise<BackupOutcome>) => {
    setBusy(true);
    const outcome = await action();
    setBusy(false);
    switch (outcome.kind) {
      case 'exported':
        return report(tp('backup_exported', outcome.sessions, outcome.sessions, outcome.goals));
      case 'imported':
        return report(tp('backup_imported', outcome.result.sessionsAdded, outcome.result.sessionsAdded, outcome.result.goalsAdded + outcome.result.goalsUpdated));
      case 'notValid':
        return report(t('backup_not_valid'));
      case 'failed':
        return report(t(outcome.action === 'export' ? 'backup_export_failed' : 'backup_import_failed'));
    }
  };
  return (
    <View>
      <Text variant="bodySmall" color="onSurfaceVariant">
        {t('backup_desc')}
      </Text>
      <View style={[styles.rowCenter, { gap: 12, marginTop: 12 }]}>
        <Button variant="outlined" icon="upload" label={t('backup_export')} disabled={busy} onPress={() => run(platform.exportBackup)} style={{ flex: 1, paddingHorizontal: 12 }} />
        <Button variant="outlined" icon="download" label={t('backup_import')} disabled={busy} onPress={() => run(platform.importBackup)} style={{ flex: 1, paddingHorizontal: 12 }} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  rowCenter: { flexDirection: 'row', alignItems: 'center' },
  timeButton: { minHeight: 48, paddingHorizontal: 12, justifyContent: 'center', borderRadius: 20, overflow: 'hidden' },
  themePill: { flexDirection: 'row', borderRadius: 24, padding: 4, gap: 4 },
  themeOption: { width: 40, height: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
});
