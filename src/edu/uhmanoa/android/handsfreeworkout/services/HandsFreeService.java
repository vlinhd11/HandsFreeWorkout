package edu.uhmanoa.android.handsfreeworkout.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.UtteranceProgressListener;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.util.Log;
import edu.uhmanoa.android.handsfreeworkout.R;
import edu.uhmanoa.android.handsfreeworkout.ui.Workout;
import edu.uhmanoa.android.handsfreeworkout.utils.ServiceTimeManager;
import edu.uhmanoa.android.handsfreeworkout.utils.Utils;

public class HandsFreeService extends Service implements OnInitListener{
	
	protected SpeechRecognizer mSpeechRec; 
	protected MediaRecorder mRecorder; //recorder that listens for a command
	protected Handler mHandler;
	protected ServiceTimeManager mTimeManager;
	protected UpdateReceiver mReceiver;
	protected RecognitionListener mSpeechRecListen; 
	
	/**Intents*/
	protected Intent mRecognizerIntent;
	protected Intent mGetUpdateIntent;
	protected Intent mGetCurrentStateIntent;
	
	protected boolean mSpeechRecAlive;
	protected boolean mFinished; //if speech recognition heard something
	protected boolean mCheckBaseline;
	protected int mBaselineAmp;
	protected int mCounter;
	protected String mUpdateTime;
	protected boolean mSleeping;
	protected boolean mStop;
	protected boolean mListening;

	/* Value to determine what needs to be said */
	protected int mResponse;
	
	/** The time frequency at which check if speech recognizer is still alive */
	protected static final long UPDATE_FREQUENCY =4000L;
	
	/** The time frequency at which check the max amplitude of the recording */
	protected static final long CHECK_FREQUENCY = 350L; 
	
	/**Name of the current application state to be passed to Workout*/
	public static final String APPLICATION_STATE = "application state";
	
	/**Possible application states*/
	public static final int STOP = 1;
	public static final int START = 2;
	public static final int PAUSE = 3;
	public static final int ALREADY_PAUSED = 4;
	
	/**Commands that don't require announcing to Workout*/
	public static final int UPDATE = 5;
	public static final int COMMAND_NOT_RECOGNIZED_RESULT = 6;
	
	/**Possible action values from Workout*/
	public static final int START_BUTTON_CLICK = 7;
	public static final int STOP_BUTTON_CLICK = 8;
	public static final int PAUSE_BUTTON_CLICK = 9;
	public static final int UPDATE_TIME = 10;
	public static final int INITIAL_CREATE = 11;
	public static final int BEGIN_WORKOUT = 12;
	public static final int START_BUTTON_RESUME_CLICK = 13;
	public static final int NOTHING_SAID = 14;

		
	/**Current state of the application*/
	protected boolean mWorkoutRunning;
	protected boolean mWorkoutPaused;
	protected boolean mWorkoutStopped;
	
	/**Command that was said*/
	protected int command;
	
	//State the app is in 
	protected static final boolean OFFLINE = true;
	protected static final boolean ONLINE = false;
	
