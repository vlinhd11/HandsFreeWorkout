package edu.uhmanoa.android.handsfreeworkout.ui;

import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Chronometer;
import android.widget.Chronometer.OnChronometerTickListener;
import android.widget.TextView;
import edu.uhmanoa.android.handsfreeworkout.R;
import edu.uhmanoa.android.handsfreeworkout.customcomponents.CustomButton;
import edu.uhmanoa.android.handsfreeworkout.customcomponents.CustomTimer;
import edu.uhmanoa.android.handsfreeworkout.customcomponents.DialogActivity;
import edu.uhmanoa.android.handsfreeworkout.services.HandsFreeService;
import edu.uhmanoa.android.handsfreeworkout.utils.Utils;

public class Workout extends Activity implements OnClickListener{

	protected CustomButton mStartButton; 
	protected CustomButton mStopButton;
	protected CustomButton mPauseButton;
	protected TextView mWelcomeMessage;
	protected TextView mCommandText;
	protected CustomTimer mTimer;
	/**App representation of the time in the timer*/
	protected TextView mDisplayClock;
	protected ServiceReceiver mReceiver;
	
	protected Intent mHandsFreeIntent;
	protected Intent mCurrentStateIntent;
	protected Intent mUpdateIntent;
	protected Intent mSleepingIntent;
	/**How much time has passed so far*/
	protected long mAmountTimePassed;
	/**Text displayed on the actual timer */
	protected String mTimerText; 
	protected boolean mInitialCreate; 
	protected boolean mWorkoutRunning;
	protected boolean mWorkoutStopped;
	protected boolean mWorkoutPaused;
/*	protected long mCurrentBase;*/
	protected boolean mGetLagTime;
	protected boolean isSleeping;
	
	/**Keeps track of when action was committed*/
	protected long mTimeOfAction;

	/** Keys for saving and restoring instance data */
	protected static final String SAVED_TIMER_TEXT_VALUE = "timer text";
	protected static final String SAVED_TIME_WHEN_STOPPED_VALUE = "time when stopped";
	protected static final String SAVED_INITIAL_CREATE = "initial create";
	protected static final String START_BUTTON_TEXT = "start button text";
	protected static final String SAVED_UPDATE_TIME = "saved update time";
/*	protected static final String SAVED_CURRENT_BASE = "saved current base";*/
	
	/** Name of the string that identifies what the response should be */
	public static final String RESPONSE_STRING = "response string";

	/**Modes for display clock*/
	protected static final int CLASSIC = 1;
	protected static final int HIPSTER = 2;
	protected static final int HYBRID = 3;

	/**Extra for update */
	public static final String UPDATE_TIME_STRING = "update value string";
	public static final String UPDATE_ACTION = "update action";
	public static final String APP_SLEEPING = "app sleeping";
	/**Booleans to show which part of the app is doing the updating*/
	protected static final boolean UI = true;
	protected static final boolean HF_SERVICE = false;
	
	protected boolean mReceive;
	
	/**
	 * ISSUES TO DEAL WITH STILL:
	 * accidental loud noises
	 * 
	 * maybe for baseline, require user to say a phrase for x amount of seconds.  
	 * if amp is within 5% of that, then it is a command
	 * 
	 * need to stop listening if there is a phone call interruption
	 *
	 */


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.e("Workout", " in on Create");
		super.onCreate(savedInstanceState);
		//Remove title bar
		this.requestWindowFeature(Window.FEATURE_NO_TITLE);

		//inflate the layout
		setContentView(R.layout.workout);
		Utils.setLayoutFont(this, this, Utils.WORKOUT);

		//initialize variables
		mWelcomeMessage = (TextView) findViewById(R.id.welcomeMessage);
		mCommandText = (TextView) findViewById(R.id.command);
		mDisplayClock = (TextView) findViewById(R.id.displayClock);

		mStartButton = (CustomButton) findViewById(R.id.speakButton);
		mStartButton.setOnClickListener(this);

		mStopButton = (CustomButton) findViewById(R.id.stopButton);
		mStopButton.setOnClickListener(this);

		mPauseButton = (CustomButton) findViewById(R.id.pauseButton);
		mPauseButton.setOnClickListener(this);

		mInitialCreate = true;

