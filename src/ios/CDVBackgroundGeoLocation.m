////
//  CDVBackgroundGeoLocation
//
//  Created by Chris Scott <chris@transistorsoft.com> on 2013-06-15
//
#import "CDVLocation.h"
#import "CDVBackgroundGeoLocation.h"
#import <Cordova/CDVJSON.h>

// Debug sounds for bg-geolocation life-cycle events.
// http://iphonedevwiki.net/index.php/AudioServices
#define exitRegionSound         1005
#define locationSyncSound       1004
#define paceChangeYesSound      1110
#define paceChangeNoSound       1112
#define acquiringLocationSound  1103
#define acquiredLocationSound   1052
#define locationErrorSound      1073

@implementation CDVBackgroundGeoLocation {
    BOOL isDebugging;
    BOOL enabled;
    BOOL isUpdatingLocation;
    BOOL stopOnTerminate;

    NSString *token;
    NSString *url;
    UIBackgroundTaskIdentifier bgTask;
    NSDate *lastBgTaskAt;

    NSError *locationError;

    BOOL isMoving;

    NSNumber *maxBackgroundHours;
    CLLocationManager *locationManager;
    CLLocationManager *driveDetectionManager;
    CLLocationManager *accurateDriveDetectionManager;
    UILocalNotification *localNotification;

    CDVLocationData *locationData;
    CLLocation *lastLocation;
    NSMutableArray *locationQueue;

    NSDate *suspendedAt;

    CLLocation *stationaryLocation;
    CLCircularRegion *stationaryRegion;
    NSInteger locationAcquisitionAttempts;

    BOOL isAcquiringStationaryLocation;
    NSInteger maxStationaryLocationAttempts;

    BOOL isAcquiringSpeed;
    NSInteger maxSpeedAcquistionAttempts;

    NSInteger stationaryRadius;
    NSInteger distanceFilter;
    NSInteger locationTimeout;
    NSInteger desiredAccuracy;
    CLActivityType activityType;

    NSMutableArray *driveDetectionLocations;

    NSInteger SPEEDY_LOCATIONS_THRESHOLD;
    double FLOOR;
    double CEILING;
    double SPEEDY_LOCATIONS_TIME_WINDOW;
    double DRIVE_DETECTION_DELAY_WINDOW;
    double LOCATION_UPDATE_DELAY_WINDOW;
    double ACCURATE_DRIVE_DETECTION_WINDOW;
    double DESIRED_ACCURACY;
    double ACCURATE_DESIRED_ACCURACY;
    double DISTANCE_FILTER;
    double ACCURATE_DISTANCE_FILTER;
    NSDate *driveDetectionDelayDate;
    BOOL accurateDriveDetectionMode;
    NSDate *accurateDriveDetectionModeStart;
    BOOL isDriveDetectionActive;
    NSDate *lastLocationDate;
}

@synthesize syncCallbackId;
@synthesize stationaryRegionListeners;
@synthesize driveDetectedCallbackId;

- (void)pluginInitialize
{
    // background location cache, for when no network is detected.
    locationManager = [[CLLocationManager alloc] init];
    driveDetectionManager = [[CLLocationManager alloc] init];
    accurateDriveDetectionManager = [[CLLocationManager alloc] init];
    locationManager.delegate = self;
    driveDetectionManager.delegate = self;
    accurateDriveDetectionManager.delegate = self;

    localNotification = [[UILocalNotification alloc] init];
    localNotification.timeZone = [NSTimeZone defaultTimeZone];

    locationQueue = [[NSMutableArray alloc] init];

    isMoving = NO;
    isUpdatingLocation = NO;
    stationaryLocation = nil;
    stationaryRegion = nil;
    isDebugging = NO;
    stopOnTerminate = NO;

    maxStationaryLocationAttempts   = 4;
    maxSpeedAcquistionAttempts      = 3;

    driveDetectionLocations = [NSMutableArray array];
    SPEEDY_LOCATIONS_THRESHOLD = 5;
    FLOOR = 4.02336; //4.02336 meters/s ~ 9 MPH or 2.01168 meters/s ~ 4.5 MPH
    CEILING = 53.6448; //53.6448 meters per second ~ 120 miles per hour
    SPEEDY_LOCATIONS_TIME_WINDOW = 480.0; //8 minutes
    DRIVE_DETECTION_DELAY_WINDOW = 600.0; //10 minutes
    LOCATION_UPDATE_DELAY_WINDOW = 5.0; //5 seconds
    ACCURATE_DRIVE_DETECTION_WINDOW = 480.8; //8 minutes
    DESIRED_ACCURACY = kCLLocationAccuracyHundredMeters;
    ACCURATE_DESIRED_ACCURACY = kCLLocationAccuracyBest;
    //DISTANCE_FILTER = 402.336; //meters 402.336 meters ~ 1/4 miles
    DISTANCE_FILTER = 160.934; //meters 160.934 meters ~ 1/10 miles
    ACCURATE_DISTANCE_FILTER = 30; //meters
    accurateDriveDetectionMode = false;
    isDriveDetectionActive = false;

    bgTask = UIBackgroundTaskInvalid;

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onSuspend:) name:UIApplicationDidEnterBackgroundNotification object:nil];

    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(onResume:) name:UIApplicationWillEnterForegroundNotification object:nil];
    
}
/**
 * configure plugin
 * @param {String} token
 * @param {String} url
 * @param {Number} stationaryRadius
 * @param {Number} distanceFilter
 * @param {Number} locationTimeout
 */
