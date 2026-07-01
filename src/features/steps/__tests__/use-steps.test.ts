/**
 * @jest-environment jsdom
 */
import { act, renderHook, waitFor } from "@testing-library/react";
import { requireNativeModule } from "expo-modules-core";

// Mock the native module. The health object lives inside the factory (so there
// is no temporal-dead-zone problem with hoisted imports) and is always returned
// by requireNativeModule, so the hook and the test share one instance we can
// reconfigure per test.
jest.mock("expo-modules-core", () => {
  const health = {
    isHealthDataAvailable: true,
    requestAuthorization: jest.fn(async () => true),
    getStepCount: jest.fn(async () => 1234),
    enableBackgroundDelivery: jest.fn(async () => true),
    stepListeners: [] as Array<(event: { steps: number; date: string }) => void>,
    addListener: jest.fn((event: string, cb: (e: { steps: number; date: string }) => void) => {
      if (event === "onStepDataUpdate") health.stepListeners.push(cb);
      return { remove: jest.fn() };
    }),
  };
  return {
    __esModule: true,
    NativeModule: class {},
    requireNativeModule: () => health,
  };
});

// The hook only uses Platform.select; a minimal stub keeps this out of the
// heavier React Native jest preset.
jest.mock("react-native", () => ({
  __esModule: true,
  Platform: {
    OS: "ios",
    select: (specifics: Record<string, unknown>) =>
      specifics.ios ?? specifics.native ?? specifics.default,
  },
}));

import { getTodayInUserTimezone } from "../date-utils";
import { useSteps } from "../use-steps";

// expo-module-scripts' babel uses lazy CommonJS imports, which would defer
// @testing-library/react's evaluation until the first renderHook call (inside a
// test) — its automatic cleanup then registers an afterEach mid-test and throws.
// Touch the bindings at module scope to force eager evaluation.
void [act, renderHook, waitFor];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const health: any = requireNativeModule("Health" as never);

beforeEach(() => {
  health.isHealthDataAvailable = true;
  health.requestAuthorization.mockResolvedValue(true);
  health.getStepCount.mockResolvedValue(1234);
  health.enableBackgroundDelivery.mockResolvedValue(true);
  health.requestAuthorization.mockClear();
  health.getStepCount.mockClear();
  health.addListener.mockClear();
  health.stepListeners.length = 0;
});

const today = getTodayInUserTimezone();

describe("useSteps", () => {
  it("starts uninitialized with zero steps", () => {
    const { result } = renderHook(() => useSteps(today));

    expect(result.current.steps).toBe(0);
    expect(result.current.isInitialized).toBe(false);
    expect(result.current.error).toBeNull();
    expect(result.current.loading).toBe(false);
  });

  it("authorizes and fetches the step count", async () => {
    health.getStepCount.mockResolvedValue(4210);
    const { result } = renderHook(() => useSteps(today));

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current.requestInitialization();
    });

    expect(ok).toBe(true);
    expect(health.requestAuthorization).toHaveBeenCalled();
    expect(health.getStepCount).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isInitialized).toBe(true));
    expect(result.current.steps).toBe(4210);
    expect(result.current.error).toBeNull();
  });

  it("surfaces a denied authorization", async () => {
    health.requestAuthorization.mockResolvedValue(false);
    const { result } = renderHook(() => useSteps(today));

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current.requestInitialization();
    });

    expect(ok).toBe(false);
    expect(result.current.isInitialized).toBe(false);
    expect(result.current.error).toBe("Health data access was denied");
    expect(health.getStepCount).not.toHaveBeenCalled();
  });

  it("reports when health data is unavailable", async () => {
    health.isHealthDataAvailable = false;
    const { result } = renderHook(() => useSteps(today));

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current.requestInitialization();
    });

    expect(ok).toBe(false);
    expect(result.current.error).toMatch(/not available/);
    expect(health.requestAuthorization).not.toHaveBeenCalled();
  });

  it("applies a background step update while viewing today", async () => {
    const { result } = renderHook(() => useSteps(today));

    expect(health.stepListeners.length).toBeGreaterThan(0);

    await act(async () => {
      health.stepListeners.at(-1)({ steps: 8000, date: today });
    });

    expect(result.current.steps).toBe(8000);
  });

  it("ignores background updates while viewing a past date", async () => {
    const { result } = renderHook(() => useSteps("2000-01-01"));

    await act(async () => {
      health.stepListeners.at(-1)({ steps: 8000, date: "2000-01-01" });
    });

    expect(result.current.steps).toBe(0);
  });
});