		//disable button if no recognition service is present
		PackageManager pm = getPackageManager();
		/*queryIntentActivities() = Retrieves all activities that can be performed for
		 * 						    the given intent.  Takes in intent and flags as parameters,
		 * 							which are optional
		 * RecognizerIntent.CONSTANT = Starts an activity that will prompt the user for speech and 
		 * 							   send it through a speech recognizer.  The results will 
		 *                             be returned via activity results (in onActivityResult(int, int, Intent), 
		 *                             if you  start the intent using startActivityForResult(Intent, int))*/
		List<ResolveInfo> activities = pm.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
		if (activities.size() == 0) {
			mStartButton.turnOff();
			mStartButton.setText("Recognizer not present");
		}
		/* tell OS that volume buttons should affect "media" volume, since that's the volume
		used for application */
		setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	/**Handle button clicks */
	@Override
	public void onClick(View view) {
		//starting new stage
		mTimeOfAction = SystemClock.elapsedRealtime();
		Log.w("Workout", "(on click) running:  " + mWorkoutRunning);
		Log.w("Workout", "(on click) paused:  " + mWorkoutPaused);
		Log.w("Workout", "(on click) stopped:  " + mWorkoutStopped);

		switch (view.getId()){
			case(R.id.speakButton):{
				startButtonClicked(UI);
				break;
			}
			case (R.id.stopButton):{
				stopButtonClicked(UI);
				break;
			}
			case (R.id.pauseButton):{
				pauseButtonClicked(UI);
				break;
			}
		}		
	}
	
	/**If UI is the one updating, then also announce*/
	public void startButtonClicked (boolean UI) {
		Log.w("Workout", "start button is pressed");
		mCommandText.setTextColor(Color.GREEN);
		if (mWorkoutStopped) {
			startHandsFreeService();
			//reset initialCreate
			mInitialCreate =true;
			createTimer();
/*			createTimer(0);*/
			announceAction(HandsFreeService.INITIAL_CREATE/* , "" */);
			flashText("Begin");
			//for smoother transition of the buttons
			setStateVariables(true, false, false);
			setButtons();
			mStartButton.setText("Resume");
			//reset
			//mTimerText = "0 seconds";

			return;
		}
		if (mWorkoutPaused) {
			//resumeTimer();
			flashText("Resume");
			announceAction(HandsFreeService.START_BUTTON_RESUME_CLICK/* , "" */);	
			return;
		}
		else{
			//just in case
			announceAction(HandsFreeService.START_BUTTON_CLICK/* , "" */);
		}
	}
	
	public void pauseButtonClicked(boolean UI) {
		Log.w("Workout", "pause button is pressed");
		if (!mWorkoutPaused) {
			//pauseTimer();
			setStateVariables(false, true, false);
			setButtons();
			mCommandText.setText("Pause");
			mCommandText.setTextColor(Color.YELLOW);
			announceAction(HandsFreeService.PAUSE_BUTTON_CLICK/* , "" */);
		}
	}
	
	public void stopButtonClicked(boolean UI) {
		if (!mWorkoutStopped) {
			if (mTimer != null) {
				mTimer.stop();
				mTimer = null;
			}
/*			setStateVariables(false, true, true);
			if (UI) {
				announceAction(HandsFreeService.STOP_BUTTON_CLICK, mUpdateTime);	
			}
			mAmountTimePassed = 0;	*/
			setStateVariables(false, true, true);
			setButtons();
			mStartButton.setText("Start");
			mCommandText.setText("Stop");
			mCommandText.setTextColor(Color.RED);
			announceAction(HandsFreeService.STOP_BUTTON_CLICK/*, mUpdateTime*/);
		}
	}
	/**Sets the text for the display clock.  This is called at every tick of the clock */
	public void setDisplayClock(int mode) {
		if(mTimer == null) {
			Log.w("Workout", "(set display clock) timer is null");
		}

		String time = (String) mTimer.getText();
		mTimerText = time;
		//want the most current time possible
/*		mUpdateTime = Utils.getUpdate(time, mGetLagTime);*/

		//Log.w("workout", "time in setDisplayClock:  " + time);
		if (!time.equals("")) {
			switch(mode) {
				case HIPSTER:{				
				}
				case HYBRID:{
					time = Utils.getPrettyHybridTime(time);	
				}
				case CLASSIC:{				
				}
			}	
			mDisplayClock.setText(time);
		}	
	}

	/** method for persistence .  modes are just for debugging right now*/
	public void setDisplayClock(int mode, String timerText) {
		switch(mode) {
			case HIPSTER:{				
			}
			case HYBRID:{
				mDisplayClock.setText(Utils.getPrettyHybridTime(timerText));	
			}
			case CLASSIC:{			
			}
		}	
	}

