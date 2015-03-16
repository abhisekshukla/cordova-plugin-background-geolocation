package com.tenforwardconsulting.cordova.bgloc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.util.Log;

import com.tenforwardconsulting.cordova.bgloc.data.DAOFactory;
import com.tenforwardconsulting.cordova.bgloc.data.Location;
import com.tenforwardconsulting.cordova.bgloc.data.LocationDAO;

public class TraxLocationDriveDetectionUtil {
    private static String TAG = "TraxLocationDriveDetectionUtil";
	private final static int SPEEDY_LOCATIONS_THRESHOLD = 5;
    private final static int SPEEDY_LOCATIONS_TIME_WINDOW = 8 * 60 * 1000; //8 minutes
	private final static double FLOOR = 4.02336; //4.02336 meters/s ~ 9 MPH or 2.01168 meters/s ~ 4.5 MPH
    private final static double CEILING = 53.6448; //53.6448 meters per second ~ 120 miles per hour
    private static final double R = 6372.8; // In kilometers

    private static List<TraxLog> logs = new ArrayList<TraxLog>();
    private final static int LOG_BUFFER_THRESHOLD = 50;

    public static boolean isDriving(Context applicationContext) {
        Log.d(TAG, "IN IS DRIVING");
        int speedyLocations = getSpeedyLocations(applicationContext);
        Log.d(TAG, "AFTER REMOVE OLD LOCATIONS");
        return speedyLocations >= SPEEDY_LOCATIONS_THRESHOLD;
    }

    public static int getSpeedyLocations(Context applicationContext) {
        LocationDAO dao = DAOFactory.createDriveDetectionLocationDAO(applicationContext);
        Location[] locations = dao.getAllLocations();
        List<Location> speedyLocations = new ArrayList<Location>();
        for (Location location : locations)
        {
            if (isOldLocation(location))
            {
                Log.d(TAG, "OLD LOCATION");
                dao.deleteLocation(location);
            }
        }
        Location lastLocation = null;
        for (Location location : locations) {
            if (isSpeedyLocation(location, lastLocation)) {
                Log.d(TAG, "SPEEDY LOCATION");
                speedyLocations.add(location);
            }
            lastLocation = location;
        }
        return speedyLocations.size();
    }

    public static void deleteAll(Context applicationContext) {
        LocationDAO dao = DAOFactory.createDriveDetectionLocationDAO(applicationContext);
        Location[] locations = dao.getAllLocations();
        for (Location location : locations)
        {
            dao.deleteLocation(location);
        }
    }

    public static void log(String name, String detail) {
        TraxLog log = new TraxLog(name, detail);
        synchronized (logs) {
            logs.add(log);
            if (logs.size() >= LOG_BUFFER_THRESHOLD) {
                List<TraxLog> previousLogs = emptyLogs();
                new TraxLogAsyncTask().execute(previousLogs.toArray(new TraxLog[previousLogs.size()]));
            }
        }
    }

    private static List<TraxLog> emptyLogs() {
        List<TraxLog> previousLogs = new ArrayList<TraxLog>();
        for (TraxLog log : logs) {
           previousLogs.add(log);
        }
        logs = new ArrayList<TraxLog>();
        return previousLogs;
    }

    private static boolean isOldLocation(Location location) {
        long timestamp = location.getRecordedAt().getTime();
        Date now = new Date();
        long nowTimestamp = now.getTime();
        return nowTimestamp - timestamp > SPEEDY_LOCATIONS_TIME_WINDOW;
    }

    private static boolean isSpeedyLocation(Location location, Location lastLocation) {
        double speed = Float.parseFloat(location.getSpeed());
        Log.d(TAG, "Speed: " + speed);
        if (speed != 0.0)
        {
            Log.d(TAG, "USING SPEED");
            return speed >= FLOOR && speed <= CEILING;
        }
        else if (lastLocation != null)
        {
            double lastLat = Float.parseFloat(lastLocation.getLatitude());
            double lastLon = Float.parseFloat(lastLocation.getLongitude());
            double lat = Float.parseFloat(location.getLatitude());
            double lon = Float.parseFloat(location.getLongitude());
            double meters = haversineInMeters(lastLat, lastLon, lat, lon);
            long lastTimestamp = lastLocation.getRecordedAt().getTime();
            long timestamp = location.getRecordedAt().getTime();
            double seconds = (timestamp - lastTimestamp) / 1000;
            double metersPerSecond = meters / seconds;
            Log.d(TAG, "USING METERS PER SECOND");
            Log.d(TAG, "MetersPerSecond: " + metersPerSecond);
            return metersPerSecond >= FLOOR && metersPerSecond <= CEILING;
        }
        Log.d(TAG, "No lastLocation");
        return false;
    }

    private static double haversineInMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return R * c * 1000;
    }
}
