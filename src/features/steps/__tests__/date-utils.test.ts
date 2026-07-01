// Pin the timezone to a negative-UTC-offset zone BEFORE importing the module
// under test. This is deliberate: these helpers exist to work in the device's
// *local* calendar day, and a UTC-based implementation only diverges from a
// local one when the local date differs from the UTC date — which happens in
// the Americas during local evening hours. A test that runs in UTC (or pins
// `now` to UTC midnight) would mask exactly the bug these helpers guard against.
process.env.TZ = "America/Los_Angeles";

import { getDayBoundsMs, getTodayInUserTimezone } from "../date-utils";

describe("getDayBoundsMs", () => {
  it("returns local midnight through the last millisecond of the day", () => {
    const { startTime, endTime } = getDayBoundsMs("2026-06-30");

    const start = new Date(startTime);
    const end = new Date(endTime);

    expect(start.getFullYear()).toBe(2026);
    expect(start.getMonth()).toBe(5); // June (0-indexed)
    expect(start.getDate()).toBe(30);
    expect(start.getHours()).toBe(0);
    expect(start.getMinutes()).toBe(0);
    expect(start.getSeconds()).toBe(0);
    expect(start.getMilliseconds()).toBe(0);

    expect(end.getDate()).toBe(30);
    expect(end.getHours()).toBe(23);
    expect(end.getMinutes()).toBe(59);
    expect(end.getSeconds()).toBe(59);
    expect(end.getMilliseconds()).toBe(999);
  });

  it("spans exactly one day minus a millisecond (timezone-independent)", () => {
    const { startTime, endTime } = getDayBoundsMs("2026-01-15");
    expect(endTime - startTime).toBe(24 * 60 * 60 * 1000 - 1);
  });

  it("orders start before end", () => {
    const { startTime, endTime } = getDayBoundsMs("2026-12-31");
    expect(startTime).toBeLessThan(endTime);
  });
});

describe("getTodayInUserTimezone", () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it("uses the local calendar date, not the UTC date", () => {
    // 2026-07-01T06:30:00Z is still 2026-06-30 (23:30) in Los Angeles.
    // A UTC-based implementation would incorrectly return "2026-07-01".
    jest.useFakeTimers().setSystemTime(new Date("2026-07-01T06:30:00.000Z"));
    expect(getTodayInUserTimezone()).toBe("2026-06-30");
  });

  it("zero-pads single-digit months and days", () => {
    // 2026-03-05T20:00:00Z -> 2026-03-05 12:00 in Los Angeles.
    jest.useFakeTimers().setSystemTime(new Date("2026-03-05T20:00:00.000Z"));
    expect(getTodayInUserTimezone()).toBe("2026-03-05");
  });

  it("matches the boundaries produced by getDayBoundsMs for the same day", () => {
    jest.useFakeTimers().setSystemTime(new Date("2026-07-01T06:30:00.000Z"));
    const today = getTodayInUserTimezone();
    const { startTime } = getDayBoundsMs(today);
    expect(new Date(startTime).getDate()).toBe(30);
  });
});
