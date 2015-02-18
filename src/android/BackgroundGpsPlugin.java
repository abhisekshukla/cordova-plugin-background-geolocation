package com.tenforwardconsulting.cordova.bgloc;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.tenforwardconsulting.cordova.bgloc.data.DAOFactory;
import com.tenforwardconsulting.cordova.bgloc.data.Location;
import com.tenforwardconsulting.cordova.bgloc.data.LocationDAO;
import com.tenforwardconsulting.cordova.bgloc.data.LocationSerializer;

public class BackgroundGpsPlugin extends CordovaPlugin {
    private static final String TAG = "BackgroundGpsPlugin";

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_CONFIGURE = "configure";
    public static final String ACTION_SET_CONFIG = "setConfig";
    public static final String ACTION_GET_LOCATIONS = "getLocations";
    public static final String ACTION_START_DRIVE_DETECTION = "startDriveDetection";
    public static final String ACTION_STOP_DRIVE_DETECTION = "stopDriveDetection";
    public static final String ACTION_IS_DRIVE_DETECTED = "isDriveDetected";
    public static final String WATCH_DRIVE_DETECTION = "watchDriveDetection";

	static Activity activity = null;
	static Context context = null;
	private Intent updateServiceIntent = null;
	private Intent driveDetectionServiceIntent = null;

    private Boolean isEnabled = false;

    private String url;
    private String params;
    private String headers;
    private String stationaryRadius = "30";
    private String desiredAccuracy = "100";
    private String distanceFilter = "30";
    private String locationTimeout = "60";
    private String isDebugging = "false";
    private String notificationTitle = "Background tracking";
    private String notificationText = "ENABLED";
    private String stopOnTerminate = "false";
    private boolean postLocationsToServer = true;

	private Boolean isInitialized = false;
	static CallbackContext driveDetectionCallbackContext = null;

	private void init() throws JSONException {
		if (!isInitialized) {
			isInitialized = true;
			activity = cordova.getActivity();
			context = activity.getApplicationContext();
			updateServiceIntent = new Intent(context, TraxLocationUpdateService.class);
			driveDetectionServiceIntent = new Intent(context, TraxLocationDriveDetectionService.class);
		}
	}

