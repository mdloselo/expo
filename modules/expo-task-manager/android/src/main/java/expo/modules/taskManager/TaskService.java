package expo.modules.taskManager;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import expo.core.interfaces.SingletonModule;
import expo.interfaces.taskManager.TaskManagerUtilsInterface;
import expo.interfaces.taskManager.TaskServiceInterface;
import expo.interfaces.taskManager.TaskConsumerInterface;
import expo.interfaces.taskManager.TaskInterface;
import expo.interfaces.taskManager.TaskManagerInterface;
import expo.loaders.provider.interfaces.AppLoaderInterface;
import expo.loaders.provider.AppLoaderProvider;
import expo.loaders.provider.interfaces.AppRecordInterface;

public class TaskService implements SingletonModule, TaskServiceInterface {
  public static String INTENT_ACTION_PREFIX = "expo.modules.taskManager.";

  private static final String TAG = "TaskService";
  private static final String SHARED_PREFERENCES_NAME = "TaskManagerModule";
  private static TaskService sInstance;

  private Context mContext;

  // { [appId]: { [taskName]: TaskInterface } }
  private final Map<String, Map<String, TaskInterface>> mTasksTable = new HashMap<>();

  // { [appId]: WeakReference(TaskManagerInterface) }
  private final Map<String, WeakReference<TaskManagerInterface>> mTaskManagers = new HashMap<>();

  // { [appId]: List(eventIds...) }
  private final Map<String, List<String>> mEvents = new HashMap<>();

  // { [appId]: List(eventBodies...) }
  private final Map<String, List<Bundle>> mEventsQueues = new HashMap<>();

  // { [appId]: AppRecordInterface }
  private final Map<String, AppRecordInterface> mAppRecords = new HashMap<>();

  public static TaskService getInstance(Context context) {
    if (sInstance == null) {
      sInstance = new TaskService(context);
    }
    return sInstance;
  }

  public TaskService(Context context) {
    super();
    mContext = context;

    if (sInstance == null) {
      sInstance = this;
    }
    restoreTasks();
  }

  public String getName() {
    return "TaskService";
  }

  //region TaskServiceInterface

  @Override
  public boolean hasRegisteredTask(String taskName, String appId) {
    TaskInterface task = getTask(taskName, appId);
    return task != null;
  }

  @Override
  public void registerTask(String taskName, String appId, String appUrl, Class<TaskConsumerInterface> consumerClass, Map<String, Object> options) throws Exception {
    TaskInterface task = internalRegisterTask(taskName, appId, appUrl, consumerClass, options);
    saveTasksForAppWithId(appId);
  }

  @Override
  public void unregisterTask(String taskName, String appId, Class<TaskConsumerInterface> consumerClass) throws Exception {
    TaskInterface task = getTask(taskName, appId);

    // Task not found.
    if (task == null) {
      throw new Exception("Task '" + taskName + "' not found for app ID '" + appId + "'.");
    }

    // Check if the consumer is an instance of given consumer class.
    if (consumerClass != null && !consumerClass.isInstance(task.getConsumer())) {
      throw new Exception("Cannot unregister task with name '" + taskName + "' because it is associated with different consumer class.");
    }

    Map<String, TaskInterface> appTasks = mTasksTable.get(appId);

    if (appTasks != null) {
      appTasks.remove(taskName);
    }
    saveTasksForAppWithId(appId);
  }

  @Override
  public void unregisterAllTasksForAppId(String appId) {
    Map<String, TaskInterface> appTasks = mTasksTable.get(appId);

    if (appTasks != null) {
      for (TaskInterface task : appTasks.values()) {
        task.getConsumer().didUnregister();
      }

      appTasks.clear();
      removeAppFromConfig(appId);
    }
  }

  @Override
  public boolean taskHasConsumerOfClass(String taskName, String appId, Class<TaskConsumerInterface> consumerClass) {
    TaskInterface task = getTask(taskName, appId);
    return task != null && consumerClass.isInstance(task.getConsumer());
  }

