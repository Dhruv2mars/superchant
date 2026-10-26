export interface SwitchProps {
  value: boolean;
  enabled?: boolean;
}

export interface SliderProps {
  value: number;
  min: number;
  max: number;
  enabled?: boolean;
  onChange: (value: number) => void;
  /** The drag ended: one write per drag instead of one per frame. */
  onDone: () => void;
  label: string;
}

export interface TimePickerProps {
  visible: boolean;
  minuteOfDay: number;
  is24Hour: boolean;
  onPick: (minuteOfDay: number) => void;
  onDismiss: () => void;
  confirmLabel: string;
  cancelLabel: string;
}
