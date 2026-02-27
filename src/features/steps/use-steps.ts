import { useCallback, useEffect, useRef, useState } from "react";
import { Platform } from "react-native";

// Import the health module with its type
import { Health, StepUpdateEvent } from "../..";
import { AuthStatus } from "../../types";
import { getDayBoundsMs } from "./date-utils";

export { AuthStatus };

export function useSteps(date: string) {
  const [steps, setSteps] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [authStatus, setAuthStatus] = useState<AuthStatus>(AuthStatus.Unknown);

  // Platform-specific health service name
  const healthServiceName = Platform.select({
    ios: "HealthKit",
    android: "Health Connect",
    default: "Health Service",
  });

  // Fetch steps using our custom Health module
  const fetchSteps = useCallback(async () => {
    try {
      if (!Health.isHealthDataAvailable) {
        setError(`${healthServiceName} is not available on this device`);
        return;
      }

      setLoading(true);
      const { startTime, endTime } = getDayBoundsMs(date);

      const totalSteps = await Health.getStepCount(startTime, endTime);
      setSteps(totalSteps);
      setError(null);
    } catch (err) {
      console.error(`${healthServiceName} error:`, err);
      setError("Error getting steps");
    } finally {
      setLoading(false);
    }
  }, [date, healthServiceName]);

  // Fetch steps when date changes or when initialized
  useEffect(() => {
    if (isInitialized && authStatus === AuthStatus.Authorized) {
      fetchSteps();
    }
  }, [date, isInitialized, authStatus, fetchSteps]);

  const backgroundDeliveryEnabled = useRef(false);

  // Set up background delivery and event listeners
  useEffect(() => {
    const subscription = Health.addListener("onStepDataUpdate", (event: StepUpdateEvent) => {
      // Only update steps if we're looking at today's date
      const today = new Date().toISOString().split("T")[0];
      if (date === today && event.steps !== undefined) {
        setSteps(event.steps);
      }
    });

    // Enable background delivery once
    const setupBackgroundDelivery = async () => {
      if (isInitialized && authStatus === AuthStatus.Authorized && !backgroundDeliveryEnabled.current) {
        try {
          await Health.enableBackgroundDelivery("immediate");
          backgroundDeliveryEnabled.current = true;
        } catch (err) {
          // noop - background delivery might already be enabled
        }
      }
    };

    setupBackgroundDelivery();

    // Clean up event listener when component unmounts
    return () => {
      subscription.remove();

      // Don't disable background delivery on unmount - it should remain active
      // so the app can receive updates even when not in foreground
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
        await fetchSteps();
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
    steps: Math.round(steps),
    loading,
    error,
    isInitialized,
    requestInitialization,
  };
}