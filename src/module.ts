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
}

export default requireNativeModule<HealthModule>("Health");