	/** Creates the timer and sets the base time */
	protected void createTimer(/*long baseTime*/) {
		Log.w("Workout", "create timer");
		mTimer = (CustomTimer) findViewById(R.id.timer);
		mTimer.setOnChronometerTickListener(new OnChronometerTickListener() {

			@Override
			public void onChronometerTick(Chronometer chronometer) {
				setDisplayClock(HYBRID);
			}
		});

		mTimer.setCorrectBaseAndStart(mWorkoutStopped, mWorkoutRunning,mInitialCreate, mWorkoutPaused, mAmountTimePassed);
		if (!mInitialCreate) {
			setDisplayClock(HYBRID, mTimerText);
			if (!mWorkoutPaused) {
				mCommandText.setText("");
			}
		} 
		mTimerText = (String) mTimer.getText();
		mDisplayClock.setText(Utils.getPrettyHybridTime(mTimerText));
	}
	/** Destroys the timer */
	protected void destroyTimer() {
		//to prevent nullPointer
		if (mTimer != null) {
			mTimer.stop();
			mTimer = null;
		}
	}

	/** Sets up the pause state */
	public void pauseTimer () {
		//save the time when the timer was stopped
		mAmountTimePassed = mTimer.getBase() - SystemClock.elapsedRealtime();
		mTimer.stop();
	}

	/*Resumes the timer */
	public void resumeTimer() {
		//adjust the timer to the correct time
		setStateVariables(true, false, false);
/*		createTimer(0);*/
	}
	/** Called when activity is interrupted, like orientation change */
	@Override
	protected void onPause() {
		Log.w("Workout", "onPause");
		if (mTimer != null) {
			mTimerText = (String) mTimer.getText();
/*			mCurrentBase = mTimer.getBase();*/
			Log.w("Workout", "(on pause) timer Text:  " + mTimerText);
			//for persistence
			if (!mWorkoutPaused) {
				mAmountTimePassed = mTimer.getBase() - SystemClock.elapsedRealtime();
			}
		}
		isSleeping = true;
		announceSleeping();
		//unregister the receiver
		this.unregisterReceiver(mReceiver);
		super.onPause();
	}

	/** Called when user comes back to the activity */
	@Override
	protected void onResume() {
		super.onResume();
/*		IntentFilter handsFree = new IntentFilter(ServiceReceiver.UPDATE);
		handsFree.addCategory(Intent.CATEGORY_DEFAULT);
		IntentFilter getCurrentState = new IntentFilter(ServiceReceiver.GET_CURRENT_STATE);
		getCurrentState.addCategory(Intent.CATEGORY_DEFAULT);*/
		IntentFilter setCurrentState = new IntentFilter(ServiceReceiver.SET_CURRENT_STATE);
		setCurrentState.addCategory(Intent.CATEGORY_DEFAULT);
		
		//register the receiver
		mReceiver = new ServiceReceiver();
/*		this.registerReceiver(mReceiver, handsFree);
		this.registerReceiver(mReceiver, getCurrentState);*/
		this.registerReceiver(mReceiver, setCurrentState);
		
		Log.w("Workout", "on resume");
		//so that can resume, but also considering first run of app
		Log.w("Workout","initial create:  "+ mInitialCreate);
		isSleeping = false;
		announceSleeping();
	}

	/** Save the instance data */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		//store the current values in outState
		Log.w("Workout", "saving instance state");

		outState.putString(SAVED_TIMER_TEXT_VALUE, mTimerText);
		outState.putLong(SAVED_TIME_WHEN_STOPPED_VALUE, mAmountTimePassed);
		outState.putBoolean(SAVED_INITIAL_CREATE, mInitialCreate);
		outState.putString(START_BUTTON_TEXT, (String) mStartButton.getText());
/*		outState.putString(SAVED_UPDATE_TIME, mUpdateTime);*/
/*		outState.putLong(SAVED_CURRENT_BASE, mCurrentBase);*/
/*		Log.w("Workout", "(save) timer text:  " + mTimerText);
		Log.w("Workout", "(save) time when stopped text:  " + mTimeWhenStopped);
		Log.w("Workout", "(save) initial create:  " + mInitialCreate);
		Log.w("Workout", "(save) running workout:  " + mWorkoutRunning);
		Log.w("Workout", "(save) workout paused:  " + mWorkoutPaused);
		Log.w("Workout", "(save) workout stopped:  " + mWorkoutStopped);*/
		super.onSaveInstanceState(outState);
	}

	/** Restore the instance data*/
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.w("Workout", "restoring instance state");
		super.onRestoreInstanceState(savedInstanceState);
		//retrieve the values and set them
		//maybe test for null and use Bundle.containsKey() method later

		mTimerText = savedInstanceState.getString(SAVED_TIMER_TEXT_VALUE);
		mAmountTimePassed = savedInstanceState.getLong(SAVED_TIME_WHEN_STOPPED_VALUE);
		mInitialCreate = savedInstanceState.getBoolean(SAVED_INITIAL_CREATE);
		mStartButton.setText(savedInstanceState.getString(START_BUTTON_TEXT));
