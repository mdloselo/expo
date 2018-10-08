import React from 'react';
import { Notifications } from 'expo';
import { StyleSheet, Text, View, Button } from 'react-native';
import registerForPushNotificationsAsync from '../api/registerForPushNotificationsAsync';

export default class UserNotificationsScreen extends React.Component {
  static navigationOptions = {
    title: 'Notifications',
  };

  state = {
    gotNotification: false,
    expoPushToken: null,
    receivedEvent: null,
  };

  async componentWillMount() {
    const action = {
      actionId: 'touch_action',
      buttonTitle: 'touch',
      isDestructive: true,
      isAuthenticationRequired: true,
    };

    const textAction = {
      actionId: 'add_action',
      buttonTitle: 'add',
      isDestructive: true,
      isAuthenticationRequired: false,
      textInput: {
        submitButtonTitle: 'button_name',
        placeholder: 'default text',
      },
    };

    await Notifications.setCategoryAsync('super-category', [action, textAction]);

    this._notificationListener = Notifications.addListener(e => {
      this.setState({ gotNotification: true, receivedEvent: JSON.stringify(e) });
      console.log('event received ', e.actionId);
    });
  }

  async componentDidMount() {
    this.setState({ expoPushToken: await Notifications.getExpoPushTokenAsync() });
  }

  componentWillUnmount() {
    this._notificationListener.remove();
    this._notificationListener = null;
  }

  _onButtonPress = () => {
    Notifications.presentLocalNotificationAsync({
      title: 'notification',
      body: 'notification-body',
      data: { scheduledAt: new Date().getTime() },
      categoryId: 'super-category',
    });
  };

  _schedule = () => {
    Notifications.scheduleLocalNotificationWithMatchAsync(
      {
        title: 'notification',
        body: 'notification-body',
        data: { scheduledAt: new Date().getTime() },
      },
      {
        hour: 7,
        minute: 41,
        second: 50
      }
    );
  };

  _waitTenSec = async () => {
    this.notificationId = await Notifications.scheduleLocalNotificationWithTimeIntervalAsync(
      {
        title: 'notification',
        body: 'notification-body',
        data: { scheduledAt: new Date().getTime() },
      },
      {
        'time-interval': 10,
      }
    );
  };

  _cancelWithId = () => {
    Notifications.cancelScheduledNotificationAsync(this.notificationId);
  };

  _cancelAll = () => {
    Notifications.cancelAllScheduledNotificationsAsync();
  };

  _pushNotification = () => {
    registerForPushNotificationsAsync().done();
  };

  _legacyScheduling = () => {
    Notifications.scheduleLocalNotificationAsync(
      {
        title: 'Here is a scheduled notifiation!',
        body: 'This is the body',
        data: {
          hello: 'there',
          future: 'self',
        },
      },
      {
        time: new Date().getTime() + 10000,
      }
    );
  };

  render() {
    return (
      <View style={styles.container}>
        <Text>{this.state.gotNotification}</Text>
        <Text>notifications example</Text>
        <Button onPress={this._pushNotification} title="push notification test" />
        <Button onPress={this._onButtonPress} title="trigger" />
        <Button onPress={this._schedule} title="schedule" />
        <Button onPress={this._waitTenSec} title="10 sec" />
        <Button onPress={this._legacyScheduling} title="legacy scheduling" />
        <Button onPress={this._cancelWithId} title="cancel" />
        <Button onPress={this._cancelAll} title="cancel all" />
        <Text> ExpoPushToken: {this.state.expoPushToken} </Text>
        <Text> {this.state.receivedEvent} </Text>
      </View>
    );
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    marginTop: 15,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
