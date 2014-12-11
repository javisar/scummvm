package org.scummvm.scummvm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.File;
import java.net.URL;

public class ScummVMActivity extends Activity {

	private View mGame;
	private MouseEmulatorTask task1;
	private MouseEmulatorTask task2;
	
	/* Establish whether the hover events are available */
	private static boolean _hoverAvailable;

	static {
		try {
			MouseHelper.checkHoverAvailable(); // this throws exception if we're on too old version
			_hoverAvailable = true;
		} catch (Throwable t) {
			_hoverAvailable = false;
		}
	}

	private class MyScummVM extends ScummVM {
		private boolean usingSmallScreen() {
			// Multiple screen sizes came in with Android 1.6.  Have
			// to use reflection in order to continue supporting 1.5
			// devices :(
			DisplayMetrics metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);

			try {
				// This 'density' term is very confusing.
				int DENSITY_LOW = metrics.getClass().getField("DENSITY_LOW").getInt(null);
				int densityDpi = metrics.getClass().getField("densityDpi").getInt(metrics);
				return densityDpi <= DENSITY_LOW;
			} catch (Exception e) {
				return false;
			}
		}

		public MyScummVM(SurfaceHolder holder) {
			super(ScummVMActivity.this.getAssets(), holder);

			// Enable ScummVM zoning on 'small' screens.
			// FIXME make this optional for the user
			// disabled for now since it crops too much
			//enableZoning(usingSmallScreen());
		}

		@Override
		protected void getDPI(float[] values) {
			DisplayMetrics metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);

