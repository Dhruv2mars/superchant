import { NativeModule, requireNativeModule } from 'expo';

declare class SuperchantNativeModule extends NativeModule<{}> {}

export default requireNativeModule<SuperchantNativeModule>('SuperchantNative');
