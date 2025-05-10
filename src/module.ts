import { NativeModule, requireNativeModule } from "expo-modules-core";

// No events needed for our simplified module
export type HealthModuleEvents = {};

export interface HealthModuleInterface {
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  getStepCount(startDate: Date, endDate: Date): Promise<number>;
}

declare class HealthModule
  extends NativeModule<HealthModuleEvents>
  implements HealthModuleInterface
{
  isHealthDataAvailable: boolean;
  checkHealthDataAvailable(): boolean;
  requestAuthorization(): Promise<boolean>;
  getStepCount(startDate: Date, endDate: Date): Promise<number>;
}

const Health = requireNativeModule<HealthModule>("Health");

// Export as a named export instead of default
export { Health };
