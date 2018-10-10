package expo.modules.location.taskConsumers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofenceStatusCodes;
import com.google.android.gms.location.GeofencingClient;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import expo.interfaces.taskManager.TaskConsumer;
import expo.interfaces.taskManager.TaskManagerUtilsInterface;
import expo.interfaces.taskManager.TaskConsumerInterface;
import expo.interfaces.taskManager.TaskInterface;
import expo.modules.location.LocationModule;

public class GeofencingTaskConsumer extends TaskConsumer implements TaskConsumerInterface, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
  private static final String TAG = "GeofencingTaskConsumer";

  private TaskInterface mTask;
  private PendingIntent mPendingIntent;
  private GeofencingClient mGeofencingClient;
  private GeofencingRequest mGeofencingRequest;
  private GoogleApiClient mGoogleApiClient;
  private List<Geofence> mGeofencingList;
  private Map<String, Bundle> mRegions;

  public GeofencingTaskConsumer(Context context, TaskManagerUtilsInterface taskManagerUtils) {
    super(context, taskManagerUtils);
  }

  //region TaskConsumerInterface

  @Override
  @SuppressWarnings("unchecked")
  public void didRegister(TaskInterface task) {
    Context context = getContext();

    if (context == null) {
      Log.w(TAG, "The context has been abandoned.");
      return;
    }

    mTask = task;
    mRegions = new HashMap<>();
    mGeofencingList = new ArrayList<>();

    // Create geofences from task options.
    Map<String, Object> options = task.getOptions();
    List<HashMap<String, Object>> regions = (ArrayList<HashMap<String, Object>>) options.get("regions");

    for (Map<String, Object> region : regions) {
      Geofence geofence = geofenceFromRegion(region);

      if (geofence != null) {
        String regionIdentifier = geofence.getRequestId();

        // Make a bundle for the region to remember its attributes. Only request ID is public in Geofence object.
        mRegions.put(regionIdentifier, bundleFromRegion(regionIdentifier, region));

        // Add geofence to the list of observed regions.
        mGeofencingList.add(geofence);
      }
    }

    // Prepare pending intent, geofencing request and client.
    mPendingIntent = preparePendingIntent();
    mGeofencingRequest = prepareGeofencingRequest(mGeofencingList);
    mGoogleApiClient = prepareGoogleClient();
    mGeofencingClient = LocationServices.getGeofencingClient(getContext());

    try {
      mGeofencingClient.addGeofences(mGeofencingRequest, mPendingIntent);
    } catch (SecurityException e) {
      Log.w(TAG, "Geofencing request has been rejected.", e);
    }
  }

  @Override
  public void didUnregister() {
    if (mGeofencingClient != null && mPendingIntent != null) {
      mGeofencingClient.removeGeofences(mPendingIntent);
    }
    mTask = null;
    mPendingIntent = null;
    mGeofencingClient = null;
    mGeofencingRequest = null;
    mGeofencingList = null;
  }

  @Override
  public void didWakeUpWithIntent(Intent intent) {
    Log.i("EXPO", "GeofencingTaskConsumer.onHandleIntent 1");

    GeofencingEvent event = GeofencingEvent.fromIntent(intent);

    if (event.hasError()) {
      String errorMessage = getErrorString(event.getErrorCode());
      Error error = new Error(errorMessage);
      mTask.execute(null, error);
      return;
    }

    // Get region state and event type from given transition type.
    int geofenceTransition = event.getGeofenceTransition();
    String regionState = regionStateForTransitionType(geofenceTransition);
    String eventType = eventTypeFromTransitionType(geofenceTransition);

    // Get the geofences that were triggered. A single event can trigger multiple geofences.
    List<Geofence> triggeringGeofences = event.getTriggeringGeofences();

    for (Geofence geofence : triggeringGeofences) {
      Bundle region = mRegions.get(geofence.getRequestId());

      if (region != null) {
        Bundle data = new Bundle();

        // Update region state in region bundle.
        region.putString("state", regionState);

        data.putString("eventType", eventType);
        data.putBundle("region", region);

        mTask.execute(data, null);
      }
    }
  }

  //endregion
  //region GoogleApiClient callbacks

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    Log.i("EXPO", "Google API Client connected");
  }

  @Override
  public void onConnectionSuspended(int i) {
    Log.i("EXPO", "Google API Client connection suspended");
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    Log.i("EXPO", "Google API Client connection failed");
  }

  //endregion
  //region helpers

  private GoogleApiClient prepareGoogleClient() {
    Context context = getContext();
    GoogleApiClient client = new GoogleApiClient.Builder(context)
        .addConnectionCallbacks(this)
        .addApi(LocationServices.API)
        .build();

    client.connect();
    return client;
  }

  private GeofencingRequest prepareGeofencingRequest(List<Geofence> geofences) {
    return new GeofencingRequest.Builder()
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER | GeofencingRequest.INITIAL_TRIGGER_EXIT)
        .addGeofences(geofences)
        .build();
  }

  private PendingIntent preparePendingIntent() {
    return getTaskManagerUtils().createTaskIntent(getContext(), mTask);
  }

  private Geofence geofenceFromRegion(Map<String, Object> region) {
    String identifier = region.containsKey("identifier") ? (String) region.get("identifier") : UUID.randomUUID().toString();
    double latitude = doubleFromObject(region.get("latitude"));
    double longitude = doubleFromObject(region.get("longitude"));
    double radius = doubleFromObject(region.get("radius"));

    boolean notifyOnEnter = !region.containsKey("notifyOnEnter") || (boolean) region.get("notifyOnEnter");
    boolean notifyOnExit = !region.containsKey("notifyOnExit") || (boolean) region.get("notifyOnExit");
    int transitionTypes = (notifyOnEnter ? Geofence.GEOFENCE_TRANSITION_ENTER : 0) | (notifyOnExit ? Geofence.GEOFENCE_TRANSITION_EXIT : 0);

    return new Geofence.Builder()
        .setRequestId(identifier)
        .setCircularRegion(latitude, longitude, (float) radius)
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(transitionTypes)
        .build();
  }

  private Bundle bundleFromRegion(String identifier, Map<String, Object> region) {
    Bundle bundle = new Bundle();

    bundle.putString("identifier", identifier);
    bundle.putDouble("radius", doubleFromObject(region.get("radius")));
    bundle.putDouble("latitude", doubleFromObject(region.get("latitude")));
    bundle.putDouble("longitude", doubleFromObject(region.get("longitude")));
    bundle.putString("state", LocationModule.GEOFENCING_REGION_STATE_UNKNOWN);

    return bundle;
  }

  private static double doubleFromObject(Object object) {
    if (object instanceof Integer) {
      return ((Integer) object).doubleValue();
    }
    return (Double) object;
  }

  private String regionStateForTransitionType(int transitionType) {
    switch (transitionType) {
      case Geofence.GEOFENCE_TRANSITION_ENTER:
      case Geofence.GEOFENCE_TRANSITION_DWELL:
        return LocationModule.GEOFENCING_REGION_STATE_INSIDE;
      case Geofence.GEOFENCE_TRANSITION_EXIT:
        return LocationModule.GEOFENCING_REGION_STATE_OUTSIDE;
      default:
        return LocationModule.GEOFENCING_REGION_STATE_UNKNOWN;
    }
  }

  private String eventTypeFromTransitionType(int transitionType) {
    switch (transitionType) {
      case Geofence.GEOFENCE_TRANSITION_ENTER:
        return LocationModule.GEOFENCING_EVENT_ENTER;
      case Geofence.GEOFENCE_TRANSITION_EXIT:
        return LocationModule.GEOFENCING_EVENT_EXIT;
      default:
        return "unknown";
    }
  }

  private static String getErrorString(int errorCode) {
    switch (errorCode) {
      case GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE:
        return "Geofencing not available.";
      case GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES:
        return "Too many geofences.";
      case GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS:
        return "Too many pending intents.";
      default:
        return "Unknown geofencing error.";
    }
  }

  //endregion
}
