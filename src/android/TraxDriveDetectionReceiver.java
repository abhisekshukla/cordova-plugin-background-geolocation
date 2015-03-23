package com.tenforwardconsulting.cordova.bgloc;

import org.apache.cordova.PluginResult;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

public class TraxDriveDetectionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent driveDetectionServiceIntent = new Intent(context, TraxLocationDriveDetectionService.class);
            context.startService(driveDetectionServiceIntent);
        }
    }
}