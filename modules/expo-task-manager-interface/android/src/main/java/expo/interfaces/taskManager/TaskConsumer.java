package expo.interfaces.taskManager;

import android.content.Context;

import java.lang.ref.WeakReference;
import java.util.Map;

public abstract class TaskConsumer {
  private WeakReference<Context> mContextRef;
  private TaskManagerUtilsInterface mTaskManagerUtils;

  public TaskConsumer(Context context, TaskManagerUtilsInterface taskManagerUtils) {
    mContextRef = new WeakReference<>(context);
    mTaskManagerUtils = taskManagerUtils;
  }

  protected Context getContext() {
    return mContextRef != null ? mContextRef.get() : null;
  }

  protected TaskManagerUtilsInterface getTaskManagerUtils() {
    return mTaskManagerUtils;
  }

  public void setOptions(Map<String, Object> options) {
    // nothing
  }
}
