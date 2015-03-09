package com.tenforwardconsulting.cordova.bgloc;

import java.util.Date;

import org.apache.cordova.PluginResult;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.tenforwardconsulting.cordova.bgloc.data.DAOFactory;
import com.tenforwardconsulting.cordova.bgloc.data.LocationDAO;

public class TraxLocationDriveDetectionService extends Service implements LocationListener {
    private Criteria criteria;
    private Criteria accurateCriteria;
    private LocationManager locationManager;
    private LocationManager accurateLocationManager;
    private PowerManager.WakeLock wakeLock;
	private long MIN_TIME_BETWEEN_LOCATION_UPDATES = 15 * 1000; //milliseconds
//	private double MIN_DISTANCE_BETWEEN_LOCATION_UPDATES = 201.168; //meters 402.336 meters ~ 1/4 mile or 201.168 ~ 1/8 mile
    private double MIN_DISTANCE_BETWEEN_LOCATION_UPDATES = 160.934; //meters 160.934 meters ~ 1/10 miles
    private String TAG = "TraxLocationDriveDetectionService";
    private Date driveDetectionDelayDate;
	private double DRIVE_DETECTION_DELAY_WINDOW = 60 * 10 * 1000; //milliseconds
    private boolean isDriving = false;
    private boolean accurateDriveDetectionMode = false;
    private Date accurateDriveDetectionModeStart;
    private double ACCURATE_DRIVE_DETECTION_WINDOW = 8 * 60 * 1000; //8 minutes
    private boolean isDriveDetectionActive = false;

