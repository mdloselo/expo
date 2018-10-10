package expo.interfaces.taskManager;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import java.util.Map;

import expo.core.interfaces.SingletonModule;
import expo.interfaces.taskManager.TaskConsumerInterface;
import expo.interfaces.taskManager.TaskInterface;
import expo.interfaces.taskManager.TaskManagerInterface;

public interface TaskServiceInterface extends SingletonModule {

  /**
   *  Returns boolean value whether the task with given name is already registered for given appId.
   */
  boolean hasRegisteredTask(String taskName, String appId);

  /**
   *  Registers task in any kind of persistent storage, so it could be restored in future sessions.
   */
  void registerTask(String taskName, String appId, String appUrl, Class<TaskConsumerInterface> consumerClass, Map<String, Object> options) throws Exception;

  /**
   *  Unregisters task with given name and for given appId. If consumer class is provided,
   *  it can throw an exception if task's consumer is not a member of that class.
   */
  void unregisterTask(String taskName, String appId, Class<TaskConsumerInterface> consumerClass) throws Exception;

  /**
   *  Unregisters all tasks registered for the app with given appId.
   */
  void unregisterAllTasksForAppId(String appId);

  /**
   *  Returns boolean value whether or not the task's consumer is a member of given class.
   */
  boolean taskHasConsumerOfClass(String taskName, String appId, Class<TaskConsumerInterface> consumerClass);

  /**
   *  Returns dictionary of tasks for given appId. Dictionary in which the keys are the names for tasks,
   *  while the values are the task objects.
   */
  Map<String, Map<String, Object>> getTasksForAppId(String appId);

  /**
   *  Notifies the service that a task has just finished.
   */
  void notifyTaskDidFinish(String taskName, String appId, Map<String, Object> response);

  /**
   *  Updates task's options and notifies the consumer.
   *  Can throw an exception if there is no task with given name or its consumer class is incompatible.
   */
  void setTaskOptions(String taskName, String appId, Map<String, Object> options, Class<TaskConsumerInterface> consumerClass) throws Exception;

  /**
   *  Passes a reference of task manager for given appId to the service.
   */
  void setTaskManager(TaskManagerInterface taskManager, String appId);

  /**
   *  Handles intent that just woke up.
   */
  void handleIntent(Application application, Intent intent);

  /**
   *  Executes the task with given data bundle and given error.
   */
  void executeTask(TaskInterface task, Bundle data, Error error);
}