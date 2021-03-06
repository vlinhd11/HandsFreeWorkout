package edu.uhmanoa.android.handsfreeworkout.customcomponents;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Chronometer;

public class CustomTimer extends Chronometer{
	
	public CustomTimer(Context context) {
		super(context);
	}
	
	public CustomTimer(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	
	/** Sets the correct base for the timer and runs it */
	public void setCorrectBaseAndStart(boolean workoutStopped, boolean workoutRunning, boolean initialCreate, boolean mWorkoutPaused, long amountTimePassed) {
/*		Log.w("CustomTimer", "initialCreate:  " + initialCreate);*/
		Log.w("CustomTimer", "workoutPaused:  " + mWorkoutPaused);
/*		Log.w("CustomTimer", "timeWhenStopped:  " + timeWhenStopped);*/

		Log.w("CustomTimer", "workout stopped:  " + workoutStopped);
		Log.w("CustomTimer", "workout running:  " + workoutRunning);
		//if initial start up
		if (initialCreate) {
			Log.w("CustomTimer", "in initial create");
			//use "this" keyword because modifying the current object
			this.setBase(SystemClock.elapsedRealtime());
			this.start();
		}
		else {
			if (workoutRunning) {
				//amountTimePassed = base - currentTime
				Log.w("CustomTimer", "in workoutRunning");
				this.setBase(SystemClock.elapsedRealtime() + amountTimePassed);
				this.start();
			}
			if (workoutStopped) {
				Log.w("CustomTimer", "in workoutStopped");
				this.setBase(SystemClock.elapsedRealtime() + amountTimePassed);			
			}
			if (mWorkoutPaused) {
				Log.w("CustomTimer", "in workoutPaused");
				this.setBase(SystemClock.elapsedRealtime() + amountTimePassed);
			}
		}
	}
}
