import { NativeModule, requireNativeModule } from "expo-modules-core";

export interface StepUpdateEvent {
  steps: number;
  date: string;
}

export interface BodyWeightSample {
  /** Body weight value expressed in kilograms */
  value: number;
  /** Epoch timestamp in milliseconds */
  time: number;
  /** ISO-8601 timestamp string */
  isoDate: string;
  /** Optional source of the measurement */
  source?: string;
}

export type BodyWeightUpdateEvent = BodyWeightSample;

export type HealthSubscription = {
  remove(): void;
};

export type HealthModuleEvents = {
  onStepDataUpdate: (event: StepUpdateEvent) => void;
  onBodyWeightDataUpdate: (event: BodyWeightUpdateEvent) => void;
};

export type UpdateFrequency = "immediate" | "hourly" | "daily" | "weekly";

/**
 * Snapshot describing the runtime state of the native health integration.
 * Returned by `getHealthDiagnostics()` — every field is best-effort and may be
 * `null`/empty if the underlying API call failed or the platform is iOS.
 */
export interface HealthDiagnostics {
  /** AVAILABLE | UNAVAILABLE | PROVIDER_UPDATE_REQUIRED | EXCEPTION | UNKNOWN */
  sdkStatus: string;
  providerPackage: string | null;
  providerVersionCode: number | null;
  providerVersionName: string | null;
  /** null on iOS — HealthKit doesn't expose read-auth state */
  permissionsGranted: boolean | null;
  grantedPermissions: string[];
  backgroundDeliveryEnabled: boolean;
  /** Epoch ms of the last WorkManager run, or null if it has never run */
  lastWorkerRunMs: number | null;
  lastWorkerResult: string | null;
  lastWorkerError: string | null;
  lastChangesTokenIssuedMs: number | null;
  /** WorkInfo state name: ENQUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED | BLOCKED */
  workManagerState: string | null;
  oemBrand: string | null;
  oemManufacturer: string | null;
  oemModel: string | null;
  oemDevice: string | null;
  osSdkInt: number | null;
  osRelease: string | null;
  ignoringBatteryOptimizations: boolean;
}

export interface OpenSettingsResult {
  ok: boolean;
  intentUsed: string | null;
}

export interface OpenOemSettingsResult extends OpenSettingsResult {
  oem: string;
}

export interface HealthModuleInterface {
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  /**
   * Get the step count for a specific day
   * @param startDate - The start date in milliseconds since epoch
   * @param endDate - The end date in milliseconds since epoch
   */
  getStepCount(startDate: number, endDate: number): Promise<number>;
  /**
   * Check whether any step records exist for a given date range.
   * @param startDate - The start date in milliseconds since epoch
   * @param endDate - The end date in milliseconds since epoch
   */
  hasStepDataForDate(startDate: number, endDate: number): Promise<boolean>;
  /**
   * Enable background delivery for step count updates
   */
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  /**
   * Disable background delivery for step count updates
   */
  disableBackgroundDelivery(): Promise<boolean>;
  /**
   * Enable body weight change notifications.
   */
  enableBodyWeightUpdates(frequency: UpdateFrequency): Promise<boolean>;
  /**
   * Disable body weight change notifications.
   */
  disableBodyWeightUpdates(): Promise<boolean>;
  /**
   * Fetch weight samples between two timestamps (inclusive) in chronological order.
   */
  getBodyWeightSamples(startDate: number, endDate: number): Promise<BodyWeightSample[]>;
  /**
   * Retrieve the most recent recorded body weight or null if none exist.
   */
  getLatestBodyWeight(): Promise<BodyWeightSample | null>;
  /**
   * Returns a fresh diagnostic snapshot of the health integration. Used by the
   * step-tracking diagnostic screen and Sentry tag enricher. Never throws —
   * fields are populated best-effort.
   */
  getHealthDiagnostics(): Promise<HealthDiagnostics>;
  /**
   * Open the system Health Connect settings UI (Android). On failure, falls
   * back to the Play Store listing. iOS opens the Health app via Settings.
   */
  openHealthConnectSettings(): Promise<boolean>;
  /**
   * Open the OS battery-optimization settings for this app. Tries multiple
   * intents in order of specificity. iOS resolves to false (not applicable).
   */
  openBatteryOptimizationSettings(): Promise<OpenSettingsResult>;
  /**
   * Open the OEM-specific auto-launch / background-activity manager (ColorOS,
   * MIUI, EMUI, OnePlus, Vivo). Falls back to app details settings. The `oem`
   * field reports which family was detected.
   */
  openOemAppLaunchSettings(): Promise<OpenOemSettingsResult>;
  /**
   * Schedule an immediate WorkManager sync run for the Health Connect change
   * pipeline. Useful from the diagnostic screen "Run sync now" button.
   */
  triggerSyncNow(): Promise<boolean>;
}

declare class HealthModule
  extends NativeModule<HealthModuleEvents>
  implements HealthModuleInterface
{
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  getStepCount(startDate: number, endDate: number): Promise<number>;
  hasStepDataForDate(startDate: number, endDate: number): Promise<boolean>;
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  disableBackgroundDelivery(): Promise<boolean>;
  enableBodyWeightUpdates(frequency: UpdateFrequency): Promise<boolean>;
  disableBodyWeightUpdates(): Promise<boolean>;
  getBodyWeightSamples(startDate: number, endDate: number): Promise<BodyWeightSample[]>;
  getLatestBodyWeight(): Promise<BodyWeightSample | null>;
  getHealthDiagnostics(): Promise<HealthDiagnostics>;
  openHealthConnectSettings(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<OpenSettingsResult>;
  openOemAppLaunchSettings(): Promise<OpenOemSettingsResult>;
  triggerSyncNow(): Promise<boolean>;
}

export default requireNativeModule<HealthModule>("Health");
