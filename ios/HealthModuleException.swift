import ExpoModulesCore
import HealthKit

/// A rejection whose description actually reaches JavaScript.
///
/// `Promise.reject(_ code:, _ description:)` builds an `Exception` whose
/// `description` is set but whose `reason` is never overridden, and the JS
/// error message is built from `debugDescription` — which interpolates
/// `reason`. Every such rejection therefore arrives in JS as the literal
/// `<code>: undefined reason (at ExpoModulesCore/Promise.swift:65)`, and an
/// `HKError` (data-protection locked, authorization not determined, no data,
/// transient store failure, …) becomes indistinguishable from any other.
///
/// Overriding `reason` is the only way to get the text across the bridge. For
/// HealthKit errors the message is prefixed with the `HKError.Code` so the
/// consumer can classify without matching localized text.
final class HealthModuleException: Exception {
  private let reasonText: String

  /// A rejection with a fixed, human-readable reason.
  init(_ code: String, _ reason: String, file: String = #fileID, line: UInt = #line, function: String = #function) {
    self.reasonText = reason
    super.init(name: code, description: reason, code: code, file: file, line: line, function: function)
  }

  /// A rejection wrapping an error thrown by HealthKit (or Foundation).
  convenience init(_ code: String, error: Error, file: String = #fileID, line: UInt = #line, function: String = #function) {
    self.init(code, HealthModuleException.describe(error), file: file, line: line, function: function)
    self.cause = error
  }

  override var reason: String {
    reasonText
  }

  /// `HKError.<code> (<name>): <localizedDescription>` for HealthKit errors,
  /// `<domain>.<code>: <localizedDescription>` for anything else.
  static func describe(_ error: Error) -> String {
    let nsError = error as NSError
    if nsError.domain == HKErrorDomain {
      return "HKError.\(nsError.code) (\(hkErrorName(nsError.code))): \(error.localizedDescription)"
    }
    return "\(nsError.domain).\(nsError.code): \(error.localizedDescription)"
  }

  private static func hkErrorName(_ rawValue: Int) -> String {
    switch HKError.Code(rawValue: rawValue) {
    case .noError: return "noError"
    case .errorHealthDataUnavailable: return "healthDataUnavailable"
    case .errorHealthDataRestricted: return "healthDataRestricted"
    case .errorInvalidArgument: return "invalidArgument"
    case .errorAuthorizationDenied: return "authorizationDenied"
    case .errorAuthorizationNotDetermined: return "authorizationNotDetermined"
    case .errorDatabaseInaccessible: return "databaseInaccessible"
    case .errorUserCanceled: return "userCanceled"
    case .errorAnotherWorkoutSessionStarted: return "anotherWorkoutSessionStarted"
    case .errorUserExitedWorkoutSession: return "userExitedWorkoutSession"
    case .errorRequiredAuthorizationDenied: return "requiredAuthorizationDenied"
    case .errorNoData: return "noData"
    default: return "unknown"
    }
  }
}
