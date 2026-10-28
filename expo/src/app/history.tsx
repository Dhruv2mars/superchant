/** History (HistoryScreen.kt): all-time stats, insights, and sessions grouped by today/week/month/older. */
import { useEffect, useMemo, useState } from 'react';
import { Alert, AppState, Platform, SectionList, StyleSheet, View } from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';
import { recordDuration, SessionRecord } from '@/core/model';
import { bucketSessions, millisUntilNextMidnight } from '@/core/stats';
import { t, tp } from '@/i18n';
import { platform } from '@/native/platform';
import { useHistory, useSettings } from '@/state/store';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { AppTopBar, BackButton, EmptyState } from '@/ui/chrome';
import { Card, Divider, IconButton } from '@/ui/controls';
import { Dialog } from '@/ui/Dialog';
import { Icon } from '@/ui/Icon';
import { firstDayOfWeek, InsightsCard } from '@/ui/insights';
import { Text } from '@/ui/Text';

const ios = Platform.OS === 'ios';

const Separator = () => <View style={{ height: 12 }} />;

/** Re-buckets at midnight while open and whenever the app comes back. */
function useNow(): number {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const id = setTimeout(() => setNow(Date.now()), millisUntilNextMidnight(now) + 1000);
    return () => clearTimeout(id);
  }, [now]);
  useEffect(() => {
    const sub = AppState.addEventListener('change', (s) => s === 'active' && setNow(Date.now()));
    return () => sub.remove();
  }, []);
  return now;
}

