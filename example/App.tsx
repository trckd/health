import { Button, SafeAreaView, ScrollView, Text, View } from 'react-native';
import { useSteps } from './use-steps';

export default function App() {
  const { steps, loading, error, requestInitialization, isAuthorized } = useSteps("2025-05-01");

  return (
    <SafeAreaView style={styles.container}>
      <ScrollView style={styles.container}>
        <Text style={styles.header}>Steps API Example</Text>
        <Group name="Steps">
          <Text>{steps}</Text>
        </Group>
        <Group name="Loading">
          <Text>{loading ? "Loading..." : "Not loading"}</Text>
        </Group>
        <Group name="Request Authorization">
          <Button
            title="Request Authorization"
            onPress={requestInitialization}
          />
        </Group>
        <Group name="Error">
          <Text>{error}</Text>
        </Group>
        <Group name="Is Authorized">
          <Text>{isAuthorized ? "Authorized" : "Not authorized"}</Text>
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
    margin: 20,
    backgroundColor: '#fff',
    borderRadius: 10,
    padding: 20,
  },
  container: {
    flex: 1,
    backgroundColor: '#eee',
  },
  view: {
    flex: 1,
    height: 200,
  },
};
