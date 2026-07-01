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

// React hooks for consuming health data. Exporting the maintained
// implementations here lets apps import them directly instead of copying the
// hooks into each codebase (which is how bugs like the local-date mismatch
// previously drifted between copies).
export { useSteps } from "./features/steps/use-steps";
export { useBodyWeight } from "./features/bodyweight/use-body-weight";
