import { NativeModule, requireNativeModule } from "expo-modules-core";

// No events needed for our simplified module
export type HealthModuleEvents = {};

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
}

export default requireNativeModule<HealthModule>("Health");
