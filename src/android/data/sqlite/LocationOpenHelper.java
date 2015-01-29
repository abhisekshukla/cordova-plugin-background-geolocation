package com.tenforwardconsulting.cordova.bgloc.data.sqlite;


import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class LocationOpenHelper extends SQLiteOpenHelper {
    private static final String SQLITE_DATABASE_NAME = "cordova_bg_locations";
    private static final int DATABASE_VERSION = 2;
    public static final String LOCATION_TABLE_NAME = "location";
    private static final String LOCATION_TABLE_COLUMNS = 
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " recordedAt TEXT," +
        " accuracy TEXT," +
        " speed TEXT," +
        " bearing TEXT," +
        " altitude TEXT," +
        " latitude TEXT," +
        " longitude TEXT";
    private static final String LOCATION_TABLE_CREATE =
        "CREATE TABLE " + LOCATION_TABLE_NAME + " (" +
        LOCATION_TABLE_COLUMNS +
        ");";

    public static final String DRIVE_DETECTION_LOCATION_TABLE_NAME = "driveDetectionLocation";
    private static final String DRIVE_DETECTION_LOCATION_TABLE_COLUMNS =
        " id INTEGER PRIMARY KEY AUTOINCREMENT," +
        " recordedAt TEXT," +
        " accuracy TEXT," +
        " speed TEXT," +
        " bearing TEXT," +
        " altitude TEXT," +
        " latitude TEXT," +
        " longitude TEXT";
    private static final String DRIVE_DETECTION_LOCATION_TABLE_CREATE =
        "CREATE TABLE IF NOT EXISTS " + DRIVE_DETECTION_LOCATION_TABLE_NAME + " (" +
        DRIVE_DETECTION_LOCATION_TABLE_COLUMNS +
        ");";

    LocationOpenHelper(Context context) {
        super(context, SQLITE_DATABASE_NAME, null, DATABASE_VERSION);
    }    

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(LOCATION_TABLE_CREATE);
        Log.d(this.getClass().getName(), LOCATION_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DRIVE_DETECTION_LOCATION_TABLE_CREATE);
        Log.d(this.getClass().getName(), DRIVE_DETECTION_LOCATION_TABLE_CREATE);
    }
}