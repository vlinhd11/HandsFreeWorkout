package edu.uhmanoa.android.handsfreeworkout.services;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;

public class GPSTrackingService extends Service implements GooglePlayServicesClient.ConnectionCallbacks, 
	GooglePlayServicesClient.OnConnectionFailedListener,LocationListener {
	
	/**Name of extra to pass to HFS*/
	public static final String GPS_STATE = "gps state";
    // Milliseconds per second
    private static final int MILLISECONDS_PER_SECOND = 1000;
    // Update frequency in seconds
    public static final int UPDATE_INTERVAL_IN_SECONDS = 5;
    // Update frequency in milliseconds
    private static final long UPDATE_INTERVAL =
            MILLISECONDS_PER_SECOND * UPDATE_INTERVAL_IN_SECONDS;
    // The fastest update frequency, in seconds
    private static final int FASTEST_INTERVAL_IN_SECONDS = 3;
    // A fast frequency ceiling in milliseconds
    private static final long FASTEST_INTERVAL =
            MILLISECONDS_PER_SECOND * FASTEST_INTERVAL_IN_SECONDS;
    
	LocationClient mLocationClient;
	/**holds accuracy and frequency parameters*/
	LocationRequest mLocationRequest;
	Location mCurrentLocation;
	BroadcastReceiver mReceiver;
	LocationManager mLocationManager;
	protected boolean initialGPS;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	/** This is started in WelcomeV2 when start button is clicked */
	@Override
	public void onCreate() {
		IntentFilter aliveFilter = new IntentFilter(Receiver.HFS_ALIVE);
		aliveFilter.addCategory(Intent.CATEGORY_DEFAULT);
		mReceiver = new Receiver();
		
		this.registerReceiver(mReceiver, aliveFilter);
		super.onCreate();
		
		//create the location client and connect it
		mLocationClient = new LocationClient(this,this,this);
		mLocationClient.connect();
		//set up the location request
		setUpLocationRequest();
		initialGPS = true;
		
		 mLocationManager= (LocationManager) 
				getSystemService(Context.LOCATION_SERVICE);
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.w("GPS", "on destroy of service");
		announceGPSAlive(false);
		mLocationClient.removeLocationUpdates(this);
		mLocationClient.disconnect();
		mLocationClient = null;
		this.unregisterReceiver(mReceiver);
	}
	
	public void announceGPSAlive(boolean alive) {
		Log.w("GPS", "announcing GPS alive:  " + alive);
		Intent gps  = new Intent(HandsFreeService.UpdateReceiver.GPS_ENABLED);
		gps.putExtra(GPS_STATE, alive);
		this.sendBroadcast(gps);
	}

	//callback method that receives location updates
	@Override
	public void onLocationChanged(Location location) {
		//get distance between current point and previous point
/*		Log.w("GPS", "currentLocation:  " + mCurrentLocation);
		Log.w("GPS", "changedLocation:  " + location);*/
		//update current location
		String userLocation = Double.toString(mCurrentLocation.getLatitude()) + "," +
						  Double.toString(mCurrentLocation.getLongitude());
		String currentLocation = Double.toString(location.getLatitude()) + "," +
				  Double.toString(location.getLongitude());
		Log.w("GPS", "userLocation:  " + userLocation);
		Log.w("GPS", "currentLocation:  " + currentLocation);
		Log.w("GPS", "provider:  " + location.getProvider());
		if (initialGPS) {
			Log.w("GPS", "initial gps location");
			mCurrentLocation = location;
			initialGPS = false;
			return;
		}
		float[] results = {1};
	    Location.distanceBetween(location.getLatitude(), location.getLongitude(), 
					mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude(), results);
	    
	    //not accurate because this is impossible
		if (results[0] > 1) {
			Log.e("GPS", "results is greater:  " + results[0]);
			Log.w("GPS", "");
		}
	    //update mCurrentLocation
	    mCurrentLocation = location;
/*	    Log.w("GPS", "length of results:  " + results.length);*/
		Log.w("GPS", "accuracy:  " + location.getAccuracy());
	    Log.w("GPS", "results:  " + results[0]);
		Log.w("GPS", "userLocation:  " + userLocation);
		Log.w("GPS", "mCurrentLocation:  " +currentLocation);
		/*Toast.makeText(getApplicationContext(), "location:  " + userLocation, Toast.LENGTH_SHORT).show();*/
	}
	/**Called by Location Services when the request to connect the client finishes successfully*/
	@Override
	public void onConnected(Bundle connectionHint) {
		// TODO Auto-generated method stub
		Toast.makeText(getBaseContext(), "connected", Toast.LENGTH_SHORT).show();
		//initialize the location
		mCurrentLocation = mLocationClient.getLastLocation();
		//start the updates
		mLocationClient.requestLocationUpdates(mLocationRequest, this);
		
	}
	public void setUpLocationRequest() {
		mLocationRequest = LocationRequest.create();
		//use high accuracy
		mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		//set update interval
		mLocationRequest.setInterval(UPDATE_INTERVAL);
		//set the fastest update interval
		mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
/*		//set the minimum displacement between location updates in meters

		mLocationRequest.setSmallestDisplacement(1);*/
	}
	
	/**Called if attempt to Location Services fails*/
	@Override
	public void onConnectionFailed(ConnectionResult result) {
		Log.e("GPS", "connection failed");
		// TODO Auto-generated method stub
	}

	
	/**Called if the connection drops because of an error*/
	@Override
	public void onDisconnected() {
		Log.e("GPS", "service disconnected");
		Toast.makeText(this, "service disconnected", Toast.LENGTH_SHORT).show();
		// TODO Auto-generated method stub
		
	}
	
	public class Receiver extends BroadcastReceiver{
		public static final String HFS_ALIVE = "hfs alive";
		
		@Override
		public void onReceive(Context context, Intent intent) {
			//for initial create, make sure HFS is alive first 
			if (intent.getAction().equals(HFS_ALIVE)) {
				Log.w("GPSTrackingService", "Receive HFS is alive");
				announceGPSAlive(true);
			}
		}
		
	}
}
