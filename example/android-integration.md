# Android Health Connect Integration

This document explains how to integrate Health Connect on Android to match the iOS HealthKit functionality.

## Prerequisites

1. **Android API Level 26+**: Health Connect requires Android 8.0 (API level 26) or higher
2. **Health Connect App**: Users need to have the Health Connect app installed on their device
3. **Permissions**: Your app needs to declare and request Health Connect permissions

## App Configuration

### 1. Add to your app's `android/app/src/main/AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    
    <!-- Health Connect permissions -->
    <uses-permission android:name="android.permission.health.READ_STEPS" />
    <uses-permission android:name="android.permission.health.WRITE_STEPS" />
    
    <!-- Health Connect intent filters -->
    <queries>
        <package android:name="com.google.android.apps.healthdata" />
        <intent>
            <action android:name="androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE" />
        </intent>
    </queries>
    
    <application>
        <!-- Your app configuration -->
        
        <!-- Health Connect provider -->
        <activity-alias
            android:name="ViewPermissionUsageActivity"
            android:exported="true"
            android:targetActivity="androidx.health.connect.client.PermissionController"
            android:permission="android.permission.START_VIEW_PERMISSION_USAGE">
            <intent-filter>
                <action android:name="android.intent.action.VIEW_PERMISSION_USAGE" />
                <category android:name="android.intent.category.HEALTH_PERMISSIONS" />
            </intent-filter>
        </activity-alias>
    </application>
</manifest>
```

### 2. Add to your app's `android/app/build.gradle`:

```gradle
dependencies {
    // ... other dependencies
    
    // Health Connect dependencies
    implementation 'androidx.health.connect:connect-client:1.1.0-alpha07'
    implementation 'androidx.work:work-runtime-ktx:2.9.0'
}
```

## Implementation Details

### Permission Handling

The Android module includes a dedicated `HealthPermissionActivity` that handles Health Connect permission requests properly. When you call `Health.requestAuthorization()`, it:

1. Checks if permissions are already granted
2. If not, launches the Health Connect permission screen
3. Returns the result asynchronously

### Background Delivery

The module uses Android's WorkManager to implement background delivery:

- **Immediate**: 15-minute intervals (WorkManager minimum)
- **Hourly**: 1-hour intervals
- **Daily**: 24-hour intervals
- **Weekly**: 7-day intervals

The background worker:
1. Checks for valid permissions
2. Queries Health Connect for today's step data
3. Sends events to the JavaScript layer through the module instance

### Event Communication

The module maintains a static reference to itself so that the WorkManager background tasks can communicate with the active module instance to send step update events.

## Usage

The Android implementation provides the same API as iOS:

```typescript
import { Health } from '@tracked/health';

// Check if Health Connect is available
const isAvailable = Health.isHealthDataAvailable;

// Request permissions (launches Health Connect permission screen)
const authorized = await Health.requestAuthorization();

// Get step count for a specific day
const steps = await Health.getStepCount(startTimestamp, endTimestamp);

// Enable background delivery
await Health.enableBackgroundDelivery('hourly');

// Listen for step updates
Health.addListener('onStepDataUpdate', (event) => {
  console.log('Steps updated:', event.steps);
});
```

## Key Differences from iOS

1. **Permission Model**: Health Connect uses a different permission model than HealthKit
2. **Background Delivery**: Uses WorkManager instead of native HealthKit background delivery
3. **Data Sources**: Health Connect aggregates data from multiple sources automatically
4. **Minimum Intervals**: WorkManager has a minimum interval of 15 minutes for periodic work
5. **Permission UI**: Uses Health Connect's native permission screen

## Production Considerations

### Battery Optimization

Users may need to disable battery optimization for your app to ensure background delivery works properly. You can guide users to:

1. Go to Settings > Apps > Your App > Battery
2. Select "Don't optimize" or "Unrestricted"

### Data Availability

Health Connect requires:
- The Health Connect app to be installed
- At least one connected app providing step data
- User to have granted permissions

### Error Handling

The module gracefully handles:
- Missing Health Connect app
- Denied permissions
- No available data
- Network connectivity issues

## Testing

To test the Health Connect integration:

1. Install the Health Connect app on your Android device (API 26+)
2. Add some sample step data through the Health Connect app or a fitness app
3. Run your app and test the permission flow
4. Verify step data retrieval and background delivery

## Troubleshooting

### Common Issues:

1. **Health Connect not available**: 
   - Ensure device has Android 8.0+ 
   - Install Health Connect from Google Play Store
   - Check `Health.isHealthDataAvailable`

2. **Permissions denied**: 
   - Guide users to manually grant permissions in Health Connect
   - Check if permissions are properly declared in AndroidManifest.xml

3. **No data returned**: 
   - Verify there's actual step data in Health Connect
   - Check the time range being queried
   - Ensure permissions are granted

4. **Background delivery not working**: 
   - Verify WorkManager is properly configured
   - Check if battery optimization is disabled
   - Review device logs for WorkManager errors

### Debug Steps:

1. Check device logs: `adb logcat | grep HealthModule`
2. Verify Health Connect app is installed and updated
3. Test with manual data entry in Health Connect
4. Check WorkManager status in device settings
5. Verify permissions in Health Connect app settings

## Logs and Debugging

The module provides comprehensive logging:

```
D/HealthModule: Retrieved 1234 steps for period 2024-01-01T00:00:00Z to 2024-01-01T23:59:59Z
I/HealthModule: Background delivery enabled with frequency: hourly
D/StepDataWorker: Starting background step data sync
D/StepDataWorker: Retrieved 1234 steps for today
D/StepDataWorker: Sent step data update event
```

Enable debug logging to troubleshoot issues during development and testing. 