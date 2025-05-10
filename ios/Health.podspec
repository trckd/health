require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'Health'
  s.version        = package['version']
  s.summary        = package['description']
  s.author         = package['author']
  s.homepage       = package['homepage']
  s.license        = package['license']
  s.platforms      = {
    ios: '15.1',
    tvos: '15.1'
  }

  s.swift_version  = '5.4'
  s.source         = { git: 'https://github.com/trckd/health' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  
  # Add HealthKit framework
  s.frameworks = 'HealthKit'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
