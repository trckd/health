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

/** A separately requestable, read-only health data capability. */
export type HealthCapability =
  | "steps"
  | "bodyweight"
  | "sleep"
  | "menstrualCycle";

/**
 * Result of a capability-scoped permission request.
 *
 * HealthKit deliberately does not expose read authorization state, so iOS
 * returns successfully requested capabilities in `unknown`. Android can
 * report them in `granted` or `denied`.
 */
export interface AuthorizationResult {
  /** Whether every requested capability is usable, or the iOS request completed. */
  success: boolean;
  requested: HealthCapability[];
  granted: HealthCapability[];
  denied: HealthCapability[];
  unknown: HealthCapability[];
}

export type HistoricalAccessStatus =
  | "granted"
  | "denied"
  | "not_required"
  | "unavailable";

/** Result of the separate, explicit historical-health-data permission flow. */
export interface HistoricalAccessResult {
  success: boolean;
  platform: "ios" | "android";
  status: HistoricalAccessStatus;
}

export type MenstrualCycleRecordKind = "period" | "flow";

export type MenstrualFlow =
  | "none"
  | "unknown"
  | "spotting"
  | "light"
  | "medium"
  | "heavy";

export interface HealthRecordSource {
  /** Native health data provider that returned this record. */
  platform: "healthkit" | "health_connect";
  /** Stable identifier assigned to the native record. */
  recordId: string;
  /** Bundle identifier (iOS) or package name (Android) of the writer. */
  bundleIdentifier: string;
  /** Human-readable source name when the platform exposes one. */
  name: string | null;
  /** Version of the app or device that wrote the record, when available. */
  version: string | null;
  /** Source-assigned identifier used for cross-device updates, when available. */
  clientRecordId: string | null;
  /** Epoch timestamp in milliseconds, when the platform exposes one. */
  lastModifiedTime: number | null;
}

/** A normalized, read-only menstrual period or flow record. */
export interface MenstrualCycleRecord {
  /** Stable native record identifier, equivalent to `source.recordId`. */
  id: string;
  /** Period boundaries or an individual flow observation. */
  kind: MenstrualCycleRecordKind;
  startTime: number;
  endTime: number;
  isoStartDate: string;
  isoEndDate: string;
  /** User-experienced offset at the record boundaries, or null if absent. */
  startZoneOffsetMinutes: number | null;
  endZoneOffsetMinutes: number | null;
  /** IANA time-zone identifier when supplied by HealthKit. */
  zoneId: string | null;
  flow: MenstrualFlow | null;
  /** HealthKit's menstrual-cycle-start marker; true for Android period records. */
  isCycleStart: boolean | null;
  source: HealthRecordSource;
}

export type SleepStageType =
  | "InBed"
  | "Awake"
  | "AsleepREM"
  | "AsleepCore"
  | "AsleepDeep"
  | "Sleeping"
  | "Light"
  | "Deep"
  | "REM"
  | "AwakeInBed"
  | "OutOfBed"
  | "Unknown";

export interface SleepStage {
  /** The type of sleep stage */
  type: SleepStageType;
  /** Start time in milliseconds since epoch */
  startTime: number;
  /** End time in milliseconds since epoch */
  endTime: number;
  /** Duration in milliseconds */
  duration: number;
}

export interface SleepSession {
  /** Start time of the sleep session in milliseconds since epoch */
  startTime: number;
  /** End time of the sleep session in milliseconds since epoch */
  endTime: number;
  /** Total duration in milliseconds */
  totalDuration: number;
  /** ISO-8601 timestamp string for start */
  isoStartDate: string;
  /** ISO-8601 timestamp string for end */
  isoEndDate: string;
  /** Array of sleep stages within this session */
  stages: SleepStage[];
  /** Optional source of the sleep data */
  source?: string;
}

export type SleepUpdateEvent = SleepSession;

export type HealthSubscription = {
  remove(): void;
};

export type HealthModuleEvents = {
  onStepDataUpdate: (event: StepUpdateEvent) => void;
  onBodyWeightDataUpdate: (event: BodyWeightUpdateEvent) => void;
  onSleepDataUpdate: (event: SleepUpdateEvent) => void;
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
   * Request only the supplied read capabilities. This never enables menstrual
   * background observers or write access.
   */
  requestAccess(capabilities: HealthCapability[]): Promise<AuthorizationResult>;
  /**
   * Request extended historical read access independently from data-category
   * consent. iOS does not require an additional permission.
   */
  requestHistoricalAccess(): Promise<HistoricalAccessResult>;
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
  getBodyWeightSamples(
    startDate: number,
    endDate: number
  ): Promise<BodyWeightSample[]>;
  /**
   * Retrieve the most recent recorded body weight or null if none exist.
   */
  getLatestBodyWeight(): Promise<BodyWeightSample | null>;
  /**
   * Fetch normalized period and flow records in chronological order.
   */
  getMenstrualCycleRecords(
    startDate: number,
    endDate: number
  ): Promise<MenstrualCycleRecord[]>;
  /**
   * Get sleep sessions for a specific date range
   * @param startDate - The start date in milliseconds since epoch
   * @param endDate - The end date in milliseconds since epoch
   */
  getSleepSessions(startDate: number, endDate: number): Promise<SleepSession[]>;
  /**
   * Enable sleep data change notifications.
   */
  enableSleepUpdates(frequency: UpdateFrequency): Promise<boolean>;
  /**
   * Disable sleep data change notifications.
   */
  disableSleepUpdates(): Promise<boolean>;
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
  requestAccess(capabilities: HealthCapability[]): Promise<AuthorizationResult>;
  requestHistoricalAccess(): Promise<HistoricalAccessResult>;
  getStepCount(startDate: number, endDate: number): Promise<number>;
  hasStepDataForDate(startDate: number, endDate: number): Promise<boolean>;
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  disableBackgroundDelivery(): Promise<boolean>;
  enableBodyWeightUpdates(frequency: UpdateFrequency): Promise<boolean>;
  disableBodyWeightUpdates(): Promise<boolean>;
  getBodyWeightSamples(
    startDate: number,
    endDate: number
  ): Promise<BodyWeightSample[]>;
  getLatestBodyWeight(): Promise<BodyWeightSample | null>;
  getMenstrualCycleRecords(
    startDate: number,
    endDate: number
  ): Promise<MenstrualCycleRecord[]>;
  getSleepSessions(startDate: number, endDate: number): Promise<SleepSession[]>;
  enableSleepUpdates(frequency: UpdateFrequency): Promise<boolean>;
  disableSleepUpdates(): Promise<boolean>;
  getHealthDiagnostics(): Promise<HealthDiagnostics>;
  openHealthConnectSettings(): Promise<boolean>;
  openBatteryOptimizationSettings(): Promise<OpenSettingsResult>;
  openOemAppLaunchSettings(): Promise<OpenOemSettingsResult>;
  triggerSyncNow(): Promise<boolean>;
}

export default requireNativeModule<HealthModule>("Health");
