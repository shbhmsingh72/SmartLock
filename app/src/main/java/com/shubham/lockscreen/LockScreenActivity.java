package com.shubham.lockscreen;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.shubham.lockscreen.ui.GraphicOverlay;
import com.shubham.lockscreen.utils.LockscreenService;
import com.shubham.lockscreen.utils.LockscreenUtils;
import com.shubham.lockscreen.ui.CameraSourcePreview;

import java.io.IOException;

public class LockScreenActivity extends AppCompatActivity implements
		LockscreenUtils.OnLockStatusChangedListener {

	// User-interface
	private Button btnUnlock;
	private static final String TAG = "FaceTracker";
	private CameraSource mCameraSource = null;

	private CameraSourcePreview mPreview;
	private GraphicOverlay mGraphicOverlay;

	private static final int RC_HANDLE_GMS = 9001;
	// permission request codes need to be < 256
	private static final int RC_HANDLE_CAMERA_PERM = 2;
	// Member variables
	private LockscreenUtils mLockscreenUtils;
	// Set appropriate flags to make the screen appear over the keyguard
	@Override
	public void onAttachedToWindow() {
		this.getWindow().setType(
				WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
		this.getWindow().addFlags(
				WindowManager.LayoutParams.FLAG_FULLSCREEN
						| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
						| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
						| WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
						);

		super.onAttachedToWindow();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_lockscreen);
		mPreview = (CameraSourcePreview) findViewById(R.id.preview);
		mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
		init();

		// unlock screen in case of app get killed by system
		if (getIntent() != null && getIntent().hasExtra("kill")
				&& getIntent().getExtras().getInt("kill") == 1) {
			enableKeyguard();
			unlockHomeButton();
		} else {

			try {
				// disable keyguard
				disableKeyguard();

				// lock home button
				lockHomeButton();

				// start service for observing intents
				startService(new Intent(this, LockscreenService.class));

				// listen the events get fired during the call
				StateListener phoneStateListener = new StateListener();
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
				telephonyManager.listen(phoneStateListener,
						PhoneStateListener.LISTEN_CALL_STATE);

			} catch (Exception e) {
			}

		}
	}

	private void init() {
		mLockscreenUtils = new LockscreenUtils();
		btnUnlock = (Button) findViewById(R.id.btnUnlock);

		mPreview = (CameraSourcePreview) findViewById(R.id.preview);
		mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);

		// Check for the camera permission before accessing the camera.  If the
		// permission is not granted yet, request permission.
		int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
		if (rc == PackageManager.PERMISSION_GRANTED) {
			createCameraSource();
		} else {
			requestCameraPermission();
		}
		btnUnlock.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				// unlock home button and then screen on button press
				unlockHomeButton();
			}
		});
	}
	private void requestCameraPermission() {
		Log.w(TAG, "Camera permission is not granted. Requesting permission");

		final String[] permissions = new String[]{Manifest.permission.CAMERA};

		if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
				Manifest.permission.CAMERA)) {
			ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
			return;
		}

		final Activity thisActivity = this;

		View.OnClickListener listener = new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				ActivityCompat.requestPermissions(thisActivity, permissions,
						RC_HANDLE_CAMERA_PERM);
			}
		};

		Snackbar.make(mGraphicOverlay, R.string.permission_camera_rationale,
				Snackbar.LENGTH_INDEFINITE)
				.setAction(R.string.ok, listener)
				.show();
	}

	// Handle events of calls and unlock screen if necessary
	private class StateListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String incomingNumber) {

			super.onCallStateChanged(state, incomingNumber);
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
				unlockHomeButton();
				break;
			case TelephonyManager.CALL_STATE_OFFHOOK:
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				break;
			}
		}
	};
	private void createCameraSource() {

		Context context = getApplicationContext();
		FaceDetector detector = new FaceDetector.Builder(context)
				.setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
				.setProminentFaceOnly(true)
				.build();

		detector.setProcessor(
				new MultiProcessor.Builder<>(new GraphicFaceTrackerFactory())
						.build());

		if (!detector.isOperational()) {
			// Note: The first time that an app using face API is installed on a device, GMS will
			// download a native library to the device in order to do detection.  Usually this
			// completes before the app is run for the first time.  But if that download has not yet
			// completed, then the above call will not detect any faces.
			//
			// isOperational() can be used to check if the required native library is currently
			// available.  The detector will automatically become operational once the library
			// download completes on device.
			Log.w(TAG, "Face detector dependencies are not yet available.");
		}

		mCameraSource = new CameraSource.Builder(context, detector)
				.setRequestedPreviewSize(1080, 480)
				.setFacing(CameraSource.CAMERA_FACING_FRONT)
				.setRequestedFps(30.0f)
				.build();
	}
	/**
	 * Restarts the camera.
	 */
	@Override
	protected void onResume() {
		super.onResume();

		startCameraSource();
	}

	/**
	 * Stops the camera.
	 */
	@Override
	protected void onPause() {
		super.onPause();
		mPreview.stop();
	}

	/**
	 * Releases the resources associated with the camera source, the associated detector, and the
	 * rest of the processing pipeline.
	 */
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (mCameraSource != null) {
			mCameraSource.release();
		}
	}

	/**
	 * Callback for the result from requesting permissions. This method
	 * is invoked for every call on {@link #requestPermissions(String[], int)}.
	 * <p>
	 * <strong>Note:</strong> It is possible that the permissions request interaction
	 * with the user is interrupted. In this case you will receive empty permissions
	 * and results arrays which should be treated as a cancellation.
	 * </p>
	 *
	 * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
	 * @param permissions  The requested permissions. Never null.
	 * @param grantResults The grant results for the corresponding permissions
	 *                     which is either {@link PackageManager#PERMISSION_GRANTED}
	 *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
	 * @see #requestPermissions(String[], int)
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode != RC_HANDLE_CAMERA_PERM) {
			Log.d(TAG, "Got unexpected permission result: " + requestCode);
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
			return;
		}

		if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			Log.d(TAG, "Camera permission granted - initialize the camera source");
			// we have permission, so create the camerasource
			createCameraSource();
			return;
		}

		Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
				" Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				finish();
			}
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Face Tracker sample")
				.setMessage(R.string.no_camera_permission)
				.setPositiveButton(R.string.ok, listener)
				.show();
	}

	//==============================================================================================
	// Camera Source Preview
	//==============================================================================================

	/**
	 * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
	 * (e.g., because onResume was called before the camera source was created), this will be called
	 * again when the camera source is created.
	 */
	private void startCameraSource() {

		// check that the device has play services available.
		int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
				getApplicationContext());
		if (code != ConnectionResult.SUCCESS) {
			Dialog dlg =
					GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
			dlg.show();
		}

		if (mCameraSource != null) {
			try {
				mPreview.start(mCameraSource, mGraphicOverlay);
			} catch (IOException e) {
				Log.e(TAG, "Unable to start camera source.", e);
				mCameraSource.release();
				mCameraSource = null;
			}
		}
	}
	private class GraphicFaceTrackerFactory implements MultiProcessor.Factory<Face> {
		@Override
		public Tracker<Face> create(Face face) {
			return new GraphicFaceTracker(mGraphicOverlay);
		}
	}
	private class GraphicFaceTracker extends Tracker<Face> {
		private GraphicOverlay mOverlay;
		private FaceGraphic mFaceGraphic;

		GraphicFaceTracker(GraphicOverlay overlay) {
			mOverlay = overlay;
			mFaceGraphic = new FaceGraphic(overlay);
		}
		@Override
		public void onNewItem(int faceId, Face item) {
			mFaceGraphic.setId(faceId);
		}

		/**
		 * Update the position/characteristics of the face within the overlay.
		 */
		@Override
		public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
			SharedPreferences prefs = getSharedPreferences(
					"com.shubham.lockscreen", Context.MODE_PRIVATE);
			int choice=prefs.getInt("Choice",0);
            if(choice==0)
            {
                if(face.getIsLeftEyeOpenProbability()<0.4)
                    unlockHomeButton();
            }
            else if(choice==1){
				if(face.getIsRightEyeOpenProbability()<0.4)
					unlockHomeButton();
			}
			else if(choice==2){
				if(face.getIsSmilingProbability()>0.3)
					unlockHomeButton();
			}
			else{
				if(face.getIsRightEyeOpenProbability()==Face.UNCOMPUTED_PROBABILITY||face.getIsLeftEyeOpenProbability()==Face.UNCOMPUTED_PROBABILITY)
					unlockHomeButton();
			}
			mOverlay.add(mFaceGraphic);
			mFaceGraphic.updateFace(face);
		}

		/**
		 * Hide the graphic when the corresponding face was not detected.  This can happen for
		 * intermediate frames temporarily (e.g., if the face was momentarily blocked from
		 * view).
		 */
		@Override
		public void onMissing(FaceDetector.Detections<Face> detectionResults) {
			mOverlay.remove(mFaceGraphic);
		}

		/**
		 * Called when the face is assumed to be gone for good. Remove the graphic annotation from
		 * the overlay.
		 */
		@Override
		public void onDone() {
			mOverlay.remove(mFaceGraphic);
		}
	}
	// Don't finish Activity on Back press
	@Override
	public void onBackPressed() {
		return;
	}

	// Handle button clicks
	@Override
	public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {

		if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
				|| (keyCode == KeyEvent.KEYCODE_POWER)
				|| (keyCode == KeyEvent.KEYCODE_VOLUME_UP)
				|| (keyCode == KeyEvent.KEYCODE_CAMERA)) {
			return true;
		}
		if ((keyCode == KeyEvent.KEYCODE_HOME)) {

			return true;
		}

		return false;

	}

	// handle the key press events here itself
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP
				|| (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN)
				|| (event.getKeyCode() == KeyEvent.KEYCODE_POWER)) {
			return false;
		}
		if ((event.getKeyCode() == KeyEvent.KEYCODE_HOME)) {

			return true;
		}
		return false;
	}

	// Lock home button
	public void lockHomeButton() {
		mLockscreenUtils.lock(LockScreenActivity.this);
	}

	// Unlock home button and wait for its callback
	public void unlockHomeButton() {
		mLockscreenUtils.unlock();
	}

	// Simply unlock device when home button is successfully unlocked
	@Override
	public void onLockStatusChanged(boolean isLocked) {
		if (!isLocked) {
			unlockDevice();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		unlockHomeButton();
	}

	@SuppressWarnings("deprecation")
	private void disableKeyguard() {
		KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
		KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
		mKL.disableKeyguard();
	}

	@SuppressWarnings("deprecation")
	private void enableKeyguard() {
		KeyguardManager mKM = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
		KeyguardManager.KeyguardLock mKL = mKM.newKeyguardLock("IN");
		mKL.reenableKeyguard();
	}
	
	//Simply unlock device by finishing the activity
	private void unlockDevice()
	{
		finish();
	}

}