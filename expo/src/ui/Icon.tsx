/**
 * Icons: SF Symbols on iOS, Material Symbols on Android (the Compose app's icon family), so each platform
 * looks like itself. The Material font is loaded before the first frame (see iconFonts), so icons never pop in.
 */
import { SymbolView } from 'expo-symbols';
import type { SFSymbol } from 'sf-symbols-typescript';
import { Platform, Text, View } from 'react-native';
import { useTheme } from '@/theme';
import { Palette } from '@/theme/colors';

const ICONS = {
  goals: { ios: 'flag', android: 'flag' },
  history: { ios: 'clock.arrow.circlepath', android: 'history' },
  settings: { ios: 'gearshape', android: 'settings' },
  back: { ios: 'chevron.backward', android: 'arrow_back' },
  close: { ios: 'xmark', android: 'close' },
  check: { ios: 'checkmark', android: 'check' },
  checkCircle: { ios: 'checkmark.circle.fill', android: 'check_circle' },
  block: { ios: 'nosign', android: 'block' },
  pause: { ios: 'pause.fill', android: 'pause' },
  play: { ios: 'play.fill', android: 'play_arrow' },
  touch: { ios: 'hand.tap', android: 'touch_app' },
  touchOn: { ios: 'hand.tap.fill', android: 'touch_app' },
  expandMore: { ios: 'chevron.down', android: 'expand_more' },
  expandLess: { ios: 'chevron.up', android: 'expand_less' },
  volume: { ios: 'speaker.wave.2', android: 'volume_up' },
  add: { ios: 'plus', android: 'add' },
  remove: { ios: 'minus', android: 'remove' },
  edit: { ios: 'pencil', android: 'edit' },
  delete: { ios: 'trash', android: 'delete' },
  deleteOutline: { ios: 'trash', android: 'delete' },
  more: { ios: 'ellipsis', android: 'more_vert' },
  dragHandle: { ios: 'line.3.horizontal', android: 'drag_handle' },
  schedule: { ios: 'clock', android: 'schedule' },
  lightMode: { ios: 'sun.max', android: 'light_mode' },
  darkMode: { ios: 'moon', android: 'dark_mode' },
  systemMode: { ios: 'circle.lefthalf.filled', android: 'settings_brightness' },
  upload: { ios: 'square.and.arrow.up', android: 'file_upload' },
  download: { ios: 'square.and.arrow.down', android: 'file_download' },
} as const;

export type IconName = keyof typeof ICONS;

/** Glyphs drawn filled on Android (the Compose app used the filled variants for these). */
const FILLED: Partial<Record<IconName, true>> = { touchOn: true, checkCircle: true, pause: true, play: true };

// Material Symbols codepoints by name, from expo-symbols
const symbols: Record<string, number> = require('expo-symbols/build/android/symbols.json');

export const iconFonts = {
  MaterialSymbols: require('@expo-google-fonts/material-symbols/400Regular/MaterialSymbols_400Regular.ttf'),
  MaterialSymbolsFilled: require('@expo-google-fonts/material-symbols/400Regular_Filled/MaterialSymbols_400Regular_Filled.ttf'),
};

export function Icon({ name, size = 24, color = 'onSurfaceVariant' }: { name: IconName; size?: number; color?: keyof Palette | (string & {}) }) {
  const { c } = useTheme();
  const tint = (c as unknown as Record<string, string>)[color] ?? color;
  if (Platform.OS === 'ios') {
    return <SymbolView name={ICONS[name].ios as SFSymbol} size={size} tintColor={tint} weight="regular" style={{ width: size, height: size }} />;
  }
  const code = symbols[ICONS[name].android];
  return (
    <View style={{ width: size, height: size }} accessible={false} importantForAccessibility="no-hide-descendants">
      <Text
        style={{
          fontFamily: FILLED[name] ? 'MaterialSymbolsFilled' : 'MaterialSymbols',
          fontSize: size,
          lineHeight: size,
          color: tint,
          includeFontPadding: false,
        }}
      >
        {String.fromCharCode(code)}
      </Text>
    </View>
  );
}
