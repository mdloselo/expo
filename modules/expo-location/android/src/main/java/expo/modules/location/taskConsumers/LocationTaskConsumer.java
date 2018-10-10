package expo.modules.location.taskConsumers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import expo.interfaces.taskManager.TaskConsumer;
import expo.interfaces.taskManager.TaskManagerUtilsInterface;
import expo.interfaces.taskManager.TaskConsumerInterface;
import expo.interfaces.taskManager.TaskInterface;
import expo.modules.location.LocationModule;
import io.nlopez.smartlocation.location.config.LocationParams;

public class LocationTaskConsumer extends TaskConsumer implements TaskConsumerInterface, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
  private static final String TAG = "LocationTaskConsumer";

  private TaskInterface mTask;
  private PendingIntent mPendingIntent;
  private GoogleApiClient mGoogleApiClient;
  private LocationRequest mLocationRequest;
  private FusedLocationProviderClient mLocationClient;

  public LocationTaskConsumer(Context context, TaskManagerUtilsInterface taskManagerUtils) {
    super(context, taskManagerUtils);
  }

  //region TaskConsumerInterface

  public void didRegister(TaskInterface task) {
    Context context = getContext();

    if (context == null) {
      Log.w(TAG, "The context has been abandoned.");
      return;
    }
    if (!isAnyProviderAvailable()) {
      Log.w(TAG, "There is no location provider available.");
      return;
    }

    mTask = task;
    mGoogleApiClient = prepareGoogleClient();
    mLocationRequest = prepareLocationRequest();
    mPendingIntent = preparePendingIntent();

    try {
      mLocationClient = LocationServices.getFusedLocationProviderClient(context);
      mLocationClient.requestLocationUpdates(mLocationRequest, mPendingIntent);
    } catch (SecurityException e) {
      Log.w(TAG, "Location request has been rejected.", e);
    }
  }

  public void didUnregister() {
    if (mLocationClient != null && mPendingIntent != null) {
      mLocationClient.removeLocationUpdates(mPendingIntent);
    }
    mTask = null;
    mPendingIntent = null;
    mGoogleApiClient = null;
    mLocationRequest = null;
    mLocationClient = null;
  }

  public void didWakeUpWithIntent(Intent intent) {
    if (mTask == null) {
      return;
    }

    LocationResult result = LocationResult.extractResult(intent);

    if (result != null) {
      List<Location> locations = result.getLocations();
      ArrayList<Bundle> locationBundles = new ArrayList<>();
      Bundle data = new Bundle();

      for (Location location : locations) {
        Bundle locationBundle = LocationModule.locationToMap(location);
        locationBundles.add(locationBundle);
      }

      data.putParcelableArrayList("locations", locationBundles);
      mTask.execute(data, null);
    }
  }

  //endregion
  // GoogleApiClient callbacks

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

  //region private

  private GoogleApiClient prepareGoogleClient() {
    Context context = getContext();
    GoogleApiClient client = new GoogleApiClient.Builder(context)
        .addConnectionCallbacks(this)
        .addApi(LocationServices.API)
        .build();

    client.connect();
    return client;
  }

  private LocationRequest prepareLocationRequest() {
    Map<String, Object> options = mTask.getOptions();
    LocationParams locationParams = LocationModule.mapOptionsToLocationParams(options);

    String accuracy = options.containsKey("accuracy")
        ? (String) options.get("accuracy")
        : LocationModule.ACCURACY_BALANCED;

    return new LocationRequest()
        .setFastestInterval(locationParams.getInterval())
        .setInterval(locationParams.getInterval())
        .setSmallestDisplacement(locationParams.getDistance())
        .setPriority(mapAccuracyToPriority(accuracy));
  }

  private PendingIntent preparePendingIntent() {
    return getTaskManagerUtils().createTaskIntent(getContext(), mTask);
  }

  private int mapAccuracyToPriority(String accuracy) {
    switch (accuracy) {
      case LocationModule.ACCURACY_BEST_FOR_NAVIGATION:
      case LocationModule.ACCURACY_HIGHEST:
      case LocationModule.ACCURACY_HIGH:
        return LocationRequest.PRIORITY_HIGH_ACCURACY;
      case LocationModule.ACCURACY_BALANCED:
      default:
        return LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
      case LocationModule.ACCURACY_LOW:
        return LocationRequest.PRIORITY_LOW_POWER;
      case LocationModule.ACCURACY_LOWEST:
        return LocationRequest.PRIORITY_NO_POWER;
    }
  }

  private boolean isAnyProviderAvailable() {
    Context context = getContext();

    if (context == null) {
      return false;
    }

    LocationManager locationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
    return locationManager.isProviderEnabled("gps") || locationManager.isProviderEnabled("network");
  }

  //endregion
}
