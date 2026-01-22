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

}

declare class HealthModule
  extends NativeModule<HealthModuleEvents>
  implements HealthModuleInterface
{
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  getStepCount(startDate: number, endDate: number): Promise<number>;
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  disableBackgroundDelivery(): Promise<boolean>;
  enableBodyWeightUpdates(frequency: UpdateFrequency): Promise<boolean>;
  disableBodyWeightUpdates(): Promise<boolean>;
  getBodyWeightSamples(startDate: number, endDate: number): Promise<BodyWeightSample[]>;
  getLatestBodyWeight(): Promise<BodyWeightSample | null>;
  getSleepSessions(startDate: number, endDate: number): Promise<SleepSession[]>;
  enableSleepUpdates(frequency: UpdateFrequency): Promise<boolean>;
  disableSleepUpdates(): Promise<boolean>;
}

export default requireNativeModule<HealthModule>("Health");