- (void) configure:(CDVInvokedUrlCommand*)command
{
    // in iOS, we call to javascript for HTTP now so token and url should be @deprecated until Android calls out to javascript.
    // Params.
    //    0       1       2           3               4                5               6            7           8                9               10               11
    //[params, headers, url, stationaryRadius, distanceFilter, locationTimeout, desiredAccuracy, debug, notificationTitle, notificationText, activityType, stopOnTerminate]

    // UNUSED ANDROID VARS
    //params = [command.arguments objectAtIndex: 0];
    //headers = [command.arguments objectAtIndex: 1];
    //url = [command.arguments objectAtIndex: 2];
    stationaryRadius    = [[command.arguments objectAtIndex: 3] intValue];
    distanceFilter      = [[command.arguments objectAtIndex: 4] intValue];
    locationTimeout     = [[command.arguments objectAtIndex: 5] intValue];
    desiredAccuracy     = [self decodeDesiredAccuracy:[[command.arguments objectAtIndex: 6] intValue]];
    isDebugging         = [[command.arguments objectAtIndex: 7] boolValue];
    activityType        = [self decodeActivityType:[command.arguments objectAtIndex:10]];
    stopOnTerminate     = [[command.arguments objectAtIndex: 11] boolValue];

    self.syncCallbackId = command.callbackId;

    locationManager.activityType = activityType;
    locationManager.pausesLocationUpdatesAutomatically = YES;
    locationManager.distanceFilter = distanceFilter; // meters
    locationManager.desiredAccuracy = desiredAccuracy;

    driveDetectionManager.activityType = activityType;
    driveDetectionManager.pausesLocationUpdatesAutomatically = YES;
    driveDetectionManager.distanceFilter = DISTANCE_FILTER;
    driveDetectionManager.desiredAccuracy = DESIRED_ACCURACY;
    //pausesLocationUpdatesAutomatically defaults to true
    accurateDriveDetectionManager.activityType = activityType;
    accurateDriveDetectionManager.pausesLocationUpdatesAutomatically = YES;
    accurateDriveDetectionManager.distanceFilter = ACCURATE_DISTANCE_FILTER;
    accurateDriveDetectionManager.desiredAccuracy = ACCURATE_DESIRED_ACCURACY;
    //pausesLocationUpdatesAutomatically defaults to true

    NSLog(@"CDVBackgroundGeoLocation configure");
    NSLog(@"  - token: %@", token);
    NSLog(@"  - url: %@", url);
    NSLog(@"  - distanceFilter: %ld", (long)distanceFilter);
    NSLog(@"  - stationaryRadius: %ld", (long)stationaryRadius);
    NSLog(@"  - locationTimeout: %ld", (long)locationTimeout);
    NSLog(@"  - desiredAccuracy: %ld", (long)desiredAccuracy);
    NSLog(@"  - activityType: %@", [command.arguments objectAtIndex:7]);
    NSLog(@"  - debug: %d", isDebugging);
    NSLog(@"  - stopOnTerminate: %d", stopOnTerminate);
    
    // ios 8 requires permissions to send local-notifications
    if (isDebugging) {
        UIApplication *app = [UIApplication sharedApplication];
        if ([app respondsToSelector:@selector(registerUserNotificationSettings:)]) {
            [app registerUserNotificationSettings:[UIUserNotificationSettings settingsForTypes:UIUserNotificationTypeAlert|UIUserNotificationTypeBadge|UIUserNotificationTypeSound categories:nil]];
        }
    }
}

- (void) addStationaryRegionListener:(CDVInvokedUrlCommand*)command
{
    if (self.stationaryRegionListeners == nil) {
        self.stationaryRegionListeners = [[NSMutableArray alloc] init];
    }
    [self.stationaryRegionListeners addObject:command.callbackId];
    if (stationaryRegion) {
        [self queue:stationaryLocation type:@"stationary"];
    }
}

