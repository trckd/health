// Export the native module directly
export type {
  BodyWeightSample,
  BodyWeightUpdateEvent,
  HealthModuleEvents,
  HealthModuleInterface,
  HealthSubscription,
  SleepSession,
  SleepStage,
  SleepStageType,
  SleepUpdateEvent,
  StepUpdateEvent,
  UpdateFrequency,
} from "./module";
export { default as Health } from "./module";

// Export hooks
export { useSleep } from "./features/sleep";