  @Override
  public Map<String, Map<String, Object>> getTasksForAppId(String appId) {
    Map<String, TaskInterface> appTasks = mTasksTable.get(appId);
    Map<String, Map<String, Object>> resultMap = new HashMap<>();

    if (appTasks != null) {
      for (TaskInterface task : appTasks.values()) {
        resultMap.put(task.getName(), task.getOptions());
      }
    }
    return resultMap;
  }

  @Override
  public void notifyTaskDidFinish(String taskName, String appId, Map<String, Object> response) {
    String eventId = (String) response.get("eventId");
    List<String> appEvents = mEvents.get(appId);

    if (appEvents != null) {
      appEvents.remove(eventId);

      if (appEvents.size() == 0) {
        mEvents.remove(appId);
        invalidateAppRecord(appId);
      }
    }
  }

  @Override
  public void setTaskOptions(String taskName, String appId, Map<String, Object> options, Class<TaskConsumerInterface> consumerClass) {
    // TODO(@tsapeta);
    TaskInterface task = getTask(taskName, appId);

    if (task != null) {
      TaskConsumerInterface consumer = task.getConsumer();

      task.setOptions(options);
      consumer.setOptions(options);
    }
  }

  @Override
  public void setTaskManager(TaskManagerInterface taskManager, String appId) {
    if (taskManager == null) {
      mTaskManagers.remove(appId);
    } else {
      mTaskManagers.put(appId, new WeakReference<>(taskManager));

      List<Bundle> eventsQueue = mEventsQueues.get(appId);

      if (eventsQueue != null) {
        for (Bundle body : eventsQueue) {
          taskManager.executeTaskWithBody(body);
        }
      }
    }
    mEventsQueues.remove(appId);
  }

  public void handleIntent(Application application, Intent intent) {
    String action = intent.getAction();
    Uri dataUri = intent.getData();

    if (!action.startsWith(INTENT_ACTION_PREFIX)) {
      return;
    }

    String appId = dataUri.getQueryParameter("appId");
    String taskName = dataUri.getQueryParameter("taskName");

    Log.i(TAG, "Handling TaskService intent with task name '" + taskName + "' for app with ID '" + appId + "'.");

    TaskInterface task = getTask(taskName, appId);
    TaskConsumerInterface consumer = task != null ? task.getConsumer() : null;

    if (consumer == null) {
      Log.w(TAG, "Task or consumer not found.");
      return;
    }

    // executes task
    consumer.didWakeUpWithIntent(intent);
  }

  public void executeTask(TaskInterface task, Bundle data, Error error) {
    TaskManagerInterface taskManager = getTaskManager(task.getAppId());
    Bundle body = createExecutionEventBody(task, data, error);
    String eventId = body.getBundle("executionInfo").getString("eventId");
    String appId = task.getAppId();
    List<String> appEvents = mEvents.get(appId);

    Log.i(TAG, "Executing task '" + task.getName() + "'.");

    if (appEvents == null) {
      appEvents = new ArrayList<>();
      appEvents.add(eventId);
      mEvents.put(appId, appEvents);
    } else {
      appEvents.add(eventId);
    }

    if (taskManager != null) {
      taskManager.executeTaskWithBody(body);
      return;
    }

    if (!mAppRecords.containsKey(appId)) {
      // No app record yet - let's spin it up!

      if (!loadApp(appId, task.getAppUrl())) {
        // Loading failed because parameters are invalid - unregister the task.
        try {
          unregisterTask(task.getName(), appId, null);
        } catch (Exception e) {
          Log.e(TAG, "Error occurred while unregistering invalid task.", e);
        }
        appEvents.remove(eventId);
        return;
      }
    }

    // App record for that app exists, but it's not fully loaded as its task manager is not there yet.
    // We need to add event's body to the queue from which events will be executed once the task manager is ready.
    if (!mEventsQueues.containsKey(appId)) {
      mEventsQueues.put(appId, new ArrayList<Bundle>());
    }
    mEventsQueues.get(appId).add(body);
  }