- (void) flushQueue
{
    // Sanity-check the duration of last bgTask:  If greater than 30s, kill it.
    if (bgTask != UIBackgroundTaskInvalid) {
        if (-[lastBgTaskAt timeIntervalSinceNow] > 30.0) {
            NSLog(@"- CDVBackgroundGeoLocation#flushQueue has to kill an out-standing background-task!");
            if (isDebugging) {
                [self notify:@"Outstanding bg-task was force-killed"];
            }
            [self stopBackgroundTask];
        }
        return;
    }
    if ([locationQueue count] > 0) {
        NSMutableDictionary *data = [locationQueue lastObject];
        [locationQueue removeObject:data];

        // Create a background-task and delegate to Javascript for syncing location
        bgTask = [self createBackgroundTask];
        [self.commandDelegate runInBackground:^{
            [self sync:data];
        }];
    }
}
- (void) setConfig:(CDVInvokedUrlCommand*)command
{
    NSLog(@"- CDVBackgroundGeoLocation setConfig");
    NSDictionary *config = [command.arguments objectAtIndex:0];

    if (config[@"desiredAccuracy"]) {
        desiredAccuracy = [self decodeDesiredAccuracy:[config[@"desiredAccuracy"] floatValue]];
        NSLog(@"    desiredAccuracy: %@", config[@"desiredAccuracy"]);
    }
    if (config[@"stationaryRadius"]) {
        stationaryRadius = [config[@"stationaryRadius"] intValue];
        NSLog(@"    stationaryRadius: %@", config[@"stationaryRadius"]);
    }
    if (config[@"distanceFilter"]) {
        distanceFilter = [config[@"distanceFilter"] intValue];
        NSLog(@"    distanceFilter: %@", config[@"distanceFilter"]);
    }
    if (config[@"locationTimeout"]) {
        locationTimeout = [config[@"locationTimeout"] intValue];
        NSLog(@"    locationTimeout: %@", config[@"locationTimeout"]);
    }

    CDVPluginResult* result = nil;
    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

-(NSInteger)decodeDesiredAccuracy:(NSInteger)accuracy
{
    switch (accuracy) {
        case 1000:
            accuracy = kCLLocationAccuracyKilometer;
            break;
        case 100:
            accuracy = kCLLocationAccuracyHundredMeters;
            break;
        case 10:
            accuracy = kCLLocationAccuracyNearestTenMeters;
            break;
        case 0:
            accuracy = kCLLocationAccuracyBest;
            break;
        default:
            accuracy = kCLLocationAccuracyHundredMeters;
    }
    return accuracy;
}

-(CLActivityType)decodeActivityType:(NSString*)name
{
    if ([name caseInsensitiveCompare:@"AutomotiveNavigation"]) {
        return CLActivityTypeAutomotiveNavigation;
    } else if ([name caseInsensitiveCompare:@"OtherNavigation"]) {
        return CLActivityTypeOtherNavigation;
    } else if ([name caseInsensitiveCompare:@"Fitness"]) {
        return CLActivityTypeFitness;
    } else {
        return CLActivityTypeOther;
    }
}

/**
 * Turn on background geolocation
 */
- (void) start:(CDVInvokedUrlCommand*)command
{
    enabled = YES;
    UIApplicationState state = [[UIApplication sharedApplication] applicationState];

    //NSLog(@"- CDVBackgroundGeoLocation start (background? %d)", state);

    [locationManager startMonitoringSignificantLocationChanges];
    if (state == UIApplicationStateBackground) {
        [self setPace:isMoving];
    }
    CDVPluginResult* result = nil;
    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}
/**
 * Turn it off
 */
- (void) stop:(CDVInvokedUrlCommand*)command
{
    NSLog(@"- CDVBackgroundGeoLocation stop");
    enabled = NO;
    isMoving = NO;

    [self stopUpdatingLocation];
    [locationManager stopMonitoringSignificantLocationChanges];
    if (stationaryRegion != nil) {
        [locationManager stopMonitoringForRegion:stationaryRegion];
        stationaryRegion = nil;
    }
    CDVPluginResult* result = nil;
    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];

}

