# Health Module for Expo

A cross-platform Expo module for accessing health data on iOS (HealthKit) and Android (Health Connect).

## Features

- ✅ **Step Count Tracking**: Get daily step counts
- ✅ **Body Weight Sync**: Read daily bodyweight entries alongside step data
- ✅ **Background Delivery**: Real-time updates when step data changes
- ✅ **Cross-Platform**: Works on both iOS and Android
- ✅ **TypeScript Support**: Full type safety
- ✅ **Permission Management**: Handle health data permissions properly

## Installation

```bash
npm install @tracked/health
```

## Platform Support

| Platform | Health Framework | Minimum Version |
|----------|------------------|-----------------|
| iOS      | HealthKit        | iOS 12.0+      |
| Android  | Health Connect   | Android 8.0+ (API 26) |

## Quick Start

```typescript
import { Health } from '@tracked/health';

// Check if health data is available
if (Health.isHealthDataAvailable) {
  // Request authorization
  const authorized = await Health.requestAuthorization();
  
  if (authorized) {
    // Get today's steps
    const today = new Date();
    const startOfDay = new Date(today.setHours(0, 0, 0, 0));
    const endOfDay = new Date(today.setHours(23, 59, 59, 999));
    
    const steps = await Health.getStepCount(
      startOfDay.getTime(),
      endOfDay.getTime()
    );

    console.log(`Today's steps: ${steps}`);

    const latestWeight = await Health.getLatestBodyWeight();
    if (latestWeight) {
      console.log(`Most recent weight: ${latestWeight.value}kg`);
    }

    // Enable background delivery
    await Health.enableBackgroundDelivery('hourly');

    // Listen for updates
    Health.addListener('onStepDataUpdate', (event) => {
      console.log('Steps updated:', event.steps);
    });

  }
}
```

## API Reference

### Properties

#### `isHealthDataAvailable: boolean`
Returns whether health data is available on the current device.

### Methods

#### `checkHealthDataAvailable(): boolean`
Synchronously checks if health data is available.

#### `requestAuthorization(): Promise<boolean>`
Requests permission to access health data. Returns `true` if authorized.

#### `getStepCount(startDate: number, endDate: number): Promise<number>`
Gets the step count for a specific time range.

- `startDate`: Start timestamp in milliseconds since epoch
- `endDate`: End timestamp in milliseconds since epoch
- Returns: Total step count for the time range

#### `hasStepDataForDate(startDate: number, endDate: number): Promise<boolean>`
Checks whether any step records exist for the given time range. Useful for deciding whether to show an empty state versus a genuine zero.

- `startDate`: Start timestamp in milliseconds since epoch
- `endDate`: End timestamp in milliseconds since epoch
- Returns: `true` if at least one step record exists in the range

#### `getBodyWeightSamples(startDate: number, endDate: number): Promise<BodyWeightSample[]>`
Returns body weight samples for the provided range (timestamps in milliseconds).

- `startDate`: Start timestamp in milliseconds since epoch
- `endDate`: End timestamp in milliseconds since epoch
- Returns: Array of weight samples sorted ascending

#### `getLatestBodyWeight(): Promise<BodyWeightSample | null>`
Fetches the most recent weight entry or `null` if none exist.

The current implementation targets read-only access so that it remains compatible with older Health Connect releases.

#### `enableBodyWeightUpdates(frequency: UpdateFrequency): Promise<boolean>`
Enables background notifications for body weight changes, delivered via the `onBodyWeightDataUpdate` event.

- `frequency`: Update frequency - `"immediate"`, `"hourly"`, `"daily"`, or `"weekly"`
- Returns: `true` if successfully enabled

#### `disableBodyWeightUpdates(): Promise<boolean>`
Disables background body weight change notifications.

#### `enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>`
Enables background delivery of step data updates.

- `frequency`: Update frequency - `"immediate"`, `"hourly"`, `"daily"`, or `"weekly"`
- Returns: `true` if successfully enabled

#### `disableBackgroundDelivery(): Promise<boolean>`
Disables background delivery of step data updates.

### Diagnostics & Recovery (Android)

These helpers surface the runtime state of the native integration and open the
relevant OS settings screens. They are primarily used to build a
step-tracking diagnostic screen and to guide users through fixing background
delivery on aggressive OEM battery managers. On iOS the "open settings" helpers
resolve to `false` / a no-op where not applicable.

#### `getHealthDiagnostics(): Promise<HealthDiagnostics>`
Returns a best-effort snapshot of the health integration's runtime state (SDK status, granted permissions, background delivery, last WorkManager run, OEM/OS info, battery-optimization state). Never throws — fields are populated best-effort and may be `null` on iOS or when an underlying call fails.

#### `triggerSyncNow(): Promise<boolean>`
Schedules an immediate WorkManager sync run for the Health Connect change pipeline (e.g. a "Run sync now" button on a diagnostic screen).

- Returns: `true` if the sync was successfully enqueued

#### `openHealthConnectSettings(): Promise<boolean>`
Opens the system Health Connect settings UI on Android, falling back to the Play Store listing if Health Connect is not installed. On iOS, opens the Health app via Settings.

#### `openBatteryOptimizationSettings(): Promise<OpenSettingsResult>`
Opens the OS battery-optimization settings for this app, trying multiple intents in order of specificity. iOS resolves to `{ ok: false }` (not applicable).

#### `openOemAppLaunchSettings(): Promise<OpenOemSettingsResult>`
Opens the OEM-specific auto-launch / background-activity manager (ColorOS, MIUI, EMUI, OnePlus, Vivo), falling back to the app details settings. The `oem` field reports which family was detected.

### Events

#### `onStepDataUpdate`
Fired when step data is updated in the background.

```typescript
Health.addListener('onStepDataUpdate', (event: StepUpdateEvent) => {
  console.log('Steps:', event.steps);
  console.log('Date:', event.date);
});
```

Event payload:
```typescript
interface StepUpdateEvent {
  steps: number;
  date: string;
}
```

#### `onBodyWeightDataUpdate`
Fired when a new body weight sample is recorded in the background (requires `enableBodyWeightUpdates`).

```typescript
Health.addListener('onBodyWeightDataUpdate', (event: BodyWeightSample) => {
  console.log('Weight (kg):', event.value);
  console.log('Recorded at:', event.isoDate);
});
```

### Types

```typescript
interface StepUpdateEvent {
  steps: number;
  date: string;
}

