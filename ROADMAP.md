• Bodyweight Roadmap

  - Validate demand: confirm whether users need read-only weight history,
    in-app logging/write support, trend analytics, or goal tracking; gather
    target units (kg/lb) and acceptable latency for updates.
  - Expand API surface: design new TypeScript types
    (BodyWeightSample, BodyWeightTrend), methods (getLatestBodyWeight,
    getBodyWeightSamples(range)), and optional events; keep step API
    backward compatible. Document limitations when running on legacy
    Health Connect builds (read-only where write APIs are unavailable).
  - iOS implementation (ios/HealthModule.swift): request
    HKQuantityTypeIdentifier.bodyMass read (& write if logging), add
    queries via HKSampleQuery/HKStatisticsQuery for latest and range,
    handle unit conversion, and optionally schedule an observer query for
    same-day updates.
  - Android implementation (android/src/main/java/com/tracked/health/
    HealthModule.kt): include WeightRecord permissions, implement read/
    write using ReadRecordsRequest and UpsertRecordsRequest, guard for
    HealthConnectClient API availability, and add WorkManager task only if
    ongoing sync is required.
  - Surface the feature: wire the JS bridge (src/module.ts) and example app
    hooks, gating UI on authorization; add doc updates (README, platform
    setup, migration notes) and cover unit tests (TS) plus manual QA
    checklists on real devices/emulators.
  - Slipstream launch: ship behind a feature flag, gather beta feedback,
    then promote doc snippets/blog release; outline metrics to monitor
    (usage, error rates, permission denial).

  Future Integrations To Queue

      1. Heart rate & resting trends (high engagement win for fitness
         apps).
      2. Workouts & energy burned (aligns with coaching features).
      3. Sleep duration/stages (popular retention lever).
      4. Body composition (body fat %, lean mass) once weight API is
         stable.
      5. Hydration or nutrition data if your product roadmap includes
         coaching/nutrition.

  Next steps: pick API design scope (read-only vs read/write), then create
  tickets per platform + docs, and schedule device testing once native
  stubs are ready.
