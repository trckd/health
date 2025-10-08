import { Button, SafeAreaView, ScrollView, Text, View } from 'react-native';
import { useSteps } from './use-steps';
import { useBodyWeight } from './use-body-weight';

const DEMO_DATE = "2025-05-01";

export default function App() {
  const {
    steps,
    loading: stepsLoading,
    error: stepsError,
    requestInitialization,
    isAuthorized,
    backgroundDeliveryStatus,
    disableBackgroundDelivery,
  } = useSteps(DEMO_DATE);

  const {
    displayValue: bodyWeight,
    loading: weightLoading,
    error: weightError,
    refresh: refreshBodyWeight,
  } = useBodyWeight(DEMO_DATE, { units: 'kg' });

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Health Data Example</Text>

        <Group name="Authorization">
          <Text>{isAuthorized ? 'Authorized' : 'Not authorized'}</Text>
          <Button title="Request Authorization" onPress={requestInitialization} />
        </Group>

        <Group name="Steps">
          <Text>Total: {steps}</Text>
          <Text>Status: {stepsLoading ? 'Loading…' : 'Idle'}</Text>
          <Text>Error: {stepsError ?? '—'}</Text>
          <Text>Background: {backgroundDeliveryStatus ?? 'Disabled'}</Text>
          <Button title="Disable Background Delivery" onPress={disableBackgroundDelivery} />
        </Group>

        <Group name="Body Weight (kg)">
          <Text>Value: {bodyWeight ?? 'No entry'}</Text>
          <Text>Status: {weightLoading ? 'Syncing…' : 'Idle'}</Text>
          <Text>Error: {weightError ?? '—'}</Text>
          <Button title="Refresh" onPress={refreshBodyWeight} />
        </Group>
      </ScrollView>
    </SafeAreaView>
  );
}

function Group(props: { name: string; children: React.ReactNode }) {
  return (
    <View style={styles.group}>
      <Text style={styles.groupHeader}>{props.name}</Text>
      {props.children}
    </View>
  );
}

const styles = {
  header: {
    fontSize: 30,
    margin: 20,
  },
  groupHeader: {
    fontSize: 20,
    marginBottom: 20,
  },
  group: {
    marginVertical: 5,
    marginHorizontal: 20,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
    gap: 8,
  },
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
};
