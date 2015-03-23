package com.tenforwardconsulting.cordova.bgloc;

import org.apache.cordova.PluginResult;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

public class TraxDistanceTrackerReceiver extends BroadcastReceiver {

    private Intent driveDetectionServiceIntent = null;

	private static final String ACTION_OPEN_MILEAGE_TRACKER = "TraxOpenMileageTracker";
	private static final String ACTION_CANCEL_MILEAGE_TRACKER = "TraxCancerlMileageTracker";
	private static final String ACTION_COMPLETE_MILEAGE_TRACKER = "TraxCompleteMileageTracker";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            this.driveDetectionServiceIntent = new Intent(context, TraxLocationDriveDetectionService.class);
            context.startService(driveDetectionServiceIntent);
        } else if (intent.getAction() == ACTION_OPEN_MILEAGE_TRACKER) {
        	Log.d("TraxDistanceTrackerReceiver", "Mileage tracker completed");
        	BackgroundGpsPlugin.activity.runOnUiThread(new Runnable() {
        		@Override
        		public void run() {
					PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "openMileageTracker");
				    pluginResult.setKeepCallback(true);
				    BackgroundGpsPlugin.notificationCallbackContext.sendPluginResult(pluginResult);
        		}
        	});
        } else if (intent.getAction() == ACTION_COMPLETE_MILEAGE_TRACKER) {
        	Log.d("TraxDistanceTrackerReceiver", "Mileage tracker completed");
            BackgroundGpsPlugin.activity.runOnUiThread(new Runnable() {
        		@Override
        		public void run() {
					PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "completeMileageTracker");
				    pluginResult.setKeepCallback(true);
				    BackgroundGpsPlugin.notificationCallbackContext.sendPluginResult(pluginResult);
        		}
        	});
        } else if (intent.getAction() == ACTION_CANCEL_MILEAGE_TRACKER) {
        	Log.d("TraxDistanceTrackerReceiver", "Mileage tracker canceled");
			BackgroundGpsPlugin.activity.runOnUiThread(new Runnable() {
        		@Override
        		public void run() {
					PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "cancelMileageTracker");
				    pluginResult.setKeepCallback(true);
				    BackgroundGpsPlugin.notificationCallbackContext.sendPluginResult(pluginResult);
        		}
        	});
        }
    }
}