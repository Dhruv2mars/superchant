/**
 * A list reordered by long-pressing a row and dragging it (the Compose app's reorderable LazyColumn). Rows
 * have a fixed height, so positions are plain arithmetic on the UI thread; the new order reaches JS once,
 * when the finger lifts. Screen readers get "Move up"/"Move down" actions instead of the drag.
 */
import { ReactNode, useEffect } from 'react';
import { View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import * as Haptics from 'expo-haptics';
import Animated, { runOnJS, SharedValue, useAnimatedReaction, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';

const SPRING = { damping: 30, stiffness: 400, mass: 0.6 };

export function ReorderList<T extends { id: string }>({
  items,
  rowHeight,
  gap,
  onReorder,
  renderItem,
}: {
  items: T[];
  rowHeight: number;
  gap: number;
  onReorder: (from: number, to: number) => void;
  renderItem: (item: T, index: number, dragging: SharedValue<boolean>) => ReactNode;
}) {
  const step = rowHeight + gap;
  // Index each row is drawn at while a drag is in progress
  const order = useSharedValue<string[]>(items.map((i) => i.id));
  useEffect(() => {
    order.value = items.map((i) => i.id);
  }, [items, order]);
  return (
    <View style={{ height: items.length * step - gap }}>
      {items.map((item, index) => (
        <Row key={item.id} id={item.id} index={index} step={step} height={rowHeight} order={order} count={items.length} onReorder={onReorder}>
          {(dragging) => renderItem(item, index, dragging)}
        </Row>
      ))}
    </View>
  );
}

function Row({
  id,
  index,
  step,
  height,
  order,
  count,
  onReorder,
  children,
}: {
  id: string;
  index: number;
  step: number;
  height: number;
  order: SharedValue<string[]>;
  count: number;
  onReorder: (from: number, to: number) => void;
  children: (dragging: SharedValue<boolean>) => ReactNode;
}) {
  const top = useSharedValue(index * step);
  const dragging = useSharedValue(false);
  const startTop = useSharedValue(0);
  const startIndex = useSharedValue(index);

  useEffect(() => {
    top.value = index * step;
  }, [index, step, top]);

  // Other rows slide to their slot when the dragged row passes them
  useAnimatedReaction(
    () => order.value.indexOf(id),
    (slot, previous) => {
      if (previous !== null && slot !== previous && !dragging.value) top.value = withTiming(slot * step, { duration: 160 });
    },
  );

  const pan = Gesture.Pan()
    .activateAfterLongPress(350)
    .onStart(() => {
      dragging.value = true;
      startTop.value = top.value;
      startIndex.value = order.value.indexOf(id);
      runOnJS(Haptics.performAndroidHapticsAsync)(Haptics.AndroidHaptics.Drag_Start);
    })
    .onUpdate((e) => {
      const y = Math.min(Math.max(startTop.value + e.translationY, 0), (count - 1) * step);
      top.value = y;
      const slot = Math.round(y / step);
      const current = order.value.indexOf(id);
      if (slot !== current) {
        const next = order.value.slice();
        next.splice(current, 1);
        next.splice(slot, 0, id);
        order.value = next;
      }
    })
    .onEnd(() => {
      const slot = order.value.indexOf(id);
      top.value = withSpring(slot * step, SPRING, () => {
        dragging.value = false;
      });
      if (slot !== startIndex.value) runOnJS(onReorder)(startIndex.value, slot);
    });

  const style = useAnimatedStyle(() => ({
    position: 'absolute',
    left: 0,
    right: 0,
    height,
    transform: [{ translateY: top.value }],
    zIndex: dragging.value ? 10 : 0,
  }));

  return (
    <GestureDetector gesture={pan}>
      <Animated.View style={style}>{children(dragging)}</Animated.View>
    </GestureDetector>
  );
}