export default function History() {
  const history = useHistory();
  const settings = useSettings();
  const now = useNow();
  const [confirm, setConfirm] = useState(false);
  const sections = useMemo(() => {
    if (!history) return [];
    const b = bucketSessions(history, now, firstDayOfWeek());
    return [
      { title: t('history_today'), showDate: false, data: b.today },
      { title: t('history_this_week'), showDate: true, data: b.thisWeek },
      { title: t('history_this_month'), showDate: true, data: b.thisMonth },
      { title: t('history_older'), showDate: true, data: b.older },
    ].filter((s) => s.data.length > 0);
  }, [history, now]);

  const clear = () => {
    if (ios) {
      Alert.alert(t('history_clear_title'), t('history_clear_message'), [
        { text: t('cancel'), style: 'cancel' },
        { text: t('history_clear_confirm'), style: 'destructive', onPress: platform.clearHistory },
      ]);
    } else {
      setConfirm(true);
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <AppTopBar
        title={t('history')}
        navigation={<BackButton />}
        actions={history && history.length > 0 ? <IconButton name="deleteOutline" label={t('history_clear')} onPress={clear} /> : null}
      />
      {history?.length === 0 ? (
        <Animated.View entering={FadeIn} style={{ flex: 1 }}>
          <EmptyState icon="history" title={t('history_empty_title')} message={t('history_empty_message')} />
        </Animated.View>
      ) : history ? (
        <SectionList
          sections={sections}
          keyExtractor={(s) => s.id}
          stickySectionHeadersEnabled={false}
          contentContainerStyle={{ padding: 16, paddingBottom: 32 }}
          ItemSeparatorComponent={Separator}
          initialNumToRender={12}
          windowSize={7}
          ListHeaderComponent={
            <View style={{ gap: 12 }}>
              <SummaryCard sessions={history} />
              <InsightsCard sessions={history} dailyTarget={settings?.dailyTarget ?? 0} />
            </View>
          }
          renderSectionHeader={({ section }) => (
            <Text variant="titleSmall" color="primary" accessibilityRole="header" style={{ marginStart: 4, marginTop: 24, marginBottom: 12 }}>
              {section.title}
            </Text>
          )}
          renderItem={({ item, section }) => <SessionCard session={item} showDate={section.showDate} />}
        />
      ) : null}
      <Dialog
        visible={confirm}
        title={t('history_clear_title')}
        onDismiss={() => setConfirm(false)}
        actions={[
          { label: t('cancel'), onPress: () => setConfirm(false) },
          {
            label: t('history_clear_confirm'),
            style: 'destructive',
            onPress: () => {
              platform.clearHistory();
              setConfirm(false);
            },
          },
        ]}
      >
        <Text variant="bodyMedium" color="onSurfaceVariant">
          {t('history_clear_message')}
        </Text>
      </Dialog>
    </View>
  );
}

function SummaryCard({ sessions }: { sessions: SessionRecord[] }) {
  const { c } = useTheme();
  const totals = useMemo(
    () =>
      sessions.reduce(
        (a, s) => ({ counts: a.counts + s.totalCounts, rounds: a.rounds + s.totalRounds, millis: a.millis + recordDuration(s) }),
        { counts: 0, rounds: 0, millis: 0 },
      ),
    [sessions],
  );
  return (
    <Card tint={alpha(c.primaryContainer, 0.3)} style={{ padding: 20 }}>
      <Text variant="titleSmall" color="primary" accessibilityRole="header">
        {t('history_all_time_stats')}
      </Text>
      <View style={[styles.stats, { marginTop: 16 }]}>
        <Stat value={formatNumber(totals.counts)} label={t('history_stat_counts')} />
        <Stat value={formatNumber(totals.rounds)} label={t('history_stat_rounds')} />
        <Stat value={String(sessions.length)} label={t('history_stat_sessions')} />
      </View>
      <Divider color={alpha(c.outline, 0.2)} style={{ marginTop: 16, marginBottom: 12 }} />
      <View style={[styles.rowCenter, { justifyContent: 'center', gap: 8 }]}>
        <Icon name="schedule" size={16} />
        <Text variant="bodyMedium" color="onSurfaceVariant">
          {t('history_total_time', formatDurationLong(totals.millis))}
        </Text>
      </View>
    </Card>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <View style={{ alignItems: 'center' }} accessible accessibilityLabel={`${value} ${label}`}>
      <Text variant="headlineMedium" weight="600">
        {value}
      </Text>
      <Text variant="bodySmall" color="onSurfaceVariant">
        {label}
      </Text>
    </View>
  );
}

const dateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' });

function SessionCard({ session, showDate }: { session: SessionRecord; showDate: boolean }) {
  const { c } = useTheme();
  const start = new Date(session.startTime);
  return (
    <Card radius={12} style={{ padding: 16 }}>
      <View style={[styles.rowCenter, { justifyContent: 'space-between', alignItems: 'flex-start' }]}>
        <View>
          {showDate ? (
            <Text variant="titleSmall" weight="600">
              {dateFormat.format(start)}
            </Text>
          ) : null}
          <Text variant="bodyMedium" color={showDate ? 'onSurfaceVariant' : 'onSurface'}>
            {`${timeFormat.format(start)} • ${formatDuration(recordDuration(session))}`}
          </Text>
        </View>
        <View style={[styles.rowCenter, { gap: 20 }]}>
          <View style={{ alignItems: 'flex-end' }}>
            <Text variant="titleMedium" weight="600">
              {session.totalCounts}
            </Text>
            <Text variant="labelSmall" color="onSurfaceVariant">
              {tp('counts_label', session.totalCounts)}
            </Text>
          </View>
          <View style={{ alignItems: 'flex-end' }}>
            <Text variant="titleMedium" weight="600" color="primary">
              {session.totalRounds}
            </Text>
            <Text variant="labelSmall" color="onSurfaceVariant">
              {tp('rounds_label', session.totalRounds)}
            </Text>
          </View>
        </View>
      </View>
      {session.goalName != null ? (
        <View style={[styles.rowCenter, styles.goalChip, { backgroundColor: alpha(c.primaryContainer, 0.4) }]}>
          <Icon name="goals" size={14} color="primary" />
          <Text variant="labelMedium" color="primary" numberOfLines={1} style={{ marginStart: 6, flexShrink: 1 }}>
            {session.goalName}
          </Text>
        </View>
      ) : null}
    </Card>
  );
}

function formatDuration(millis: number): string {
  const hours = Math.floor(millis / 3_600_000);
  const minutes = Math.floor(millis / 60_000) % 60;
  const seconds = Math.floor(millis / 1000) % 60;
  if (hours > 0) return t('history_duration_hm', hours, minutes);
  if (minutes > 0) return t('history_duration_ms', minutes, seconds);
  return t('history_duration_s', seconds);
}

function formatDurationLong(millis: number): string {
  const hours = Math.floor(millis / 3_600_000);
  const minutes = Math.floor(millis / 60_000) % 60;
  if (hours > 0) return t('history_duration_hm', hours, minutes);
  if (minutes > 0) return tp('minutes_count', minutes);
  return t('history_less_than_minute');
}

const decimal = new Intl.NumberFormat(undefined, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const grouped = new Intl.NumberFormat();

function formatNumber(n: number): string {
  if (n >= 999_950) return `${decimal.format(n / 1_000_000)}M`;
  if (n >= 10_000) return `${decimal.format(n / 1_000)}K`;
  if (n >= 1_000) return grouped.format(n);
  return String(n);
}

const styles = StyleSheet.create({
  stats: { flexDirection: 'row', justifyContent: 'space-around' },
  rowCenter: { flexDirection: 'row', alignItems: 'center' },
  goalChip: { alignSelf: 'flex-start', marginTop: 12, borderRadius: 8, paddingHorizontal: 10, paddingVertical: 6 },
});
