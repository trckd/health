// Test script for verifying bodyweight functionality
// Run this in the example app to test both platforms

import { Health } from '@tracked/health';

async function testBodyweightFunctionality() {
  console.log('=== Testing Bodyweight Functionality ===\n');

  try {
    // 1. Check if health data is available
    console.log('1. Checking health data availability...');
    const isAvailable = Health.isHealthDataAvailable;
    console.log(`   ✓ Health data available: ${isAvailable}`);

    if (!isAvailable) {
      console.log('   ⚠️ Health data not available on this device');
      return;
    }

    // 2. Request authorization
    console.log('\n2. Requesting authorization...');
    const authorized = await Health.requestAuthorization();
    console.log(`   ✓ Authorization granted: ${authorized}`);

    if (!authorized) {
      console.log('   ⚠️ Permission denied by user');
      return;
    }

    // 3. Test fetching bodyweight samples
    console.log('\n3. Fetching bodyweight samples (last 30 days)...');
    const endDate = Date.now();
    const startDate = endDate - (30 * 24 * 60 * 60 * 1000); // 30 days ago

    const samples = await Health.getBodyWeightSamples(startDate, endDate);
    console.log(`   ✓ Found ${samples.length} bodyweight samples`);

    if (samples.length > 0) {
      console.log('   Sample data:');
      samples.slice(0, 3).forEach((sample, i) => {
        console.log(`     ${i + 1}. Weight: ${sample.value.toFixed(1)} kg`);
        console.log(`        Date: ${sample.isoDate}`);
        console.log(`        Source: ${sample.source || 'Unknown'}`);
      });
    }

    // 4. Test getting latest bodyweight
    console.log('\n4. Getting latest bodyweight...');
    const latest = await Health.getLatestBodyWeight();

    if (latest) {
      console.log(`   ✓ Latest weight: ${latest.value.toFixed(1)} kg`);
      console.log(`     Date: ${latest.isoDate}`);
      console.log(`     Source: ${latest.source || 'Unknown'}`);
    } else {
      console.log('   ⚠️ No bodyweight data found');
    }

    // 5. Test enabling background updates
    console.log('\n5. Enabling bodyweight background updates...');
    const backgroundEnabled = await Health.enableBodyWeightUpdates('daily');
    console.log(`   ✓ Background updates enabled: ${backgroundEnabled}`);

    // 6. Set up event listener
    console.log('\n6. Setting up bodyweight update listener...');
    const subscription = Health.addListener('onBodyWeightDataUpdate', (event) => {
      console.log('   📊 New bodyweight data received:');
      console.log(`      Weight: ${event.value.toFixed(1)} kg`);
      console.log(`      Time: ${event.isoDate}`);
    });
    console.log('   ✓ Listener registered');

    // 7. Wait for potential updates
    console.log('\n7. Waiting 10 seconds for any updates...');
    console.log('   (Add a new weight entry in Health/Health Connect to test)');

    await new Promise(resolve => setTimeout(resolve, 10000));

    // 8. Clean up
    console.log('\n8. Cleaning up...');
    subscription.remove();
    await Health.disableBodyWeightUpdates();
    console.log('   ✓ Listener removed and updates disabled');

    console.log('\n=== Test Complete ===');
    console.log('✅ All bodyweight functionality tests passed!');

  } catch (error) {
    console.error('\n❌ Test failed with error:', error);
    console.error('Stack trace:', error.stack);
  }
}

// Export for use in React Native
export default testBodyweightFunctionality;

// If running in Node.js environment for testing
if (typeof module !== 'undefined' && module.exports) {
  module.exports = testBodyweightFunctionality;
}