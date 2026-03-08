import ExpoModulesCore
import HealthKit

public class HealthModule: Module {
  private let healthStore = HKHealthStore()
  private var stepCountObserver: HKObserverQuery?
  private var observerStarted = false
  private var bodyWeightObserver: HKObserverQuery?
  private var bodyWeightObservationStarted = false
  private var bodyWeightAnchor: HKQueryAnchor?
  private let bodyWeightAnchorKey = "com.tracked.health.bodyweight.anchor"
  private let isoFormatter: ISO8601DateFormatter = {
    let formatter = ISO8601DateFormatter()
    formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return formatter
  }()
  private let dayFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd"
    formatter.locale = Locale(identifier: "en_US_POSIX")
    return formatter
  }()
  public func definition() -> ModuleDefinition {
    Name("Health")

    Events("onStepDataUpdate", "onBodyWeightDataUpdate")

    Function("addListener") { (_: String) in
      // Required for Expo event emitter compliance
    }

    Function("removeListeners") { (_: Int) in
      // Required for Expo event emitter compliance
    }

    Constants([
      "isHealthDataAvailable": HKHealthStore.isHealthDataAvailable()
    ])

    Function("checkHealthDataAvailable") {
      HKHealthStore.isHealthDataAvailable()
    }

    OnCreate {
      self.bodyWeightAnchor = self.loadBodyWeightAnchor()
    }

    OnDestroy {
      // Clean up observers to prevent memory leaks
      if let stepObserver = self.stepCountObserver {
        self.healthStore.stop(stepObserver)
        self.stepCountObserver = nil
        self.observerStarted = false
      }

      if let weightObserver = self.bodyWeightObserver {
        self.healthStore.stop(weightObserver)
        self.bodyWeightObserver = nil
        self.bodyWeightObservationStarted = false
      }
    }

    AsyncFunction("requestAuthorization") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard
        let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount),
        let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass)
      else {
        promise.reject("Type unavailable", "Required HealthKit data types are not available")
        return
      }

      let typesToRead: Set<HKObjectType> = [stepCountType, bodyMassType]

      healthStore.requestAuthorization(toShare: [], read: typesToRead) { success, error in
        if let error {
          promise.reject("authorization_error", error.localizedDescription)
          return
        }

        promise.resolve(success)
      }
    }

    AsyncFunction("getStepCount") { (startDateMs: Double, endDateMs: Double, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject("Type unavailable", "Step count type is not available")
        return
      }

      let startDate = Date(timeIntervalSince1970: startDateMs / 1000.0)
      let endDate = Date(timeIntervalSince1970: endDateMs / 1000.0)
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)

      let query = HKStatisticsQuery(
        quantityType: stepCountType,
        quantitySamplePredicate: predicate,
        options: .cumulativeSum
      ) { _, result, error in
        if let error {
          promise.reject("statistics_error", error.localizedDescription)
          return
        }

        let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
        DispatchQueue.main.async {
          promise.resolve(steps)
        }
      }

      healthStore.execute(query)
    }

    AsyncFunction("hasStepDataForDate") { (startDateMs: Double, endDateMs: Double, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject("Type unavailable", "Step count type is not available")
        return
      }

      let startDate = Date(timeIntervalSince1970: startDateMs / 1000.0)
      let endDate = Date(timeIntervalSince1970: endDateMs / 1000.0)
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)

      let query = HKStatisticsQuery(
        quantityType: stepCountType,
        quantitySamplePredicate: predicate,
        options: .cumulativeSum
      ) { _, result, error in
        if let error {
          promise.reject("statistics_error", error.localizedDescription)
          return
        }

        let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
        DispatchQueue.main.async {
          promise.resolve(steps > 0)
        }
      }

      healthStore.execute(query)
    }

    AsyncFunction("getBodyWeightSamples") { (startDateMs: Double, endDateMs: Double, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else {
        promise.reject("Type unavailable", "Body mass type is not available")
        return
      }

      let startDate = Date(timeIntervalSince1970: startDateMs / 1000.0)
      let endDate = Date(timeIntervalSince1970: endDateMs / 1000.0)
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)
      let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)

      let query = HKSampleQuery(
        sampleType: bodyMassType,
        predicate: predicate,
        limit: HKObjectQueryNoLimit,
        sortDescriptors: [sortDescriptor]
      ) { [weak self] _, samples, error in
        guard let self else {
          DispatchQueue.main.async { promise.resolve([]) }
          return
        }

        if let error {
          promise.reject("body_weight_read_error", error.localizedDescription)
          return
        }

        let quantitySamples = (samples as? [HKQuantitySample]) ?? []
        let payload = quantitySamples.map(self.makeBodyWeightPayload)

        DispatchQueue.main.async {
          promise.resolve(payload)
        }
      }

      healthStore.execute(query)
    }

    AsyncFunction("getLatestBodyWeight") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else {
        promise.reject("Type unavailable", "Body mass type is not available")
        return
      }

      let sortDescriptor = NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: false)

      let query = HKSampleQuery(
        sampleType: bodyMassType,
        predicate: nil,
        limit: 1,
        sortDescriptors: [sortDescriptor]
      ) { [weak self] _, samples, error in
        guard let self else {
          DispatchQueue.main.async { promise.resolve(nil) }
          return
        }

        if let error {
          promise.reject("body_weight_read_error", error.localizedDescription)
          return
        }

        let quantitySamples = (samples as? [HKQuantitySample]) ?? []
        let payload = quantitySamples.first.map(self.makeBodyWeightPayload)

        DispatchQueue.main.async {
          promise.resolve(payload)
        }
      }

      healthStore.execute(query)
    }

    AsyncFunction("enableBackgroundDelivery") { (frequency: String, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject("Type unavailable", "Step count type is not available")
        return
      }

      let updateFrequency: HKUpdateFrequency
      switch frequency.lowercased() {
      case "immediate": updateFrequency = .immediate
      case "hourly": updateFrequency = .hourly
      case "daily": updateFrequency = .daily
      case "weekly": updateFrequency = .weekly
      default: updateFrequency = .hourly
      }

      healthStore.enableBackgroundDelivery(for: stepCountType, frequency: updateFrequency) { success, error in
        if let error {
          promise.reject("background_delivery_error", error.localizedDescription)
          return
        }

        if success && !self.observerStarted {
          let query = HKObserverQuery(sampleType: stepCountType, predicate: nil) { _, completionHandler, error in
            if let error {
              print("Observer query error: \(error.localizedDescription)")
              completionHandler()
              return
            }

            let now = Date()
            let (startOfDay, endOfDay) = self.dayBounds(for: now)
            let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: endOfDay, options: .strictStartDate)

            let statsQuery = HKStatisticsQuery(
              quantityType: stepCountType,
              quantitySamplePredicate: predicate,
              options: .cumulativeSum
            ) { _, result, _ in
              let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
              let payload: [String: Any] = [
                "steps": steps,
                "date": self.dayFormatter.string(from: now)
              ]

              DispatchQueue.main.async {
                self.sendEvent("onStepDataUpdate", payload)
              }

              completionHandler()
            }

            self.healthStore.execute(statsQuery)
          }

          self.healthStore.execute(query)
          self.stepCountObserver = query
          self.observerStarted = true
        }

        promise.resolve(success)
      }
    }

    AsyncFunction("disableBackgroundDelivery") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject("Type unavailable", "Step count type is not available")
        return
      }

      if let query = self.stepCountObserver {
        self.healthStore.stop(query)
        self.stepCountObserver = nil
        self.observerStarted = false
      }

      self.healthStore.disableBackgroundDelivery(for: stepCountType) { success, error in
        if let error {
          promise.reject("background_delivery_error", error.localizedDescription)
          return
        }

        promise.resolve(success)
      }
    }

    AsyncFunction("enableBodyWeightUpdates") { (frequency: String, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else {
        promise.reject("Type unavailable", "Body mass type is not available")
        return
      }

      let updateFrequency: HKUpdateFrequency
      switch frequency.lowercased() {
      case "immediate": updateFrequency = .immediate
      case "hourly": updateFrequency = .hourly
      case "daily": updateFrequency = .daily
      case "weekly": updateFrequency = .weekly
      default: updateFrequency = .daily
      }

      healthStore.enableBackgroundDelivery(for: bodyMassType, frequency: updateFrequency) { success, error in
        if let error {
          promise.reject("body_weight_delivery_error", error.localizedDescription)
          return
        }

        if success {
          self.startBodyWeightObservation(bodyMassType: bodyMassType)
        }

        promise.resolve(success)
      }
    }

    AsyncFunction("disableBodyWeightUpdates") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject("HealthKit unavailable", "HealthKit is not available on this device")
        return
      }

      guard let bodyMassType = HKQuantityType.quantityType(forIdentifier: .bodyMass) else {
        promise.reject("Type unavailable", "Body mass type is not available")
        return
      }

      if let observer = self.bodyWeightObserver {
        self.healthStore.stop(observer)
        self.bodyWeightObserver = nil
      }
      self.bodyWeightObservationStarted = false
      self.bodyWeightAnchor = nil
      self.saveBodyWeightAnchor(nil)

      self.healthStore.disableBackgroundDelivery(for: bodyMassType) { success, error in
        if let error {
          promise.reject("body_weight_delivery_error", error.localizedDescription)
          return
        }

        promise.resolve(success)
      }
    }
  }

  private func makeBodyWeightPayload(from sample: HKQuantitySample) -> [String: Any] {
    let value = sample.quantity.doubleValue(for: HKUnit.gramUnit(with: .kilo))
    let timeMs = Int(sample.startDate.timeIntervalSince1970 * 1000.0)
    return [
      "value": value,
      "time": timeMs,
      "isoDate": isoFormatter.string(from: sample.startDate),
      "source": sample.sourceRevision.source.name
    ]
  }

  private func dayBounds(for date: Date) -> (start: Date, end: Date) {
    let calendar = Calendar.current
    let start = calendar.startOfDay(for: date)
    let end = calendar.date(byAdding: DateComponents(day: 1), to: start) ?? start
    return (start, end)
  }

  private func startBodyWeightObservation(bodyMassType: HKQuantityType) {
    // Clean up any existing observer before starting a new one
    if let existingObserver = bodyWeightObserver {
      healthStore.stop(existingObserver)
      bodyWeightObserver = nil
    }

    if bodyWeightObservationStarted && bodyWeightObserver != nil { return }

    if bodyWeightAnchor == nil {
      bodyWeightAnchor = loadBodyWeightAnchor()
    }

    bodyWeightObservationStarted = true

    let observer = HKObserverQuery(sampleType: bodyMassType, predicate: nil) { [weak self] _, completionHandler, error in
      guard let self else {
        completionHandler()
        return
      }

      if let error {
        print("Body weight observer error: \(error.localizedDescription)")
        completionHandler()
        return
      }

      self.fetchBodyWeightChanges(bodyMassType: bodyMassType) {
        completionHandler()
      }
    }

    healthStore.execute(observer)
    bodyWeightObserver = observer

    fetchBodyWeightChanges(bodyMassType: bodyMassType, completion: nil)
  }

  private func fetchBodyWeightChanges(
    bodyMassType: HKQuantityType,
    completion: (() -> Void)?
  ) {
    let completionHandler = completion
    let anchoredQuery = HKAnchoredObjectQuery(
      type: bodyMassType,
      predicate: nil,
      anchor: bodyWeightAnchor,
      limit: HKObjectQueryNoLimit
    ) { [weak self] _, samples, _, newAnchor, error in
      defer { completionHandler?() }

      guard let self else { return }

      if let error {
        print("Anchored body weight query error: \(error.localizedDescription)")
        return
      }

      if let newAnchor {
        self.bodyWeightAnchor = newAnchor
        self.saveBodyWeightAnchor(newAnchor)
      }

      guard let quantitySamples = samples as? [HKQuantitySample], !quantitySamples.isEmpty else {
        return
      }

      guard let latestSample = quantitySamples.last else {
        return
      }

      let payload = self.makeBodyWeightPayload(from: latestSample)
      DispatchQueue.main.async {
        self.sendEvent("onBodyWeightDataUpdate", payload)
      }
    }

    healthStore.execute(anchoredQuery)
  }

  private func loadBodyWeightAnchor() -> HKQueryAnchor? {
    guard let data = UserDefaults.standard.data(forKey: bodyWeightAnchorKey) else {
      return nil
    }

    if let anchor = try? NSKeyedUnarchiver.unarchivedObject(ofClass: HKQueryAnchor.self, from: data) {
      return anchor
    }

    return nil
  }

  private func saveBodyWeightAnchor(_ anchor: HKQueryAnchor?) {
    if let anchor {
      if let data = try? NSKeyedArchiver.archivedData(withRootObject: anchor, requiringSecureCoding: true) {
        UserDefaults.standard.set(data, forKey: bodyWeightAnchorKey)
      }
    } else {
      UserDefaults.standard.removeObject(forKey: bodyWeightAnchorKey)
    }
  }
}
