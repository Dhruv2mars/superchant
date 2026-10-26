import { createContext, ReactNode, useContext, useEffect, useMemo } from 'react';
import { useColorScheme } from 'react-native';
import * as SystemUI from 'expo-system-ui';
import { dark, light, Palette } from './colors';
import type { ThemeMode } from '@/native/types';

export interface Theme {
  c: Palette;
  dark: boolean;
}

const ThemeContext = createContext<Theme>({ c: light, dark: false });

/** Resolves the saved theme mode against the system setting and colors the root view behind every screen. */
export function ThemeProvider({ mode, children }: { mode: ThemeMode; children: ReactNode }) {
  const system = useColorScheme();
  const isDark = mode === 'DARK' || (mode === 'SYSTEM' && system === 'dark');
  const theme = useMemo<Theme>(() => ({ c: isDark ? dark : light, dark: isDark }), [isDark]);
  useEffect(() => {
    SystemUI.setBackgroundColorAsync(theme.c.surface);
  }, [theme]);
  return <ThemeContext.Provider value={theme}>{children}</ThemeContext.Provider>;
}

export const useTheme = () => useContext(ThemeContext);
