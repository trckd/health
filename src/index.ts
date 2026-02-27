// Export the native module directly
export type {
  BodyWeightSample,
  BodyWeightUpdateEvent,
  HealthModuleEvents,
  HealthModuleInterface,
  HealthSubscription,
  StepUpdateEvent,
  UpdateFrequency,
} from "./module";
export { default as Health } from "./module";
export { AuthStatus } from "./types";