  //endregion
  //region helpers

  private TaskInterface internalRegisterTask(String taskName, String appId, String appUrl, Class<TaskConsumerInterface> consumerClass, Map<String, Object> options) throws Exception {
    TaskManagerUtilsInterface taskManagerUtils = new TaskManagerUtils();
    TaskConsumerInterface consumer = consumerClass.getDeclaredConstructor(Context.class, TaskManagerUtilsInterface.class).newInstance(mContext, taskManagerUtils);
    Task task = new Task(taskName, appId, appUrl, consumer, options, this);

    consumer.didRegister(task);

    Map<String, TaskInterface> appTasks = mTasksTable.containsKey(appId) ? mTasksTable.get(appId) : new HashMap<String, TaskInterface>();
    appTasks.put(taskName, task);
    mTasksTable.put(appId, appTasks);

    Log.i(TAG, "Registered task with name '" + taskName + "' for app with ID '" + appId + "'.");

    return task;
  }

  private Bundle createExecutionEventBody(TaskInterface task, Bundle data, Error error) {
    Bundle body = new Bundle();
    Bundle executionInfo = new Bundle();
    Bundle errorBundle = errorBundleForError(error);
    String eventId = UUID.randomUUID().toString();

    executionInfo.putString("eventId", eventId);
    executionInfo.putString("taskName", task.getName());

    body.putBundle("executionInfo", executionInfo);
    body.putBundle("data", data);
    body.putBundle("error", errorBundle);

    return body;
  }

  private Bundle errorBundleForError(Error error) {
    if (error == null) {
      return null;
    }
    Bundle errorBundle = new Bundle();
    errorBundle.putString("message", error.getMessage());
    return errorBundle;
  }

  private TaskInterface getTask(String taskName, String appId) {
    Map<String, TaskInterface> appTasks = mTasksTable.get(appId);
    return appTasks != null ? appTasks.get(taskName) : null;
  }

  private SharedPreferences getSharedPreferences() {
    return mContext.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
  }

  @SuppressWarnings("unchecked")
  private void restoreTasks() {
    SharedPreferences preferences = getSharedPreferences();
    Map<String, ?> config = preferences.getAll();

    for (Map.Entry<String, ?> entry : config.entrySet()) {
      Map<String, Object> appConfig = jsonToMap(entry.getValue().toString(), true);
      Map<String, Object> tasksConfig = (HashMap<String, Object>) appConfig.get("tasks");
      String appUrl = (String) appConfig.get("appUrl");

      if (appUrl != null && tasksConfig != null && tasksConfig.size() > 0) {
        for (String taskName : tasksConfig.keySet()) {
          Map<String, Object> taskConfig = (HashMap<String, Object>) tasksConfig.get(taskName);
          Map<String, Object> options = (HashMap<String, Object>) taskConfig.get("options");
          String consumerClassString = (String) taskConfig.get("consumerClass");

          try {
            Class consumerClass = Class.forName(consumerClassString);

            // register the task using internal method which doesn't change shared preferences.
            internalRegisterTask(taskName, entry.getKey(), appUrl, consumerClass, options);
          } catch (Exception e) {
            Log.e("EXPO", e.getMessage());
            e.printStackTrace();
            // nothing, just skip it.
          }
        }
      }
    }
  }

