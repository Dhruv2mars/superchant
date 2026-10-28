/** A short message at the bottom (Material snackbar), announced to screen readers. One at a time; '' hides it. */
import { createContext, ReactNode, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, StyleSheet, View } from 'react-native';
import Animated, { FadeInDown, FadeOut } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTheme } from '@/theme';
import { EaseOutStrong } from '@/theme/motion';
import { Text } from './Text';

const SnackbarContext = createContext<(message: string) => void>(() => {});

export const useSnackbar = () => useContext(SnackbarContext);

/** Bottom padding (plus the inset) that keeps a screen's last line above a one-line snackbar: its 16 margin, 48 height and a 16 gap. */
export const SNACKBAR_CLEARANCE = 80;

export function SnackbarHost({ children }: { children: ReactNode }) {
  const { c } = useTheme();
  const insets = useSafeAreaInsets();
  const [message, setMessage] = useState<{ text: string; key: number } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);
  const show = useCallback((text: string) => {
    if (!text) return setMessage(null);
    setMessage({ text, key: Date.now() });
    AccessibilityInfo.announceForAccessibility(text);
  }, []);
  useEffect(() => {
    if (!message) return;
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 4000);
    return () => clearTimeout(timer.current);
  }, [message]);
  return (
    <SnackbarContext.Provider value={show}>
      {children}
      <View pointerEvents="none" style={[styles.host, { bottom: insets.bottom + 16 }]}>
        {message ? (
          <Animated.View
            key={message.key}
            entering={FadeInDown.duration(200).easing(EaseOutStrong)}
            exiting={FadeOut.duration(150)}
            style={[styles.bar, { backgroundColor: c.inverseSurface }]}
          >
            <Text variant="bodyMedium" color="inverseOnSurface">
              {message.text}
            </Text>
          </Animated.View>
        ) : null}
      </View>
    </SnackbarContext.Provider>
  );
}

const styles = StyleSheet.create({
  host: { position: 'absolute', left: 16, right: 16, alignItems: 'center' },
  bar: { minHeight: 48, borderRadius: 4, paddingHorizontal: 16, paddingVertical: 14, width: '100%', maxWidth: 560, justifyContent: 'center' },
});
