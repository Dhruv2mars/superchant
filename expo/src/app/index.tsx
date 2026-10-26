/**
 * Home: the counter (HomeScreen.kt). Idle, it shows the empty ring and Start. During a session it shows the
 * controls (touch counting, pause, Stop) above the goal strip and the counter, which takes all remaining
 * space. The ring and its count are a native view that redraws itself on each count; this screen only
 * re-renders on coarse changes (start, stop, pause, a round, the goal advancing).
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Platform, ScrollView, StyleSheet, ToastAndroid, useWindowDimensions, View } from 'react-native';
import { router } from 'expo-router';
import { activateKeepAwakeAsync, deactivateKeepAwake } from 'expo-keep-awake';
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CounterView } from 'superchant-native';
import { Goal, isGoalComplete } from '@/core/model';
import { t, tp } from '@/i18n';
import { platform } from '@/native/platform';
import { getState, requestStart, useAppState, useHistory, useSession, useSettings } from '@/state/store';
import { useTheme } from '@/theme';
import { AccessibilitySetupDialog } from '@/ui/AccessibilitySetup';
import { AppTopBar } from '@/ui/chrome';
import { Button, IconButton } from '@/ui/controls';
import { GoalStrip, SessionControls, VolumeButtonsOffer } from '@/ui/home';
import { TodayLine } from '@/ui/insights';
import { Text } from '@/ui/Text';

const ios = Platform.OS === 'ios';
const SUMMARY_VISIBLE_MS = 6000;
const KEEP_AWAKE_TAG = 'touch-counting';

/** The session and volume readiness touch counting was last turned on for automatically (process-wide). */
let autoTouchHandled: string | null = null;

/** Whether the accessibility service is on, re-checked whenever the app comes back (Android). */
function useVolumeServiceEnabled(): boolean {
  const [enabled, setEnabled] = useState(() => platform.volume?.isServiceEnabled() ?? false);
  useEffect(() => {
    if (!platform.volume) return;
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') setEnabled(platform.volume!.isServiceEnabled());
    });
    return () => sub.remove();
  }, []);
  return enabled;
}

interface Summary {
  counts: number;
  rounds: number;
  minutes: number;
}