	@Override
	public boolean execute(String action, JSONArray data, final CallbackContext callbackContext) throws JSONException {
		init();
		Boolean retVal = false;

		if (ACTION_START.equalsIgnoreCase(action) && !isEnabled) {
			retVal = true;
			if (params == null || headers == null || url == null) {
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						callbackContext.error("Call configure before calling start");
					}
				});
			} else {
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {

						updateServiceIntent.putExtra("url", url);
						updateServiceIntent.putExtra("params", params);
						updateServiceIntent.putExtra("headers", headers);
						updateServiceIntent.putExtra("stationaryRadius", stationaryRadius);
						updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
						updateServiceIntent.putExtra("distanceFilter", distanceFilter);
						updateServiceIntent.putExtra("locationTimeout", locationTimeout);
						updateServiceIntent.putExtra("desiredAccuracy", desiredAccuracy);
						updateServiceIntent.putExtra("isDebugging", isDebugging);
						updateServiceIntent.putExtra("notificationTitle", notificationTitle);
						updateServiceIntent.putExtra("notificationText", notificationText);
						updateServiceIntent.putExtra("stopOnTerminate", stopOnTerminate);
						updateServiceIntent.putExtra("postLocationsToServer", postLocationsToServer);

						activity.startService(updateServiceIntent);
						activity.stopService(driveDetectionServiceIntent);

						callbackContext.success();
					}
				});
				isEnabled = true;
			}
		} else if (ACTION_STOP.equalsIgnoreCase(action)) {
			isEnabled = false;
			retVal = true;
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					activity.stopService(updateServiceIntent);
					activity.startService(driveDetectionServiceIntent);
					callbackContext.success();
				}
			});

		} else if (ACTION_CONFIGURE.equalsIgnoreCase(action)) {
			retVal = true;
			try {
				// Params.
				//    0       1       2           3               4                5               6            7           8                9               10              11                 12
				//[params, headers, url, stationaryRadius, distanceFilter, locationTimeout, desiredAccuracy, debug, notificationTitle, notificationText, activityType, stopOnTerminate, postLocationsToServer]
				this.params = data.getString(0);
				this.headers = data.getString(1);
				this.url = data.getString(2);
				this.stationaryRadius = data.getString(3);
				this.distanceFilter = data.getString(4);
				this.locationTimeout = data.getString(5);
				this.desiredAccuracy = data.getString(6);
				this.isDebugging = data.getString(7);
				this.notificationTitle = data.getString(8);
				this.notificationText = data.getString(9);
				this.stopOnTerminate = data.getString(11);
				this.postLocationsToServer = data.getBoolean(12);
				activity.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						callbackContext.success();
					}
				});
			} catch (JSONException e) {
				callbackContext.error("authToken/url required as parameters: " + e.getMessage());
			}

		} else if (ACTION_SET_CONFIG.equalsIgnoreCase(action)) {
			retVal = true;
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// TODO reconfigure Service
					callbackContext.success();
				}
			});
		} else if (ACTION_GET_LOCATIONS.equalsIgnoreCase(action)) {
			retVal = true;
			//Params.
			//    0
			//[deleteLocations]
			retVal = true;

			final boolean deleteLocations = data.getBoolean(0);
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					// TODO reconfigure Service
					try {
						/**
						 * Get all non deleted tracked locations for this application and pass them to a success callback.
						 * 
						 * @param deleteLocations
						 *            Delete the locations that you have retrieved
						 */

						LocationDAO dao = DAOFactory.createLocationDAO(context);

						Location[] locations = dao.getAllLocations();
						JSONArray json = LocationSerializer.toJSON(locations);
						if (deleteLocations) {
							for (Location location : locations) {
								dao.deleteLocation(location);
							}
						}
						callbackContext.success(json);
					} catch (JSONException e) {
						callbackContext.error(e.getMessage());
					}
				}

			});

		} else if (ACTION_START_DRIVE_DETECTION.equalsIgnoreCase(action)) {
			retVal = true;
			boolean delayDriveDetection = data.getBoolean(0);
			driveDetectionServiceIntent.putExtra("delayDriveDetection", delayDriveDetection);
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					activity.startService(driveDetectionServiceIntent);
					callbackContext.success();
				}
			});

		} else if (ACTION_STOP_DRIVE_DETECTION.equalsIgnoreCase(action)) {
			retVal = true;
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					activity.stopService(driveDetectionServiceIntent);
					callbackContext.success();
				}
			});

		} else if (ACTION_IS_DRIVE_DETECTED.equalsIgnoreCase(action)) {
			retVal = true;
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					boolean isDriving = TraxLocationDriveDetectionUtil.isDriving(context);
					JSONObject obj = new JSONObject();
					try {
						obj.put("isDriving", isDriving);
						callbackContext.success(obj);
					} catch (JSONException e) {
						callbackContext.error(e.getMessage());
					}
				}
			});
			
		} else if (WATCH_DRIVE_DETECTION.equalsIgnoreCase(action)) {
			retVal = true;
			activity.stopService(driveDetectionServiceIntent);
			activity.startService(driveDetectionServiceIntent);
			BackgroundGpsPlugin.driveDetectionCallbackContext = callbackContext;
			activity.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				    pluginResult.setKeepCallback(true);
				    BackgroundGpsPlugin.driveDetectionCallbackContext.sendPluginResult(pluginResult);
				}
			});

		}
		return retVal;
	}

    /**
     * Override method in CordovaPlugin.
     * Checks to see if it should turn off
     */
	@Override
    public void onDestroy() {
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				activity.stopService(updateServiceIntent);
				activity.stopService(driveDetectionServiceIntent);
			}
		});
    }

}