interface BodyWeightSample {
  value: number; // kilograms
  time: number; // epoch milliseconds
  isoDate: string; // ISO 8601 timestamp
  source?: string;
}

// Alias for the onBodyWeightDataUpdate event payload
type BodyWeightUpdateEvent = BodyWeightSample;

type UpdateFrequency = 'immediate' | 'hourly' | 'daily' | 'weekly';

interface HealthDiagnostics {
  sdkStatus: string; // AVAILABLE | UNAVAILABLE | PROVIDER_UPDATE_REQUIRED | EXCEPTION | UNKNOWN
  providerPackage: string | null;
  providerVersionCode: number | null;
  providerVersionName: string | null;
  permissionsGranted: boolean | null; // null on iOS
  grantedPermissions: string[];
  backgroundDeliveryEnabled: boolean;
  lastWorkerRunMs: number | null;
  lastWorkerResult: string | null;
  lastWorkerError: string | null;
  lastChangesTokenIssuedMs: number | null;
  workManagerState: string | null; // ENQUEUED | RUNNING | SUCCEEDED | FAILED | CANCELLED | BLOCKED
  oemBrand: string | null;
  oemManufacturer: string | null;
  oemModel: string | null;
  oemDevice: string | null;
  osSdkInt: number | null;
  osRelease: string | null;
  ignoringBatteryOptimizations: boolean;
}

interface OpenSettingsResult {
  ok: boolean;
  intentUsed: string | null;
}

interface OpenOemSettingsResult extends OpenSettingsResult {
  oem: string;
}
```

## Platform-Specific Setup

### iOS (HealthKit)

Add the following to your `ios/YourApp/Info.plist`:

```xml
<key>NSHealthShareUsageDescription</key>
<string>This app needs access to your step count and body weight to keep your daily logs in sync.</string>
<key>NSHealthUpdateUsageDescription</key>
<string>This app reads your step count and body weight to keep your daily logs in sync.</string>
```

### Android (Health Connect)

1. **Add permissions** to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_WEIGHT" />

<queries>
    <package android:name="com.google.android.apps.healthdata" />
    <intent>
        <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
    </intent>
</queries>
```

2. **Install Health Connect** on your Android device from the Google Play Store.

