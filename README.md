# Health Module

A focused native module for integrating with HealthKit on iOS to read step count data.

## Setup

This module is already set up with the following configurations:

1. **HealthKit Capability**: HealthKit is enabled in the app's capabilities.
2. **Privacy Descriptions**:
   - `NSHealthShareUsageDescription` - Explains why the app reads HealthKit data.
3. **Entitlements**:
   - `com.apple.developer.healthkit`

## Usage

Import the module:

```typescript
import Health from "modules/health";
```

### Checking HealthKit Availability

```typescript
const isAvailable = Health.isHealthDataAvailable;
```

### Requesting Authorization

```typescript
try {
  const authorized = await Health.requestAuthorization();
  console.log("HealthKit authorization:", authorized ? "granted" : "denied");
} catch (error) {
  console.error("Error requesting HealthKit authorization:", error);
}
```

### Reading Step Count

```typescript
try {
  // Get steps for today
  const now = new Date();
  const startOfDay = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
    0,
    0,
    0,
  );

  const steps = await Health.getStepCount(startOfDay, now);
  console.log("Steps today:", steps);
} catch (error) {
  console.error("Error fetching step count:", error);
}
```

## Example

See the `example-usage.tsx` file for a complete example of how to use this module.

## Supported Data Types

The module currently supports reading:

- Step Count

## Extending

To add more HealthKit data types in the future:

1. Edit `HealthModule.swift` to add more data types to `typesToRead` in the `requestAuthorization` function.
2. Create additional query functions similar to `getStepCount` for the new data types.
3. Update the TypeScript definition in `module.ts` to expose the new functions.
