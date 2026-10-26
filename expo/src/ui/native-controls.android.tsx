/** Android: Material 3 controls from Jetpack Compose (@expo/ui), in the app's colors. */
import { Host, Slider as ComposeSlider, Switch as ComposeSwitch, TimePickerDialog } from '@expo/ui/jetpack-compose';
import { View } from 'react-native';
import { useTheme } from '@/theme';
import { alpha } from '@/theme/colors';
import type { SliderProps, SwitchProps, TimePickerProps } from './native-controls.types';

/** Display only: the whole row toggles (it owns the touch target and the accessibility node). */
export function Switch({ value, enabled = true }: SwitchProps) {
  const { c } = useTheme();
  return (
    <View pointerEvents="none" accessible={false} importantForAccessibility="no-hide-descendants">
      <Host matchContents>
        <ComposeSwitch
        value={value}
        enabled={enabled}
        colors={{
          checkedThumbColor: c.primary,
          checkedTrackColor: c.primaryContainer,
          checkedBorderColor: c.primaryContainer,
          uncheckedThumbColor: c.outline,
          uncheckedTrackColor: c.surfaceVariant,
          uncheckedBorderColor: c.outline,
          }}
        />
      </Host>
    </View>
  );
}

export function Slider({ value, min, max, enabled = true, onChange, onDone, label }: SliderProps) {
  const { c } = useTheme();
  return (
    <View accessibilityLabel={label}>
      <Host style={{ height: 44, width: '100%' }}>
        <ComposeSlider
        value={value}
        min={min}
        max={max}
        enabled={enabled}
        onValueChange={onChange}
        onValueChangeFinished={onDone}
        colors={{ thumbColor: c.primary, activeTrackColor: c.primary, inactiveTrackColor: c.primaryContainer }}
        />
      </Host>
    </View>
  );
}

/** The Material 3 time picker dialog, like the Kotlin app's ReminderTimeDialog. */
export function TimePicker({ visible, minuteOfDay, is24Hour, onPick, onDismiss, confirmLabel, cancelLabel }: TimePickerProps) {
  const { c } = useTheme();
  if (!visible) return null;
  const initial = new Date(2000, 0, 1, Math.floor(minuteOfDay / 60), minuteOfDay % 60);
  return (
    <Host matchContents>
      <TimePickerDialog
        initialDate={initial.toISOString()}
        is24Hour={is24Hour}
        confirmButtonLabel={confirmLabel}
        dismissButtonLabel={cancelLabel}
        color={c.primary}
        elementColors={{ containerColor: c.surface, clockDialColor: alpha(c.surfaceVariant, 0.8), selectorColor: c.primary }}
        onDateSelected={(d) => onPick(d.getHours() * 60 + d.getMinutes())}
        onDismissRequest={onDismiss}
      />
    </Host>
  );
}

/** iOS only (Android uses the time picker dialog). */
export function InlineTimePicker(_: { minuteOfDay: number; onPick: (minute: number) => void }) {
  return null;
}
