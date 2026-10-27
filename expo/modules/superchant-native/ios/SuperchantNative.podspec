Pod::Spec.new do |s|
  s.name           = 'SuperchantNative'
  s.version        = '1.0.0'
  s.summary        = 'SuperChant engine: live session, haptics, Live Activity and counter view'
  s.description    = 'The native side of the SuperChant Expo app on iOS.'
  s.author         = ''
  s.homepage       = 'https://docs.expo.dev/modules/'
  s.platforms      = {
    :ios => '17.0'
  }
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
