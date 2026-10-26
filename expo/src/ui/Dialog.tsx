/**
 * A modal dialog: Material 3 on Android (28 dp corners, title, content, text buttons at the end), an alert
 * card on iOS (centered title, full-width buttons). It fades in within 200 ms on the strong ease-out and
 * leaves faster; nothing scales from zero.
 */
import { ReactNode, useEffect, useState } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import Animated, { FadeIn, FadeOut } from 'react-native-reanimated';
import { useTheme } from '@/theme';
import { alpha, over } from '@/theme/colors';
import { EaseOutStrong } from '@/theme/motion';
import { PressScale } from './controls';
import { Text } from './Text';

export interface DialogAction {
  label: string;
  onPress: () => void;
  /** "primary" filled button (Android) or bold (iOS); "destructive" in the error color. */
  style?: 'default' | 'primary' | 'destructive';
  disabled?: boolean;
}

const ios = Platform.OS === 'ios';

export function Dialog({
  visible,
  title,
  children,
  actions,
  onDismiss,
}: {
  visible: boolean;
  title: string;
  children?: ReactNode;
  actions: DialogAction[];
  onDismiss: () => void;
}) {
  const { c, dark } = useTheme();
  // Keep the modal mounted while the exit animation runs
  const [mounted, setMounted] = useState(visible);
  useEffect(() => {
    if (visible) setMounted(true);
  }, [visible]);
  if (!mounted && !visible) return null;
  const container = ios ? (dark ? '#2C2C2E' : '#F7F2EE') : over(c.surfaceVariant, dark ? 0.35 : 0.45, c.surface);
  return (
    <Modal transparent visible={mounted} onRequestClose={onDismiss} statusBarTranslucent navigationBarTranslucent animationType="none">
      {visible ? (
        <KeyboardAvoidingView behavior={ios ? 'padding' : undefined} style={styles.root}>
          <Animated.View entering={FadeIn.duration(180)} exiting={FadeOut.duration(140)} style={StyleSheet.absoluteFill}>
            <Pressable style={[StyleSheet.absoluteFill, { backgroundColor: alpha(c.scrim, 0.32) }]} onPress={onDismiss} accessibilityLabel={title} />
          </Animated.View>
          <Animated.View
            entering={FadeIn.duration(200).easing(EaseOutStrong)}
            exiting={FadeOut.duration(140)}
            style={[styles.card, ios ? styles.cardIos : styles.cardAndroid, { backgroundColor: container }]}
            accessibilityViewIsModal
          >
            <Text variant={ios ? 'titleMedium' : 'headlineSmall'} weight={ios ? '600' : '400'} align={ios ? 'center' : 'auto'} accessibilityRole="header">
              {title}
            </Text>
            {children ? (
              <ScrollView style={styles.body} contentContainerStyle={{ paddingTop: ios ? 8 : 16 }} bounces={false}>
                {children}
              </ScrollView>
            ) : null}
            <View style={ios ? styles.actionsIos : styles.actionsAndroid}>
              {actions.map((a) => (
                <DialogButton key={a.label} action={a} />
              ))}
            </View>
          </Animated.View>
        </KeyboardAvoidingView>
      ) : (
        <ExitTimer onDone={() => setMounted(false)} />
      )}
    </Modal>
  );
}

/** Unmounts the modal after the exit fade. */
function ExitTimer({ onDone }: { onDone: () => void }) {
  useEffect(() => {
    const id = setTimeout(onDone, 150);
    return () => clearTimeout(id);
  }, [onDone]);
  return null;
}

function DialogButton({ action }: { action: DialogAction }) {
  const { c } = useTheme();
  const color = action.style === 'destructive' ? 'error' : action.style === 'primary' && !ios ? 'onPrimary' : 'primary';
  return (
    <PressScale
      onPress={action.onPress}
      disabled={action.disabled}
      accessibilityRole="button"
      accessibilityLabel={action.label}
      style={[
        styles.button,
        ios ? styles.buttonIos : null,
        action.style === 'primary' && !ios ? { backgroundColor: c.primary, paddingHorizontal: 24 } : null,
      ]}
    >
      <Text variant={ios ? 'bodyLarge' : 'labelLarge'} color={color} weight={ios && action.style === 'primary' ? '600' : undefined}>
        {action.label}
      </Text>
    </PressScale>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, alignItems: 'center', justifyContent: 'center', padding: 24 },
  card: { width: '100%', maxWidth: 560, maxHeight: '86%' },
  cardAndroid: { borderRadius: 28, padding: 24, minWidth: 280 },
  cardIos: { borderRadius: 16, paddingTop: 20, paddingHorizontal: 16, paddingBottom: 8, maxWidth: 340 },
  body: { flexGrow: 0 },
  actionsAndroid: { flexDirection: 'row', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 8, marginTop: 24 },
  actionsIos: { marginTop: 16, gap: 4 },
  button: { minHeight: 40, borderRadius: 20, paddingHorizontal: 12, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  buttonIos: { minHeight: 44, borderRadius: 12 },
});
