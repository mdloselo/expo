package expo.interfaces.taskManager;

import android.app.PendingIntent;
import android.content.Context;

import expo.interfaces.taskManager.TaskInterface;

public interface TaskManagerUtilsInterface {
  PendingIntent createTaskIntent(Context context, TaskInterface task);
}
