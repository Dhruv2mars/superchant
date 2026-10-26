/**
 * Material 3 colors from the Kotlin app (ui/theme/Color.kt). Primary is the lightest saffron that reaches
 * WCAG AA (4.5:1) as text on every light background the app uses.
 */
export interface Palette {
  primary: string;
  onPrimary: string;
  primaryContainer: string;
  onPrimaryContainer: string;
  secondary: string;
  secondaryContainer: string;
  onSecondaryContainer: string;
  error: string;
  onError: string;
  errorContainer: string;
  onErrorContainer: string;
  background: string;
  surface: string;
  onSurface: string;
  surfaceVariant: string;
  onSurfaceVariant: string;
  outline: string;
  outlineVariant: string;
  inverseSurface: string;
  inverseOnSurface: string;
  scrim: string;
  counterGold: string;
  progressTrack: string;
}

export const light: Palette = {
  primary: '#8C6136',
  onPrimary: '#FFFFFF',
  primaryContainer: '#FFDDB5',
  onPrimaryContainer: '#2D1600',
  secondary: '#735B4A',
  secondaryContainer: '#FFDCC8',
  onSecondaryContainer: '#29180C',
  error: '#BA1A1A',
  onError: '#FFFFFF',
  errorContainer: '#FFDAD6',
  onErrorContainer: '#410002',
  background: '#FFFBF8',
  surface: '#FFFBF8',
  onSurface: '#1F1B16',
  surfaceVariant: '#F2E0D0',
  onSurfaceVariant: '#504539',
  outline: '#71665B',
  outlineVariant: '#D5C4B5',
  inverseSurface: '#352F2B',
  inverseOnSurface: '#FAEFE7',
  scrim: '#000000',
  counterGold: '#D4A574',
  progressTrack: '#E8DDD4',
};

export const dark: Palette = {
  primary: '#FFB95A',
  onPrimary: '#4A2800',
  primaryContainer: '#6A3C00',
  onPrimaryContainer: '#FFDDB5',
  secondary: '#E3C0AA',
  secondaryContainer: '#5A4434',
  onSecondaryContainer: '#FFDCC8',
  error: '#FFB4AB',
  onError: '#690005',
  errorContainer: '#93000A',
  onErrorContainer: '#FFDAD6',
  background: '#16130F',
  surface: '#16130F',
  onSurface: '#EBE0D9',
  surfaceVariant: '#504539',
  onSurfaceVariant: '#D5C4B5',
  outline: '#9D8E80',
  outlineVariant: '#504539',
  inverseSurface: '#EBE0D9',
  inverseOnSurface: '#352F2B',
  scrim: '#000000',
  counterGold: '#FFB95A',
  progressTrack: '#3A3330',
};

/** `color` at `alpha` (0..1) as #RRGGBBAA, like Compose's Color.copy(alpha = ...). */
export function alpha(color: string, a: number): string {
  const hex = Math.round(Math.min(Math.max(a, 0), 1) * 255).toString(16).padStart(2, '0');
  return color.slice(0, 7) + hex;
}

/** `top` at `a` composited over an opaque `base`, as an opaque color (for contrast checks and flat fills). */
export function over(top: string, a: number, base: string): string {
  const c = (s: string, i: number) => parseInt(s.slice(1 + i * 2, 3 + i * 2), 16);
  return (
    '#' +
    [0, 1, 2]
      .map((i) => Math.round(c(top, i) * a + c(base, i) * (1 - a)).toString(16).padStart(2, '0'))
      .join('')
      .toUpperCase()
  );
}
