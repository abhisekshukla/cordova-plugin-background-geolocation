package com.tenforwardconsulting.cordova.bgloc;

import java.util.List;
import java.util.Iterator;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONException;
import org.json.JSONObject;

import com.tenforwardconsulting.cordova.bgloc.data.DAOFactory;
import com.tenforwardconsulting.cordova.bgloc.data.LocationDAO;

import android.app.Service;

import android.content.Context;
import android.content.Intent;

import android.location.Location;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;

import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;

import android.util.Log;

public class TraxLocationUpdateService extends Service implements LocationListener {
    private Criteria criteria;
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;
    private long MIN_TIME_BETWEEN_LOCATION_UPDATES = 30 * 1000; //milliseconds
    private float MIN_DISTANCE_BETWEEN_LOCATION_UPDATES = 0; //meters
    private String TAG = "TraxLocationUpdateService";

    @Override
    public void onCreate() {
        Log.d(TAG, "creating");
        this.setupLocationManager();
        this.setupCriteria();
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        this.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "starting");
        this.locationManager.removeUpdates(this);
        this.locationManager.requestLocationUpdates(MIN_TIME_BETWEEN_LOCATION_UPDATES, MIN_DISTANCE_BETWEEN_LOCATION_UPDATES, this.criteria, this, null);
        return this.START_REDELIVER_INTENT;
    }

    public void onLocationChanged(Location location) {
        this.persistLocation(location);
    }

    @Override
    public boolean stopService(Intent intent) {
        Log.d(TAG, "stoping");
        this.locationManager.removeUpdates(this);
        this.wakeLock.release();
        return super.stopService(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "destroying");
        this.locationManager.removeUpdates(this);
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

    private void setupCriteria() {
        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setBearingAccuracy(Criteria.NO_REQUIREMENT);
        criteria.setSpeedAccuracy(Criteria.NO_REQUIREMENT);
        criteria.setVerticalAccuracy(Criteria.NO_REQUIREMENT);
    }

    private void persistLocation(Location location) {
        LocationDAO dao = DAOFactory.createLocationDAO(this.getApplicationContext());
        com.tenforwardconsulting.cordova.bgloc.data.Location savedLocation = com.tenforwardconsulting.cordova.bgloc.data.Location.fromAndroidLocation(location);

        if (dao.persistLocation(savedLocation)) {
            Log.d(TAG, "Persisted Location: " + savedLocation);
        } else {
            Log.w(TAG, "Failed to persist location");
        }
    }
}