  private void saveTasksForAppWithId(String appId) {
    SharedPreferences preferences = getSharedPreferences();
    Map<String, TaskInterface> appRow = mTasksTable.get(appId);

    if (appRow == null || appRow.size() == 0) {
      preferences.edit().remove(appId).apply();
      return;
    }

    Map<String, Object> appConfig = new HashMap<>();
    Map<String, Object> tasks = new HashMap<>();
    String appUrl = null;

    for (TaskInterface task : appRow.values()) {
      Map<String, Object> taskConfig = exportTaskToHashmap(task);
      tasks.put(task.getName(), taskConfig);
      appUrl = task.getAppUrl();
    }

    appConfig.put("appUrl", appUrl);
    appConfig.put("tasks", tasks);

    preferences
        .edit()
        .putString(appId, new JSONObject(appConfig).toString())
        .apply();
  }

  private void removeAppFromConfig(String appId) {
    getSharedPreferences().edit().remove(appId).apply();
  }

  private TaskManagerInterface getTaskManager(String appId) {
    WeakReference<TaskManagerInterface> weakRef = mTaskManagers.get(appId);
    return weakRef == null ? null : weakRef.get();
  }

  private Map<String, Object> exportTaskToHashmap(TaskInterface task) {
    Map<String, Object> map = new HashMap<>();

    map.put("name", task.getName());
    map.put("consumerClass", task.getConsumer().getClass().getName());
    map.put("options", task.getOptions());

    return map;
  }

  private AppLoaderInterface createAppLoader() {
    // for now only react-native apps in Expo are supported
    return AppLoaderProvider.createLoader("react-native-experience", mContext);
  }

  private boolean loadApp(final String appId, String appUrl) {
    AppLoaderInterface appLoader = createAppLoader();

    if (appLoader == null) {
      Log.e(TAG, "Cannot execute background task because application loader can't be found.");
      return false;
    }
    if (appUrl == null) {
      Log.e(TAG, "Cannot execute background task because application URL is invalid: " + appUrl);
      return false;
    }

    // TODO(@tsapeta): add timeout option;
    Map<String, Object> options = new HashMap<>();

    AppRecordInterface appRecord = appLoader.loadApp(appUrl, options, new AppLoaderProvider.Callback() {
      @Override
      public void onComplete(boolean success, Error error) {
        if (!success) {
          mEvents.remove(appId);
          mEventsQueues.remove(appId);
          mAppRecords.remove(appId);
        }
      }
    });

    mAppRecords.put(appId, appRecord);
    return true;
  }

  private void invalidateAppRecord(String appId) {
    AppRecordInterface appRecord = mAppRecords.get(appId);

    if (appRecord != null) {
      appRecord.invalidate();
      mAppRecords.remove(appId);
      mTaskManagers.remove(appId);
    }
  }

  public static Map<String, Object> jsonToMap(String jsonStr, boolean recursive) {
    try {
      return jsonToMap(new JSONObject(jsonStr), recursive);
    } catch (JSONException e) {
      return new HashMap<>();
    }
  }

  private static Map<String, Object> jsonToMap(JSONObject json, boolean recursive) {
    Map<String, Object> map = new HashMap<>();

    try {
      Iterator<?> keys = json.keys();

      while (keys.hasNext()) {
        String key = (String) keys.next();
        Object value = json.get(key);

        if (recursive) {
          value = jsonObjectToObject(value, recursive);
        }

        map.put(key, value);
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return map;
  }

  private static List<Object> jsonToList(JSONArray json, boolean recursive) {
    List<Object> list = new ArrayList<>();

    try {
      for (int i = 0; i < json.length(); i++) {
        Object value = json.get(i);

        if (recursive) {
          if (value instanceof JSONArray) {
            value = jsonToList((JSONArray) value, recursive);
          } else if (value instanceof JSONObject) {
            value = jsonToMap((JSONObject) value, recursive);
          }
        }
        list.add(value);
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return list;
  }

  private static Object jsonObjectToObject(Object json, boolean recursive) {
    if (json instanceof JSONObject) {
      return jsonToMap((JSONObject) json, recursive);
    }
    if (json instanceof JSONArray) {
      return jsonToList((JSONArray) json, recursive);
    }
    return json;
  }

  //endregion
}
