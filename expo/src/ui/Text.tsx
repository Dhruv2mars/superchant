import { Text as RNText, TextProps, TextStyle } from 'react-native';
import { useTheme } from '@/theme';
import { Palette } from '@/theme/colors';
import { type as typeScale, TypeStyle } from '@/theme/type';

export interface AppTextProps extends TextProps {
  variant?: TypeStyle;
  color?: keyof Palette | (string & {});
  weight?: TextStyle['fontWeight'];
  align?: TextStyle['textAlign'];
}

/** Text in the app's type scale; `color` is a palette role (e.g. "primary") or a literal color. */
export function Text({ variant = 'bodyMedium', color = 'onSurface', weight, align, style, ...rest }: AppTextProps) {
  const { c } = useTheme();
  const resolved = (c as unknown as Record<string, string>)[color] ?? color;
  return (
    <RNText
      {...rest}
      style={[typeScale[variant], { color: resolved }, weight ? { fontWeight: weight } : null, align ? { textAlign: align } : null, style]}
    />
  );
}
