import { useCallback, useEffect, useState } from "react";

// Import the health module with its type
import { Health as HealthModule } from "@tracked/health";

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
        await fetchSteps();
        return true;
      } else {
        setError("Health data access was denied");
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

  return {
    steps,
    loading,
    error,
    isAuthorized: authStatus === AuthStatus.Authorized,
    requestInitialization,
  };
}
