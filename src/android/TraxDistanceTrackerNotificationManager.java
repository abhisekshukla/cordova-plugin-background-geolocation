package com.tenforwardconsulting.cordova.bgloc;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.util.Log;

public class TraxDistanceTrackerNotificationManager {

	private static final int IC_MENU_DELETE = 17301564;
	private static final int IC_MENU_UPLOAD = 17301589;
	private static final int DISTANCE_TRACKER_NOTIFICATION_ID = 1005;
	
	private static final String ACTION_OPEN_MILEAGE_TRACKER = "TraxOpenMileageTracker";
	private static final String ACTION_CANCEL_MILEAGE_TRACKER = "TraxCancerlMileageTracker";
	private static final String ACTION_COMPLETE_MILEAGE_TRACKER = "TraxCompleteMileageTracker";
    
    public static void createTrackingMileageNotification(Context applicationContext) {
    	
    	PendingIntent openMileageTrackerPendingIntent = createOpenMileageTrackerPendingIntent(applicationContext);
    	PendingIntent cancelMileageTrackerPendingIntent = createCancelMileageTrackerPendingIntent(applicationContext);
    	PendingIntent completeMileageTrackerPendingIntent = createCompleteMileageTrackerPendingIntent(applicationContext);
    	    	    	
        Notification notification = new Notification.Builder(applicationContext)
        	.setContentTitle("")
        	.setContentText("Hurdlr is tracking your mileage")
        	.setSmallIcon(applicationContext.getApplicationInfo().icon)
        	.setContentIntent(openMileageTrackerPendingIntent)
        	.setOngoing(true)
        	.addAction(IC_MENU_DELETE, "CANCEL", cancelMileageTrackerPendingIntent)
        	.addAction(IC_MENU_UPLOAD, "COMPLETE", completeMileageTrackerPendingIntent)
        	.build();
        
        NotificationManager notificationManager = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
	    notificationManager.notify(DISTANCE_TRACKER_NOTIFICATION_ID, notification);
    }
    
    public static void cancelTrackingMileageNotification(Context applicationContext) {
        NotificationManager notificationManager = (NotificationManager) applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(DISTANCE_TRACKER_NOTIFICATION_ID);
    }
    
    private static PendingIntent createOpenMileageTrackerPendingIntent(Context applicationContext) {
    	Intent openMileageTrackerIntent = new Intent(applicationContext, TraxDistanceTrackerReceiver.class);
    	openMileageTrackerIntent.setAction(ACTION_OPEN_MILEAGE_TRACKER);
    	return PendingIntent.getBroadcast(applicationContext, /*requestCode*/0, openMileageTrackerIntent, /*flags*/0);
    }
    
    private static PendingIntent createCancelMileageTrackerPendingIntent(Context applicationContext) {
    	Intent cancelMileageTrackerIntent = new Intent(applicationContext, TraxDistanceTrackerReceiver.class);
    	cancelMileageTrackerIntent.setAction(ACTION_CANCEL_MILEAGE_TRACKER);
    	return PendingIntent.getBroadcast(applicationContext, /*requestCode*/0, cancelMileageTrackerIntent, /*flags*/0);
    }
    
    private static PendingIntent createCompleteMileageTrackerPendingIntent(Context applicationContext) {
    	Intent completeMileageTrackerIntent = new Intent(applicationContext, TraxDistanceTrackerReceiver.class);
    	completeMileageTrackerIntent.setAction(ACTION_COMPLETE_MILEAGE_TRACKER);
    	return PendingIntent.getBroadcast(applicationContext, /*requestCode*/0, completeMileageTrackerIntent, /*flags*/0);
    }
}