- (void) requestPermissionIfNecessary
{
#ifdef __IPHONE_8_0
    NSUInteger code = [CLLocationManager authorizationStatus];
    if (code == kCLAuthorizationStatusNotDetermined && ([driveDetectionManager respondsToSelector:@selector(requestAlwaysAuthorization)] || [driveDetectionManager respondsToSelector:@selector(requestWhenInUseAuthorization)])) { //iOS8+
        if([[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSLocationAlwaysUsageDescription"]){
            [driveDetectionManager requestAlwaysAuthorization];
        } else if([[NSBundle mainBundle] objectForInfoDictionaryKey:@"NSLocationWhenInUseUsageDescription"]) {
            [driveDetectionManager requestWhenInUseAuthorization];
        } else {
            NSLog(@"[Warning] No NSLocationAlwaysUsageDescription or NSLocationWhenInUseUsageDescription key is defined in the Info.plist file.");
        }
        return;
    }
#endif
}

- (void) watchDriveDetection:(CDVInvokedUrlCommand*)command
{
    NSLog(@"- CDVBackgroundGeoLocation watchDriveDetection");
    [self requestPermissionIfNecessary];
    [driveDetectionManager stopUpdatingLocation];
    [driveDetectionManager startUpdatingLocation];
    [accurateDriveDetectionManager stopUpdatingLocation];
    isDriveDetectionActive = true;

    BOOL delayDriveDetection = [[command.arguments objectAtIndex: 0] boolValue];
    if (delayDriveDetection)
    {
        driveDetectionDelayDate = [NSDate date];
    }
    else
    {
        driveDetectionDelayDate = nil;
    }

    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [result setKeepCallbackAsBool:YES];
    self.driveDetectedCallbackId = command.callbackId;
    NSLog(@" - CDVBackgroundGeoLocation driveDetectedCallbackId: %@", self.driveDetectedCallbackId);
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) stopDriveDetection:(CDVInvokedUrlCommand*)command
{
    NSLog(@"- CDVBackgroundGeoLocation stopDriveDetection");
    [driveDetectionManager stopUpdatingLocation];
    [accurateDriveDetectionManager stopUpdatingLocation];
    isDriveDetectionActive = false;
    CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}



/**
 * Change pace to moving/stopped
 * @param {Boolean} isMoving
 */
- (void) onPaceChange:(CDVInvokedUrlCommand *)command
{
    isMoving = [[command.arguments objectAtIndex: 0] boolValue];
    NSLog(@"- CDVBackgroundGeoLocation onPaceChange %d", isMoving);
    UIApplicationState state = [[UIApplication sharedApplication] applicationState];
    if (state == UIApplicationStateBackground) {
        [self setPace:isMoving];
    }
}

/**
 * toggle passive or aggressive location services
 */
- (void)setPace:(BOOL)value
{
    NSLog(@"- CDVBackgroundGeoLocation setPace %d, stationaryRegion? %d", value, stationaryRegion!=nil);
    isMoving                        = value;
    isAcquiringStationaryLocation   = NO;
    isAcquiringSpeed                = NO;
    locationAcquisitionAttempts     = 0;
    stationaryLocation              = nil;

    if (isDebugging) {
        //AudioServicesPlaySystemSound (isMoving ? paceChangeYesSound : paceChangeNoSound);
    }
    if (isMoving) {
        if (stationaryRegion) {
            [locationManager stopMonitoringForRegion:stationaryRegion];
            stationaryRegion = nil;
        }
        isAcquiringSpeed = YES;
    } else {
        isAcquiringStationaryLocation   = YES;
    }
    if (isAcquiringSpeed || isAcquiringStationaryLocation) {
        // Crank up the GPS power temporarily to get a good fix on our current location
        [self stopUpdatingLocation];
        locationManager.distanceFilter = kCLDistanceFilterNone;
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation;
        [self startUpdatingLocation];
    }
}

/**
 * Fetches current stationaryLocation
 */
- (void) getStationaryLocation:(CDVInvokedUrlCommand *)command
{
    NSLog(@"- CDVBackgroundGeoLocation getStationaryLocation");

    // Build a resultset for javascript callback.
    CDVPluginResult* result = nil;

    if (stationaryLocation) {
        NSMutableDictionary *returnInfo = [self locationToHash:stationaryLocation];

        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnInfo];
    } else {
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsBool:NO];
    }
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

-(NSMutableDictionary*) locationToHash:(CLLocation*)location
{
    NSMutableDictionary *returnInfo;
    returnInfo = [NSMutableDictionary dictionaryWithCapacity:10];

    NSNumber* timestamp = [NSNumber numberWithDouble:([location.timestamp timeIntervalSince1970] * 1000)];
    [returnInfo setObject:timestamp forKey:@"timestamp"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.speed] forKey:@"speed"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.verticalAccuracy] forKey:@"altitudeAccuracy"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.horizontalAccuracy] forKey:@"accuracy"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.course] forKey:@"heading"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.altitude] forKey:@"altitude"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.coordinate.latitude] forKey:@"latitude"];
    [returnInfo setObject:[NSNumber numberWithDouble:location.coordinate.longitude] forKey:@"longitude"];

    return returnInfo;
}
/**
 * Called by js to signify the end of a background-geolocation event
 */
