# Health Module for Expo

A cross-platform Expo module for accessing health data on iOS (HealthKit) and Android (Health Connect).

## Features

- ✅ **Step Count Tracking**: Get daily step counts
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
      Math.floor(startOfDay.getTime() / 1000),
      Math.floor(endOfDay.getTime() / 1000)
    );
    
    console.log(`Today's steps: ${steps}`);
    
    // Enable background delivery
    await Health.enableBackgroundDelivery('hourly');
    
    // Listen for step updates
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

- `startDate`: Start timestamp in seconds since epoch
- `endDate`: End timestamp in seconds since epoch
- Returns: Total step count for the time range

#### `enableBackgroundDelivery(frequency: UpdateFrequency): Promise<boolean>`
Enables background delivery of step data updates.

- `frequency`: Update frequency - `"immediate"`, `"hourly"`, `"daily"`, or `"weekly"`
- Returns: `true` if successfully enabled

#### `disableBackgroundDelivery(): Promise<boolean>`
Disables background delivery of step data updates.

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

## Platform-Specific Setup

### iOS (HealthKit)

Add the following to your `ios/YourApp/Info.plist`:

```xml
<key>NSHealthShareUsageDescription</key>
<string>This app needs access to health data to track your daily steps.</string>
<key>NSHealthUpdateUsageDescription</key>
<string>This app needs access to health data to track your daily steps.</string>
```

### Android (Health Connect)

1. **Add permissions** to your `android/app/src/main/AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.WRITE_STEPS" />

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

Here's an example React hook that uses the Health module:

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

      const totalSteps = await Health.getStepCount(
        Math.floor(startTime.getTime() / 1000),
        Math.floor(endTime.getTime() / 1000)
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
      const today = new Date().toISOString().split('T')[0];
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
