import { useCallback, useEffect, useState } from "react";

// Import the health module with its type
import { Health as HealthModule, UpdateFrequency } from "@tracked/health";
import { StepUpdateEvent } from "@tracked/health/module";

// Define our own status constants to replace the HKAuthorizationRequestStatus
export enum AuthStatus {
  Unknown = "UNKNOWN",
  Authorized = "AUTHORIZED",
  NotAuthorized = "NOT_AUTHORIZED",
}

export function useSteps(date: string) {
  const [steps, setSteps] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authStatus, setAuthStatus] = useState<AuthStatus>(AuthStatus.Unknown);
  const [backgroundDeliveryStatus, setBackgroundDeliveryStatus] = useState<UpdateFrequency | null>(null);

  const getDateRange = useCallback((dateString: string) => {
    const localDate = new Date(dateString + "T00:00:00.000");
    const startTime = new Date(localDate);
    startTime.setHours(0, 0, 0, 0);
    const endTime = new Date(localDate);
    endTime.setHours(23, 59, 59, 999);
    return {
      startTime: Math.floor(startTime.getTime() / 1000),
      endTime: Math.floor(endTime.getTime() / 1000)
    };
  }, []);

  const fetchSteps = useCallback(async () => {
    if (!HealthModule.isHealthDataAvailable) {
      setError("HealthKit is not available on this device");
      return;
    }

    try {
      setLoading(true);
      const { startTime, endTime } = getDateRange(date);
      const totalSteps = await HealthModule.getStepCount(startTime, endTime);
      setSteps(totalSteps);
      setError(null);
    } catch (err) {
      console.error("HealthKit error:", err);
      setError("Error getting steps");
    } finally {
      setLoading(false);
    }
  }, [date, getDateRange]);

  useEffect(() => {
    if (authStatus === AuthStatus.Authorized) {
      fetchSteps();
    }
  }, [date, authStatus, fetchSteps]);

  // Set up background delivery
  useEffect(() => {
    if (authStatus === AuthStatus.Authorized) {
      HealthModule.enableBackgroundDelivery("immediate").then((success) => {
        if (success) {
          setBackgroundDeliveryStatus("immediate");
        } else {
          setBackgroundDeliveryStatus(null);
        }
      });
    }
  }, [authStatus]);

  // Listen for step count updates from background delivery
  useEffect(() => {
    // Only set up listener if authorized
    if (authStatus !== AuthStatus.Authorized) return;

    // Add event listener for step updates
    const subscription = HealthModule.addListener("onStepDataUpdate", (event: StepUpdateEvent) => {
      // Only update steps if we're looking at today's date
      const today = new Date().toISOString().split('T')[0];
      if (date === today) {
        console.log("Received background step update:", event.steps);
        setSteps(event.steps);
      }
    });
    
    // Clean up event listener when component unmounts or date changes
    return () => {
      subscription.remove();
    };
  }, [date, authStatus]);

  const requestInitialization = async () => {
    if (!HealthModule.isHealthDataAvailable) {
      setError("HealthKit is not available on this device");
      return false;
    }

    try {
      setLoading(true);
      const authorized = await HealthModule.requestAuthorization();
      
      if (authorized) {
        setAuthStatus(AuthStatus.Authorized);
        await HealthModule.enableBackgroundDelivery("immediate");
        await fetchSteps();
        return true;
      } else {
        setError("Health data access was denied");
        await HealthModule.disableBackgroundDelivery();
        setAuthStatus(AuthStatus.NotAuthorized);
        return false;
      }
    } catch (err) {
      setError("Cannot access HealthKit");
      setAuthStatus(AuthStatus.NotAuthorized);
      console.error(err);
      return false;
    } finally {
      setLoading(false);
    }
  };

  // Function to explicitly disable background delivery
  const disableBackgroundDelivery = async () => {
    try {
      const success = await HealthModule.disableBackgroundDelivery();
      if (success) {
        setBackgroundDeliveryStatus(null);
      }
      return success;
    } catch (err) {
      console.error("Error disabling background delivery:", err);
      return false;
    }
  };

  return {
    steps,
    loading,
    error,
    isAuthorized: authStatus === AuthStatus.Authorized,
    requestInitialization,
    backgroundDeliveryStatus,
    disableBackgroundDelivery,
  };
}
