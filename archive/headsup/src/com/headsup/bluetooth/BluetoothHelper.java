package com.headsup.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class BluetoothHelper {

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	private static final String TAG = "Bluetooth Helper";

	private BluetoothWorker mChatService = null;
	private Handler mHandler;
	private Context mContext;
	public static boolean mLastConnectionState = false;
	private static OnMessageListener mOnMessageListener;

	public BluetoothHelper(Context context) {
		mContext = context;

		IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		mContext.registerReceiver(mReceiver, filter);

		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter.isEnabled()) {
			start();
		} else {
			bluetoothAdapter.enable();
		}
	}

	private void start() {
		if (mHandler == null) mHandler = new MyHandler();
		mChatService = new BluetoothWorker(mContext, mHandler);
		mChatService.start();
	}

	public boolean isConnected() {
		return (mChatService.getState() == BluetoothWorker.STATE_CONNECTED);
	}

	public void sendMessage(String message) {
		if (mChatService.getState() != BluetoothWorker.STATE_CONNECTED) {
			Log.e(TAG, "Cannot send message: not connected");
			return;
		}
		if (message.length() > 0) {
			byte[] send = message.getBytes();
			mChatService.write(send);
		}
	}

	public void setOnMessageListener(OnMessageListener listener) {
		mOnMessageListener = listener;
	}

	public void destroy() {
		mContext.unregisterReceiver(mReceiver);
	}

	private static class MyHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case BluetoothWorker.STATE_CONNECTED:
					if (mOnMessageListener != null && !mLastConnectionState) {
						mLastConnectionState = true;
						mOnMessageListener.onConnected();
					}
					break;
				default:
					if (mOnMessageListener != null && mLastConnectionState) {
						mLastConnectionState = false;
						mOnMessageListener.onDisconnected();
					}
					break;
				}
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				String writeMessage = new String(writeBuf);
				Log.i(TAG, "Message sent: " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				Log.i(TAG, "Message received: " + readMessage);
				if (mOnMessageListener != null) mOnMessageListener.onTextMessage(readMessage);
				break;
			case MESSAGE_DEVICE_NAME:
				String connectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Log.i(TAG, "Connected to " + connectedDeviceName);
				break;
			case MESSAGE_TOAST:
				Log.e(TAG, msg.getData().getString(TOAST));
				break;
			}
		}
	}

	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();

			if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
				switch (state) {
				// case BluetoothAdapter.STATE_TURNING_OFF:
				// break;
				// case BluetoothAdapter.STATE_OFF:
				// break;
				// case BluetoothAdapter.STATE_TURNING_ON:
				// break;
				case BluetoothAdapter.STATE_ON:
					start();
					break;
				}
			}
		}
	};

}