-(void) finish:(CDVInvokedUrlCommand*)command
{
    NSLog(@"- CDVBackgroundGeoLocation finish");
    [self stopBackgroundTask];
}

/**
 * Suspend.  Turn on passive location services
 */
-(void) onSuspend:(NSNotification *) notification
{
    NSLog(@"- CDVBackgroundGeoLocation suspend (enabled? %d)", enabled);
    suspendedAt = [NSDate date];

    if (enabled) {
        // Sample incoming stationary-location candidate:  Is it within the current stationary-region?  If not, I guess we're moving.
        if (!isMoving && stationaryRegion) {
            if ([self locationAge:stationaryLocation] < (5 * 60.0)) {
                if (isDebugging) {
                    //AudioServicesPlaySystemSound (acquiredLocationSound);
                    [self notify:[NSString stringWithFormat:@"Continue stationary\n%f,%f", [stationaryLocation coordinate].latitude, [stationaryLocation coordinate].longitude]];
                }
                [self queue:stationaryLocation type:@"stationary"];
                return;
            }
        }
        [self setPace: isMoving];
    }
}
/**@
 * Resume.  Turn background off
 */
-(void) onResume:(NSNotification *) notification
{
    NSLog(@"- CDVBackgroundGeoLocation resume");
    if (enabled) {
        [self stopUpdatingLocation];
    }
}



/**@
 * Termination. Checks to see if it should turn off
 */
-(void) onAppTerminate
{
    NSLog(@"- CDVBackgroundGeoLocation appTerminate");
    if (enabled && stopOnTerminate) {
        NSLog(@"- CDVBackgroundGeoLocation stoping on terminate");

        enabled = NO;
        isMoving = NO;

        [self stopUpdatingLocation];
        [locationManager stopMonitoringSignificantLocationChanges];
        if (stationaryRegion != nil) {
            [locationManager stopMonitoringForRegion:stationaryRegion];
            stationaryRegion = nil;
        }
    }
}

-(BOOL) isSpeedy:(CLLocation *)location driveDetectionLastLocation:(CLLocation *)driveDetectionLastLocation
{
    if (location.speed != -1)
    {
        return location.speed >= FLOOR && location.speed <= CEILING;
    }
    else if (driveDetectionLastLocation != nil)
    {
         double meters = [ self directMetersFromLocation:location toCoordinate:driveDetectionLastLocation ];
         double seconds = [ location.timestamp timeIntervalSinceDate:driveDetectionLastLocation.timestamp ];
         double metersPerSecond = meters / seconds;
         return metersPerSecond >= FLOOR && metersPerSecond <= CEILING;
    }
    return false;
}

-(BOOL) isNotInDriveDetectionDelayWindow:(CLLocation *)location
{
    return
        driveDetectionDelayDate == nil ||
        [location.timestamp timeIntervalSinceDate:driveDetectionDelayDate] > DRIVE_DETECTION_DELAY_WINDOW;
}

-(BOOL) isNotInLocationUpdateDelayWindow:(CLLocation *)location
{
    CLLocation *lastLocationUpdate = [driveDetectionLocations lastObject];
    return
        lastLocationUpdate == nil ||
        [location.timestamp timeIntervalSinceDate:lastLocationUpdate.timestamp] > LOCATION_UPDATE_DELAY_WINDOW;
}

-(double) directMetersFromLocation:(CLLocation *)from toCoordinate:(CLLocation *)to
{
    static const double DEG_TO_RAD = 0.017453292519943295769236907684886;
    static const double EARTH_RADIUS_IN_METERS = 6372797.560856;

    double latitudeArc  = (from.coordinate.latitude - to.coordinate.latitude) * DEG_TO_RAD;
    double longitudeArc = (from.coordinate.longitude - to.coordinate.longitude) * DEG_TO_RAD;
    double latitudeH = sin(latitudeArc * 0.5);
    latitudeH *= latitudeH;
    double lontitudeH = sin(longitudeArc * 0.5);
    lontitudeH *= lontitudeH;
    double tmp = cos(from.coordinate.latitude*DEG_TO_RAD) * cos(to.coordinate.latitude*DEG_TO_RAD);
    return EARTH_RADIUS_IN_METERS * 2.0 * asin(sqrt(latitudeH + tmp*lontitudeH));
}

