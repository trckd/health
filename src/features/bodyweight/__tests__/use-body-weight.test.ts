/**
 * @jest-environment jsdom
 */
import { act, renderHook, waitFor } from "@testing-library/react";
import { requireNativeModule } from "expo-modules-core";

jest.mock("expo-modules-core", () => {
  const health = {
    isHealthDataAvailable: true,
    requestAuthorization: jest.fn(async () => true),
    getBodyWeightSamples: jest.fn(async () => [] as unknown[]),
    getLatestBodyWeight: jest.fn(async () => null as unknown),
    enableBodyWeightUpdates: jest.fn(async () => true),
    weightListeners: [] as Array<(event: unknown) => void>,
    addListener: jest.fn((event: string, cb: (e: unknown) => void) => {
      if (event === "onBodyWeightDataUpdate") health.weightListeners.push(cb);
      return { remove: jest.fn() };
    }),
  };
  return {
    __esModule: true,
    NativeModule: class {},
    requireNativeModule: () => health,
  };
});

jest.mock("react-native", () => ({
  __esModule: true,
  Platform: {
    OS: "ios",
    select: (specifics: Record<string, unknown>) =>
      specifics.ios ?? specifics.native ?? specifics.default,
  },
}));

import { getTodayInUserTimezone } from "../../steps/date-utils";
import { useBodyWeight } from "../use-body-weight";

// See use-steps.test.ts — force eager evaluation under expo's lazy babel imports.
void [act, renderHook, waitFor];

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const health: any = requireNativeModule("Health" as never);

const sample = (value: number) => ({
  value,
  time: 0,
  isoDate: "2026-07-01T12:00:00.000Z",
});

beforeEach(() => {
  health.isHealthDataAvailable = true;
  health.requestAuthorization.mockResolvedValue(true);
  health.getBodyWeightSamples.mockResolvedValue([]);
  health.getLatestBodyWeight.mockResolvedValue(null);
  health.requestAuthorization.mockClear();
  health.getBodyWeightSamples.mockClear();
  health.addListener.mockClear();
  health.weightListeners.length = 0;
});

const today = getTodayInUserTimezone();

describe("useBodyWeight", () => {
  it("starts empty and uninitialized", () => {
    const { result } = renderHook(() => useBodyWeight(today));

    expect(result.current.bodyWeightSamples).toEqual([]);
    expect(result.current.latestWeight).toBeNull();
    expect(result.current.isInitialized).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("authorizes and loads samples for the given date", async () => {
    health.getBodyWeightSamples.mockResolvedValue([sample(80)]);
    const { result } = renderHook(() => useBodyWeight(today));

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current.requestInitialization();
    });

    expect(ok).toBe(true);
    expect(health.getBodyWeightSamples).toHaveBeenCalled();
    await waitFor(() => expect(result.current.isInitialized).toBe(true));
    await waitFor(() => expect(result.current.bodyWeightSamples).toEqual([sample(80)]));
  });

  it("appends a live sample and updates latest weight while viewing today", async () => {
    const { result } = renderHook(() => useBodyWeight(today));

    await act(async () => {
      await result.current.requestInitialization();
    });
    // The listener is only registered once authorized.
    await waitFor(() => expect(health.weightListeners.length).toBeGreaterThan(0));

    await act(async () => {
      health.weightListeners.at(-1)(sample(81));
    });

    expect(result.current.latestWeight).toEqual(sample(81));
    expect(result.current.bodyWeightSamples).toContainEqual(sample(81));
  });

  it("reports when health data is unavailable", async () => {
    health.isHealthDataAvailable = false;
    const { result } = renderHook(() => useBodyWeight(today));

    let ok: boolean | undefined;
    await act(async () => {
      ok = await result.current.requestInitialization();
    });

    expect(ok).toBe(false);
    expect(result.current.error).toMatch(/not available/);
    expect(health.requestAuthorization).not.toHaveBeenCalled();
  });
});
