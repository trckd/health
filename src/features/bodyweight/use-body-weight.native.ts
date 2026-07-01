import { useCallback, useEffect, useRef, useState } from "react";
import { Platform } from "react-native";

// Import the health module with its type
import { Health, BodyWeightSample } from "../..";
import { AuthStatus } from "../../types";
import { getDayBoundsMs, getTodayInUserTimezone } from "../steps/date-utils";

export { AuthStatus, BodyWeightSample };

// The two native platforms share this implementation and differ only in how
// the underlying health provider is named in user-facing messages.
const healthServiceName = Platform.select({
  ios: "HealthKit",
  android: "Health Connect",
  default: "Health Service",
});

export function useBodyWeight(date?: string) {
  const [bodyWeightSamples, setBodyWeightSamples] = useState<
    BodyWeightSample[]
  >([]);
  const [latestWeight, setLatestWeight] = useState<BodyWeightSample | null>(
    null,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [authStatus, setAuthStatus] = useState<AuthStatus>(AuthStatus.Unknown);

  // Fetch body weight samples for a specific date range
  const fetchBodyWeightSamples = useCallback(
    async (dateString?: string) => {
      try {
        if (!Health.isHealthDataAvailable) {
          setError(`${healthServiceName} is not available on this device`);
          return;
        }

        setLoading(true);

        if (dateString) {
          // Fetch samples for specific date
          const { startTime, endTime } = getDayBoundsMs(dateString);
          const samples = await Health.getBodyWeightSamples(startTime, endTime);
          setBodyWeightSamples(samples);
        } else {
          // Fetch latest weight only
          const latest = await Health.getLatestBodyWeight();
          if (latest) {
            setLatestWeight(latest);
          }
        }

        setError(null);
      } catch (err) {
        console.error(`${healthServiceName} body weight error:`, err);
        setError("Error getting body weight data");
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  // Fetch body weight when date changes or when initialized
  useEffect(() => {
    if (isInitialized && authStatus === AuthStatus.Authorized) {
      fetchBodyWeightSamples(date);
    }
  }, [date, isInitialized, authStatus, fetchBodyWeightSamples]);

  const bodyWeightUpdatesEnabled = useRef(false);

  // Set up real-time updates for body weight
  useEffect(() => {
    let subscription: ReturnType<typeof Health.addListener> | null = null;

    if (isInitialized && authStatus === AuthStatus.Authorized) {
      subscription = Health.addListener(
        "onBodyWeightDataUpdate",
        (event: BodyWeightSample) => {
          // Update latest weight
          setLatestWeight(event);

          // If we're viewing today's data, add the new sample to the list.
          // Compare against the user's local date (not the UTC date) so evening
          // updates in negative-UTC timezones aren't dropped by an off-by-one
          // day mismatch.
          const today = getTodayInUserTimezone();
          if (date === today) {
            setBodyWeightSamples((prev) => [...prev, event]);
          }
        },
      );
    }

    // Enable body weight updates once
    const setupBodyWeightUpdates = async () => {
      if (isInitialized && authStatus === AuthStatus.Authorized && !bodyWeightUpdatesEnabled.current) {
        try {
          await Health.enableBodyWeightUpdates("immediate");
          bodyWeightUpdatesEnabled.current = true;
        } catch (err) {
          // noop - updates might already be enabled
        }
      }
    };

    setupBodyWeightUpdates();

    // Clean up event listener when component unmounts
    return () => {
      if (subscription) {
        subscription.remove();
      }

      // Don't disable body weight updates on unmount - keep them active
      // for background updates
    };
  }, [isInitialized, authStatus, date]);

  const requestInitialization = async () => {
    if (!Health.isHealthDataAvailable) {
      setError(`${healthServiceName} is not available on this device`);
      return false;
    }

    setLoading(true);
    try {
      // Request authorization using our custom module
      const authorized = await Health.requestAuthorization();

      if (authorized) {
        setIsInitialized(true);
        setAuthStatus(AuthStatus.Authorized);
        await fetchBodyWeightSamples(date);
        return true;
      } else {
        setError("Health data access was denied");
        setAuthStatus(AuthStatus.NotAuthorized);
        return false;
      }
    } catch (err) {
      setError(`Cannot access ${healthServiceName}`);
      setIsInitialized(false);
      setAuthStatus(AuthStatus.NotAuthorized);
      console.error(err);
      return false;
    } finally {
      setLoading(false);
    }
  };

  return {
    bodyWeightSamples,
    latestWeight,
    loading,
    error,
    isInitialized,
    requestInitialization,
  };
}