    @Override
    public void onCreate() {
        Log.d(TAG, "creating");
        this.setupLocationManager();
        this.setupAccurateLocationManager();
        this.setupCriteria();
        this.setupAccurateCriteria();
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "starting");
        this.isDriveDetectionActive = true;
        boolean delayDriveDetection = intent.getBooleanExtra("delayDriveDetection", /*default*/true);
        Log.d(TAG, "delayDriveDetection: " + delayDriveDetection);
        if (delayDriveDetection)
        {
            this.driveDetectionDelayDate = new Date();
        }
        this.locationManager.removeUpdates(this);
        this.accurateLocationManager.removeUpdates(this);
        this.locationManager.requestLocationUpdates(MIN_TIME_BETWEEN_LOCATION_UPDATES, (float)MIN_DISTANCE_BETWEEN_LOCATION_UPDATES, this.criteria, this, null);
        return this.START_REDELIVER_INTENT;
    }

    public void onLocationChanged(Location location) {
        Log.d(TAG, "Drive detection location changed");
        if (!this.isDriveDetectionActive) {
            this.locationManager.removeUpdates(this);
            this.accurateLocationManager.removeUpdates(this);
        }
        if (this.driveDetectionDelayDate == null ||
            new Date().getTime() - this.driveDetectionDelayDate.getTime() > DRIVE_DETECTION_DELAY_WINDOW)
        {
            this.persistLocation(location);
            //check if we're driving
            //if we're driving call the success callback
            boolean areWeDriving = this.isDriving || TraxLocationDriveDetectionUtil.isDriving(BackgroundGpsPlugin.context);
            this.toggleAccurateDriveDetectionModeIfAppropriate(areWeDriving);
            if (areWeDriving) {
            	this.isDriving = true;
            	BackgroundGpsPlugin.activity.runOnUiThread(new Runnable() {
    				@Override
    				public void run() {
    	            	JSONObject obj = new JSONObject();
    					try {
    						obj.put("isDriving", isDriving);
    		                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, obj);
    		                pluginResult.setKeepCallback(true);

    		                BackgroundGpsPlugin.driveDetectionCallbackContext.sendPluginResult(pluginResult);
    					} catch (JSONException e) {
    						isDriving = false;
    					}
    				}
    			});
            }
        }
        else {
            Log.d(TAG, "Did not persist location");
        }
        
    }

    @Override
    public boolean stopService(Intent intent) {
        Log.d(TAG, "stoping");
        this.isDriveDetectionActive = false;
        this.locationManager.removeUpdates(this);
        this.accurateLocationManager.removeUpdates(this);
        this.wakeLock.release();
        this.isDriving = false;
        return super.stopService(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying");
        this.locationManager.removeUpdates(this);
        this.accurateLocationManager.removeUpdates(this);
        this.wakeLock.release();
        super.onDestroy();
    }

    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        Log.i(TAG, "OnBind" + intent);
        return null;
    }

    public void onProviderDisabled(String provider) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onProviderDisabled: " + provider);
    }
    public void onProviderEnabled(String provider) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onProviderEnabled: " + provider);
    }
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // TODO Auto-generated method stub
        Log.d(TAG, "- onStatusChanged: " + provider + ", status: " + status);
    }

    private void setupLocationManager() {
        this.locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

    private void setupAccurateLocationManager() {
        this.accurateLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
    }

    private void setupCriteria() {
        criteria = new Criteria();
		criteria.setSpeedAccuracy(Criteria.ACCURACY_LOW);
		criteria.setHorizontalAccuracy(Criteria.ACCURACY_LOW);
		criteria.setBearingAccuracy(Criteria.ACCURACY_LOW);
		criteria.setVerticalAccuracy(Criteria.NO_REQUIREMENT);
		criteria.setPowerRequirement(Criteria.POWER_LOW);
    }

    private void setupAccurateCriteria() {
        accurateCriteria = new Criteria();
        accurateCriteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);
        accurateCriteria.setHorizontalAccuracy(Criteria.ACCURACY_HIGH);
        accurateCriteria.setBearingAccuracy(Criteria.ACCURACY_HIGH);
        accurateCriteria.setVerticalAccuracy(Criteria.NO_REQUIREMENT);
//        accurateCriteria.setPowerRequirement(Criteria.POWER_LOW);
    }

    private void persistLocation(Location location) {
        Log.d(TAG, "persisting location!!!");
        LocationDAO dao = DAOFactory.createDriveDetectionLocationDAO(this.getApplicationContext());
        com.tenforwardconsulting.cordova.bgloc.data.Location savedLocation = com.tenforwardconsulting.cordova.bgloc.data.Location.fromAndroidLocation(location);

        if (dao.persistLocation(savedLocation)) {
            Log.d(TAG, "Persisted Drive Detection Location: " + savedLocation);
        } else {
            Log.w(TAG, "Failed to Drive Detection persist location");
        }
    }

    private void turnOnAccurateDriveDetectionModeIfAppropriate(boolean isDriving) {
        int speedyLocations = TraxLocationDriveDetectionUtil.getSpeedyLocations(BackgroundGpsPlugin.context);
        if (speedyLocations > 0 && !isDriving) {
            this.accurateDriveDetectionMode = true;
            this.locationManager.removeUpdates(this);
            this.accurateLocationManager.requestLocationUpdates(0, 0, this.accurateCriteria, this, null);
            this.accurateDriveDetectionModeStart = new Date();
        }
    }

    private void turnOffAccurateDriveDetectionModeIfAppropriate(boolean isDriving) {
        int speedyLocations = TraxLocationDriveDetectionUtil.getSpeedyLocations(BackgroundGpsPlugin.context);
        boolean isTimeExpired = new Date().getTime() - this.accurateDriveDetectionModeStart.getTime() > ACCURATE_DRIVE_DETECTION_WINDOW;
        if (isDriving || (speedyLocations == 0 && isTimeExpired)) {
            this.accurateDriveDetectionMode = false;
            this.accurateLocationManager.removeUpdates(this);
            this.locationManager.requestLocationUpdates(MIN_TIME_BETWEEN_LOCATION_UPDATES, (float)MIN_DISTANCE_BETWEEN_LOCATION_UPDATES, this.criteria, this, null);
            this.accurateDriveDetectionModeStart = null;
        }
    }

    private void toggleAccurateDriveDetectionModeIfAppropriate(boolean isDriving) {
        if (!this.accurateDriveDetectionMode) {
            this.turnOnAccurateDriveDetectionModeIfAppropriate(isDriving);
        } else {
            this.turnOffAccurateDriveDetectionModeIfAppropriate(isDriving);
        }
    }

}
