/** Goals (GoalsScreen.kt): saved goals with Start, edit and delete; the editor opens as a modal screen. */
import { useState } from 'react';
import { ActionSheetIOS, Alert, FlatList, Platform, StyleSheet, View } from 'react-native';
import { router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Goal, goalTotalRounds } from '@/core/model';
import { t, tp } from '@/i18n';
import { platform } from '@/native/platform';
import { requestStart, useGoals } from '@/state/store';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { AppTopBar, BackButton, EmptyState } from '@/ui/chrome';
import { Button, IconButton, PressScale } from '@/ui/controls';
import { Dialog } from '@/ui/Dialog';
import { Icon } from '@/ui/Icon';
import { Menu } from '@/ui/Menu';
import { Text } from '@/ui/Text';

const ios = Platform.OS === 'ios';

export default function Goals() {
  const goals = useGoals();
  const insets = useSafeAreaInsets();
  const { c } = useTheme();
  const [toDelete, setToDelete] = useState<Goal | null>(null);

  const start = (goal: Goal) => {
    requestStart({ goal });
    router.back();
  };
  const confirmDelete = (goal: Goal) => {
    if (ios) {
      Alert.alert(t('goals_delete_title'), t('goals_delete_message', goal.name), [
        { text: t('cancel'), style: 'cancel' },
        { text: t('common_delete'), style: 'destructive', onPress: () => platform.deleteGoal(goal.id) },
      ]);
    } else {
      setToDelete(goal);
    }
  };

  return (
    <View style={{ flex: 1 }}>
      <AppTopBar
        title={t('goals')}
        navigation={<BackButton />}
        actions={ios ? <IconButton name="add" label={t('goals_create_goal')} color="primary" onPress={() => router.push('/goal-editor')} /> : null}
      />
      {goals?.length === 0 ? (
        <EmptyState
          icon="goals"
          title={t('goals_empty_title')}
          message={t('goals_empty_message')}
          action={<Button label={t('goals_create_first')} icon="add" onPress={() => router.push('/goal-editor')} />}
        />
      ) : (
        <FlatList
          data={goals ?? []}
          keyExtractor={(g) => g.id}
          contentContainerStyle={{ padding: 16, gap: 16, paddingBottom: 96 + insets.bottom }}
          renderItem={({ item }) => <GoalCard goal={item} onStart={() => start(item)} onDelete={() => confirmDelete(item)} />}
        />
      )}
      {!ios && goals?.length !== 0 ? (
        <PressScale
          onPress={() => router.push('/goal-editor')}
          accessibilityRole="button"
          accessibilityLabel={t('goals_create_goal')}
          rippleColor={alpha(c.onPrimary, 0.2)}
          style={[styles.fab, { backgroundColor: c.primary, bottom: 16 + insets.bottom }]}
        >
          <Icon name="add" color="onPrimary" />
        </PressScale>
      ) : null}
      <Dialog
        visible={toDelete != null}
        title={t('goals_delete_title')}
        onDismiss={() => setToDelete(null)}
        actions={[
          { label: t('cancel'), onPress: () => setToDelete(null) },
          {
            label: t('common_delete'),
            style: 'destructive',
            onPress: () => {
              if (toDelete) platform.deleteGoal(toDelete.id);
              setToDelete(null);
            },
          },
        ]}
      >
        <Text variant="bodyMedium" color="onSurfaceVariant">
          {toDelete ? t('goals_delete_message', toDelete.name) : ''}
        </Text>
      </Dialog>
    </View>
  );
}

function GoalCard({ goal, onStart, onDelete }: { goal: Goal; onStart: () => void; onDelete: () => void }) {
  const { c } = useTheme();
  const total = goalTotalRounds(goal);
  const edit = () => router.push({ pathname: '/goal-editor', params: { id: goal.id } });
  const options = () => {
    if (ios) {
      ActionSheetIOS.showActionSheetWithOptions(
        { title: goal.name, options: [t('common_edit'), t('common_delete'), t('cancel')], destructiveButtonIndex: 1, cancelButtonIndex: 2 },
        (i) => (i === 0 ? edit() : i === 1 ? onDelete() : undefined),
      );
    }
  };
  const header = (
    <View style={{ flex: 1 }}>
      <Text variant="titleMedium" weight="600" numberOfLines={1}>
        {goal.name}
      </Text>
      <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 2 }}>
        {`${tp('mantras_count', goal.mantras.length)} • ${tp('rounds_total', total)}`}
      </Text>
    </View>
  );
  return (
    <PressScale scaleTo={0.98} onPress={onStart} accessibilityRole="button" accessibilityLabel={`${goal.name}. ${t('goals_start_session')}`} style={[styles.card, { backgroundColor: alpha(c.surfaceVariant, 0.5) }]}>
      <View style={styles.rowCenter}>
        {header}
        {ios ? (
          <IconButton name="more" label={t('goals_options')} onPress={options} />
        ) : (
          <Menu
            anchor={(open) => <IconButton name="more" label={t('goals_options')} onPress={open} />}
            items={[
              { label: t('common_edit'), icon: 'edit', onPress: edit },
              { label: t('common_delete'), icon: 'delete', destructive: true, onPress: onDelete },
            ]}
          />
        )}
      </View>
      <View style={{ marginTop: 12 }}>
        {goal.mantras.slice(0, 3).map((m) => (
          <View key={m.id} style={[styles.rowCenter, { paddingVertical: 4 }]}>
            <View style={[styles.dot, { backgroundColor: c.primary }]} />
            <Text variant="bodyMedium" numberOfLines={1} style={{ flex: 1, marginStart: 12 }}>
              {m.name}
            </Text>
            <Text variant="bodySmall" color="primary">{`${m.targetRounds}×`}</Text>
          </View>
        ))}
        {goal.mantras.length > 3 ? (
          <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 4, marginStart: 18 }}>
            {tp('goals_more_mantras', goal.mantras.length - 3)}
          </Text>
        ) : null}
      </View>
      <PressScale onPress={onStart} accessibilityRole="button" accessibilityLabel={t('goals_start_session')} rippleColor={alpha(c.onPrimary, 0.2)} style={[styles.start, { backgroundColor: c.primary }]}>
        <Icon name="play" size={20} color="onPrimary" />
        <Text variant="labelLarge" color="onPrimary">
          {t('goals_start_session')}
        </Text>
      </PressScale>
    </PressScale>
  );
}

const styles = StyleSheet.create({
  card: { borderRadius: 16, padding: 16, overflow: 'hidden' },
  rowCenter: { flexDirection: 'row', alignItems: 'center' },
  dot: { width: 6, height: 6, borderRadius: 3 },
  start: { marginTop: 12, borderRadius: 12, paddingVertical: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, overflow: 'hidden' },
  fab: { position: 'absolute', end: 16, width: 56, height: 56, borderRadius: 16, alignItems: 'center', justifyContent: 'center', overflow: 'hidden', elevation: 3 },
});
