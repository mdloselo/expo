import { NativeModules, Platform } from 'react-native';

export enum BrightnessMode {
  UNKNOWN = 0,
  AUTOMATIC = 1,
  MANUAL = 2,
};

export async function getBrightnessAsync(): Promise<number> {
  return await NativeModules.ExponentBrightness.getBrightnessAsync();
}

export async function setBrightnessAsync(brightnessValue: number): Promise<void> {
  brightnessValue = Math.max(0, Math.min(brightnessValue, 1));
  return await NativeModules.ExponentBrightness.setBrightnessAsync(brightnessValue);
}

export async function getSystemBrightnessAsync(): Promise<number> {
  if (Platform.OS !== 'android') {
    return await getBrightnessAsync();
  }
  return await NativeModules.ExponentBrightness.getSystemBrightnessAsync();
}

export async function setSystemBrightnessAsync(brightnessValue: number): Promise<void> {
  brightnessValue = Math.max(0, Math.min(brightnessValue, 1));
  if (Platform.OS !== 'android') {
    return await setBrightnessAsync(brightnessValue);
  } else {
    return await NativeModules.ExponentBrightness.setSystemBrightnessAsync(brightnessValue);
  }
}

export async function useSystemBrightnessAsync(): Promise<void> {
  if (Platform.OS !== 'android') {
    return;
  } else {
    return await NativeModules.ExponentBrightness.useSystemBrightnessAsync();
  }
}

export async function getSystemBrightnessModeAsync(): Promise<BrightnessMode> {
  if (Platform.OS !== 'android') {
    return BrightnessMode.UNKNOWN;
  } else {
    return await NativeModules.ExponentBrightness.getSystemBrightnessAsync();
  }
}

export async function setSystemBrightnessModeAsync(brightnessMode: BrightnessMode): Promise<void> {
  if (Platform.OS !== 'android' || brightnessMode === BrightnessMode.UNKNOWN) {
    return;
  } else {
    return await NativeModules.ExponentBrightness.setSystemBrightnessModeAsync(brightnessMode);
  }
}
