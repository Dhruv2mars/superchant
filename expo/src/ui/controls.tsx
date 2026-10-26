/**
 * Pressable building blocks. Every control answers the finger the instant it lands: it shrinks to 0.95 in
 * 100 ms and settles back in 160 ms (Motion.kt's pressScale), with the platform ripple on Android.
 */
import { ReactNode } from 'react';
import { Platform, Pressable, PressableProps, StyleProp, StyleSheet, View, ViewStyle } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';
import { useTheme } from '@/theme';
import { alpha, Palette } from '@/theme/colors';
import { durations, EaseOutStrong, PRESS_SCALE } from '@/theme/motion';
import { Icon, IconName } from './Icon';
import { Text } from './Text';

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

interface PressScaleProps extends Omit<PressableProps, 'style'> {
  style?: StyleProp<ViewStyle>;
  /** Ripple color (Android); defaults to a translucent onSurface. */
  rippleColor?: string;
  /** Borderless ripple, for round icon buttons. */
  borderless?: boolean;
  scaleTo?: number;
  children?: ReactNode;
}

export function PressScale({ style, rippleColor, borderless, scaleTo = PRESS_SCALE, onPressIn, onPressOut, disabled, ...rest }: PressScaleProps) {
  const { c } = useTheme();
  const scale = useSharedValue(1);
  const animated = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));
  return (
    <AnimatedPressable
      {...rest}
      disabled={disabled}
      android_ripple={{ color: rippleColor ?? alpha(c.onSurface, 0.12), borderless, foreground: true }}
      onPressIn={(e) => {
        scale.value = withTiming(scaleTo, { duration: durations.pressIn, easing: EaseOutStrong });
        onPressIn?.(e);
      }}
      onPressOut={(e) => {
        scale.value = withTiming(1, { duration: durations.pressOut, easing: EaseOutStrong });
        onPressOut?.(e);
      }}
      style={[style, animated, disabled ? { opacity: 0.38 } : null]}
    />
  );
}

export function IconButton({
  name,
  label,
  onPress,
  color = 'onSurfaceVariant',
  tonal,
  disabled,
  size = 24,
}: {
  name: IconName;
  label: string;
  onPress: () => void;
  color?: keyof Palette | (string & {});
  tonal?: boolean;
  disabled?: boolean;
  size?: number;
}) {
  const { c } = useTheme();
  return (
    <PressScale
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={4}
      borderless={!tonal}
      style={[styles.iconButton, tonal ? { backgroundColor: c.secondaryContainer } : null]}
    >
      <Icon name={name} size={size} color={tonal ? 'onSecondaryContainer' : color} />
    </PressScale>
  );
}

type ButtonVariant = 'filled' | 'tonal' | 'outlined' | 'text' | 'stop';

export function Button({
  label,
  onPress,
  variant = 'filled',
  icon,
  disabled,
  style,
  large,
  labelColor,
}: {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  icon?: IconName;
  disabled?: boolean;
  style?: StyleProp<ViewStyle>;
  /** The big Start pill: 56 dp, titleLarge. */
  large?: boolean;
  labelColor?: keyof Palette;
}) {
  const { c } = useTheme();
  const look: Record<ButtonVariant, { bg?: string; fg: keyof Palette; border?: string }> = {
    filled: { bg: c.primary, fg: 'onPrimary' },
    tonal: { bg: c.primaryContainer, fg: 'onPrimaryContainer' },
    outlined: { fg: 'primary', border: c.outline },
    text: { fg: 'primary' },
    stop: { bg: c.errorContainer, fg: 'onErrorContainer' },
  };
  const l = look[variant];
  const isPill = variant === 'tonal' || variant === 'stop';
  return (
    <PressScale
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={[
        styles.button,
        { borderRadius: isPill ? 32 : 20, minHeight: large ? 56 : 48, paddingHorizontal: large ? 32 : variant === 'text' ? 12 : 24 },
        l.bg ? { backgroundColor: l.bg } : null,
        l.border ? { borderWidth: 1, borderColor: l.border } : null,
        style,
      ]}
    >
      {icon ? <Icon name={icon} size={18} color={labelColor ?? l.fg} /> : null}
      <Text
        variant={large ? 'titleLarge' : variant === 'stop' ? 'titleMedium' : 'labelLarge'}
        color={labelColor ?? l.fg}
        weight={isPill ? '600' : undefined}
        numberOfLines={1}
      >
        {label}
      </Text>
    </PressScale>
  );
}

/** A tinted rounded surface (Material Card with a translucent surfaceVariant fill). */
export function Card({ children, style, tint, radius = 16 }: { children: ReactNode; style?: StyleProp<ViewStyle>; tint?: string; radius?: number }) {
  const { c } = useTheme();
  return <View style={[{ backgroundColor: tint ?? alpha(c.surfaceVariant, 0.5), borderRadius: radius }, style]}>{children}</View>;
}

export function Divider({ style, color }: { style?: StyleProp<ViewStyle>; color?: string }) {
  const { c } = useTheme();
  return <View style={[{ height: StyleSheet.hairlineWidth * (Platform.OS === 'android' ? 2 : 1), backgroundColor: color ?? c.outlineVariant }, style]} />;
}

const styles = StyleSheet.create({
  iconButton: { width: 48, height: 48, borderRadius: 24, alignItems: 'center', justifyContent: 'center', overflow: 'hidden' },
  button: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8, overflow: 'hidden' },
});
