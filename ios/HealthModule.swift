import ExpoModulesCore
import HealthKit

public class HealthModule: Module {
  // Create a single HealthKit store that can be reused
  private let healthStore = HKHealthStore()
  private var stepCountObserver: HKObserverQuery?
  private var observerStarted = false
  
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  public func definition() -> ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('Health')` in JavaScript.
    Name("Health")

    // Add events that can be emitted to JavaScript
    Events("onStepDataUpdate")

    // Sets constant properties on the module. Can take a dictionary or a closure that returns a dictionary.
    Constants([
      "isHealthDataAvailable": HKHealthStore.isHealthDataAvailable()
    ])

    // Defines a JavaScript synchronous function that runs the native code on the JavaScript thread.
    Function("checkHealthDataAvailable") {
      return HKHealthStore.isHealthDataAvailable()
    }
    
    // Request authorization for step count only
    AsyncFunction("requestAuthorization") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject(
          "HealthKit unavailable",
          "HealthKit is not available on this device"
        )
        return
      }
      
      // Define step count as the only type we want to read
      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject(
          "Type unavailable",
          "Step count type is not available"
        )
        return
      }
      
      let typesToRead: Set<HKObjectType> = [stepCountType]
      let typesToWrite: Set<HKSampleType> = []
      
      // Request authorization
      self.healthStore.requestAuthorization(toShare: typesToWrite, read: typesToRead) { (success, error) in
        if let error = error {
          promise.reject("authorization_error", error.localizedDescription)
          return
        }
        
        promise.resolve(success)
      }
    }
    
    // Get step count for a specific day
    AsyncFunction("getStepCount") { (startDate: Date, endDate: Date, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject(
          "HealthKit unavailable",
          "HealthKit is not available on this device"
        )
        return
      }
      
      // The type of data we want to read
      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject(
          "Type unavailable",
          "Step count type is not available"
        )
        return
      }
      
      // Create a predicate to filter the data by date
      let predicate = HKQuery.predicateForSamples(withStart: startDate, end: endDate, options: .strictStartDate)
      
      // Create the query
      let query = HKStatisticsQuery(
        quantityType: stepCountType,
        quantitySamplePredicate: predicate,
        options: .cumulativeSum
      ) { _, result, error in
        // Get step count or default to 0 if nil
        let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
        
        // Always resolve with steps (will be 0 if no data or error)
        promise.resolve(steps)
      }
      
      // Execute the query
      self.healthStore.execute(query)
    }
    
    // Enable background delivery for step count updates
    AsyncFunction("enableBackgroundDelivery") { (frequency: String, promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject(
          "HealthKit unavailable",
          "HealthKit is not available on this device"
        )
        return
      }
      
      // Define step count as the type we want to observe
      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject(
          "Type unavailable",
          "Step count type is not available"
        )
        return
      }
      
      // Convert string frequency to HKUpdateFrequency
      let updateFrequency: HKUpdateFrequency
      switch frequency.lowercased() {
      case "immediate":
        updateFrequency = .immediate
      case "hourly":
        updateFrequency = .hourly
      case "daily":
        updateFrequency = .daily
      case "weekly":
        updateFrequency = .weekly
      default:
        updateFrequency = .hourly  // Default to hourly if not specified correctly
      }
      
      // Enable background delivery using trailing closure syntax
      self.healthStore.enableBackgroundDelivery(for: stepCountType, frequency: updateFrequency) { success, error in
        if let error {
          promise.reject("background_delivery_error", error.localizedDescription)
          return
        }
        
        // Set up the observer query to handle background updates
        if success && !self.observerStarted {
          // Create an observer query
          let query = HKObserverQuery(sampleType: stepCountType, predicate: nil) { (query, completionHandler, error) in
            if let error = error {
              print("Observer query error: \(error.localizedDescription)")
              completionHandler()
              return
            }
            
            // Get today's date range
            let now = Date()
            let startOfDay = Calendar.current.startOfDay(for: now)
            let endOfDay = Calendar.current.date(bySettingHour: 23, minute: 59, second: 59, of: now)!
            
            // Query for the latest step count
            let predicate = HKQuery.predicateForSamples(withStart: startOfDay, end: endOfDay, options: .strictStartDate)
            
            let statsQuery = HKStatisticsQuery(
              quantityType: stepCountType,
              quantitySamplePredicate: predicate,
              options: .cumulativeSum
            ) { _, result, queryError in
              // Get the step count or default to 0
              let steps = result?.sumQuantity()?.doubleValue(for: HKUnit.count()) ?? 0
              
              // Send the updated step count to JS
              self.sendEvent("onStepDataUpdate", [
                "steps": steps,
                "date": ISO8601DateFormatter().string(from: now)
              ])
              
              // Complete the background task
              completionHandler()
            }
            
            self.healthStore.execute(statsQuery)
          }
          
          // Start the observer query
          self.healthStore.execute(query)
          self.stepCountObserver = query
          self.observerStarted = true
        }
        
        promise.resolve(success)
      }
    }
    
    // Disable background delivery for step count updates
    AsyncFunction("disableBackgroundDelivery") { (promise: Promise) in
      guard HKHealthStore.isHealthDataAvailable() else {
        promise.reject(
          "HealthKit unavailable",
          "HealthKit is not available on this device"
        )
        return
      }
      
      // Define step count as the type we want to stop observing
      guard let stepCountType = HKQuantityType.quantityType(forIdentifier: .stepCount) else {
        promise.reject(
          "Type unavailable",
          "Step count type is not available"
        )
        return
      }
      
      // Stop the observer query if it exists
      if let query = self.stepCountObserver {
        self.healthStore.stop(query)
        self.stepCountObserver = nil
        self.observerStarted = false
      }
      
      // Disable background delivery using trailing closure syntax
      self.healthStore.disableBackgroundDelivery(for: stepCountType) { success, error in
        if let error {
          promise.reject("background_delivery_error", error.localizedDescription)
          return
        }
        
        promise.resolve(success)
      }
    }
  }
}
