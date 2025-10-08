import { useCallback, useEffect, useMemo, useState } from "react";
import { BodyWeightSample, Health } from "@tracked/health";

export interface BodyWeightOptions {
  units: "kg" | "lb";
}

const LB_PER_KG = 2.20462;

function toDisplayValue(valueInKg: number, units: "kg" | "lb") {
  return units === "lb" ? valueInKg * LB_PER_KG : valueInKg;
}

function getDayRange(timestamp: number) {
  const date = new Date(timestamp);
  const start = new Date(date);
  start.setHours(0, 0, 0, 0);
  const end = new Date(date);
  end.setHours(23, 59, 59, 999);
  return { start: start.getTime(), end: end.getTime() };
}

export function useBodyWeight(date: string, options: BodyWeightOptions = { units: "kg" }) {
  const [sample, setSample] = useState<BodyWeightSample | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dateTimestamp = useMemo(() => new Date(date + "T00:00:00.000").getTime(), [date]);

  const fetchBodyWeight = useCallback(async () => {
    if (!Health.isHealthDataAvailable) {
      setError("Health data not available on this device");
      return;
    }

    try {
      setLoading(true);
      const { start, end } = getDayRange(dateTimestamp);
      const samples = await Health.getBodyWeightSamples(start, end);
      setSample(samples.length > 0 ? samples[samples.length - 1] : null);
      setError(null);
    } catch (err) {
      console.error("Failed to fetch body weight", err);
      setError("Failed to fetch body weight");
    } finally {
      setLoading(false);
    }
  }, [dateTimestamp]);

  useEffect(() => {
    fetchBodyWeight();
  }, [fetchBodyWeight]);

  const displayValue = sample ? toDisplayValue(sample.value, options.units) : null;

  return {
    sample,
    displayValue,
    loading,
    error,
    refresh: fetchBodyWeight,
  };
}
