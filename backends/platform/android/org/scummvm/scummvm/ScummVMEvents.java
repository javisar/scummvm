package org.scummvm.scummvm;

import java.util.List;

import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.content.Context;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.KeyCharacterMap;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.GestureDetector;
import android.view.InputDevice.MotionRange;
import android.view.InputDevice;
import android.view.inputmethod.InputMethodManager;

public class ScummVMEvents implements
		android.view.View.OnKeyListener,
		android.view.View.OnTouchListener,
		android.view.View.OnGenericMotionListener,
		android.view.GestureDetector.OnGestureListener,
		android.view.GestureDetector.OnDoubleTapListener {

	public static final int JE_SYS_KEY = 0;
	public static final int JE_KEY = 1;
	public static final int JE_DPAD = 2;
	public static final int JE_DOWN = 3;
	public static final int JE_SCROLL = 4;
	public static final int JE_TAP = 5;
	public static final int JE_DOUBLE_TAP = 6;
	public static final int JE_MULTI = 7;
	public static final int JE_BALL = 8;
	public static final int JE_LMB_DOWN = 9;
	public static final int JE_LMB_UP = 10;
	public static final int JE_RMB_DOWN = 11;
	public static final int JE_RMB_UP = 12;
	public static final int JE_MOUSE_MOVE = 13;
	public static final int JE_GAMEPAD = 14;
	public static final int JE_JOYSTICK = 15;
	public static final int JE_MMB_DOWN = 16;
	public static final int JE_MMB_UP = 17;
	public static final int JE_QUIT = 0x1000;

	final protected Context _context;
	final protected ScummVM _scummvm;
	final protected GestureDetector _gd;
	final protected int _longPress;
	final protected MouseHelper _mouseHelper;

	public ScummVMEvents(Context context, ScummVM scummvm, MouseHelper mouseHelper) {
		_context = context;
		_scummvm = scummvm;
		_mouseHelper = mouseHelper;

		_gd = new GestureDetector(context, this);
		_gd.setOnDoubleTapListener(this);
		_gd.setIsLongpressEnabled(false);
		
		mInputDeviceStates = new SparseArray<InputDeviceState>();

		_longPress = ViewConfiguration.getLongPressTimeout();
	}

	final public void sendQuitEvent() {
		_scummvm.pushEvent(JE_QUIT, 0, 0, 0, 0, 0);
	}

	public boolean onTrackballEvent(MotionEvent e) {
		_scummvm.pushEvent(JE_BALL, e.getAction(),
							(int)(e.getX() * e.getXPrecision() * 100),
							(int)(e.getY() * e.getYPrecision() * 100),
							0, 0);
		return true;
	}
	
	protected boolean[] activeCursor1 = new boolean[4];
	protected boolean[] activeCursor2 = new boolean[4];
	
	private SparseArray<InputDeviceState> mInputDeviceStates;
	
	private InputDeviceState getInputDeviceState(InputEvent event) {
        final int deviceId = event.getDeviceId();
        InputDeviceState state = mInputDeviceStates.get(deviceId);
        if (state == null) {
            final InputDevice device = event.getDevice();
            if (device == null) {
                return null;
            }
            state = new InputDeviceState(device);
            mInputDeviceStates.put(deviceId, state);

            Log.i(ScummVM.LOG_TAG, device.toString());
        }
        return state;
    }

	/*
	public boolean onGenericMotionEvent(MotionEvent e) {
		return false;
	}
	*/

	final static int MSG_MENU_LONG_PRESS = 1;

	final private Handler keyHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_MENU_LONG_PRESS) {
				InputMethodManager imm = (InputMethodManager)
					_context.getSystemService(Context.INPUT_METHOD_SERVICE);

				if (imm != null)
					imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
			}
		}
	};

	// OnKeyListener
	@Override
	final public boolean onKey(View v, int keyCode, KeyEvent e) {
		Log.d(ScummVM.LOG_TAG,"onKey keyCode="+keyCode);
		final int action = e.getAction();

		if (keyCode == 238) {
			// this (undocumented) event is sent when ACTION_HOVER_ENTER or ACTION_HOVER_EXIT occurs
			return false;
		}

		if (keyCode == KeyEvent. KEYCODE_BUTTON_1) {
			((ScummVMActivity)_context).generateKeyEvent(KeyEvent.KEYCODE_SPACE,action);
			return true;
		}
		if (keyCode == KeyEvent. KEYCODE_BUTTON_2) {
			_scummvm.pushEvent(JE_DPAD, action, KeyEvent.KEYCODE_DPAD_CENTER,
					(int)(e.getEventTime() - e.getDownTime()),
					e.getRepeatCount(), 0);
			return true;
		}
		
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (action != KeyEvent.ACTION_UP) {
				// only send event from back button on up event, since down event is sent on right mouse click and
				// cannot be caught (thus rmb click would send escape key first)
				return true;
			}

			if (_mouseHelper != null) {
				if (_mouseHelper.getRmbGuard()) {
					// right mouse button was just clicked which sends an extra back button press
					return true;
				}
			}
		}

		if (e.isSystem()) {
			// filter what we handle
			switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
			case KeyEvent.KEYCODE_MENU:
			case KeyEvent.KEYCODE_CAMERA:
			case KeyEvent.KEYCODE_SEARCH:
				break;

			default:
				return false;
			}

			// no repeats for system keys
			if (e.getRepeatCount() > 0)
				return false;

			// Have to reimplement hold-down-menu-brings-up-softkeybd
			// ourselves, since we are otherwise hijacking the menu key :(
			// See com.android.internal.policy.impl.PhoneWindow.onKeyDownPanel()
			// for the usual Android implementation of this feature.
			if (keyCode == KeyEvent.KEYCODE_MENU) {
				final boolean fired =
					!keyHandler.hasMessages(MSG_MENU_LONG_PRESS);

				keyHandler.removeMessages(MSG_MENU_LONG_PRESS);

				if (action == KeyEvent.ACTION_DOWN) {
					keyHandler.sendMessageDelayed(keyHandler.obtainMessage(
									MSG_MENU_LONG_PRESS), _longPress);
					return true;
				}

				if (fired)
					return true;

				// only send up events of the menu button to the native side
				if (action != KeyEvent.ACTION_UP)
					return true;
			}

			_scummvm.pushEvent(JE_SYS_KEY, action, keyCode, 0, 0, 0);

			return true;
		}

		// sequence of characters
		if (action == KeyEvent.ACTION_MULTIPLE &&
				keyCode == KeyEvent.KEYCODE_UNKNOWN) {
			final KeyCharacterMap m = KeyCharacterMap.load(e.getDeviceId());
			final KeyEvent[] es = m.getEvents(e.getCharacters().toCharArray());

			if (es == null)
				return true;

			for (KeyEvent s : es) {
				_scummvm.pushEvent(JE_KEY, s.getAction(), s.getKeyCode(),
					s.getUnicodeChar() & KeyCharacterMap.COMBINING_ACCENT_MASK,
					s.getMetaState(), s.getRepeatCount());
			}

			return true;
		}

		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_UP:
		case KeyEvent.KEYCODE_DPAD_DOWN:
		case KeyEvent.KEYCODE_DPAD_LEFT:
		case KeyEvent.KEYCODE_DPAD_RIGHT:
		case KeyEvent.KEYCODE_DPAD_CENTER:
			_scummvm.pushEvent(JE_DPAD, action, keyCode,
								(int)(e.getEventTime() - e.getDownTime()),
								e.getRepeatCount(), 0);
			return true;
		case KeyEvent.KEYCODE_BUTTON_A:
		case KeyEvent.KEYCODE_BUTTON_B:
		case KeyEvent.KEYCODE_BUTTON_C:
		case KeyEvent.KEYCODE_BUTTON_X:
		case KeyEvent.KEYCODE_BUTTON_Y:
		case KeyEvent.KEYCODE_BUTTON_Z:
		case KeyEvent.KEYCODE_BUTTON_L1:
		case KeyEvent.KEYCODE_BUTTON_R1:
		case KeyEvent.KEYCODE_BUTTON_L2:
		case KeyEvent.KEYCODE_BUTTON_R2:
		case KeyEvent.KEYCODE_BUTTON_THUMBL:
		case KeyEvent.KEYCODE_BUTTON_THUMBR:
		case KeyEvent.KEYCODE_BUTTON_START:
		case KeyEvent.KEYCODE_BUTTON_SELECT:
		case KeyEvent.KEYCODE_BUTTON_MODE:
			_scummvm.pushEvent(JE_GAMEPAD, action, keyCode,
								(int)(e.getEventTime() - e.getDownTime()),
								e.getRepeatCount(), 0);
			return true;
		}

		_scummvm.pushEvent(JE_KEY, action, keyCode,
					e.getUnicodeChar() & KeyCharacterMap.COMBINING_ACCENT_MASK,
					e.getMetaState(), e.getRepeatCount());

		return true;
	}

	// OnTouchListener
	@Override
	final public boolean onTouch(View v, MotionEvent e) {
		if (_mouseHelper != null) {
			boolean isMouse = MouseHelper.isMouse(e);
			if (isMouse) {
				// mouse button is pressed
				return _mouseHelper.onMouseEvent(e, false);
			}
		}

		final int action = e.getAction();

		// constants from APIv5:
		// (action & ACTION_POINTER_INDEX_MASK) >> ACTION_POINTER_INDEX_SHIFT
		final int pointer = (action & 0xff00) >> 8;

		if (pointer > 0) {
			_scummvm.pushEvent(JE_MULTI, pointer, action & 0xff, // ACTION_MASK
								(int)e.getX(), (int)e.getY(), 0);
			return true;
		}

		return _gd.onTouchEvent(e);
	}
	
	// OnGenericMotionListener
	final public boolean onGenericMotion(View v, MotionEvent event) {
		Log.i(ScummVM.LOG_TAG,"onGenericMotion");
		if ((event.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0
                && event.getAction() == MotionEvent.ACTION_MOVE) {
            // Update device state for visualization and logging.
            InputDeviceState state = getInputDeviceState(event);
            if (state != null && state.onJoystickMotion(event)) {
            	            	
                //mSummaryAdapter.show(state);
                
                if (state.getAxisValue(0)>0.9) {
            		if (activeCursor1[0] == false) activeCursor1[0] = true;
            	}
                else if (state.getAxisValue(0)<0.9) {
                	if (activeCursor1[0] == true) activeCursor1[0] = false;
                }
                
            	if (state.getAxisValue(0)<-0.9) {
            		if (activeCursor1[1] == false) activeCursor1[1] = true;
            	}
            	else if (state.getAxisValue(0)>-0.9) {
            		if (activeCursor1[1] == true) activeCursor1[1] = false;
            	}
            	
            	if (state.getAxisValue(1)>0.9) {            		
            		if (activeCursor1[2] == false) activeCursor1[2] = true;
            	}
            	else if (state.getAxisValue(1)<0.9) {
            		if (activeCursor1[2] == true) activeCursor1[2] = false;
            	}
            	
            	
            	if (state.getAxisValue(1)<-0.9) {
            		if (activeCursor1[3] == false) activeCursor1[3] = true;
            	}
            	else if (state.getAxisValue(1)>-0.9) {
            		if (activeCursor1[3] == true) activeCursor1[3] = false;
            	}
            	
            	//
            	if (state.getAxisValue(3)>0.9) {
            		if (activeCursor2[0] == false) activeCursor2[0] = true;
            	}
                else if (state.getAxisValue(3)<0.9) {
                	if (activeCursor2[0] == true) activeCursor2[0] = false;
                }
                
            	if (state.getAxisValue(3)<-0.9) {
            		if (activeCursor2[1] == false) activeCursor2[1] = true;
            	}
            	else if (state.getAxisValue(3)>-0.9) {
            		if (activeCursor2[1] == true) activeCursor2[1] = false;
            	}
            	
            	if (state.getAxisValue(4)>0.9) {            		
            		if (activeCursor2[2] == false) activeCursor2[2] = true;
            	}
            	else if (state.getAxisValue(4)<0.9) {
            		if (activeCursor2[2] == true) activeCursor2[2] = false;
            	}
            	
            	
            	if (state.getAxisValue(4)<-0.9) {
            		if (activeCursor2[3] == false) activeCursor2[3] = true;
            	}
            	else if (state.getAxisValue(4)>-0.9) {
            		if (activeCursor2[3] == true) activeCursor2[3] = false;
            	}          
            	
            }
        }
		return true;
	}

	// OnGestureListener
	@Override
	final public boolean onDown(MotionEvent e) {
		_scummvm.pushEvent(JE_DOWN, (int)e.getX(), (int)e.getY(), 0, 0, 0);
		return true;
	}

	@Override
	final public boolean onFling(MotionEvent e1, MotionEvent e2,
									float velocityX, float velocityY) {
		//Log.d(ScummVM.LOG_TAG, String.format("onFling: %s -> %s (%.3f %.3f)",
		//										e1.toString(), e2.toString(),
		//										velocityX, velocityY));

		return true;
	}

	@Override
	final public void onLongPress(MotionEvent e) {
		// disabled, interferes with drag&drop
	}

	@Override
	final public boolean onScroll(MotionEvent e1, MotionEvent e2,
									float distanceX, float distanceY) {
		_scummvm.pushEvent(JE_SCROLL, (int)e1.getX(), (int)e1.getY(),
							(int)e2.getX(), (int)e2.getY(), 0);

		return true;
	}

	@Override
	final public void onShowPress(MotionEvent e) {
	}

	@Override
	final public boolean onSingleTapUp(MotionEvent e) {
		_scummvm.pushEvent(JE_TAP, (int)e.getX(), (int)e.getY(),
							(int)(e.getEventTime() - e.getDownTime()), 0, 0);

		return true;
	}

	// OnDoubleTapListener
	@Override
	final public boolean onDoubleTap(MotionEvent e) {
		return true;
	}

	@Override
	final public boolean onDoubleTapEvent(MotionEvent e) {
		_scummvm.pushEvent(JE_DOUBLE_TAP, (int)e.getX(), (int)e.getY(),
							e.getAction(), 0, 0);

		return true;
	}

	@Override
	final public boolean onSingleTapConfirmed(MotionEvent e) {
		return true;
	}
	
	/**
     * Tracks the state of joystick axes and game controller buttons for a particular
     * input device for diagnostic purposes.
     */
    private static class InputDeviceState {
        private final InputDevice mDevice;
        private final int[] mAxes;
        private final float[] mAxisValues;
        private final SparseIntArray mKeys;

        public InputDeviceState(InputDevice device) {
            mDevice = device;

            int numAxes = 0;
            final List<MotionRange> ranges = device.getMotionRanges();
            for (MotionRange range : ranges) {
                if ((range.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
                    numAxes += 1;
                }
            }

            mAxes = new int[numAxes];
            mAxisValues = new float[numAxes];
            int i = 0;
            for (MotionRange range : ranges) {
                if ((range.getSource() & InputDevice.SOURCE_CLASS_JOYSTICK) != 0) {
                    mAxes[i++] = range.getAxis();
                }
            }

            mKeys = new SparseIntArray();
        }

        public InputDevice getDevice() {
            return mDevice;
        }

        public int getAxisCount() {
            return mAxes.length;
        }

        public int getAxis(int axisIndex) {
            return mAxes[axisIndex];
        }

        public float getAxisValue(int axisIndex) {
            return mAxisValues[axisIndex];
        }

        public int getKeyCount() {
            return mKeys.size();
        }

        public int getKeyCode(int keyIndex) {
            return mKeys.keyAt(keyIndex);
        }

        public boolean isKeyPressed(int keyIndex) {
            return mKeys.valueAt(keyIndex) != 0;
        }

        public boolean onKeyDown(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            if (isGameKey(keyCode)) {
                if (event.getRepeatCount() == 0) {
                    final String symbolicName = KeyEvent.keyCodeToString(keyCode);
                    mKeys.put(keyCode, 1);
                    Log.i(ScummVM.LOG_TAG, mDevice.getName() + " - Key Down: " + symbolicName);
                }
                return true;
            }
            return false;
        }

        public boolean onKeyUp(KeyEvent event) {
            final int keyCode = event.getKeyCode();
            if (isGameKey(keyCode)) {
                int index = mKeys.indexOfKey(keyCode);
                if (index >= 0) {
                    final String symbolicName = KeyEvent.keyCodeToString(keyCode);
                    mKeys.put(keyCode, 0);
                    Log.i(ScummVM.LOG_TAG, mDevice.getName() + " - Key Up: " + symbolicName);
                }
                return true;
            }
            return false;
        }

        public boolean onJoystickMotion(MotionEvent event) {
            StringBuilder message = new StringBuilder();
            message.append(mDevice.getName()).append(" - Joystick Motion:\n");

            final int historySize = event.getHistorySize();
            for (int i = 0; i < mAxes.length; i++) {
                final int axis = mAxes[i];
                final float value = event.getAxisValue(axis);
                mAxisValues[i] = value;
                message.append("  ").append(MotionEvent.axisToString(axis)).append(": ");

                // Append all historical values in the batch.
                for (int historyPos = 0; historyPos < historySize; historyPos++) {
                    message.append(event.getHistoricalAxisValue(axis, historyPos));
                    message.append(", ");
                }

                // Append the current value.
                message.append(value);
                message.append("\n");
            }
            //Log.i(TAG, message.toString());
            
            
        	
            
            return true;
        }

        // Check whether this is a key we care about.
        // In a real game, we would probably let the user configure which keys to use
        // instead of hardcoding the keys like this.
        private static boolean isGameKey(int keyCode) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_SPACE:
                    return true;
                default:
                    return KeyEvent.isGamepadButton(keyCode);
            }
        }
    }

	public boolean getActiveCursor(int axis, int i) {
		if (axis == 1) return activeCursor1[i];
		if (axis == 2) return activeCursor2[i];
		return false;
	}
}
