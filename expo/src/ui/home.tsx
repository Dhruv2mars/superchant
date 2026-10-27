/** Home pieces (HomeScreen.kt, OnScreenCounter.kt): session controls, the goal strip and the idle lines. */
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import * as Haptics from 'expo-haptics';
import Animated, { FadeIn, FadeOut, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { ChantState, Goal, goalTotalRounds, isGoalComplete } from '@/core/model';
import { t, tp } from '@/i18n';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { durations, EaseOutStrong } from '@/theme/motion';
import { Button, IconButton, PressScale } from './controls';
import { Icon } from './Icon';
import { Text } from './Text';

/**
 * Switch for touch counting: an outlined hand when off, a filled hand on a tinted circle when on. The tint
 * fades in over 180 ms (out in 120 ms) with a toggle haptic on the same frame as the icon change.
 */
export function TouchCountingToggle({ checked, onChange }: { checked: boolean; onChange: (on: boolean) => void }) {
  const { c } = useTheme();
  const fill = useSharedValue(checked ? 1 : 0);
  useEffect(() => {
    fill.value = withTiming(checked ? 1 : 0, { duration: checked ? durations.toggleOn : durations.toggleOff, easing: EaseOutStrong });
  }, [checked, fill]);
  const fillStyle = useAnimatedStyle(() => ({ opacity: fill.value }));
  const ringStyle = useAnimatedStyle(() => ({ opacity: 1 - fill.value }));
  return (
    <PressScale
      onPress={() => {
        Haptics.performAndroidHapticsAsync(checked ? Haptics.AndroidHaptics.Toggle_Off : Haptics.AndroidHaptics.Toggle_On);
        onChange(!checked);
      }}
      accessibilityRole="switch"
      accessibilityLabel={t('touch_counting')}
      accessibilityState={{ checked }}
      borderless
      style={styles.toggle}
    >
      <Animated.View style={[StyleSheet.absoluteFill, styles.round, { borderWidth: 1, borderColor: c.outline }, ringStyle]} />
      <Animated.View style={[StyleSheet.absoluteFill, styles.round, { backgroundColor: c.primaryContainer }, fillStyle]} />
      <Icon name={checked ? 'touchOn' : 'touch'} color={checked ? 'onPrimaryContainer' : 'onSurfaceVariant'} />
    </PressScale>
  );
}

/** A plain tooltip under the toggle for the first session ever: shown after 600 ms, for 5 s. */
export function TouchHint({ show, onShown }: { show: boolean; onShown: () => void }) {
  const { c } = useTheme();
  const [visible, setVisible] = useState(false);
  const [started, setStarted] = useState(false);
  const latched = useRef(false);
  useEffect(() => {
    if (!show || latched.current) return;
    latched.current = true;
    setStarted(true);
    onShown();
  }, [show, onShown]);
  // Marking the hint seen turns `show` off, so the timers can't depend on it
  useEffect(() => {
    if (!started) return;
    const a = setTimeout(() => setVisible(true), 600);
    const b = setTimeout(() => setVisible(false), 5600);
    return () => {
      clearTimeout(a);
      clearTimeout(b);
    };
  }, [started]);
  if (!visible) return null;
  return (
    <Animated.View
      entering={FadeIn.duration(150)}
      exiting={FadeOut.duration(150)}
      pointerEvents="none"
      style={[styles.tooltip, { backgroundColor: c.inverseSurface }]}
      accessibilityLiveRegion="polite"
    >
      <Text variant="bodySmall" color="inverseOnSurface">
        {t('touch_counting_hint')}
      </Text>
    </Animated.View>
  );
}

export function SessionControls({
  session,
  showTouchToggle,
  showTouchHint,
  onTouchHintShown,
  onTouchCounting,
  onPauseToggle,
  onStop,
}: {
  session: Pick<ChantState, 'touchCounting' | 'isPaused'> & { goalComplete: boolean };
  showTouchToggle: boolean;
  showTouchHint: boolean;
  onTouchHintShown: () => void;
  onTouchCounting: (on: boolean) => void;
  onPauseToggle: () => void;
  onStop: () => void;
}) {
  return (
    <View style={styles.controls}>
      {showTouchToggle ? (
        <View>
          <TouchCountingToggle checked={session.touchCounting} onChange={onTouchCounting} />
          <TouchHint show={showTouchHint} onShown={onTouchHintShown} />
        </View>
      ) : null}
      <View style={{ flex: 1 }} />
      <IconButton
        name={session.isPaused ? 'play' : 'pause'}
        label={t(session.isPaused ? 'action_resume' : 'action_pause')}
        tonal
        disabled={session.goalComplete}
        onPress={onPauseToggle}
      />
      <Button label={t('action_stop')} variant="stop" onPress={onStop} />
    </View>
  );
}

/** Quiet offer to count with the volume buttons too, shown while they can't (Android). */
export function VolumeButtonsOffer({ onPress }: { onPress: () => void }) {
  return <Button label={t('volume_buttons_offer')} variant="text" icon="volume" onPress={onPress} style={{ alignSelf: 'center' }} />;
}

/**
 * Compact goal progress: name, current mantra and overall progress in a fixed-height card; tap to expand
 * the mantra list (height-capped and scrollable). Changes only at round boundaries.
 */
export function GoalStrip({ state, goal }: { state: ChantState; goal: Goal }) {
  const { c } = useTheme();
  const [expanded, setExpanded] = useState(false);
  const complete = isGoalComplete(state);
  const mantra = goal.mantras[state.currentMantraIndex];
  const total = goalTotalRounds(goal);
  const done = goal.mantras.slice(0, state.currentMantraIndex).reduce((s, m) => s + m.targetRounds, 0) + state.mantraRoundsCompleted;
  const progress = total > 0 ? Math.min(done / total, 1) : 0;
  return (
    <PressScale
      scaleTo={0.98}
      onPress={() => setExpanded((e) => !e)}
      accessibilityRole="button"
      accessibilityState={{ expanded }}
      accessibilityHint={t(expanded ? 'goal_hide_mantras' : 'goal_show_mantras')}
      style={[styles.strip, { backgroundColor: complete ? alpha(c.primaryContainer, 0.5) : alpha(c.surfaceVariant, 0.5) }]}
    >
      <View style={styles.rowCenter}>
        <Text variant="titleSmall" weight="600" numberOfLines={1} style={{ flex: 1 }}>
          {goal.name}
        </Text>
        {complete ? (
          <Icon name="checkCircle" size={20} color="primary" />
        ) : (
          <Text variant="labelMedium" color="onSurfaceVariant">
            {t('goal_mantra_position', state.currentMantraIndex + 1, goal.mantras.length)}
          </Text>
        )}
        <View style={{ marginStart: 8 }}>
          <Icon name={expanded ? 'expandLess' : 'expandMore'} size={20} />
        </View>
      </View>
      <View style={[styles.rowCenter, { marginTop: 2 }]}>
        {complete || !mantra ? (
          <Text variant="bodyMedium" color="primary">
            {tp('goal_all_rounds_done', total, total)}
          </Text>
        ) : (
          <>
            <Text variant="bodyMedium" numberOfLines={1} style={{ flex: 1 }}>
              {mantra.name}
            </Text>
            <Text variant="bodySmall" color="primary">
              {tp('goal_mantra_progress', mantra.targetRounds, state.mantraRoundsCompleted, mantra.targetRounds)}
            </Text>
          </>
        )}
      </View>
      <LinearProgress progress={progress} style={{ marginTop: 8 }} />
      {expanded ? (
        <Animated.ScrollView entering={FadeIn.duration(200)} style={{ maxHeight: 160, marginTop: 8 }} nestedScrollEnabled>
          {goal.mantras.map((m, index) => {
            const current = index === state.currentMantraIndex;
            const completed = index < state.currentMantraIndex || (current && state.mantraRoundsCompleted >= m.targetRounds);
            return (
              <View key={m.id} style={[styles.rowCenter, { paddingVertical: 4 }]}>
                <View
                  style={[
                    styles.badge,
                    { backgroundColor: completed ? c.primary : current ? c.primaryContainer : c.surfaceVariant },
                  ]}
                >
                  {completed ? (
                    <Icon name="checkCircle" size={18} color="onPrimary" />
                  ) : (
                    <Text variant="labelMedium" color={current ? 'onPrimaryContainer' : 'onSurfaceVariant'}>
                      {index + 1}
                    </Text>
                  )}
                </View>
                <Text variant="bodyMedium" weight={current ? '600' : '400'} color={current ? 'onSurface' : 'onSurfaceVariant'} numberOfLines={1} style={{ flex: 1, marginStart: 12 }}>
                  {m.name}
                </Text>
                <Text variant="bodySmall" color={current && !completed ? 'primary' : 'onSurfaceVariant'}>
                  {current && !completed
                    ? tp('goal_mantra_progress', m.targetRounds, state.mantraRoundsCompleted, m.targetRounds)
                    : tp('rounds_count', m.targetRounds)}
                </Text>
              </View>
            );
          })}
        </Animated.ScrollView>
      ) : null}
    </PressScale>
  );
}

/** Material linear progress: a 4 dp track with a gap before the remaining part. */
export function LinearProgress({ progress, style, width }: { progress: number; style?: object; width?: number }) {
  const { c } = useTheme();
  const p = Math.min(Math.max(progress, 0), 1);
  return (
    <View
      style={[{ height: 4, flexDirection: 'row', width }, style]}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(p * 100) }}
    >
      {p > 0 ? <View style={{ flex: p, backgroundColor: c.primary, borderRadius: 2 }} /> : null}
      {p < 1 ? <View style={{ flex: 1 - p, backgroundColor: c.primaryContainer, borderRadius: 2, marginStart: p > 0 ? 4 : 0 }} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  toggle: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24 },
  round: { borderRadius: 24 },
  tooltip: { position: 'absolute', top: 52, left: 0, width: 220, borderRadius: 4, paddingHorizontal: 8, paddingVertical: 6, zIndex: 10 },
  controls: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingTop: 4, zIndex: 2 },
  strip: { borderRadius: 16, paddingHorizontal: 16, paddingVertical: 12, overflow: 'hidden' },
  rowCenter: { flexDirection: 'row', alignItems: 'center' },
  badge: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
});