- (BOOL) isDriving
{
    NSLog(@"- CDVBackgroundGeoLocation isDriveDetected");
    NSDate* now = [NSDate date];
    NSMutableArray *discardedItems = [NSMutableArray array];
    for (CLLocation *loc in driveDetectionLocations)
    {
        if ([now timeIntervalSinceDate:loc.timestamp] > SPEEDY_LOCATIONS_TIME_WINDOW)
        {
            [discardedItems addObject:loc];
        }
    }
    [driveDetectionLocations removeObjectsInArray: discardedItems];

    int speedyLocations = [self getSpeedyLocations];
    BOOL isDriving = speedyLocations >= SPEEDY_LOCATIONS_THRESHOLD;
    return isDriving;
}

- (int) getSpeedyLocations
{
    int speedyLocations = 0;
    CLLocation *driveDetectionLastLocation = nil;
    for (CLLocation *loc in driveDetectionLocations)
    {
        if ([self isSpeedy:loc driveDetectionLastLocation:driveDetectionLastLocation])
        {
            speedyLocations++;
        }
        driveDetectionLastLocation = loc;
    }
    return speedyLocations;
}

-(void) locationManager:(CLLocationManager *)manager didUpdateLocations:(NSArray *)locations
{
    NSLog(@"- CDVBackgroundGeoLocation didUpdateLocations");
    if (!isDriveDetectionActive)
    {
        [driveDetectionManager stopUpdatingLocation];
        [accurateDriveDetectionManager stopUpdatingLocation];
        return;
    }
    for (CLLocation *loc in locations)
    {
        if ([self isNotInDriveDetectionDelayWindow:loc] && [self isNotInLocationUpdateDelayWindow:loc])
        {
            [driveDetectionLocations addObject:loc];
        }
    }
    BOOL isDriving = [self isDriving];
    if (isDriving)
    {
        [driveDetectionLocations removeAllObjects];
        NSMutableDictionary *returnInfo = [NSMutableDictionary dictionaryWithCapacity:1];
        [returnInfo setObject:[NSNumber numberWithBool:isDriving] forKey:@"isDriving"];
        CDVPluginResult* result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:returnInfo];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:self.driveDetectedCallbackId];
    }

    [self toggleAccurateDriveDetectionModeIfAppropriate:isDriving];
}

-(void) turnOnAccurateDriveDetectionModeIfAppropriate:(BOOL) isDriving
{
    NSDate* now = [NSDate date];
    int speedyLocations = [self getSpeedyLocations];
    if (speedyLocations > 0 && !isDriving)
    {
        [driveDetectionManager stopUpdatingLocation];
        [accurateDriveDetectionManager startUpdatingLocation];
        accurateDriveDetectionModeStart = now;
        accurateDriveDetectionMode = true;
    }

}


-(void) turnOffAccurateDriveDetectionModeIfAppropriate:(BOOL) isDriving
{
    NSDate* now = [NSDate date];
    int speedyLocations = [self getSpeedyLocations];
    BOOL isTimeExpired = [now timeIntervalSinceDate:accurateDriveDetectionModeStart] > ACCURATE_DRIVE_DETECTION_WINDOW;
    if (isDriving || (speedyLocations == 0 && isTimeExpired))
    {
        [driveDetectionManager startUpdatingLocation];
        [accurateDriveDetectionManager stopUpdatingLocation];
        accurateDriveDetectionModeStart = nil;
        accurateDriveDetectionMode = false;
    }
}

-(void) toggleAccurateDriveDetectionModeIfAppropriate:(BOOL) isDriving
{
    if (!accurateDriveDetectionMode)
    {
        [self turnOnAccurateDriveDetectionModeIfAppropriate:isDriving];
    }
    else
    {
        [self turnOffAccurateDriveDetectionModeIfAppropriate:isDriving];
    }
}


/**
* Manual stationary location his-testing.  This seems to help stationary-exit detection in some places where the automatic geo-fencing soesn't
*/
-(bool)locationIsBeyondStationaryRegion:(CLLocation*)location
{
    NSLog(@"- CDVBackgroundGeoLocation locationIsBeyondStationaryRegion");
    if (![stationaryRegion containsCoordinate:[location coordinate]]) {
        double pointDistance = [stationaryLocation distanceFromLocation:location];
        return (pointDistance - stationaryLocation.horizontalAccuracy - location.horizontalAccuracy) > stationaryRadius;
    } else {
        return NO;
    }
}
/**
 * Calculates distanceFilter by rounding speed to nearest 5 and multiplying by 10.  Clamped at 1km max.
 */
-(float) calculateDistanceFilter:(float)speed
{
    float newDistanceFilter = distanceFilter;
    if (speed < 100) {
        // (rounded-speed-to-nearest-5) / 2)^2
        // eg 5.2 becomes (5/2)^2
        newDistanceFilter = pow((5.0 * floorf(fabsf(speed) / 5.0 + 0.5f)), 2) + distanceFilter;
    }
    return (newDistanceFilter < 1000) ? newDistanceFilter : 1000;
}

