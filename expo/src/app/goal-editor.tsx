/**
 * Create or edit a goal (GoalEditorScreen in GoalsScreen.kt): a name and an ordered list of mantras, each
 * with a target in rounds of 108. Closing with unsaved changes asks before discarding them; the new goal's
 * ID is fixed when the editor opens, so saving twice updates instead of duplicating.
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, BackHandler, Platform, ScrollView, StyleSheet, TextInput, View } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import Animated, { SharedValue, useAnimatedStyle } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Goal, MantraGoal, newId } from '@/core/model';
import { t, tp } from '@/i18n';
import { platform } from '@/native/platform';
import { getState } from '@/state/store';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { AppTopBar } from '@/ui/chrome';
import { Button, Divider, IconButton } from '@/ui/controls';
import { Dialog } from '@/ui/Dialog';
import { Icon } from '@/ui/Icon';
import { ReorderList } from '@/ui/ReorderList';
import { Text } from '@/ui/Text';

const ios = Platform.OS === 'ios';
const ROW = 64;

export default function GoalEditor() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const existing = useMemo(() => (id ? (getState().goals?.find((g) => g.id === id) ?? null) : null), [id]);
  const key = useRef(newId()).current;
  const insets = useSafeAreaInsets();
  const { c } = useTheme();
  const [name, setName] = useState(existing?.name ?? '');
  const [mantras, setMantras] = useState<MantraGoal[]>(existing?.mantras ?? []);
  const [editing, setEditing] = useState<{ index: number | null } | null>(null);
  const [discard, setDiscard] = useState(false);

  const valid = name.trim() !== '' && mantras.length > 0;
  const dirty = name !== (existing?.name ?? '') || JSON.stringify(mantras) !== JSON.stringify(existing?.mantras ?? []);

  const close = () => router.back();
  const requestClose = () => {
    if (!dirty) return close();
    if (ios) {
      Alert.alert(t('goals_discard_title'), t('goals_discard_message'), [
        { text: t('cancel'), style: 'cancel' },
        { text: t('goals_discard_confirm'), style: 'destructive', onPress: close },
      ]);
    } else {
      setDiscard(true);
    }
  };
  // System back closes the editor through the same check
  useEffect(() => {
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      requestClose();
      return true;
    });
    return () => sub.remove();
  });

  const save = () => {
    if (!valid) return;
    const goal: Goal = { id: existing?.id ?? key, name: name.trim(), mantras, createdAt: existing?.createdAt ?? Date.now() };
    platform.saveGoal(goal);
    close();
  };
  const move = (from: number, to: number) =>
    setMantras((list) => {
      const next = list.slice();
      next.splice(to, 0, next.splice(from, 1)[0]);
      return next;
    });

  return (
    <View style={{ flex: 1, backgroundColor: c.surface }}>
      <AppTopBar
        title={t(existing ? 'goals_edit_goal' : 'goals_create_goal')}
        navigation={<IconButton name="close" label={t('cancel')} color="onSurface" onPress={requestClose} />}
        actions={<IconButton name="check" label={t('common_save')} color={valid ? 'primary' : alpha(c.onSurface, 0.38)} disabled={!valid} onPress={save} />}
      />
      <Field label={t('goals_name_label')} placeholder={t('goals_name_placeholder')} value={name} onChangeText={setName} style={{ marginHorizontal: 16, marginVertical: 8 }} />
      <View style={[styles.header]}>
        <View>
          <Text variant="titleSmall" color="primary" accessibilityRole="header">
            {t('goals_mantras')}
          </Text>
          {mantras.length > 0 ? (
            <Text variant="bodySmall" color="onSurfaceVariant">
              {t('goals_reorder_hint')}
            </Text>
          ) : null}
        </View>
        <Button variant="text" icon="add" label={t('common_add')} onPress={() => setEditing({ index: null })} />
      </View>
      {mantras.length === 0 ? (
        <View style={styles.emptyMantras}>
          <Text variant="bodyLarge" color="onSurfaceVariant">
            {t('goals_no_mantras')}
          </Text>
          <Text variant="bodyMedium" color="outline" style={{ marginTop: 8 }}>
            {t('goals_no_mantras_hint')}
          </Text>
        </View>
      ) : (
        <ScrollView style={{ flex: 1 }} contentContainerStyle={{ paddingHorizontal: 16, paddingVertical: 8 }}>
          <ReorderList
            items={mantras}
            rowHeight={ROW}
            gap={8}
            onReorder={move}
            renderItem={(m, index, dragging) => (
              <MantraRow
                mantra={m}
                index={index}
                dragging={dragging}
                onEdit={() => setEditing({ index })}
                onDelete={() => setMantras((list) => list.filter((x) => x.id !== m.id))}
                onMoveUp={index > 0 ? () => move(index, index - 1) : undefined}
                onMoveDown={index < mantras.length - 1 ? () => move(index, index + 1) : undefined}
              />
            )}
          />
        </ScrollView>
      )}
      {mantras.length > 0 ? (
        <>
          <Divider />
          <View style={[styles.footer, { paddingBottom: 16 + insets.bottom }]}>
            <Text variant="bodyLarge">{t('goals_total')}</Text>
            <Text variant="bodyLarge" weight="600" color="primary">
              {tp('rounds_count', mantras.reduce((s, m) => s + m.targetRounds, 0))}
            </Text>
          </View>
        </>
      ) : null}
      {editing ? (
        <MantraDialog
          existing={editing.index != null ? mantras[editing.index] : null}
          onCancel={() => setEditing(null)}
          onSave={(m) => {
            setMantras((list) => (editing.index != null ? list.map((x, i) => (i === editing.index ? m : x)) : [...list, m]));
            setEditing(null);
          }}
        />
      ) : null}
      <Dialog
        visible={discard}
        title={t('goals_discard_title')}
        onDismiss={() => setDiscard(false)}
        actions={[
          { label: t('cancel'), onPress: () => setDiscard(false) },
          {
            label: t('goals_discard_confirm'),
            style: 'destructive',
            onPress: () => {
              setDiscard(false);
              close();
            },
          },
        ]}
      >
        <Text variant="bodyMedium" color="onSurfaceVariant">
          {t('goals_discard_message')}
        </Text>
      </Dialog>
    </View>
  );
}

function MantraRow({
  mantra,
  index,
  dragging,
  onEdit,
  onDelete,
  onMoveUp,
  onMoveDown,
}: {
  mantra: MantraGoal;
  index: number;
  dragging: SharedValue<boolean>;
  onEdit: () => void;
  onDelete: () => void;
  onMoveUp?: () => void;
  onMoveDown?: () => void;
}) {
  const { c } = useTheme();
  const rest = alpha(c.surfaceVariant, 0.5);
  const lifted = useAnimatedStyle(() => ({
    backgroundColor: dragging.value ? c.primaryContainer : rest,
    elevation: dragging.value ? 8 : 0,
    shadowOpacity: dragging.value ? 0.18 : 0,
  }));
  const actions = [
    ...(onMoveUp ? [{ name: 'moveUp', label: t('goals_move_up') }] : []),
    ...(onMoveDown ? [{ name: 'moveDown', label: t('goals_move_down') }] : []),
  ];
  return (
    <Animated.View
      style={[styles.row, { shadowColor: '#000', shadowRadius: 8, shadowOffset: { width: 0, height: 3 } }, lifted]}
      accessible
      accessibilityLabel={`${index + 1}. ${mantra.name}, ${tp('rounds_count', mantra.targetRounds)}`}
      accessibilityActions={actions}
      onAccessibilityAction={(e) => (e.nativeEvent.actionName === 'moveUp' ? onMoveUp?.() : onMoveDown?.())}
    >
      <View accessibilityLabel={t('goals_drag_to_reorder')}>
        <Icon name="dragHandle" />
      </View>
      <View style={[styles.badge, { backgroundColor: c.primary }]}>
        <Text variant="labelMedium" color="onPrimary">
          {index + 1}
        </Text>
      </View>
      <View style={{ flex: 1 }}>
        <Text variant="bodyLarge" numberOfLines={1}>
          {mantra.name}
        </Text>
        <Text variant="bodySmall" color="onSurfaceVariant">
          {tp('rounds_count', mantra.targetRounds)}
        </Text>
      </View>
      <IconButton name="edit" size={18} label={t('common_edit')} onPress={onEdit} />
      <IconButton name="delete" size={18} label={t('common_delete')} color={alpha(c.error, 0.7)} onPress={onDelete} />
    </Animated.View>
  );
}

function MantraDialog({ existing, onSave, onCancel }: { existing: MantraGoal | null; onSave: (m: MantraGoal) => void; onCancel: () => void }) {
  const [name, setName] = useState(existing?.name ?? '');
  const [rounds, setRounds] = useState(existing ? String(existing.targetRounds) : '1');
  const roundsRef = useRef<React.ComponentRef<typeof TextInput>>(null);
  const valid = name.trim() !== '' && Number(rounds) > 0;
  const submit = () => {
    if (valid) onSave({ id: existing?.id ?? newId(), name: name.trim(), targetRounds: Number(rounds) || 1 });
  };
  return (
    <Dialog
      visible
      title={t(existing ? 'goals_edit_mantra' : 'goals_add_mantra')}
      onDismiss={onCancel}
      actions={[
        { label: t('cancel'), onPress: onCancel },
        { label: t(existing ? 'common_save' : 'common_add'), onPress: submit, disabled: !valid, style: ios ? 'primary' : 'default' },
      ]}
    >
      <View style={{ gap: 16 }}>
        <Field
          label={t('goals_mantra_name_label')}
          placeholder={t('goals_mantra_name_placeholder')}
          value={name}
          onChangeText={setName}
          autoFocus={!existing}
          returnKeyType="next"
          onSubmitEditing={() => roundsRef.current?.focus()}
        />
        <Field
          ref={roundsRef}
          label={t('goals_target_rounds_label')}
          value={rounds}
          onChangeText={(v) => {
            if (v === '' || (/^\d+$/.test(v) && v.length <= 3)) setRounds(v);
          }}
          keyboardType="number-pad"
          returnKeyType="done"
          onSubmitEditing={submit}
          supporting={t('goals_round_is_108')}
        />
      </View>
    </Dialog>
  );
}

/** An outlined text field with its label above (Material outlined field, simplified). */
function Field({
  label,
  supporting,
  style,
  ref,
  ...input
}: React.ComponentProps<typeof TextInput> & { label: string; supporting?: string; ref?: React.Ref<React.ComponentRef<typeof TextInput>> }) {
  const { c } = useTheme();
  const [focused, setFocused] = useState(false);
  return (
    <View style={style}>
      <Text variant="bodySmall" color={focused ? 'primary' : 'onSurfaceVariant'} style={{ marginBottom: 4, marginStart: 4 }}>
        {label}
      </Text>
      <TextInput
        ref={ref}
        {...input}
        accessibilityLabel={label}
        placeholderTextColor={c.outline}
        selectionColor={c.primary}
        cursorColor={c.primary}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        style={[styles.input, { color: c.onSurface, borderColor: focused ? c.primary : c.outline, borderWidth: focused ? 2 : 1 }]}
      />
      {supporting ? (
        <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 4, marginStart: 4 }}>
          {supporting}
        </Text>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 12 },
  emptyMantras: { flex: 1, alignItems: 'center', justifyContent: 'center' },
  row: { height: ROW, borderRadius: 12, flexDirection: 'row', alignItems: 'center', paddingStart: 12, gap: 12 },
  badge: { width: 28, height: 28, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  footer: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 16, paddingTop: 16 },
  input: { minHeight: 52, borderRadius: 12, paddingHorizontal: 16, fontSize: 16 },
});
