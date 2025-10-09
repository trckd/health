import { Platform } from "react-native";

// Platform-specific imports
const useBodyWeightImplementation = Platform.select({
  ios: () => require("./use-body-weight.ios").useBodyWeight,
  android: () => require("./use-body-weight.android").useBodyWeight,
  default: () => {
    // Fallback for web or other platforms
    return () => ({
      bodyWeightSamples: [],
      latestWeight: null,
      loading: false,
      error: "Body weight tracking is not available on this platform",
      isInitialized: false,
      requestInitialization: async () => false,
    });
  },
})!();

export const useBodyWeight = useBodyWeightImplementation;

// Re-export types from the iOS implementation (they're the same for both platforms)
export type { BodyWeightSample } from "./use-body-weight.ios";
export { AuthStatus } from "./use-body-weight.ios";