-(void) queue:(CLLocation*)location type:(id)type
{
    NSLog(@"- CDVBackgroundGeoLocation queue %@", type);
    NSMutableDictionary *data = [self locationToHash:location];
    [data setObject:type forKey:@"location_type"];
    [locationQueue addObject:data];
    [self flushQueue];
}

-(UIBackgroundTaskIdentifier) createBackgroundTask
{
    lastBgTaskAt = [NSDate date];
    return [[UIApplication sharedApplication] beginBackgroundTaskWithExpirationHandler:^{
        [self stopBackgroundTask];
    }];
}

/**
 * We are running in the background if this is being executed.
 * We can't assume normal network access.
 * bgTask is defined as an instance variable of type UIBackgroundTaskIdentifier
 */
-(void) sync:(NSMutableDictionary*)data
{
    NSLog(@"- CDVBackgroundGeoLocation#sync");
    NSLog(@"  type: %@, position: %@,%@ speed: %@", [data objectForKey:@"location_type"], [data objectForKey:@"latitude"], [data objectForKey:@"longitude"], [data objectForKey:@"speed"]);
    if (isDebugging) {
        [self notify:[NSString stringWithFormat:@"Location update: %s\nSPD: %0.0f | DF: %ld | ACY: %0.0f",
                      ((isMoving) ? "MOVING" : "STATIONARY"),
                      [[data objectForKey:@"speed"] doubleValue],
                      (long) locationManager.distanceFilter,
                      [[data objectForKey:@"accuracy"] doubleValue]]];

        //AudioServicesPlaySystemSound (locationSyncSound);
    }

    // Build a resultset for javascript callback.
    NSString *locationType = [data objectForKey:@"location_type"];
    if ([locationType isEqualToString:@"stationary"]) {
        [self fireStationaryRegionListeners:data];
    } else if ([locationType isEqualToString:@"current"]) {
        CDVPluginResult* result = nil;
        result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:data];
        [result setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:result callbackId:self.syncCallbackId];
    } else {
        NSLog(@"- CDVBackgroundGeoLocation#sync could not determine location_type.");
        [self stopBackgroundTask];
    }
}

- (void) fireStationaryRegionListeners:(NSMutableDictionary*)data
{
    NSLog(@"- CDVBackgroundGeoLocation#fireStationaryRegionListener");
    if (![self.stationaryRegionListeners count]) {
        [self stopBackgroundTask];
        return;
    }
    // Any javascript stationaryRegion event-listeners?
    [data setObject:[NSNumber numberWithDouble:stationaryRadius] forKey:@"radius"];

    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsDictionary:data];
    [result setKeepCallbackAsBool:YES];
    for (NSString *callbackId in self.stationaryRegionListeners)
    {
        [self.commandDelegate sendPluginResult:result callbackId:callbackId];
    }
}

/**
 * Creates a new circle around user and region-monitors it for exit
 */
- (void) startMonitoringStationaryRegion:(CLLocation*)location {
    stationaryLocation = location;

    // fire onStationary @event for Javascript.
    [self queue:location type:@"stationary"];

    CLLocationCoordinate2D coord = [location coordinate];
    NSLog(@"- CDVBackgroundGeoLocation createStationaryRegion (%f,%f)", coord.latitude, coord.longitude);

    if (isDebugging) {
        //AudioServicesPlaySystemSound (acquiredLocationSound);
        [self notify:[NSString stringWithFormat:@"Acquired stationary location\n%f, %f", location.coordinate.latitude,location.coordinate.longitude]];
    }
    if (stationaryRegion != nil) {
        [locationManager stopMonitoringForRegion:stationaryRegion];
    }
    isAcquiringStationaryLocation = NO;
    stationaryRegion = [[CLCircularRegion alloc] initWithCenter: coord radius:stationaryRadius identifier:@"BackgroundGeoLocation stationary region"];
    stationaryRegion.notifyOnExit = YES;
    [locationManager startMonitoringForRegion:stationaryRegion];

    [self stopUpdatingLocation];
    locationManager.distanceFilter = distanceFilter;
    locationManager.desiredAccuracy = desiredAccuracy;
}

- (bool) stationaryRegionContainsLocation:(CLLocation*)location {
    CLCircularRegion *region = [locationManager.monitoredRegions member:stationaryRegion];
    return ([region containsCoordinate:location.coordinate]) ? YES : NO;
}
- (void) stopBackgroundTask
{
    UIApplication *app = [UIApplication sharedApplication];
    NSLog(@"- CDVBackgroundGeoLocation stopBackgroundTask (remaining t: %f)", app.backgroundTimeRemaining);
    if (bgTask != UIBackgroundTaskInvalid)
    {
        [app endBackgroundTask:bgTask];
        bgTask = UIBackgroundTaskInvalid;
    }
    [self flushQueue];
}
/**
 * Called when user exits their stationary radius (ie: they walked ~50m away from their last recorded location.
 * - turn on more aggressive location monitoring.
 */