			values[0] = metrics.xdpi;
			values[1] = metrics.ydpi;
		}

		@Override
		protected void displayMessageOnOSD(String msg) {
			Log.i(LOG_TAG, "OSD: " + msg);
			Toast.makeText(ScummVMActivity.this, msg, Toast.LENGTH_LONG).show();
		}

		@Override
		protected void setWindowCaption(final String caption) {
			runOnUiThread(new Runnable() {
					public void run() {
						setTitle(caption);
					}
				});
		}

		@Override
		protected String[] getPluginDirectories() {
			String[] dirs = new String[1];
			dirs[0] = ScummVMApplication.getLastCacheDir().getPath();
			return dirs;
		}

		@Override
		protected void showVirtualKeyboard(final boolean enable) {
			runOnUiThread(new Runnable() {
					public void run() {
						showKeyboard(enable);
					}
				});
		}

		@Override
		protected String[] getSysArchives() {
			return new String[0];
		}

	}

	private MyScummVM _scummvm;
	private ScummVMEvents _events;
	private MouseHelper _mouseHelper;
	private Thread _scummvm_thread;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		setContentView(R.layout.main);
		takeKeyEvents(true);

		// This is a common enough error that we should warn about it
		// explicitly.
		if (!Environment.getExternalStorageDirectory().canRead()) {
			new AlertDialog.Builder(this)
				.setTitle(R.string.no_sdcard_title)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setMessage(R.string.no_sdcard)
				.setNegativeButton(R.string.quit,
									new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog,
															int which) {
											finish();
										}
									})
				.show();

			return;
		}

		SurfaceView main_surface = (SurfaceView)findViewById(R.id.main_surface);

		main_surface.requestFocus();

		getFilesDir().mkdirs();

		// Store savegames on external storage if we can, which means they're
		// world-readable and don't get deleted on uninstall.
		String savePath = Environment.getExternalStorageDirectory() + "/ScummVM/Saves/";
		File saveDir = new File(savePath);
		saveDir.mkdirs();
		if (!saveDir.isDirectory()) {
			// If it doesn't work, resort to the internal app path.
			savePath = getDir("saves", MODE_WORLD_READABLE).getPath();
		}

		// Start ScummVM
		_scummvm = new MyScummVM(main_surface.getHolder());

		_scummvm.setArgs(new String[] {
			"ScummVM",
			"--config=" + getFileStreamPath("scummvmrc").getPath(),
			"--path=" + Environment.getExternalStorageDirectory().getPath(),
			"--savepath=" + savePath
		});

		Log.d(ScummVM.LOG_TAG, "Hover available: " + _hoverAvailable);
		if (_hoverAvailable) {
			_mouseHelper = new MouseHelper(_scummvm);
			_mouseHelper.attach(main_surface);
		}

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1)
		{
			_events = new ScummVMEvents(this, _scummvm, _mouseHelper);
		}
		else
		{
			_events = new ScummVMEventsHoneycomb(this, _scummvm, _mouseHelper);
		}

		main_surface.setOnKeyListener(_events);
		main_surface.setOnTouchListener(_events);
		main_surface.setOnGenericMotionListener(_events);

		mGame = (View) findViewById(R.id.main_surface);
		task1 = new MouseEmulatorTask();
        task1.execute(new Integer[]{5,1});
        task2 = new MouseEmulatorTask();
        task2.execute(new Integer[]{5,2});
		
		_scummvm_thread = new Thread(_scummvm, "ScummVM");
		_scummvm_thread.start();
	}

	@Override
	public void onStart() {
		Log.d(ScummVM.LOG_TAG, "onStart");

		super.onStart();
	}

	@Override
	public void onResume() {
		Log.d(ScummVM.LOG_TAG, "onResume");

		super.onResume();

		if (_scummvm != null)
			_scummvm.setPause(false);
		showMouseCursor(false);
	}

	@Override
	public void onPause() {
		Log.d(ScummVM.LOG_TAG, "onPause");

		super.onPause();

		if (_scummvm != null)
			_scummvm.setPause(true);
		showMouseCursor(true);
	}

	@Override
	public void onStop() {
		Log.d(ScummVM.LOG_TAG, "onStop");

		super.onStop();
	}

	@Override
	public void onDestroy() {
		Log.d(ScummVM.LOG_TAG, "onDestroy");
		task1.cancel(true);
		task2.cancel(true);
		super.onDestroy();

		if (_events != null) {
			_events.sendQuitEvent();

			try {
				// 1s timeout
				_scummvm_thread.join(1000);
			} catch (InterruptedException e) {
				Log.i(ScummVM.LOG_TAG, "Error while joining ScummVM thread", e);
			}

			_scummvm = null;
		}
	}

	@Override
	public boolean onTrackballEvent(MotionEvent e) {
		if (_events != null)
			return _events.onTrackballEvent(e);

		return false;
	}

	/*
	@Override
	public boolean onGenericMotionEvent(final MotionEvent e) {
		if (_events != null)
			return _events.onGenericMotionEvent(e);

		return false;
	}
	*/

	private void showKeyboard(boolean show) {
		SurfaceView main_surface = (SurfaceView)findViewById(R.id.main_surface);
		InputMethodManager imm = (InputMethodManager)
			getSystemService(INPUT_METHOD_SERVICE);

		if (show)
			imm.showSoftInput(main_surface, InputMethodManager.SHOW_IMPLICIT);
		else
			imm.hideSoftInputFromWindow(main_surface.getWindowToken(),
										InputMethodManager.HIDE_IMPLICIT_ONLY);
	}

	private void showMouseCursor(boolean show) {
		/* Currently hiding the system mouse cursor is only
		   supported on OUYA.  If other systems provide similar
		   intents, please add them here as well */
		Intent intent =
			new Intent(show?
				   "tv.ouya.controller.action.SHOW_CURSOR" :
				   "tv.ouya.controller.action.HIDE_CURSOR");
		sendBroadcast(intent);
	}
	
	public class MouseEmulatorTask extends AsyncTask<Integer, Integer, Long> {
		protected void onPreExecute() {
	         //showDialog("Downloaded " + result + " bytes");
	    	 Log.i(ScummVM.LOG_TAG,"MouseEmulatorTask started");
	     }
	
	     protected Long doInBackground(Integer... data) {
	         int delay = data[0];
	         int axis = data[1];
	         long totalSize = 0;
	         
	         while (true) {
		         try {
					Thread.sleep(delay);
					//getRunningApps();
				 } catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				 }
		         if (isCancelled()) break;

		         boolean done[] = new boolean[4];
		         /*
		         if (_events.getActiveCursor(axis,(axis==1 ? 0 : 2))) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN);
		        	 done[0] = true;
		         }
		         if (_events.getActiveCursor(axis,(axis==1 ? 1 : 3))) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_DOWN);
		        	 done[1] = true;
		         }
		         if (_events.getActiveCursor(axis,(axis==1 ? 2 : 0))) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_DOWN);
		        	 done[2] = true;
		         }
		         if (_events.getActiveCursor(axis,(axis==1 ? 3 : 1))) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.ACTION_DOWN);
		        	 done[3] = true;
		         }
		         */
		         
		         if (axis == 1 && _events.getActiveCursor(1,0)) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_DOWN);
		        	 done[0] = true;
		         }
		         if (axis == 1 && _events.getActiveCursor(1,1)) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_DOWN);
		        	 done[1] = true;
		         }
		         if (axis == 2 && _events.getActiveCursor(1,2)) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_DOWN);
		        	 done[2] = true;
		         }
		         if (axis == 2 && _events.getActiveCursor(1,3)) {
		        	 generateKeyEvent(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.ACTION_DOWN);
		        	 done[3] = true;
		         }
	         	 if (done[0]) generateKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.ACTION_UP);
	         	 if (done[1]) generateKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.ACTION_UP);
	         	 if (done[2]) generateKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.ACTION_UP);
	         	 if (done[3]) generateKeyEvent(KeyEvent.KEYCODE_DPAD_UP,KeyEvent.ACTION_UP);	         	 	         
	         }
	        
	         return totalSize;
	     }
	    

	     protected void onPostExecute(Long result) {
	         //showDialog("Downloaded " + result + " bytes");
	    	 Log.i(ScummVM.LOG_TAG,"MouseEmulatorTask finished");
	     }
	}
	

	public void generateKeyEvent(int keyCode,int action) {
		Log.d(ScummVM.LOG_TAG,"keyCode = "+keyCode+", action = "+action);
		/*
		if (KeyEvent.KEYCODE_DPAD_RIGHT == keyCode) System.out.println("Right");
		else if (KeyEvent.KEYCODE_DPAD_LEFT == keyCode) System.out.println("Left");
		else if (KeyEvent.KEYCODE_DPAD_DOWN == keyCode) System.out.println("Down");
		else if (KeyEvent.KEYCODE_DPAD_UP == keyCode) System.out.println("Up");
		*/
		if (mGame != null) {
			BaseInputConnection  mInputConnection = new BaseInputConnection(mGame, true);
			mInputConnection.sendKeyEvent(new KeyEvent(action,keyCode));
			//mInputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,keyCode));
		}
	}
}
