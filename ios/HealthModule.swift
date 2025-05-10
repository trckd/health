import ExpoModulesCore
import HealthKit

public class HealthModule: Module {
  // Create a single HealthKit store that can be reused
  private let healthStore = HKHealthStore()
  
  // Each module class must implement the definition function. The definition consists of components
  // that describes the module's functionality and behavior.
  // See https://docs.expo.dev/modules/module-api for more details about available components.
  public func definition() -> ModuleDefinition {
    // Sets the name of the module that JavaScript code will use to refer to the module. Takes a string as an argument.
    // Can be inferred from module's class name, but it's recommended to set it explicitly for clarity.
    // The module will be accessible from `requireNativeModule('Health')` in JavaScript.
    Name("Health")

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
        if let error = error {
          promise.reject("query_error", error.localizedDescription)
          return
        }
        
        guard let result = result, let sum = result.sumQuantity() else {
          promise.resolve(0)
          return
        }
        
        let steps = sum.doubleValue(for: HKUnit.count())
        promise.resolve(steps)
      }
      
      // Execute the query
      self.healthStore.execute(query)
    }
  }
}
