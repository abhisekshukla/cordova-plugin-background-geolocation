package com.tenforwardconsulting.cordova.bgloc;

import com.tenforwardconsulting.cordova.bgloc.data.DAOFactory;
import com.tenforwardconsulting.cordova.bgloc.data.Location;
import com.tenforwardconsulting.cordova.bgloc.data.LocationDAO;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Date;
import java.lang.Float;
import java.util.List;
import java.util.Iterator;


public class TraxLocationDriveDetectionUtil {
    private static String TAG = "TraxLocationDriveDetectionUtil";
    private final static int SPEEDY_LOCATIONS_THRESHOLD = 2;
    private final static int SPEED_LOCATIONS_TIME_WINDOW = 8 * 60 * 1000; //8 minutes
    private final static double FLOOR = 4.02336; //4.02336 meters/s ~ 9 miles per hour
    private final static double CEILING = 53.6448; //53.6448 meters per second ~ 120 miles per hour

    public static boolean isDriving(Context applicationContext) {
        Log.d(TAG, "IN IS DRIVING");
        List<Location> speedyLocations = getSpeedyLocations(applicationContext);
        Log.d(TAG, "AFTER REMOVE OLD LOCATIONS");
        return speedyLocations.size() >= SPEEDY_LOCATIONS_THRESHOLD;
    }

    private static List<Location> getSpeedyLocations(Context applicationContext) {
        LocationDAO dao = DAOFactory.createDriveDetectionLocationDAO(applicationContext);
        Location[] locations = dao.getAllLocations();
        List<Location> speedyLocations = new ArrayList<Location>();
        for (Location location : locations) {
            if (!isOldLocation(location) && isSpeedyLocation(location)) {
                Log.d(TAG, "SPEEDY LOCATION");
                speedyLocations.add(location);
            } else {
                Log.d(TAG, "OLD OR SLOW LOCATION");
                dao.deleteLocation(location);
            }
        }
        return speedyLocations;
    }

    private static boolean isOldLocation(Location location) {
        long timestamp = location.getRecordedAt().getTime();
        Date now = new Date();
        long nowTimestamp = now.getTime();
        return nowTimestamp - timestamp > SPEED_LOCATIONS_TIME_WINDOW;
    }

    private static boolean isSpeedyLocation(Location location) {
        float speed = Float.parseFloat(location.getSpeed());
        Log.d(TAG, "Speed: " + speed);
        return speed >= FLOOR && speed <= CEILING;
    }
}
