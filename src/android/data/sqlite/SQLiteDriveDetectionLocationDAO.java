package com.tenforwardconsulting.cordova.bgloc.data.sqlite;

import android.content.Context;

public class SQLiteDriveDetectionLocationDAO extends SQLiteLocationDAO {
    private static final String TAG = "SQLiteDriveDetectionLocationDAO";

    public SQLiteDriveDetectionLocationDAO(Context context) {
        super(context);
    }

    @Override
    protected String getTableName() {
        return LocationOpenHelper.DRIVE_DETECTION_LOCATION_TABLE_NAME;
    }

    @Override
    protected String getTag() {
        return TAG;
    }
}
