import { useCallback, useEffect, useState } from "react";
import {
  getValueFromAsyncStorage,
  saveToAsyncStorage,
} from "@features/async-storage";
import { captureException } from "@sentry/react-native";
import { useQueryClient } from "@tanstack/react-query";

// Import the health module with its type
import { Health } from "@tracked/health";

export enum AuthStatus {
  Unknown = "UNKNOWN",
  Authorized = "AUTHORIZED",
  NotAuthorized = "NOT_AUTHORIZED",
}

export function useSteps(date: string) {
  const [steps, setSteps] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isInitialized, setIsInitialized] = useState(false);
  const [authStatus, setAuthStatus] = useState<AuthStatus>(AuthStatus.Unknown);

  const queryClient = useQueryClient();

  // Check initialization status on mount
  useEffect(() => {
    getValueFromAsyncStorage("healthkitInitialized").then((value) => {
      if (value === "true") {
        setIsInitialized(true);
        setAuthStatus(AuthStatus.Authorized);
      }
    });
  }, []);

  // Prepare date range for the query
  const getDateRange = useCallback((dateString: string) => {
    const localDate = new Date(dateString + "T00:00:00.000");
    const startTime = new Date(localDate);
    startTime.setHours(0, 0, 0, 0);
    const endTime = new Date(localDate);
    endTime.setHours(23, 59, 59, 999);
    return { startTime: startTime.getTime(), endTime: endTime.getTime() };
  }, []);

  // Fetch steps using our custom Health module
  const fetchSteps = useCallback(async () => {
    try {
      if (!Health.isHealthDataAvailable) {
        setError("Health Connect is not available on this device");
        return;
      }

      setLoading(true);
      const { startTime, endTime } = getDateRange(date);

      const totalSteps = await Health.getStepCount(startTime, endTime);
      setSteps(totalSteps);
      setError(null);
    } catch (err) {
      console.error("Health Connect error:", err);
      captureException(err, {
        extra: {
          feature: "use-steps",
          message: "Error fetching steps",
        },
      });
      setError("Error getting steps");
    } finally {
      setLoading(false);
    }
  }, [date, getDateRange]);

  // Fetch steps when date changes or when initialized
  useEffect(() => {
    if (isInitialized && authStatus === AuthStatus.Authorized) {
      fetchSteps();
    }
  }, [date, isInitialized, authStatus, fetchSteps]);

  // Set up background delivery and event listeners
  useEffect(() => {
    const subscription = Health.addListener("onStepDataUpdate", (event) => {
      // Only update steps if we're looking at today's date
      const today = new Date().toISOString().split("T")[0];
      if (date === today) {
        setSteps(event.steps);
      }
    });

    // Enable background delivery if initialized and not already enabled
    const setupBackgroundDelivery = async () => {
      if (isInitialized && authStatus === AuthStatus.Authorized) {
        try {
          await Health.enableBackgroundDelivery("immediate");
        } catch (err) {
          // noop
        }
      }
    };

    setupBackgroundDelivery();

    // Clean up event listener when component unmounts
    return () => {
      subscription.remove();

      // Don't disable background delivery on unmount - it should remain active
      // so the app can receive updates even when not in foreground
      // We'll only disable it when the app is being uninstalled or on explicit user request
    };
  }, [isInitialized, authStatus, date, queryClient]);

  const requestInitialization = async () => {
    if (!Health.isHealthDataAvailable) {
      setError("Health Connect is not available on this device");
      return false;
    }

    setLoading(true);
    try {
      // Request authorization using our custom module
      const authorized = await Health.requestAuthorization();

      if (authorized) {
        await saveToAsyncStorage("healthkitInitialized", "true");
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
      setError("Cannot access Health Connect");
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

Above is how I'm getting steps for android in my app. 