/*		mUpdateTime = savedInstanceState.getString(SAVED_UPDATE_TIME);*/
/*		mCurrentBase = savedInstanceState.getLong(SAVED_CURRENT_BASE);*/

		Log.w("Workout", "(restore) timer text:  " + mTimerText);
		Log.w("Workout", "(restore) time when stopped text:  " + mAmountTimePassed);
		Log.w("Workout", "(restore) initial create:  " + mInitialCreate);

	}

	public void startHandsFreeService() {
		Log.w("Workout", "starting hands free service");
		if (mHandsFreeIntent == null) {
			mHandsFreeIntent = new Intent(this, HandsFreeService.class);
		}
		startService(mHandsFreeIntent);
	}
	
	/**Called when a button is pressed*/
	protected void announceAction(int action) {
		Log.w("Workout", "announcing update");
		if (mUpdateIntent == null) {
			mUpdateIntent = new Intent(HandsFreeService.UpdateReceiver.GET_ACTION);
			mUpdateIntent.addCategory(Intent.CATEGORY_DEFAULT);
		}
		if (action == HandsFreeService.INITIAL_CREATE) {
			//send the base time
			mUpdateIntent.putExtra(HandsFreeService.UpdateReceiver.CURRENT_BASE_TIME, mTimer.getBase());
		}
		mUpdateIntent.putExtra(UPDATE_ACTION, action);
		mUpdateIntent.putExtra(HandsFreeService.UpdateReceiver.TIME_OF_ACTION, mTimeOfAction);
/*		mUpdateIntent.putExtra(UPDATE_ACTION, action);
		if (updateTime != "") {
			mUpdateIntent.putExtra(UPDATE_TIME_STRING, updateTime);	
		}*/
		this.sendBroadcast(mUpdateIntent);
	}
	
	protected void announceSleeping() {
		Log.w("Workout", "announce sleeping");
		
		if (mSleepingIntent == null) {
			mSleepingIntent = new Intent(HandsFreeService.UpdateReceiver.SLEEPING);
		}
		mSleepingIntent.putExtra(APP_SLEEPING, isSleeping);
/*		//if it's initial create, also put in the base time
		if (mTimer != null) {
			if (mInitialCreate) {
				mSleepingIntent.putExtra(HandsFreeService.UpdateReceiver.CURRENT_BASE_TIME, mCurrentBase);		
			}
		}*/
		this.sendBroadcast(mSleepingIntent);
	}
	public class ServiceReceiver extends BroadcastReceiver {

/*		public static final String UPDATE = "update";*/
/*		public static final String GET_CURRENT_STATE = "get current state";*/
		public static final String SET_CURRENT_STATE = "set current state";
		
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.w("Workout", "broadcast received");
			Log.w("Workout", "intent action:  " + intent.getAction());
			//if not sleeping anymore
/*			if (intent.getAction().equals(UPDATE)) {
				int action = intent.getIntExtra(HandsFreeService.APPLICATION_STATE, 0);

				//update the UI to reflect the current state
				switch (action) {
					case HandsFreeService.START:{
						startButtonClicked(HF_SERVICE);
						break;
					}
					case HandsFreeService.STOP:{
						announceAction(HandsFreeService.STOP_BUTTON_CLICK, mUpdateTime);
						stopButtonClicked(HF_SERVICE);
						break;
					}
					case HandsFreeService.PAUSE:{
						pauseButtonClicked(HF_SERVICE);
						break;
					}
					case HandsFreeService.UPDATE:{
						if (mWorkoutRunning) {
							mGetLagTime = true;
							mUpdateTime = Utils.getUpdate(mTimerText, mGetLagTime);
							announceAction(HandsFreeService.UPDATE_TIME, mUpdateTime);
							mGetLagTime = false;
						}
						else {
							announceAction(HandsFreeService.UPDATE_TIME, mUpdateTime);
						}
						break;
					}
				}
				Log.w("Workout", "getting update from HFService");
			}*/
			//figure out how to phase this out
/*			if (intent.getAction().equals(GET_CURRENT_STATE)) {
				Log.w("Workout", "receiving get current state");
				//announce the current state, reuse to be more efficient
				if (mCurrentStateIntent == null) {
					mCurrentStateIntent = new Intent(HandsFreeService.UpdateReceiver.RECEIVE_CURRENT_STATE);
					mCurrentStateIntent.addCategory(Intent.CATEGORY_DEFAULT);
				}
				mCurrentStateIntent.putExtra(HandsFreeService.UpdateReceiver.WORKOUT_RUNNING, mWorkoutRunning);
				mCurrentStateIntent.putExtra(HandsFreeService.UpdateReceiver.WORKOUT_PAUSED, mWorkoutPaused);
				mCurrentStateIntent.putExtra(HandsFreeService.UpdateReceiver.WORKOUT_STOPPED, mWorkoutStopped);
				sendBroadcast(mCurrentStateIntent);
				Log.w("Workout", "announcing receiving current state");
			}*/
			
			//When the app is updated 
			if (intent.getAction().equals(SET_CURRENT_STATE)) {
				Log.w("Workout", "setting the new current state");
				mWorkoutRunning = intent.getBooleanExtra(HandsFreeService.UpdateReceiver.WORKOUT_RUNNING, false);
				mWorkoutPaused = intent.getBooleanExtra(HandsFreeService.UpdateReceiver.WORKOUT_PAUSED, false);
				mWorkoutStopped = intent.getBooleanExtra(HandsFreeService.UpdateReceiver.WORKOUT_STOPPED, false);
				
				//need to wait for set current state to come back
/*				Log.w("Workout", "(BR) mWorkoutRunning:  " + mWorkoutRunning);
				Log.w("Workout", "(BR) mWorkoutPaused:  " + mWorkoutPaused);
				Log.w("Workout", "(BR) mWorkoutStopped:  " + mWorkoutStopped);*/
				
				//when app wakes up
				if (!mInitialCreate) {
						//persist the time 
					if (mWorkoutRunning) {
						//this is why it's still persisting
/*						createTimer(mCurrentBase);*/
					}
					//case if workout is paused
					if (mWorkoutPaused) {
						//create timer to display
						mCommandText.setText("Pause");
						mCommandText.setTextColor(Color.YELLOW);
					}

					//special case if workout is stopped
					if(mWorkoutStopped) {
						//create timer to display
						mCommandText.setText("Stop");
						mCommandText.setTextColor(Color.RED);
					}
				}

				//if initial create (set the states so that clock initializes)
				if (mInitialCreate) {
					createTimer();
					announceAction(HandsFreeService.INITIAL_CREATE);
					Log.w("Workout", "initial create of Receiver");
					mInitialCreate = false;
				}		
				setButtons();
			}
		}
	}

	public void onDestroy() {
		super.onDestroy();
		Log.w("Workout", "is finishing?  " + this.isFinishing());
		//if user is absolutely done with the activity
		if (isFinishing()) {
			if (mHandsFreeIntent == null) {
				mHandsFreeIntent = new Intent(this, HandsFreeService.class);
			}
			Log.w("Workout", "intent:  " + mHandsFreeIntent);
			this.stopService(mHandsFreeIntent);
		}
		Log.e("Workout", "Workout onDestroy");
	}

	/*********************** UI HELPER FUNCTIONS ***********************************/
	
	/**Set the buttons according to the state of the applications.  For persistence*/
	public void setButtons () {
		if (mWorkoutRunning) {
			mStartButton.turnOff();
		}
		if (!mWorkoutRunning) {
			mStartButton.turnOn();
		}
		if (mWorkoutPaused) {
			mPauseButton.turnOff();
		}
		if(!mWorkoutPaused) {
			mPauseButton.turnOn();
		}
		if (mWorkoutStopped) {
			mStopButton.turnOff();
		}
		if (!mWorkoutStopped) {
			mStopButton.turnOn();
		}
	}

	public void setStateVariables (boolean running, boolean pause, boolean stop) {
		if (running) {
			mWorkoutRunning = true;
		}
		if (!running) {
			mWorkoutRunning = false;
		}
		if (pause) {
			mWorkoutPaused = true;
		}
		if (!pause) {
			mWorkoutPaused = false;
		}
		if (stop) {
			mWorkoutStopped = true;
		}
		if (!stop) {
			mWorkoutStopped = false;
		}
	}

	/**Flash the command text for a second*/
	public void flashText(String command) {
		mCommandText.setText(command);
		Handler handler = new Handler();
		handler.postDelayed(new Runnable() {

			@Override
			public void run() {
				mCommandText.setText("");
			}
		}, 1000L);
	}
	
	/***********************************DIALOG STUFF *********************************/
	
	public static final int LEAVE_APP = 7;
	
	@Override
	public void onBackPressed() {
		Intent intent = new Intent(this, DialogActivity.class);
		startActivityForResult(intent, LEAVE_APP);
	}

	//callback from dialog
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (requestCode == LEAVE_APP) {
			if (resultCode == RESULT_OK) {
				finish();
			}
		}
	}
}