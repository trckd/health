import { Platform } from "react-native";

// iOS and Android share a single implementation (`use-body-weight.native`).
// Only the native platforms load it, so importing this module never pulls the
// native health module in on web or other unsupported platforms.
const useBodyWeightImplementation = Platform.select({
  native: () => require("./use-body-weight.native").useBodyWeight,
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

// Re-export types
export type { BodyWeightSample } from "./use-body-weight.native";
export { AuthStatus } from "../../types";