export default function Home() {
  const { dark } = useTheme();
  const insets = useSafeAreaInsets();
  const { width, height } = useWindowDimensions();
  const landscape = width > height;
  const settings = useSettings();
  const history = useHistory();
  const isActive = useSession((s) => s.isActive);
  const isPaused = useSession((s) => s.isPaused);
  const touchCounting = useSession((s) => s.touchCounting);
  const goalComplete = useSession(isGoalComplete);
  const sessionId = useSession((s) => (s.isActive ? s.sessionId : null));
  // Goal progress without the per-count fields, so the strip only changes at round boundaries
  const goalKey = useSession((s) => (s.activeGoal ? `${s.activeGoal.id}:${s.currentMantraIndex}:${s.mantraRoundsCompleted}` : null));
  const pendingStart = useAppState((s) => s.pendingStart);

  const serviceEnabled = useVolumeServiceEnabled();
  const consent = settings?.accessibilityConsent === true;
  const volumeReady = !ios && serviceEnabled && consent;
  const [showSetup, setShowSetup] = useState(false);

  // Awake only while taps can count: not when touch counting is off, paused or the goal is done
  const keepAwake = isActive && touchCounting && !isPaused && !goalComplete;
  useEffect(() => {
    if (!keepAwake) return;
    activateKeepAwakeAsync(KEEP_AWAKE_TAG);
    return () => {
      deactivateKeepAwake(KEEP_AWAKE_TAG);
    };
  }, [keepAwake]);

  // A session volume buttons can't count gets touch counting, once per session and readiness change, so
  // switching it off by hand sticks across navigation and reopening the app (Android).
  useEffect(() => {
    if (ios || !sessionId || settings == null) return;
    const key = `${sessionId}:${volumeReady}`;
    if (autoTouchHandled === key) return;
    autoTouchHandled = key;
    if (!volumeReady && !getState().session.touchCounting) platform.setTouchCounting(true);
  }, [sessionId, volumeReady, settings]);

  const beginSession = useCallback(
    async (goal: Goal | null) => {
      if (!ios && !(await platform.notificationsGranted())) {
        const granted = await platform.requestNotifications();
        // Counting doesn't need the notification (the service runs either way), so start and say why it's missing
        if (!granted) ToastAndroid.show(t('notifications_off_hint'), ToastAndroid.LONG);
      }
      platform.start(goal, ios || !volumeReady);
    },
    [volumeReady],
  );

  // A start asked for elsewhere: a plain "start counting" while a session runs just shows it; a goal starts
  // over. A session still to be restored after the process died counts as running: it is resumed, not ended.
  const handling = useRef(false);
  useEffect(() => {
    if (!pendingStart || settings == null || handling.current) return;
    handling.current = true;
    (async () => {
      const running = getState().session.isActive;
      const restorable = pendingStart.goal == null && !running && (await platform.hasResumableSession());
      requestStart(null);
      handling.current = false;
      if (restorable) platform.restore();
      else if (pendingStart.goal != null || !running) beginSession(pendingStart.goal);
    })();
  }, [pendingStart, settings, beginSession]);

  const [summary, setSummary] = useState<Summary | null>(null);
  useEffect(() => {
    if (!summary) return;
    const id = setTimeout(() => setSummary(null), SUMMARY_VISIBLE_MS);
    return () => clearTimeout(id);
  }, [summary]);

  const onStop = () => {
    const s = platform.getSession();
    if (s.count > 0) setSummary({ counts: s.count, rounds: s.rounds, minutes: Math.floor(Math.max(Date.now() - s.sessionStartTime, 0) / 60_000) });
    platform.stop();
  };

  const counter = (
    <CounterView dark={dark} volumeReady={volumeReady} style={styles.counter} />
  );
  const controls = isActive ? (
    <SessionControls
      session={{ touchCounting, isPaused, goalComplete }}
      showTouchToggle={!ios}
      showTouchHint={settings?.touchHintSeen === false}
      onTouchHintShown={platform.setTouchHintSeen}
      onTouchCounting={platform.setTouchCounting}
      onPauseToggle={() => platform.setPaused(!getState().session.isPaused)}
      onStop={onStop}
    />
  ) : null;
  const goal = goalKey ? getState().session.activeGoal : null;
  const strip = goal ? <GoalStrip key={goal.id} state={getState().session} goal={goal} /> : null;
  const offer = isActive && !ios && !volumeReady ? <VolumeButtonsOffer onPress={() => setShowSetup(true)} /> : null;
  const idle = !isActive ? (
    <View style={styles.idle}>
      {summary ? (
        <Animated.View entering={FadeIn.duration(200)} exiting={FadeOut.duration(200)}>
          <Text variant="bodyMedium" weight="500" color="primary" align="center" accessibilityLiveRegion="polite">
            {[
              t('session_saved'),
              summary.rounds > 0 ? tp('rounds_count', summary.rounds) : tp('insights_counts', summary.counts),
              summary.minutes > 0 ? tp('minutes_short', summary.minutes) : null,
            ]
              .filter(Boolean)
              .join(' · ')}
          </Text>
        </Animated.View>
      ) : null}
      {history && settings ? <TodayLine sessions={history} dailyTarget={settings.dailyTarget} /> : null}
      <Button
        label={t('action_start')}
        variant="tonal"
        large
        onPress={() => settings != null && beginSession(null)}
        style={{ width: landscape ? '100%' : '70%' }}
      />
    </View>
  ) : null;

  return (
    <View style={styles.screen}>
      <AppTopBar
        title={t('app_name')}
        actions={
          <>
            <IconButton name="goals" label={t('goals')} onPress={() => router.push('/goals')} />
            <IconButton name="history" label={t('history')} onPress={() => router.push('/history')} />
            <IconButton name="settings" label={t('settings')} onPress={() => router.push('/settings')} />
          </>
        }
      />
      {landscape ? (
        <View style={[styles.landscape, { paddingStart: 16 + insets.left, paddingEnd: 16 + insets.right, paddingBottom: 8 + insets.bottom }]}>
          <View style={{ flex: 1.5 }}>{counter}</View>
          <ScrollView style={{ flex: 1 }} contentContainerStyle={{ gap: 12 }}>
            {isActive ? (
              <>
                {controls}
                {strip}
                {offer}
              </>
            ) : (
              idle
            )}
          </ScrollView>
        </View>
      ) : (
        <View style={[styles.portrait, { paddingBottom: 4 + insets.bottom }]}>
          {controls}
          {strip}
          {/* The counter takes whatever is left, so everything else always fits */}
          <View style={{ flex: 1 }}>{counter}</View>
          {offer}
          {idle}
        </View>
      )}
      <AccessibilitySetupDialog
        visible={showSetup}
        consented={consent}
        onConsent={() => {
          platform.setAccessibilityConsent(true);
          // Already enabled (e.g. from an older version): nothing left to set up
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

const styles = StyleSheet.create({
  screen: { flex: 1 },
  portrait: { flex: 1, paddingHorizontal: 16, gap: 12 },
  landscape: { flex: 1, flexDirection: 'row', gap: 16, paddingTop: 8 },
  counter: { flex: 1 },
  idle: { alignItems: 'center', gap: 16 },
});
