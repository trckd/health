import React, { useState } from "react";
import { Button, StyleSheet, Text, View } from "react-native";

import { Health } from "./module";

export default function HealthExample() {
  const [isAvailable, _setIsAvailable] = useState<boolean>(
    Health.isHealthDataAvailable,
  );
  const [isAuthorized, setIsAuthorized] = useState<boolean>(false);
  const [steps, setSteps] = useState<number>(0);

  // Request HealthKit authorization
  const requestAuth = async () => {
    try {
      const result = await Health.requestAuthorization();
      setIsAuthorized(result);
    } catch (error) {
      console.error("Error requesting HealthKit authorization:", error);
    }
  };

  // Get today's step count
  const fetchStepCount = async () => {
    try {
      if (!isAuthorized) {
        console.log("Not authorized to access HealthKit data");
        return;
      }

      // Get today's data
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
      setSteps(steps);
    } catch (error) {
      console.error("Error fetching step count:", error);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>HealthKit Example</Text>

      <Text style={styles.info}>
        HealthKit available: {isAvailable ? "Yes" : "No"}
      </Text>

      <Text style={styles.info}>
        Authorization status: {isAuthorized ? "Authorized" : "Not Authorized"}
      </Text>

      <Text style={styles.info}>Steps today: {steps}</Text>

      <View style={styles.buttonContainer}>
        <Button
          title="Request Authorization"
          onPress={requestAuth}
          disabled={!isAvailable || isAuthorized}
        />
      </View>

      <View style={styles.buttonContainer}>
        <Button
          title="Fetch Step Count"
          onPress={fetchStepCount}
          disabled={!isAvailable || !isAuthorized}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center",
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: "bold",
    marginBottom: 20,
  },
  info: {
    fontSize: 16,
    marginVertical: 5,
  },
  buttonContainer: {
    marginTop: 15,
    width: "80%",
  },
});
