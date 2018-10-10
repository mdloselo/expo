import React from 'react';
import { EventEmitter } from 'fbemitter';
import { BlurView, Location, MapView, TaskManager } from 'expo';
import { AsyncStorage, StyleSheet, Text, View } from 'react-native';

import Button from '../../components/Button';
import Colors from '../../constants/Colors';

const STORAGE_KEY = 'ncl-locations';
const LOCATION_UPDATES_TASK = 'location-updates';

const locationEventsEmitter = new EventEmitter();

export default class BackgroundLocationMapScreen extends React.Component {
  static navigationOptions = {
    title: 'Location Map',
  };

  mapViewRef = React.createRef();

  state = {
    isWatching: false,
    savedLocations: [],
    initialRegion: null,
  };

  async componentDidMount() {
    await Location.requestPermissionsAsync();

    const { coords } = await Location.getCurrentPositionAsync();
    const isWatching = await Location.hasStartedLocationUpdatesAsync(LOCATION_UPDATES_TASK);
    const savedLocations = await getSavedLocations();

    this.eventSubscription = locationEventsEmitter.addListener('update', locations => {
      this.setState({ savedLocations: locations });
    });

    this.setState({
      isWatching,
      savedLocations,
      initialRegion: {
        latitude: coords.latitude,
        longitude: coords.longitude,
        latitudeDelta: 0.04,
        longitudeDelta: 0.02,
      },
    });
  }

  componentWillUnmount() {
    if (this.eventSubscription) {
      this.eventSubscription.remove();
    }
  }

  toggleWatching = async () => {
    await AsyncStorage.removeItem(STORAGE_KEY);

    if (this.state.isWatching) {
      await Location.stopLocationUpdatesAsync(LOCATION_UPDATES_TASK);
    } else {
      await Location.startLocationUpdatesAsync(LOCATION_UPDATES_TASK, {
        accuracy: Location.Accuracy.HIGH,
        showsBackgroundLocationIndicator: false,
      });
    }
    this.setState({ isWatching: !this.state.isWatching, savedLocations: [] });
  };

  centerMap = async () => {
    const { coords } = await Location.getCurrentPositionAsync();
    const mapView = this.mapViewRef.current;

    if (mapView) {
      mapView.animateToRegion({
        latitude: coords.latitude,
        longitude: coords.longitude,
        latitudeDelta: 0.04,
        longitudeDelta: 0.02,
      });
    }
  };

  renderPolyline() {
    const { savedLocations } = this.state;

    if (savedLocations.length === 0) {
      return null;
    }
    return (
      <MapView.Polyline
        coordinates={savedLocations}
        strokeWidth={2}
        strokeColor={Colors.tintColor} />
    );
  }

  render() {
    if (!this.state.initialRegion) {
      return null;
    }

    return (
      <View style={styles.screen}>
        <View style={styles.heading}>
          <BlurView tint="light" intensity={70} style={styles.blurView}>
            <Text style={styles.headingText}>
              { this.state.isWatching
                ? 'Now you can send app to the background, go somewhere and come back here! You can even terminate the app and it will be woken up when the new significant location change comes out.'
                : 'Click `Start` to start getting location updates.'
              }
            </Text>
          </BlurView>
        </View>

        <MapView
          ref={this.mapViewRef}
          style={styles.mapView}
          initialRegion={this.state.initialRegion}>
          {this.renderPolyline()}
        </MapView>
        <View style={styles.buttons}>
          <Button
            buttonStyle={styles.button}
            title={this.state.isWatching ? 'Stop recording' : 'Start recording'}
            onPress={this.toggleWatching} />
          <Button
            buttonStyle={styles.button}
            title="Center"
            onPress={this.centerMap} />
        </View>
      </View>
    );
  }
}

async function getSavedLocations() {
  try {
    const item = await AsyncStorage.getItem(STORAGE_KEY);
    return item ? JSON.parse(item) : [];
  } catch (e) {
    return [];
  }
}

TaskManager.defineTask(LOCATION_UPDATES_TASK, async ({ data: { locations } }) => {
  if (locations && locations.length > 0) {
    const savedLocations = await getSavedLocations();
    const newLocations = locations.map(({ coords }) => ({
      latitude: coords.latitude,
      longitude: coords.longitude,
    }));

    savedLocations.push(...newLocations);
    await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(savedLocations));

    locationEventsEmitter.emit('update', savedLocations);
  }
});

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  heading: {
    backgroundColor: 'rgba(255, 255, 0, 0.1)',
    position: 'absolute',
    top: 0,
    right: 0,
    left: 0,
    zIndex: 2,
  },
  blurView: {
    flex: 1,
    padding: 5,
  },
  headingText: {
    textAlign: 'center',
  },
  mapView: {
    flex: 1,
  },
  buttons: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    padding: 20,
    position: 'absolute',
    right: 0,
    bottom: 0,
    left: 0,
  },
  button: {
    padding: 10,
  },
});
