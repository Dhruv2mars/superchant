import { useEffect, useState } from 'react';
import { Platform } from 'react-native';
import { Stack, router } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import * as SplashScreen from 'expo-splash-screen';
import { useFonts } from 'expo-font';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { platform } from '@/native/platform';
import { getState, initStore, requestStart, subscribe, useSettings } from '@/state/store';
import { ThemeProvider, useTheme } from '@/theme';
import { iconFonts } from '@/ui/Icon';
import { SnackbarHost } from '@/ui/Snackbar';

SplashScreen.preventAutoHideAsync();

/** Resolves once goals have loaded (a cold start from a shortcut arrives before them). */
function goalsLoaded(): Promise<NonNullable<ReturnType<typeof getState>['goals']>> {
  return new Promise((resolve) => {
    const check = () => {
      const goals = getState().goals;
      if (goals) {
        unsubscribe();
        resolve(goals);
      }
    };
    const unsubscribe = subscribe(check);
    check();
  });
}

/** A start from a launcher shortcut or the Quick Settings tile: go Home and start there (MainActivity.kt). */
async function handleLaunchRequest(goalId: string | null) {
  platform.reportShortcutUsed(goalId);
  const goals = await goalsLoaded();
  // A shortcut to a deleted goal falls back to a free session
  const goal = goalId ? (goals.find((g) => g.id === goalId) ?? null) : null;
  requestStart({ goal });
  if (router.canDismiss()) router.dismissAll();
}

export default function RootLayout() {
  const [fontsLoaded] = useFonts(Platform.OS === 'android' ? iconFonts : {});
  const settings = useSettings();
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    initStore().catch(() => setFailed(true));
    const launch = platform.takeLaunchRequest();
    if (launch) handleLaunchRequest(launch.goalId);
    return platform.onStartRequest((r) => handleLaunchRequest(r.goalId));
  }, []);

  // Hold the splash until the saved theme is known, so a forced light/dark theme never flashes the system one
  const ready = fontsLoaded && (settings != null || failed);
  useEffect(() => {
    if (ready) SplashScreen.hideAsync();
  }, [ready]);
  if (!ready) return null;

  return (
    <GestureHandlerRootView style={{ flex: 1 }}>
      <ThemeProvider mode={settings?.themeMode ?? 'SYSTEM'}>
        <SnackbarHost>
          <Screens />
        </SnackbarHost>
      </ThemeProvider>
    </GestureHandlerRootView>
  );
}

function Screens() {
  const { c, dark } = useTheme();
  return (
    <>
      <StatusBar style={dark ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: c.surface },
          // Android: the Compose app's fade + slide; iOS: the native push with its swipe-back gesture
          animation: Platform.OS === 'android' ? 'slide_from_right' : 'default',
          animationDuration: 300,
        }}
      >
        <Stack.Screen name="index" />
        <Stack.Screen name="goals" />
        <Stack.Screen name="history" />
        <Stack.Screen name="settings" />
        <Stack.Screen
          name="goal-editor"
          options={{
            presentation: Platform.OS === 'ios' ? 'modal' : 'fullScreenModal',
            animation: 'slide_from_bottom',
            gestureEnabled: false,
          }}
        />
      </Stack>
    </>
  );
}
