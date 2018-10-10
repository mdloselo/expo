package expo.interfaces.taskManager;

import android.os.Bundle;

import java.util.Map;

public interface TaskInterface {
  String getName();
  String getAppId();
  String getAppUrl();
  TaskConsumerInterface getConsumer();
  Map<String, Object> getOptions();
  void execute(Bundle data, Error error);
  void setOptions(Map<String, Object> options);
}