	/**Variables for TTS*/
	protected  TextToSpeech mTTS;
	protected static Context mContext;
	protected static HashMap <String, String> mReplies = new HashMap<String, String>();
	protected static final String STRING = "just need one";
	protected int action;
	
	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	@Override
	public void onCreate() {
		Log.w("HFS", "on create of service");
		IntentFilter getUpdateFilter = new IntentFilter (UpdateReceiver.GET_UPDATE);
		getUpdateFilter.addCategory(Intent.CATEGORY_DEFAULT);
		IntentFilter currentStateFilter = new IntentFilter(UpdateReceiver.RECEIVE_CURRENT_STATE);
		currentStateFilter.addCategory(Intent.CATEGORY_DEFAULT);
		IntentFilter sleepingFilter = new IntentFilter(UpdateReceiver.SLEEPING);
		sleepingFilter.addCategory(Intent.CATEGORY_DEFAULT);
	
		mReceiver = new UpdateReceiver();
		this.registerReceiver(mReceiver, getUpdateFilter);
		this.registerReceiver(mReceiver, currentStateFilter);
		this.registerReceiver(mReceiver, sleepingFilter);
		
		mSpeechRecListen = new RecognitionListener() {

			/** Methods to override android.speech */
			@Override
			public void onBeginningOfSpeech() {
				mSpeechRecAlive = true;
			}

			@Override
			public void onBufferReceived(byte[] arg0) {
			}

			@Override
			public void onEndOfSpeech() {
			}

			@Override
			public void onError(int error) {
				mSpeechRecAlive = false;	
			}
			@Override
			public void onEvent(int arg0, Bundle arg1) {
				//don't need
			}

			@Override
			public void onPartialResults(Bundle arg0) {
				//don't need
			}

			@Override
			public void onReadyForSpeech(Bundle arg0) {
			}

			@Override
			public void onResults(Bundle bundle) {
				Log.w("HFS", "on Results");
				mCounter = 0;
				mSpeechRecAlive = true;	
				mFinished = true;
				//heard result so stop listening for words
				stopAndDestroyVoiceRec();
				//handle the output
				ArrayList<String> results = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
				command = COMMAND_NOT_RECOGNIZED_RESULT;
				if (results.contains("start")) {
					command = START;
				}
				if (results.contains("pause")) {
					command = PAUSE;
				}
				if (results.contains("stop")) {
					command = STOP;
				}
				if (results.contains("update")) {
					command = UPDATE;
				}
				if (command != COMMAND_NOT_RECOGNIZED_RESULT) {
					if (!mSleeping) {
						announceGetCurrentState();
					}
					if (mSleeping) {
						updateBasedOnUI(OFFLINE);
					}
				}
				else {	
					getFeedback(COMMAND_NOT_RECOGNIZED_RESULT);
				}
			}

			@Override
			public void onRmsChanged(float arg0) {
				//don't need		
			}
		};
		mHandler = new Handler();
	}
	/** Want service to continue running until it is explicitly stopped*/
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	/* Start voice recognition */
	public void startVoiceRec() {
		//starting new stage
		stopListening();
		//for persistence
		Log.w("HFS","Starting voice rec");
		mSpeechRec = SpeechRecognizer.createSpeechRecognizer(this);
		
		mSpeechRec.setRecognitionListener(mSpeechRecListen);
		if (mRecognizerIntent == null) {
			mRecognizerIntent = new Intent (RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			//this extra is required
			mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		}
		mSpeechRec.startListening(mRecognizerIntent);
	}
	/**Simulates accessing the UI.  Responds and updates state variables and time accordingly*/
	private void updateBasedOnUI(boolean offline) {		
		switch (command) {
		//app is not listening if in mWorkoutStopped state
			case START:{
				if (mWorkoutRunning) {
					getFeedback(START_BUTTON_CLICK);
				}
				if (mWorkoutPaused) { //resume after pause
					getFeedback(START_BUTTON_RESUME_CLICK);
					if (offline) {
						setStateVariables(true, false, false);
						mTimeManager.setBaseTime(SystemClock.elapsedRealtime());
					}
					else{
						announceGetUpdate(START);
					}
				}
				break;
			}
			case STOP:{
				if (offline) {
					if (mWorkoutRunning) { //stopping the workout
						mTimeManager.addSectionOfTime();
					}
					//calculate how much total time has passed and respond
					mUpdateTime = ServiceTimeManager.getTotalTimeAndFormat();
					getFeedback(STOP_BUTTON_CLICK);
					mTimeManager.resetTotalTime();
					setStateVariables(false, true, true);
				}
				
				else{
					announceGetUpdate(STOP);
				}
				break;
			}
			case PAUSE:{
				if (mWorkoutRunning) { //pausing the workout
					getFeedback(PAUSE_BUTTON_CLICK);
					if (offline) {
						setStateVariables(false, true, false);
						mTimeManager.addSectionOfTime();
					}
					else {
						announceGetUpdate(PAUSE);
					}
					break;
				}
				if (mWorkoutPaused) {
					getFeedback(ALREADY_PAUSED);
				}				
				break;
			}
			case UPDATE:{
				//get the total time passed and respond
				if (offline) {
					if (mWorkoutRunning) {
						mUpdateTime = ServiceTimeManager.getUpdateTimeFromRaw(mTimeManager.getUpdateTime());
					}
					else {
						mUpdateTime = ServiceTimeManager.getTotalTimeAndFormat();
					}
					getFeedback(UPDATE_TIME);
				}
				else {
					announceGetUpdate(UPDATE);
				}
				break;
			}
			default:{
				getFeedback(COMMAND_NOT_RECOGNIZED_RESULT);
			}
		}
	}
	/* Stop speech recognition */
	public void stopAndDestroyVoiceRec() {
		mHandler.removeCallbacks(checkSpeechRec);
		mSpeechRec.destroy();
		mSpeechRec = null;
	}

	/** This periodically checks to see if there was input into speechRecognizer */
	Runnable checkSpeechRec = new Runnable() {

		@Override
		public void run() {
			Log.w("HFS", "counter:  " + mCounter);
			if (mSpeechRecAlive) {
				Log.w("HFS", "speechRec alive");
				if (mFinished) {
					mSpeechRecAlive = false;
					//reset mFinished
					mFinished = false;
				}
			}

			else{
				Log.w("HFS", "speechRec is dead");
				if (mSpeechRec != null) {
					mSpeechRec.destroy();	
				}
				startVoiceRec();
			}
			
			if (mCounter >= 3) {
				stopAndDestroyVoiceRec();
				Log.w("HFS", "SILENCE");
				getFeedback(NOTHING_SAID);
				mCounter = 0;
				return;
			}
			if (mCounter < 3) {
				mHandler.postDelayed(checkSpeechRec, UPDATE_FREQUENCY);			
			}
			mCounter +=1;
		}
	};

	Runnable checkMaxAmp = new Runnable() {
		/**Sets the baseline max amplitude for the first 10 seconds, and for every 1/3 second
		 * after that, checks the max amplitude.  If it hears a sound that has a higher 
		 * amplitude than the one found in the baseline, launches the voice recognizer
		 * activity 
		 * Cases to consider:  panting (like when you're tired)
		 * 					   unnecessary talking*/
		@Override
		public void run() {
			//if there is no recorder (like when launch speech recognizer) don't do anything
			if (mRecorder == null) {
				return;
			}
			else {
				Long frequency;
				//for first ten seconds do an average
				int maxAmp = mRecorder.getMaxAmplitude();
				mBaselineAmp = 8000;
				//get number of digits
				int digitsCurrent = Utils.getDigits(maxAmp);
				int digitsBaseline = Utils.getDigits(mBaselineAmp);

				//if the difference is one or greater, then it is a command
				int difference = digitsCurrent - digitsBaseline;
				if (difference > 0) {
					Log.e("HFS", "spoke at volume:  " + maxAmp);
					//launch the speech recognizer and stop listening
					startVoiceRec();
					checkSpeechRec.run();
					return;
				}
				frequency = CHECK_FREQUENCY;
				mHandler.postDelayed(checkMaxAmp, frequency);
			}
		}
	};

	/* Start listening for commands */
	public void startListening() {
		destroyTTS();
		Log.w("HFS", "start listening");
		
		mListening = true;
		mRecorder = new MediaRecorder();
		mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
		mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
		String name = Utils.getOutputMediaFilePath();
		mRecorder.setOutputFile(name);
		mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

		try {
			mRecorder.prepare();
			mRecorder.start();
			checkMaxAmp.run();

		} catch (Error e) {
			Log.w("HFS", e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			Log.w("HFS", e.getMessage());
			e.printStackTrace();
		}
	}
	/* Stop listening for commands */
	public void stopListening() {
		mListening = false;
		Log.w("HFS", "Stop listening");
		mHandler.removeCallbacks(checkMaxAmp);
		//just to be safe
		mHandler.removeCallbacks(checkSpeechRec);
		stopAndDestroyRecorder();
		//if doing voice rec, destroy it
		if (mSpeechRec != null) {
			stopAndDestroyVoiceRec();
		}
	}
	public void destroySpeechRecognizer() {
		if (mSpeechRec != null) {
			mSpeechRec.destroy();
			mSpeechRec = null;
		}
	}
	/**Stops and destroys the recorder */
	public void stopAndDestroyRecorder() {
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder.reset();
			mRecorder.release();
			mRecorder = null;
		}
	}
	@Override
	public void onDestroy() {
		Log.w("HFS", "on destroy of service");
		mHandler.removeCallbacks(checkSpeechRec);
		destroySpeechRecognizer();
		stopAndDestroyRecorder();
		destroyTTS();
		this.unregisterReceiver(mReceiver);
	}
	
	/**Updates the UI and gets current time in app for updates*/
	protected void announceGetUpdate(int action) {
		if (mGetUpdateIntent == null) {
			mGetUpdateIntent = new Intent(Workout.ServiceReceiver.UPDATE);
			mGetUpdateIntent.addCategory(Intent.CATEGORY_DEFAULT);
		}
		mGetUpdateIntent.putExtra(APPLICATION_STATE, action);
		this.sendBroadcast(mGetUpdateIntent);
		Log.w("HFS", "announcing update");
	}
	
	/**Sends a broadcast to get the current state from Workout.  Only called if command is
	 * recognized in VoiceRec */
	protected void announceGetCurrentState() {
		Log.w("HFS", "sleeping:  " + mSleeping);
		if (mGetCurrentStateIntent == null) {
			mGetCurrentStateIntent = new Intent(Workout.ServiceReceiver.GET_CURRENT_STATE);			
		}
		this.sendBroadcast(mGetCurrentStateIntent);			

		Log.w("HFS", "announcing get current state");
	}
	
	public class UpdateReceiver extends BroadcastReceiver {
		
		public static final String GET_UPDATE = "get update";
		public static final String RECEIVE_CURRENT_STATE = "receive current state";
		public static final String SLEEPING = "sleeping";
		
		/**Names of the current states and base time*/
		public static final String WORKOUT_RUNNING = "workout running";
		public static final String WORKOUT_PAUSED = "workout paused";
		public static final String WORKOUT_STOPPED = "Workout stopped";
		public static final String CURRENT_BASE_TIME = "current base time";
		
		/**Denotes listening state of service*/
		public static final String START_OR_NOT = "start or not";
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String type = intent.getAction();
			/**Sent in onPause of Workout*/
			if (type.equals(SLEEPING)) {
				mSleeping = intent.getBooleanExtra(Workout.APP_SLEEPING, false);
				
				//update variables if sleeping
				if (mSleeping) {
					//run service as foreground so less likely to get killed
					startForeground(1234, buildNotification(context));
					
					//states are now updated
					mWorkoutRunning = intent.getBooleanExtra(WORKOUT_RUNNING, false);
					mWorkoutPaused = intent.getBooleanExtra(WORKOUT_PAUSED, false);
					mWorkoutStopped = intent.getBooleanExtra(WORKOUT_STOPPED, false);

					//manage the time
					if (intent.getLongExtra(CURRENT_BASE_TIME,0) != 0){
						mTimeManager = new ServiceTimeManager(intent.getLongExtra(CURRENT_BASE_TIME, 0));
					}
					else {
						//idk....gotta think it through still
					}
					//just in case
					if (!mWorkoutStopped) {
						if (!mListening) {
							startListening();
						}
					}
				}
				else {
					mTimeManager = null;
					//stop running in the foreground
					stopForeground(true);
				}
				Log.e("HFS", "APP IS SLEEPING:  " + mSleeping);
				
			}
			if (type.equals(GET_UPDATE)) {
				Log.w("HFS", "broadcast received: " + intent.getAction());

				//get the update action and the update string
				int action = intent.getIntExtra(Workout.UPDATE_ACTION, 0);
				String updateTime = intent.getStringExtra(Workout.UPDATE_TIME_STRING);
				
				if (updateTime != "") {
					//set this as updateText
					mUpdateTime = updateTime;
				}
				getFeedback(action);

			}
			/**This only happens when a command is recognized in VoiceRec*/
			if (type.equals(RECEIVE_CURRENT_STATE)) {
				Log.w("HFS", "receiving the current state");
				mWorkoutRunning = intent.getBooleanExtra(WORKOUT_RUNNING, false);
				mWorkoutPaused = intent.getBooleanExtra(WORKOUT_PAUSED, false);
				mWorkoutStopped = intent.getBooleanExtra(WORKOUT_STOPPED, false);
				Log.w("HFS","workout running:  " + mWorkoutRunning);
				Log.w("HFS","workout paused:  " + mWorkoutPaused);
				Log.w("HFS","workout stopped:  " + mWorkoutStopped);
				updateBasedOnUI(ONLINE);
			}
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
	
	protected void getFeedback(int toDo) {
		/*initialize TTS (don't need to check if it is installed because for OS 4.1 and up
		it is already included.  But maybe do checks here for older versions later */
		action = toDo;
		stopListening();
		mTTS = new TextToSpeech(this,this);
		mTTS.setOnUtteranceProgressListener(new UtteranceProgressListener() {

			@Override
			public void onStart(String arg0) {
				//don't need
			}

			@Override
			public void onError(String arg0) {
			}
			
			@Override
			public void onDone(String utteranceID) {
				Log.e("AVFB", "YES!!!!");
				Log.w("HFS", "stop:  " + mStop);
				if (!mStop) {
					mStop = false;
					startListening();
				}
			}
		});
	}
	
	@Override
	public void onInit(int status) {
		if (status == TextToSpeech.SUCCESS) {
			mTTS.setLanguage(Locale.US);
		}
		//just in case
		if (mTTS == null) {
			getFeedback(action);
		}
		mReplies.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, STRING);
		
		switch(action) {
			case START_BUTTON_CLICK:{
				mTTS.speak("workout is in progress", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;
			}
			case STOP_BUTTON_CLICK:{
				mStop = true;
				mTTS.speak("stopping workout.  Workout duration:  "+ mUpdateTime, TextToSpeech.QUEUE_FLUSH, mReplies);
				break;
			}
			case PAUSE_BUTTON_CLICK:{
				mTTS.speak("pausing workout", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;
			}
			case UPDATE_TIME:{	
				mTTS.speak(mUpdateTime + "have elapsed", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;
			}
			case INITIAL_CREATE:{
				//special case
				mStop = false;
				mTTS.speak("begin workout", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;	
			}
			case START_BUTTON_RESUME_CLICK:{
				mTTS.speak("continuing workout", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;	
			}
			case COMMAND_NOT_RECOGNIZED_RESULT:{
				mTTS.speak("command not recognized", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;	
			}
			case ALREADY_PAUSED:{
				mTTS.speak("workout is already paused", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;	
			}
			case NOTHING_SAID:{
				mTTS.speak("silence", TextToSpeech.QUEUE_FLUSH, mReplies);
				break;	
			}
		}
	}
	/** Turn off and destroy TTS */
	protected void destroyTTS() {
		if (mTTS != null) {
			mTTS.stop();
			mTTS.shutdown();
			mTTS = null;
		}
	}
	
	protected Notification buildNotification(Context context) {
		Resources resources = context.getResources();
		String title = resources.getString(R.string.programName);
		String text = resources.getString(R.string.notificationText);
		String tickerText = resources.getString(R.string.notificationTicker);
		Intent handsFreeServiceIntent = new Intent(context, Workout.class);
		PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, handsFreeServiceIntent, 0);
		Builder notificationBuilder = new NotificationCompat.Builder(context)
																	.setContentTitle(title)
																	.setContentText(text)
																	.setTicker(tickerText);
		//supply a PendingIntent to be sent when the notification is clicked
		notificationBuilder.setContentIntent(pendingIntent);
		//make the notification disappear from the notification area when it's pressed
		notificationBuilder.setAutoCancel(true);
		//get a reference to NotificationManager
		Notification notification = notificationBuilder.build();
		return notification;
	}
	
	
}
