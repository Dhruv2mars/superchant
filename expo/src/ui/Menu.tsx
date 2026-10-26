/** A Material dropdown menu anchored under its button (Android); it fades in fast and closes on outside tap. */
import { ReactNode, useRef, useState } from 'react';
import { Modal, Pressable, StyleSheet, View } from 'react-native';
import Animated, { FadeIn } from 'react-native-reanimated';
import { useTheme } from '@/theme';
import { over } from '@/theme/colors';
import { EaseOutStrong } from '@/theme/motion';
import { Icon, IconName } from './Icon';
import { Text } from './Text';

export interface MenuItem {
  label: string;
  icon: IconName;
  destructive?: boolean;
  onPress: () => void;
}

export function Menu({ anchor, items }: { anchor: (open: () => void) => ReactNode; items: MenuItem[] }) {
  const { c, dark } = useTheme();
  const ref = useRef<React.ComponentRef<typeof View>>(null);
  const [pos, setPos] = useState<{ x: number; y: number } | null>(null);
  const open = () =>
    ref.current?.measureInWindow((x: number, y: number, w: number, h: number) => {
      setPos({ x: x + w, y: y + h });
    });
  return (
    <View ref={ref} collapsable={false}>
      {anchor(open)}
      <Modal transparent visible={pos != null} onRequestClose={() => setPos(null)} statusBarTranslucent navigationBarTranslucent animationType="none">
        <Pressable style={StyleSheet.absoluteFill} onPress={() => setPos(null)} />
        {pos ? (
          <Animated.View
            entering={FadeIn.duration(150).easing(EaseOutStrong)}
            style={[styles.menu, { top: pos.y - 8, right: undefined, left: Math.max(8, pos.x - 180), backgroundColor: over(c.surfaceVariant, dark ? 0.6 : 0.5, c.surface) }]}
          >
            {items.map((item) => (
              <Pressable
                key={item.label}
                accessibilityRole="menuitem"
                android_ripple={{ color: c.outlineVariant }}
                onPress={() => {
                  setPos(null);
                  item.onPress();
                }}
                style={styles.item}
              >
                <Icon name={item.icon} color={item.destructive ? 'error' : 'onSurfaceVariant'} />
                <Text variant="labelLarge" color={item.destructive ? 'error' : 'onSurface'}>
                  {item.label}
                </Text>
              </Pressable>
            ))}
          </Animated.View>
        ) : null}
      </Modal>
    </View>
  );
}

const styles = StyleSheet.create({
  menu: { position: 'absolute', width: 180, borderRadius: 4, paddingVertical: 8, elevation: 3, shadowColor: '#000', shadowOpacity: 0.15, shadowRadius: 8, shadowOffset: { width: 0, height: 2 } },
  item: { flexDirection: 'row', alignItems: 'center', gap: 12, height: 48, paddingHorizontal: 12 },
});
