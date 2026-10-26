/** Insights (Insights.kt): streak, a practice heatmap, per-mantra totals and Home's one-line summary. */
import { useEffect, useMemo, useState } from 'react';
import { AppState, StyleSheet, View } from 'react-native';
import { getCalendars } from 'expo-localization';
import { SessionRecord } from '@/core/model';
import { addDays, dailyTotals, DayKey, dayKey, DayTotal, heatLevel, mantraTotals, millisUntilNextMidnight, streak } from '@/core/stats';
import { t, tp } from '@/i18n';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { Button, Card, Divider } from './controls';
import { LinearProgress } from './home';
import { Text } from './Text';

/** Today's local date, updated at midnight and whenever the app comes back (e.g. after travelling). */
export function useToday(): DayKey {
  const [today, setToday] = useState(() => dayKey(new Date()));
  useEffect(() => {
    const id = setTimeout(() => setToday(dayKey(new Date())), millisUntilNextMidnight(Date.now()) + 1000);
    return () => clearTimeout(id);
  }, [today]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => {
      if (s === 'active') setToday(dayKey(new Date()));
    });
    return () => sub.remove();
  }, []);
  return today;
}

/** The locale's first day of the week, 1 = Sunday ... 7 = Saturday. */
export function firstDayOfWeek(): number {
  return getCalendars()[0]?.firstWeekday ?? 1;
}

export function InsightsCard({ sessions, dailyTarget }: { sessions: SessionRecord[]; dailyTarget: number }) {
  const today = useToday();
  const daily = useMemo(() => dailyTotals(sessions), [sessions]);
  const s = useMemo(() => streak(daily, today, dailyTarget), [daily, today, dailyTarget]);
  const mantras = useMemo(() => mantraTotals(sessions), [sessions]);
  const roundsToday = daily.get(today)?.rounds ?? 0;
  return (
    <Card style={{ padding: 20 }}>
      <Text variant="titleSmall" color="primary" accessibilityRole="header">
        {t('insights_title')}
      </Text>
      <View style={[styles.stats, { marginTop: 12 }]}>
        <Stat value={String(s.current)} label={tp('insights_streak_days', s.current)} />
        <Stat value={String(s.best)} label={t('insights_best_streak')} />
        <Stat value={dailyTarget > 0 ? `${roundsToday}/${dailyTarget}` : String(roundsToday)} label={t('insights_rounds_today')} />
      </View>
      <View style={{ height: 16 }} />
      <Heatmap daily={daily} today={today} dailyTarget={dailyTarget} />
      {mantras.length > 0 ? (
        <>
          <Divider style={{ marginVertical: 12 }} />
          <MantraTotals mantras={mantras} />
        </>
      ) : null}
    </Card>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <View style={{ alignItems: 'center' }} accessible accessibilityLabel={`${value} ${label}`}>
      <Text variant="headlineSmall" weight="600">
        {value}
      </Text>
      <Text variant="bodySmall" color="onSurfaceVariant">
        {label}
      </Text>
    </View>
  );
}

const WEEKS = 18;

