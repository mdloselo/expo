package expo.interfaces.taskManager;

import android.content.Intent;

import java.util.Map;

public interface TaskConsumerInterface {
  void didRegister(TaskInterface task);
  void didUnregister();
  void didWakeUpWithIntent(Intent intent);
  void setOptions(Map<String, Object> options);
}
