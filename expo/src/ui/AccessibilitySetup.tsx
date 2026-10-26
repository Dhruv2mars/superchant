/**
 * Everything before the user reaches Android's accessibility settings (AccessibilitySetup.kt). First the
 * prominent disclosure of exactly what the service does and doesn't do, which needs an explicit "I agree"
 * (Google Play requires it). Once agreed, the setup steps, including the restricted-settings help for
 * sideloaded installs on Android 13+. Touch counting works without any of this.
 */
import { View } from 'react-native';
import { t } from '@/i18n';
import { platform } from '@/native/platform';
import { alpha } from '@/theme/colors';
import { useTheme } from '@/theme';
import { Button, Card } from './controls';
import { Dialog } from './Dialog';
import { Icon, IconName } from './Icon';
import { Text } from './Text';

export function AccessibilitySetupDialog({
  visible,
  consented,
  onConsent,
  onDismiss,
  onOpenSettings,
}: {
  visible: boolean;
  consented: boolean;
  onConsent: () => void;
  onDismiss: () => void;
  onOpenSettings: () => void;
}) {
  const volume = platform.volume;
  if (!volume) return null;
  if (!consented) {
    return (
      <Dialog
        visible={visible}
        title={t('disclosure_title')}
        onDismiss={onDismiss}
        actions={[
          { label: t('disclosure_decline'), onPress: onDismiss },
          { label: t('disclosure_agree'), onPress: onConsent, style: 'primary' },
        ]}
      >
        <Text variant="bodyMedium">{t('disclosure_intro')}</Text>
        <Heading text={t('disclosure_does_title')} />
        <Point icon="check" text={t('disclosure_does_1')} />
        <Point icon="check" text={t('disclosure_does_2')} />
        <Heading text={t('disclosure_never_title')} />
        <Point icon="block" text={t('disclosure_never_1')} />
        <Point icon="block" text={t('disclosure_never_2')} />
        <Point icon="block" text={t('disclosure_never_3')} />
        <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 12 }}>
          {t('disclosure_turn_off')}
        </Text>
      </Dialog>
    );
  }
  if (visible && volume.needsRestrictedSettings()) {
    return (
      <Dialog visible={visible} title={t('restricted_settings_title')} onDismiss={onDismiss} actions={[{ label: t('cancel'), onPress: onDismiss }]}>
        <Text variant="bodyMedium">{t('restricted_settings_message')}</Text>
        <StepCard title={t('restricted_settings_step2')} description={t('restricted_settings_step2_desc')} primary>
          <Button label={t('enable_accessibility')} onPress={onOpenSettings} />
        </StepCard>
        <StepCard title={t('restricted_settings_step1')} description={t('restricted_settings_step1_desc')}>
          <Button label={t('open_app_info')} variant="outlined" onPress={volume.openAppSettings} />
        </StepCard>
      </Dialog>
    );
  }
  return (
    <Dialog
      visible={visible}
      title={t('accessibility_setup_title')}
      onDismiss={onDismiss}
      actions={[
        { label: t('cancel'), onPress: onDismiss },
        { label: t('open_settings'), onPress: onOpenSettings, style: 'primary' },
      ]}
    >
      <Text variant="bodyMedium">{t('accessibility_setup_message')}</Text>
      <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 16 }}>
        {t('accessibility_setup_instructions')}
      </Text>
    </Dialog>
  );
}

function Heading({ text }: { text: string }) {
  return (
    <Text variant="titleSmall" weight="600" color="primary" accessibilityRole="header" style={{ marginTop: 16, marginBottom: 4 }}>
      {text}
    </Text>
  );
}

function Point({ icon, text }: { icon: IconName; text: string }) {
  return (
    <View style={{ flexDirection: 'row', paddingVertical: 4 }}>
      <View style={{ paddingTop: 2, marginEnd: 10 }}>
        <Icon name={icon} size={16} />
      </View>
      <Text variant="bodyMedium" style={{ flex: 1 }}>
        {text}
      </Text>
    </View>
  );
}

function StepCard({ title, description, primary, children }: { title: string; description: string; primary?: boolean; children: React.ReactNode }) {
  const { c } = useTheme();
  return (
    <Card radius={12} tint={primary ? alpha(c.primaryContainer, 0.5) : alpha(c.surfaceVariant, 0.5)} style={{ padding: 16, marginTop: 16 }}>
      <Text variant="titleSmall" weight="600" color={primary ? 'onSurface' : 'onSurfaceVariant'}>
        {title}
      </Text>
      <Text variant="bodySmall" color="onSurfaceVariant" style={{ marginTop: 4, marginBottom: 12 }}>
        {description}
      </Text>
      {children}
    </Card>
  );
}