/** The last 18 weeks, one column per week from the locale's first day; screen readers get one summary line. */
function Heatmap({ daily, today, dailyTarget }: { daily: Map<DayKey, DayTotal>; today: DayKey; dailyTarget: number }) {
  const { c } = useTheme();
  const [width, setWidth] = useState(0);
  const days = useMemo(() => {
    const [y, m, d] = today.split('-').map(Number);
    const dow = new Date(y, m - 1, d, 12).getDay() + 1;
    const back = (dow - firstDayOfWeek() + 7) % 7;
    const start = addDays(today, -back - (WEEKS - 1) * 7);
    const out: DayKey[] = [];
    for (let k = start; k <= today; k = addDays(k, 1)) out.push(k);
    return out;
  }, [today]);
  const reference = dailyTarget > 0 ? dailyTarget : Math.max(1, ...days.map((k) => daily.get(k)?.rounds ?? 0));
  const practiced = days.filter((k) => (daily.get(k)?.counts ?? 0) > 0).length;
  const summary = tp('insights_heatmap_summary', days.length, practiced, days.length);
  const levels = [alpha(c.onSurface, 0.07), alpha(c.primary, 0.3), alpha(c.primary, 0.5), alpha(c.primary, 0.75), c.primary];
  const gap = 3;
  const cell = width > 0 ? Math.min((width - gap * (WEEKS - 1)) / WEEKS, 16) : 0;
  return (
    <View accessible accessibilityLabel={summary}>
      <View onLayout={(e) => setWidth(e.nativeEvent.layout.width)} style={{ height: cell * 7 + gap * 6, flexDirection: 'row', justifyContent: 'center', gap }}>
        {cell > 0
          ? Array.from({ length: WEEKS }, (_, w) => (
              <View key={w} style={{ gap }}>
                {days.slice(w * 7, w * 7 + 7).map((k) => (
                  <View key={k} style={{ width: cell, height: cell, borderRadius: cell * 0.25, backgroundColor: levels[heatLevel(daily.get(k), reference)] }} />
                ))}
              </View>
            ))
          : null}
      </View>
      <View style={[styles.rowCenter, { marginTop: 8 }]}>
        <Text variant="bodySmall" color="onSurfaceVariant" style={{ flex: 1 }}>
          {summary}
        </Text>
        <Text variant="labelSmall" color="onSurfaceVariant">
          {t('insights_less')}
        </Text>
        {levels.map((l) => (
          <View key={l} style={{ width: 10, height: 10, borderRadius: 2.5, backgroundColor: l, marginStart: 3 }} />
        ))}
        <Text variant="labelSmall" color="onSurfaceVariant" style={{ marginStart: 3 }}>
          {t('insights_more')}
        </Text>
      </View>
    </View>
  );
}

const COLLAPSED_MANTRAS = 5;

function MantraTotals({ mantras }: { mantras: ReturnType<typeof mantraTotals> }) {
  const [showAll, setShowAll] = useState(false);
  const shown = showAll ? mantras : mantras.slice(0, COLLAPSED_MANTRAS);
  return (
    <View>
      <Text variant="labelLarge" color="onSurfaceVariant" style={{ marginBottom: 4 }}>
        {t('insights_mantras_title')}
      </Text>
      {shown.map((m) => (
        <View key={m.name.toLowerCase()} style={[styles.rowCenter, { paddingVertical: 4 }]}>
          <Text variant="bodyMedium" numberOfLines={1} style={{ flex: 1 }}>
            {m.name}
          </Text>
          <Text variant="bodySmall" color="primary" style={{ marginStart: 12 }}>
            {tp('rounds_count', m.rounds)}
          </Text>
          <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginStart: 12 }}>
            {tp('insights_counts', m.counts)}
          </Text>
        </View>
      ))}
      {mantras.length > COLLAPSED_MANTRAS ? (
        <Button variant="text" label={t(showAll ? 'insights_show_less' : 'insights_show_all')} onPress={() => setShowAll((v) => !v)} style={{ alignSelf: 'flex-start' }} />
      ) : null}
    </View>
  );
}

/** One quiet line for Home: today's rounds (against the target, if set) and the current streak. */
export function TodayLine({ sessions, dailyTarget }: { sessions: SessionRecord[]; dailyTarget: number }) {
  const today = useToday();
  const daily = useMemo(() => dailyTotals(sessions), [sessions]);
  const s = useMemo(() => streak(daily, today, dailyTarget), [daily, today, dailyTarget]);
  if (sessions.length === 0 && dailyTarget === 0) return null;
  const rounds = daily.get(today)?.rounds ?? 0;
  const todayText = dailyTarget > 0 ? tp('today_rounds_of_target', dailyTarget, rounds, dailyTarget) : tp('today_rounds', rounds);
  const text = s.current > 0 ? `${todayText} · ${tp('streak_days', s.current)}` : todayText;
  return (
    <View style={{ alignItems: 'center' }}>
      <Text variant="bodyMedium" color={s.todayDone && dailyTarget > 0 ? 'primary' : 'onSurfaceVariant'} align="center">
        {text}
      </Text>
      {dailyTarget > 0 ? <LinearProgress progress={rounds / dailyTarget} width={120} style={{ marginTop: 6 }} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  stats: { flexDirection: 'row', justifyContent: 'space-around' },
  rowCenter: { flexDirection: 'row', alignItems: 'center' },
});
