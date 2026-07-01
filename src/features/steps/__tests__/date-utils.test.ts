// These tests pin the timezone to America/Los_Angeles so they exercise the
// local-vs-UTC boundary the date helpers guard against — under UTC the local
// and UTC dates are identical and a broken (UTC-based) implementation would
// pass silently.
//
// The zone MUST be set before the process starts — via the `test` npm script
// and the CI workflow — not with `process.env.TZ = ...` inside this file: Node
// caches the zone at startup, so a runtime reassignment is a no-op on hosts
// whose real zone differs (e.g. a UTC CI runner), which would silently mask the
// very bug under test. The guard below fails fast with a clear message if the
// suite wasn't launched in the expected zone.
import { getDayBoundsMs, getTodayInUserTimezone } from "../date-utils";

// 2026-07-01T06:30Z is still June 30 in Los Angeles but July 1 in UTC.
if (new Date("2026-07-01T06:30:00.000Z").getDate() !== 30) {
  throw new Error(
    "date-utils tests require TZ=America/Los_Angeles. Run `yarn test` (which " +
      "sets it) instead of invoking jest directly in another timezone.",
  );
}

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
