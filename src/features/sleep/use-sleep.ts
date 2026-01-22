import { useCallback, useEffect, useState } from "react";
import { Platform } from "react-native";

// Import the health module with its types
import { Health, SleepSession } from "../..";

export enum AuthStatus {
  Unknown = "UNKNOWN",
  Authorized = "AUTHORIZED",
  NotAuthorized = "NOT_AUTHORIZED",
}

export function useSleep(date: string) {
  const [sleepSessions, setSleepSessions] = useState<SleepSession[]>([]);
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

  // Prepare date range for the query
  const getDateRange = useCallback((dateString: string) => {
    const localDate = new Date(dateString + "T00:00:00.000");
    const startTime = new Date(localDate);
    startTime.setHours(0, 0, 0, 0);
    const endTime = new Date(localDate);
    endTime.setHours(23, 59, 59, 999);
    return { startTime: startTime.getTime(), endTime: endTime.getTime() };
  }, []);

  // Fetch sleep sessions using our custom Health module
  const fetchSleepSessions = useCallback(async () => {
    try {
      if (!Health.isHealthDataAvailable) {
        setError(`${healthServiceName} is not available on this device`);
        return;
      }

      setLoading(true);
      const { startTime, endTime } = getDateRange(date);

      const sessions = await Health.getSleepSessions(startTime, endTime);
      setSleepSessions(sessions);
      setError(null);
    } catch (err) {
      console.error(`${healthServiceName} error:`, err);
      setError("Error getting sleep data");
    } finally {
      setLoading(false);
    }
  }, [date, getDateRange, healthServiceName]);

  // Fetch sleep when date changes or when initialized
  useEffect(() => {
    if (isInitialized && authStatus === AuthStatus.Authorized) {
      fetchSleepSessions();
    }
  }, [date, isInitialized, authStatus, fetchSleepSessions]);

  // Set up background delivery and event listeners
  useEffect(() => {
    const subscription = Health.addListener("onSleepDataUpdate", (event: SleepSession) => {
      // Check if this sleep session is for the current date being viewed
      const sessionDate = new Date(event.startTime).toISOString().split("T")[0];
      if (date === sessionDate) {
        // Update or add the sleep session
        setSleepSessions((prevSessions) => {
          const existingIndex = prevSessions.findIndex(
            (s) => s.startTime === event.startTime && s.endTime === event.endTime
          );
          if (existingIndex >= 0) {
            // Update existing session
            const newSessions = [...prevSessions];
            newSessions[existingIndex] = event;
            return newSessions;
          } else {
            // Add new session
            return [...prevSessions, event].sort((a, b) => a.startTime - b.startTime);
          }
        });
      }
    });

    // Enable background delivery if initialized and not already enabled
    const setupBackgroundDelivery = async () => {
      if (isInitialized && authStatus === AuthStatus.Authorized) {
        try {
          await Health.enableSleepUpdates("daily");
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
        await fetchSleepSessions();
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

  // Calculate total sleep duration from all sessions
  const totalSleepDuration = sleepSessions.reduce(
    (total, session) => total + session.totalDuration,
    0
  );

  // Calculate sleep stage durations
  const sleepStageBreakdown = sleepSessions.reduce((breakdown, session) => {
    session.stages.forEach((stage) => {
      if (!breakdown[stage.type]) {
        breakdown[stage.type] = 0;
      }
      breakdown[stage.type] += stage.duration;
    });
    return breakdown;
  }, {} as Record<string, number>);

  return {
    sleepSessions,
    totalSleepDuration,
    sleepStageBreakdown,
    loading,
    error,
    isInitialized,
    requestInitialization,
    refetch: fetchSleepSessions,
  };
}