- (void)locationManager:(CLLocationManager *)manager didExitRegion:(CLRegion *)region
{
    NSLog(@"- CDVBackgroundGeoLocation exit region");
    if (isDebugging) {
        //AudioServicesPlaySystemSound (exitRegionSound);
        [self notify:@"Exit stationary region"];
    }
    [self setPace:YES];
}

/**
 * 1. turn off std location services
 * 2. turn on significantChanges API
 * 3. create a region and start monitoring exits.
 */
- (void)locationManagerDidPauseLocationUpdates:(CLLocationManager *)manager
{
    NSLog(@"- CDVBackgroundGeoLocation paused location updates");
    if (isDebugging) {
        [self notify:@"Stop detected"];
    }
    if (locationError) {
        isMoving = NO;
        [self startMonitoringStationaryRegion:lastLocation];
        [self stopUpdatingLocation];
    } else {
        [self setPace:NO];
    }
}

/**
 * 1. Turn off significantChanges ApI
 * 2. turn on std. location services
 * 3. nullify stationaryRegion
 */
- (void)locationManagerDidResumeLocationUpdates:(CLLocationManager *)manager
{
    NSLog(@"- CDVBackgroundGeoLocation resume location updates");
    if (isDebugging) {
        [self notify:@"Resume location updates"];
    }
    [self setPace:YES];
}

- (void)locationManager:(CLLocationManager *)manager didFailWithError:(NSError *)error
{
    NSLog(@"- CDVBackgroundGeoLocation locationManager failed:  %@", error);
    if (isDebugging) {
        //AudioServicesPlaySystemSound (locationErrorSound);
        [self notify:[NSString stringWithFormat:@"Location error: %@", error.localizedDescription]];
    }

    locationError = error;

    switch(error.code) {
        case kCLErrorLocationUnknown:
        case kCLErrorNetwork:
        case kCLErrorRegionMonitoringDenied:
        case kCLErrorRegionMonitoringSetupDelayed:
        case kCLErrorRegionMonitoringResponseDelayed:
        case kCLErrorGeocodeFoundNoResult:
        case kCLErrorGeocodeFoundPartialResult:
        case kCLErrorGeocodeCanceled:
            break;
        case kCLErrorDenied:
            [self stopUpdatingLocation];
            break;
        default:
            [self stopUpdatingLocation];
    }
}

- (void) stopUpdatingLocation
{
    [locationManager stopUpdatingLocation];
    isUpdatingLocation = NO;
}

- (void) startUpdatingLocation
{
    SEL requestSelector = NSSelectorFromString(@"requestAlwaysAuthorization");
    if ([CLLocationManager authorizationStatus] == kCLAuthorizationStatusNotDetermined && [locationManager respondsToSelector:requestSelector]) {
        ((void (*)(id, SEL))[locationManager methodForSelector:requestSelector])(locationManager, requestSelector);
        [locationManager startUpdatingLocation];
        isUpdatingLocation = YES;
    } else {
        [locationManager startUpdatingLocation];
        isUpdatingLocation = YES;
    }
}
- (void) locationManager:(CLLocationManager *)manager didChangeAuthorizationStatus:(CLAuthorizationStatus)status
{
    NSLog(@"- CDVBackgroundGeoLocation didChangeAuthorizationStatus %u", status);
    if (isDebugging) {
        [self notify:[NSString stringWithFormat:@"Authorization status changed %u", status]];
    }
}

- (NSTimeInterval) locationAge:(CLLocation*)location
{
    return -[location.timestamp timeIntervalSinceNow];
}

- (void) notify:(NSString*)message
{
    localNotification.fireDate = [NSDate date];
    localNotification.alertBody = message;
    [[UIApplication sharedApplication] scheduleLocalNotification:localNotification];
}
/**
 * If you don't stopMonitoring when application terminates, the app will be awoken still when a
 * new location arrives, essentially monitoring the user's location even when they've killed the app.
 * Might be desirable in certain apps.
 */
- (void)applicationWillTerminate:(UIApplication *)application {
    [locationManager stopMonitoringSignificantLocationChanges];
    [locationManager stopUpdatingLocation];
    if (stationaryRegion != nil) {
        [locationManager stopMonitoringForRegion:stationaryRegion];
    }
}

- (void)dealloc
{
    locationManager.delegate = nil;
}

@end
