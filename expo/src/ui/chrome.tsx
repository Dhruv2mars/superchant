/** Screen chrome shared by every screen (Common.kt): the top bar, back button and empty state. */
import { ReactNode } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';
import { t } from '@/i18n';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { IconButton } from './controls';
import { Icon, IconName } from './Icon';
import { Text } from './Text';

/**
 * Android: a Material small top app bar (title at the start). iOS: a standard navigation bar (centered
 * title, chevron back). The title stays on one line and shrinks a little on narrow screens.
 */
export function AppTopBar({ title, navigation, actions }: { title: string; navigation?: ReactNode; actions?: ReactNode }) {
  const { c } = useTheme();
  const insets = useSafeAreaInsets();
  const ios = Platform.OS === 'ios';
  return (
    <View style={[styles.bar, { paddingTop: insets.top, backgroundColor: c.surface, height: (ios ? 52 : 64) + insets.top }]}>
      {navigation || ios ? <View style={styles.side}>{navigation}</View> : null}
      <View style={[styles.titleBox, ios ? styles.titleCentered : { paddingStart: navigation ? 4 : 16 }]} pointerEvents="none">
        <Text
          variant="titleLarge"
          weight="600"
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.72}
          accessibilityRole="header"
          style={ios ? { fontSize: 17, lineHeight: 22 } : null}
        >
          {title}
        </Text>
      </View>
      <View style={[styles.side, styles.actions]}>{actions}</View>
    </View>
  );
}

export function BackButton({ onPress, icon = 'back', label }: { onPress?: () => void; icon?: IconName; label?: string }) {
  return (
    <IconButton
      name={icon}
      label={label ?? t('common_back')}
      color={Platform.OS === 'ios' ? 'primary' : 'onSurface'}
      onPress={onPress ?? (() => router.back())}
    />
  );
}

/** Centered icon badge, title and message for empty lists. */
export function EmptyState({ icon, title, message, action }: { icon: IconName; title: string; message: string; action?: ReactNode }) {
  const { c } = useTheme();
  return (
    <View style={styles.empty}>
      <View style={[styles.badge, { backgroundColor: alpha(c.primaryContainer, 0.3) }]}>
        <Icon name={icon} size={40} color="primary" />
      </View>
      <Text variant="titleLarge" style={{ marginTop: 24 }}>
        {title}
      </Text>
      <Text variant="bodyMedium" color="onSurfaceVariant" align="center" style={{ marginTop: 8 }}>
        {message}
      </Text>
      {action ? <View style={{ marginTop: 24 }}>{action}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 4 },
  side: { flexDirection: 'row', alignItems: 'center', minWidth: 48 },
  actions: { justifyContent: 'flex-end' },
  titleBox: { flex: 1, justifyContent: 'center' },
  titleCentered: { alignItems: 'center', paddingHorizontal: 8 },
  empty: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 32 },
  badge: { width: 80, height: 80, borderRadius: 40, alignItems: 'center', justifyContent: 'center' },
});
