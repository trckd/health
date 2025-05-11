import { NativeModule, requireNativeModule } from "expo-modules-core";

export interface StepUpdateEvent {
  steps: number;
  date: string;
}

export type HealthModuleEvents = {
  onStepDataUpdate: (event: StepUpdateEvent) => void;
};

// Define the update frequency type
export type UpdateFrequency = "immediate" | "hourly" | "daily" | "weekly";

export interface HealthModuleInterface {
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
    /**
   * Get the step count for a specific day
   * @param startDate - The start date of the day in seconds since epoch
   * @param endDate - The end date of the day in seconds since epoch
   * @returns The step count for the day
   */
  getStepCount(startDate: number, endDate: number): Promise<number>;
  /**
   * Enable background delivery for step count updates
   * @param frequency - The frequency of updates: "immediate", "hourly", "daily", or "weekly"
   * @returns A promise that resolves to a boolean indicating success
   */
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  /**
   * Disable background delivery for step count updates
   * @returns A promise that resolves to a boolean indicating success
   */
  disableBackgroundDelivery(): Promise<boolean>;
}

declare class HealthModule
  extends NativeModule<HealthModuleEvents>
  implements HealthModuleInterface
{
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  /**
   * Get the step count for a specific day
   * @param startDate - The start date of the day in seconds since epoch
   * @param endDate - The end date of the day in seconds since epoch
   * @returns The step count for the day
   */
  getStepCount(startDate: number, endDate: number): Promise<number>;
  /**
   * Enable background delivery for step count updates
   * @param frequency - The frequency of updates: "immediate", "hourly", "daily", or "weekly"
   * @returns A promise that resolves to a boolean indicating success
   */
  enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>;
  /**
   * Disable background delivery for step count updates
   * @returns A promise that resolves to a boolean indicating success
   */
  disableBackgroundDelivery(): Promise<boolean>;
}

export default requireNativeModule<HealthModule>("Health");
