// Export the native module directly
export type {
  BodyWeightSample,
  BodyWeightUpdateEvent,
  HealthDiagnostics,
  HealthModuleEvents,
  HealthModuleInterface,
  HealthSubscription,
  OpenOemSettingsResult,
  OpenSettingsResult,
  StepUpdateEvent,
  UpdateFrequency,
} from "./module";
export { default as Health } from "./module";
export { AuthStatus } from "./types";
