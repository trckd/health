Pod::Spec.new do |s|
  s.name           = 'Health'
  s.version        = '1.0.0'
  s.summary        = 'Integration with HealthKit'
  s.description    = 'Connect to HealthKit to read and write to health data'
  s.author         = 'Tracked'
  s.homepage       = 'https://github.com/tracked'
  s.license        = { :type => 'MIT', :file => 'LICENSE' }
  s.source         = { :git => 'https://github.com/tracked-app/tracked.git', :tag => "#{s.version}" }
  s.platforms      = {
    :ios => '15.1',
    :tvos => '15.1'
  }

  s.dependency 'ExpoModulesCore'
  
  # Add HealthKit framework
  s.frameworks = 'HealthKit'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
