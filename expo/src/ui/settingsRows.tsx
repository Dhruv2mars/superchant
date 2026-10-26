/** Settings building blocks (SettingsScreen.kt): a titled card section and whole-row switches and sliders. */
import { ReactNode, useEffect, useRef, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { t } from '@/i18n';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import { Card } from './controls';
import { Slider, Switch } from './native-controls';
import { Text } from './Text';

export function SettingsSection({ title, children }: { title: string; children: ReactNode }) {
  return (
    <View>
      <Text variant="labelLarge" color="primary" style={{ marginBottom: 8, marginStart: 4 }} accessibilityRole="header">
        {title}
      </Text>
      <Card style={{ padding: 16 }}>{children}</Card>
    </View>
  );
}

export function SettingsItem({ title, subtitle, trailing }: { title: string; subtitle?: string; trailing?: ReactNode }) {
  return (
    <View style={styles.row}>
      <View style={styles.texts}>
        <Text variant="bodyLarge">{title}</Text>
        {subtitle ? (
          <Text variant="bodySmall" color="onSurfaceVariant">
            {subtitle}
          </Text>
        ) : null}
      </View>
      {trailing}
    </View>
  );
}

/** The whole row toggles, so the target is large and screen readers read it as one switch. */
export function SwitchRow({
  title,
  subtitle,
  value,
  enabled = true,
  onChange,
}: {
  title: string;
  subtitle?: string;
  value: boolean;
  enabled?: boolean;
  onChange: (value: boolean) => void;
}) {
  const { c } = useTheme();
  return (
    <Pressable
      onPress={() => onChange(!value)}
      disabled={!enabled}
      accessibilityRole="switch"
      accessibilityState={{ checked: value, disabled: !enabled }}
      accessibilityLabel={subtitle ? `${title}. ${subtitle}` : title}
      android_ripple={{ color: alpha(c.onSurface, 0.08), foreground: true }}
      style={[styles.row, { minHeight: 48, opacity: enabled ? 1 : 0.5 }]}
    >
      <View style={[styles.texts, { paddingEnd: 12 }]}>
        <Text variant="bodyLarge">{title}</Text>
        {subtitle ? (
          <Text variant="bodySmall" color="onSurfaceVariant">
            {subtitle}
          </Text>
        ) : null}
      </View>
      <Switch value={value} enabled={enabled} />
    </Pressable>
  );
}

function intensityLabel(v: number): string {
  if (v <= 50) return t('settings_intensity_very_light');
  if (v <= 100) return t('settings_intensity_light');
  if (v <= 150) return t('settings_intensity_medium');
  if (v <= 200) return t('settings_intensity_strong');
  return t('settings_intensity_very_strong');
}

/** Vibration intensity 1-255. The drag is local; the value is written once when the drag ends. */
export function IntensityRow({ title, value, enabled = true, onDone }: { title: string; value: number; enabled?: boolean; onDone: (value: number) => void }) {
  const [drag, setDrag] = useState(value);
  const latest = useRef(value);
  useEffect(() => {
    latest.current = value;
    setDrag(value);
  }, [value]);
  return (
    <View style={{ opacity: enabled ? 1 : 0.5, marginTop: 8 }}>
      <View style={styles.row}>
        <Text variant="bodyLarge">{title}</Text>
        <Text variant="bodySmall" color="primary">
          {intensityLabel(Math.round(drag))}
        </Text>
      </View>
      <Slider
        value={drag}
        min={1}
        max={255}
        enabled={enabled}
        label={`${title}, ${intensityLabel(Math.round(drag))}`}
        onChange={(v) => {
          latest.current = v;
          setDrag(v);
        }}
        onDone={() => onDone(Math.round(latest.current))}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  texts: { flex: 1 },
});
