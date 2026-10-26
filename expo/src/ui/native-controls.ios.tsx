/** iOS: UIKit/SwiftUI controls, tinted with the app's primary color. */
import { Switch as RNSwitch, View } from 'react-native';
import { DatePicker, Host, Slider as SwiftSlider } from '@expo/ui/swift-ui';
import { disabled, tint } from '@expo/ui/swift-ui/modifiers';
import { useTheme } from '@/theme';
import type { SliderProps, SwitchProps, TimePickerProps } from './native-controls.types';

export function Switch({ value, enabled = true }: SwitchProps) {
  const { c } = useTheme();
  return (
    <RNSwitch
      value={value}
      disabled={!enabled}
      pointerEvents="none"
      accessible={false}
      importantForAccessibility="no-hide-descendants"
      trackColor={{ true: c.primary }}
    />
  );
}

export function Slider({ value, min, max, enabled = true, onChange, onDone, label }: SliderProps) {
  const { c } = useTheme();
  return (
    <View accessibilityLabel={label}>
      <Host style={{ height: 44, width: '100%' }}>
        <SwiftSlider
        value={value}
        min={min}
        max={max}
        onValueChange={onChange}
        onEditingChanged={(editing) => {
          if (!editing) onDone();
        }}
        modifiers={[tint(c.primary), disabled(!enabled)]}
        />
      </Host>
    </View>
  );
}

/** iOS picks the time inline (the compact SwiftUI time button opens the wheel). */
export function InlineTimePicker({ minuteOfDay, onPick }: { minuteOfDay: number; onPick: (minute: number) => void }) {
  const { c } = useTheme();
  const selection = new Date(2000, 0, 1, Math.floor(minuteOfDay / 60), minuteOfDay % 60);
  return (
    <Host matchContents>
      <DatePicker
        selection={selection}
        displayedComponents={['hourAndMinute']}
        onDateChange={(d) => onPick(d.getHours() * 60 + d.getMinutes())}
        modifiers={[tint(c.primary)]}
      />
    </Host>
  );
}

export function TimePicker(_: TimePickerProps) {
  return null;
}
