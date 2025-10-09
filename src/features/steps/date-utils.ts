/**
 * Date utility functions for health data
 */

/**
 * Get today's date in the user's timezone as YYYY-MM-DD
 */
export function getTodayInUserTimezone(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Get the start and end timestamps for a given date string
 * @param dateString - Date in YYYY-MM-DD format
 * @returns Object with startTime and endTime in milliseconds
 */
export function getDayBoundsMs(dateString: string): {
  startTime: number;
  endTime: number;
} {
  const localDate = new Date(dateString + "T00:00:00.000");
  const startTime = new Date(localDate);
  startTime.setHours(0, 0, 0, 0);
  const endTime = new Date(localDate);
  endTime.setHours(23, 59, 59, 999);

  return {
    startTime: startTime.getTime(),
    endTime: endTime.getTime(),
  };
}