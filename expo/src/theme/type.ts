/**
 * Material 3 type scale from the Kotlin app (ui/theme/Type.kt), in the platform's default font (Roboto on
 * Android, SF Pro on iOS). Letter spacing in sp maps 1:1 to RN points.
 */
import { Platform, TextStyle } from 'react-native';

const w = (weight: '300' | '400' | '500' | '600'): TextStyle['fontWeight'] => weight;

// On iOS, SF Pro's default tracking is already tuned per size, so Material's positive tracking is dropped.
const track = (sp: number) => (Platform.OS === 'ios' && sp > 0 ? 0 : sp);

export const type = {
  displayLarge: { fontSize: 96, lineHeight: 112, fontWeight: w('300'), letterSpacing: -1.5 },
  headlineMedium: { fontSize: 28, lineHeight: 36, fontWeight: w('600'), letterSpacing: 0 },
  headlineSmall: { fontSize: 24, lineHeight: 32, fontWeight: w('600'), letterSpacing: 0 },
  titleLarge: { fontSize: 22, lineHeight: 28, fontWeight: w('500'), letterSpacing: 0 },
  titleMedium: { fontSize: 16, lineHeight: 24, fontWeight: w('500'), letterSpacing: track(0.15) },
  titleSmall: { fontSize: 14, lineHeight: 20, fontWeight: w('500'), letterSpacing: track(0.1) },
  bodyLarge: { fontSize: 16, lineHeight: 24, fontWeight: w('400'), letterSpacing: track(0.5) },
  bodyMedium: { fontSize: 14, lineHeight: 20, fontWeight: w('400'), letterSpacing: track(0.25) },
  bodySmall: { fontSize: 12, lineHeight: 16, fontWeight: w('400'), letterSpacing: track(0.4) },
  labelLarge: { fontSize: 14, lineHeight: 20, fontWeight: w('500'), letterSpacing: track(0.1) },
  labelMedium: { fontSize: 12, lineHeight: 16, fontWeight: w('500'), letterSpacing: track(0.5) },
  labelSmall: { fontSize: 11, lineHeight: 16, fontWeight: w('500'), letterSpacing: track(0.5) },
} satisfies Record<string, TextStyle>;

export type TypeStyle = keyof typeof type;