3. **Grant permissions** through the Health Connect app.

## Usage with React Hook

The module ships ready-made `useSteps` and `useBodyWeight` hooks — import them
directly instead of writing your own:

```typescript
import { useSteps, useBodyWeight } from '@tracked/health';

function Dashboard() {
  const { steps, loading, error, isInitialized, requestInitialization } =
    useSteps('2026-07-01');
  const { latestWeight } = useBodyWeight();

  if (!isInitialized) {
    return <Button title="Connect health data" onPress={requestInitialization} />;
  }

  return <Text>{loading ? 'Loading…' : error ?? `${steps} steps`}</Text>;
}
```

Both hooks manage authorization, background updates, and (for `useSteps`) live
step deliveries for you. `useBodyWeight(date?)` returns
`{ bodyWeightSamples, latestWeight, loading, error, isInitialized, requestInitialization }`.

### Building your own hook

If you need custom behavior, you can build a hook directly against the `Health`
module. The built-in `useSteps` is implemented like this:

```typescript
import { useCallback, useEffect, useState } from 'react';
import { Health, StepUpdateEvent } from '@tracked/health';

export function useSteps(date: string) {
  const [steps, setSteps] = useState<number>(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isAuthorized, setIsAuthorized] = useState(false);

  const requestAuthorization = useCallback(async () => {
    if (!Health.isHealthDataAvailable) {
      setError('Health data is not available on this device');
      return false;
    }

    try {
      setLoading(true);
      const authorized = await Health.requestAuthorization();
      setIsAuthorized(authorized);
      
      if (authorized) {
        await Health.enableBackgroundDelivery('hourly');
      }
      
      return authorized;
    } catch (err) {
      setError('Failed to request authorization');
      return false;
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchSteps = useCallback(async () => {
    if (!isAuthorized) return;

    try {
      setLoading(true);
      const localDate = new Date(date + 'T00:00:00.000');
      const startTime = new Date(localDate);
      startTime.setHours(0, 0, 0, 0);
      const endTime = new Date(localDate);
      endTime.setHours(23, 59, 59, 999);

      // getStepCount expects timestamps in milliseconds since epoch.
      const totalSteps = await Health.getStepCount(
        startTime.getTime(),
        endTime.getTime()
      );
      
      setSteps(totalSteps);
      setError(null);
    } catch (err) {
      setError('Failed to fetch steps');
    } finally {
      setLoading(false);
    }
  }, [date, isAuthorized]);

  // Listen for background updates
  useEffect(() => {
    if (!isAuthorized) return;

    const subscription = Health.addListener('onStepDataUpdate', (event: StepUpdateEvent) => {
      // Compare against the local calendar date (not the UTC date from
      // toISOString) so evening updates in negative-UTC timezones still match.
      const now = new Date();
      const today = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`;
      if (date === today) {
        setSteps(event.steps);
      }
    });

    return () => subscription.remove();
  }, [date, isAuthorized]);

  // Fetch steps when authorized or date changes
  useEffect(() => {
    if (isAuthorized) {
      fetchSteps();
    }
  }, [fetchSteps]);

  return {
    steps,
    loading,
    error,
    isAuthorized,
    requestAuthorization,
  };
}
```

## Background Delivery

The module supports background delivery of step data updates:

- **iOS**: Uses HealthKit's native background delivery system
- **Android**: Uses WorkManager for periodic background tasks

### Frequency Options

- `"immediate"`: Updates as soon as data changes (iOS) or every 15 minutes (Android minimum)
- `"hourly"`: Updates every hour
- `"daily"`: Updates once per day
- `"weekly"`: Updates once per week

### Battery Optimization

On Android, users may need to disable battery optimization for your app to ensure background delivery works properly.

## Troubleshooting

### iOS Issues

1. **No data returned**: Check that HealthKit permissions are granted in Settings > Privacy & Security > Health
2. **Background delivery not working**: Ensure Background App Refresh is enabled for your app
3. **Authorization fails**: Make sure `NSHealthShareUsageDescription` is added to Info.plist

### Android Issues

1. **Health Connect not available**: Install Health Connect from Google Play Store
2. **Permissions denied**: Grant permissions manually in the Health Connect app
3. **No background updates**: Disable battery optimization for your app
4. **No data**: Add sample data through the Health Connect app

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for details on how to contribute to this project.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
