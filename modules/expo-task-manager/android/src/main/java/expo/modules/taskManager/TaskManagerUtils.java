package expo.modules.taskManager;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import expo.interfaces.taskManager.TaskManagerUtilsInterface;
import expo.interfaces.taskManager.TaskInterface;

public class TaskManagerUtils implements TaskManagerUtilsInterface {
  public static String INTENT_ACTION_PREFIX = "expo.modules.taskManager.";

  public PendingIntent createTaskIntent(Context context, TaskInterface task) {
    String appId = task.getAppId();
    String taskName = task.getName();
    String intentAction = INTENT_ACTION_PREFIX + "<" + appId + "," + taskName + ">";
    Intent intent = new Intent(intentAction, null, context, TaskIntentService.class);

    Uri dataUri = new Uri.Builder()
        .appendQueryParameter("appId", appId)
        .appendQueryParameter("taskName", taskName)
        .build();

    intent.setData(dataUri);

    return PendingIntent.getService(context, dataUri.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }
}
