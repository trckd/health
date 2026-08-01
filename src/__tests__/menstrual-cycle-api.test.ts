import { requireNativeModule } from "expo-modules-core";

jest.mock("expo-modules-core", () => {
  const record = {
    id: "native-record-1",
    kind: "period",
    startTime: 1_774_675_200_000,
    endTime: 1_775_020_800_000,
    isoStartDate: "2026-04-01T00:00:00.000Z",
    isoEndDate: "2026-04-05T00:00:00.000Z",
    startZoneOffsetMinutes: -420,
    endZoneOffsetMinutes: -420,
    zoneId: "America/Vancouver",
    flow: "medium",
    isCycleStart: true,
    source: {
      platform: "healthkit",
      recordId: "native-record-1",
      bundleIdentifier: "com.apple.Health",
      name: "Health",
      version: "19.0",
      clientRecordId: null,
      lastModifiedTime: null,
    },
  };
  const health = {
    requestAccess: jest.fn(async (requested: string[]) => ({
      success: true,
      requested,
      granted: [],
      denied: [],
      unknown: requested,
    })),
    requestHistoricalAccess: jest.fn(async () => ({
      success: true,
      platform: "ios",
      status: "not_required",
    })),
    getMenstrualCycleRecords: jest.fn(async () => [record]),
  };
  return {
    __esModule: true,
    NativeModule: class {},
    requireNativeModule: () => health,
  };
});

import { Health, MenstrualCycleRecord } from "../index";

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const nativeHealth: any = requireNativeModule("Health" as never);

describe("menstrual-cycle API", () => {
  it("exposes capability-scoped authorization without using legacy authorization", async () => {
    const result = await Health.requestAccess(["menstrualCycle"]);

    expect(nativeHealth.requestAccess).toHaveBeenCalledWith(["menstrualCycle"]);
    expect(result).toEqual({
      success: true,
      requested: ["menstrualCycle"],
      granted: [],
      denied: [],
      unknown: ["menstrualCycle"],
    });
  });

  it("preserves stable provenance and experienced timezone fields", async () => {
    const records: MenstrualCycleRecord[] =
      await Health.getMenstrualCycleRecords(
        1_774_675_200_000,
        1_775_020_800_000
      );

    expect(records[0]).toMatchObject({
      id: "native-record-1",
      kind: "period",
      startZoneOffsetMinutes: -420,
      zoneId: "America/Vancouver",
      isCycleStart: true,
      source: {
        platform: "healthkit",
        recordId: "native-record-1",
        bundleIdentifier: "com.apple.Health",
      },
    });
  });

  it("keeps historical access separate from menstrual category consent", async () => {
    await Health.requestAccess(["menstrualCycle"]);
    const history = await Health.requestHistoricalAccess();

    expect(nativeHealth.requestAccess).toHaveBeenLastCalledWith([
      "menstrualCycle",
    ]);
    expect(nativeHealth.requestHistoricalAccess).toHaveBeenCalledTimes(1);
    expect(history).toEqual({
      success: true,
      platform: "ios",
      status: "not_required",
    });
  });
});
