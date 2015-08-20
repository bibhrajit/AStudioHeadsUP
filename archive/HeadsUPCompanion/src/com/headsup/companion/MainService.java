package com.headsup.companion;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.ContactsContract;
import android.provider.ContactsContract.RawContacts;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;

public class MainService extends Service {

	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	private static final String TAG = "HeadsUP Companion";
	public static final String BROADCAST_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
	public static final String BROADCAST_ACTION = "com.headsup.companion.ServiceReceiver";
	public static final String BROADCAST_ADDRESS = "address";
	protected static final String BROADCAST_COMMAND = "command";
	protected static final int BROADCAST_CONNECT = 1;
	protected static final int BROADCAST_DISCONNECT = 2;
	private static LocationManager mLocationManager;

	private static BluetoothHelper mChatService = null;
	private Handler mHandler;

	@Override
	public void onCreate() {
		Log.i(TAG, "Starting service");

		if (mHandler == null) mHandler = new MyHandler(MainService.this);

		IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_ACTION);
		filter.addAction(BROADCAST_SMS_RECEIVED);
		registerReceiver(mBroadcastReceiver, filter);

		if (mLocationManager == null) mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		mChatService = new BluetoothHelper(MainService.this, mHandler);

		TelephonyManager TelephonyMgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		TelephonyMgr.listen(new MyPhoneStateListener(), PhoneStateListener.LISTEN_CALL_STATE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		sendStateBroadcast(this, mChatService.getState());
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mBroadcastReceiver);
	}

	private void connectDevice(String address) {
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
		mChatService.connect(device, false);
	}

	private static void btSendText(String message) {
		if (mChatService.getState() != BluetoothHelper.STATE_CONNECTED) {
			Log.e(TAG, "Cannot send message: not connected");
			return;
		}
		if (message.length() > 0) {
			mChatService.write(message.getBytes());
		}
	}

	private static void sendStateBroadcast(Context context, int state) {
		Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
		intent.putExtra(MainActivity.BROADCAST_MESSAGE, state);
		context.sendBroadcast(intent);
	}

	private static void handleCommand(Context context, String c) {
		try {
			JSONObject jsonObject = new JSONObject(c);
			String command = jsonObject.getString("command");
			if (!TextUtils.isEmpty(command)) {
				if (command.equals("call")) {
					Contact contact = getAndSendContact(context, jsonObject);
					if (contact != null) {
						sendPhoto(context, contact.id);

						Intent intent = new Intent(Intent.ACTION_CALL);
						intent.setData(Uri.parse("tel:" + contact.phone));
						intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
						context.startActivity(intent);
					}
				} else if (command.equals("request_contact")) {
					Contact contact = getAndSendContact(context, jsonObject);
					if (contact != null) {
						sendPhoto(context, contact.id);
					}
				} else if (command.equals("send_message")) {
					String phone = jsonObject.getString("phone");
					String text = jsonObject.getString("text");
					if (phone != null && text != null) {
						SmsManager sms = SmsManager.getDefault();
						sms.sendTextMessage(phone, null, text, null, null);
						JSONObject jsonObject2 = new JSONObject();
						jsonObject2.put("command", "message_sent");
						if (jsonObject.has("name")) jsonObject2.put("name", jsonObject.getString("name"));
						btSendText(jsonObject2.toString());
						// sendMessage("message_sent");
					}
				} else if (command.equals("start_gps")) {
					mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000, 10, mLocationListener);
					mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000, 10, mLocationListener);
				} else if (command.equals("stop_gps")) {
					mLocationManager.removeUpdates(mLocationListener);
				} else if (command.equals("accept_call")) {
					Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
					intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
					context.sendOrderedBroadcast(intent, "android.permission.CALL_PRIVILEGED");
				} else if (command.equals("decline_call")) {
					Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
					intent.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
					context.sendOrderedBroadcast(intent, "android.permission.CALL_PRIVILEGED");

					try {
						TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
						Class<?> classTelephony = Class.forName(telephonyManager.getClass().getName());
						Method methodGetITelephony = classTelephony.getDeclaredMethod("getITelephony");
						methodGetITelephony.setAccessible(true);
						Object telephonyInterface = methodGetITelephony.invoke(telephonyManager);
						Class<?> telephonyInterfaceClass = Class.forName(telephonyInterface.getClass().getName());
						Method methodEndCall = telephonyInterfaceClass.getDeclaredMethod("endCall");
						methodEndCall.invoke(telephonyInterface);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private static Contact getAndSendContact(Context context, JSONObject jsonObject) throws JSONException {
		String error = null;
		String name = jsonObject.getString("name");
		if (name != null) {
			Contact contact = searchContactByName(getContacts(context), name);
			if (contact != null) {
				try {
					JSONObject jsonObject2 = new JSONObject();
					jsonObject2.put("command", "contact");
					jsonObject2.put("id", contact.id);
					jsonObject2.put("name", contact.name);
					jsonObject2.put("phone", contact.phone);
					btSendText(jsonObject2.toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}

				sendPhoto(context, contact.id);

				return contact;
			} else error = "Here is no phone number for " + name;
		} else error = "Name is unknown";
		if (error != null) {
			try {
				JSONObject jsonObject2 = new JSONObject();
				jsonObject2.put("command", "contact_error");
				jsonObject2.put("error", error);
				btSendText(jsonObject2.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return null;
	}

	private static void sendPhoto(Context context, String id) {
		try {
			Uri my_contact_Uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id);
			InputStream photo_stream = ContactsContract.Contacts.openContactPhotoInputStream(context.getContentResolver(), my_contact_Uri, true);
			BufferedInputStream buf = new BufferedInputStream(photo_stream);
			Bitmap bitmap = BitmapFactory.decodeStream(buf);
			buf.close();
			if (bitmap.getWidth() > 400 || bitmap.getHeight() > 400) {
				bitmap = scaleDown(bitmap, 400, true);
			}
			ByteArrayOutputStream stream = new ByteArrayOutputStream();
			bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
			byte[] data = stream.toByteArray();
			if (data != null) {
				try {
					JSONObject jsonObject2 = new JSONObject();
					jsonObject2.put("command", "photo");
					jsonObject2.put("photo", Base64.encodeToString(data, Base64.DEFAULT));
					btSendText(jsonObject2.toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public static Bitmap scaleDown(Bitmap realImage, float maxImageSize, boolean filter) {
		float ratio = Math.min((float) maxImageSize / realImage.getWidth(), (float) maxImageSize / realImage.getHeight());
		int width = Math.round((float) ratio * realImage.getWidth());
		int height = Math.round((float) ratio * realImage.getHeight());

		Bitmap newBitmap = Bitmap.createScaledBitmap(realImage, width, height, filter);
		return newBitmap;
	}

	// private static void sendPhoto(Context context, String id) {
	// try {
	// Uri contactUri =
	// ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI,
	// Long.valueOf(id));
	// Uri photoUri = Uri.withAppendedPath(contactUri,
	// ContactsContract.Contacts.Photo.CONTENT_DIRECTORY);
	// Cursor cursor = context.getContentResolver().query(photoUri, new String[]
	// { ContactsContract.Contacts.Photo.PHOTO }, null, null, null);
	// if (cursor != null) {
	// try {
	// if (cursor.moveToFirst()) {
	// byte[] data = cursor.getBlob(0);
	// if (data != null) {
	// try {
	// JSONObject jsonObject2 = new JSONObject();
	// jsonObject2.put("command", "photo");
	// jsonObject2.put("photo", Base64.encodeToString(data, Base64.DEFAULT));
	// btSendText(jsonObject2.toString());
	// } catch (JSONException e) {
	// e.printStackTrace();
	// }
	// }
	// }
	// } finally {
	// cursor.close();
	// }
	// }
	// } catch (NumberFormatException e) {
	// e.printStackTrace();
	// }
	// }

	private static Contact searchContactByName(ArrayList<Contact> contacts, String name) {
		String[] words1 = spliToWords(name);
		Log.d(TAG, "searchContactByName, query: "+words1.toString());
		for (int i = 0; i < contacts.size(); i++) {
			Contact contact = contacts.get(i);
			String[] words2 = spliToWords(contact.name);
			if (words1.length == words2.length) {
				int found = 0;
				for (int k = 0; k < words2.length; k++) {
					float wordLength = words2[k].length();
					for (int j = 0; j < words1.length; j++) {
						float distance = StringUtils.getLevenshteinDistance(words1[j], words2[k]);
						// If difference between words < 30%
						if (distance / wordLength <= 0.3) {
							found++;
						}
					}
				}
				if (found == words1.length) {
					Log.d(TAG, "searchContactByName, result: "+contact.name);
					return contact;
				}
			}
		}
		Log.d(TAG, "searchContactByName, not found");
		return null;
	}

	private static String[] spliToWords(String name) {
		name = name.replaceAll("[^a-zA-Z0-9]", " ");
		name = name.replaceAll("\\s+", " ");
		name = name.trim();
		name = name.toLowerCase(Locale.US);
		return name.split(" ");
	}

	private static Contact searchContactByPhone(ArrayList<Contact> contacts, String phone) {
		phone = clearPhone(phone);
		for (int i = 0; i < contacts.size(); i++) {
			Contact contact = contacts.get(i);
			if (contact.phone.equals(phone)) {
				return contact;
			}
		}
		return null;
	}

	private static String clearPhone(String phone) {
		String r = "";
		if (phone.startsWith("+")) r += "+";
		r += phone.replaceAll("[^0-9]", "");
		return r;
	}

	private static ArrayList<Contact> getContacts(Context context) {
		ArrayList<Contact> r = new ArrayList<Contact>();
		Cursor cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
		while (cursor.moveToNext()) {
			Contact result = new Contact();
			result.id = cursor.getString(cursor.getColumnIndex(RawContacts.CONTACT_ID));
			result.name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME));
			result.phone = clearPhone(cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
			r.add(result);
		}
		cursor.close();
		return r;
	}

	// private static ArrayList<Contact> getContacts(Context context) {
	// ArrayList<Contact> r = new ArrayList<Contact>();
	// ContentResolver contentResolver = context.getContentResolver();
	// Cursor cursor =
	// contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null,
	// null, null);
	// if (cursor.getCount() > 0) {
	// while (cursor.moveToNext()) {
	// if
	// (Integer.parseInt(cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)))
	// > 0) {
	// String id =
	// cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
	// Cursor pCur =
	// contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
	// null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", new
	// String[] { id }, null);
	// while (pCur.moveToNext()) {
	// Contact result = new Contact();
	// result.name =
	// cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
	// result.phone =
	// pCur.getString(pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
	// result.id = id;
	// r.add(result);
	// break;
	// }
	// pCur.close();
	// }
	// }
	// }
	// cursor.close();
	// return r;
	// }

	private static class Contact {
		public String id;
		public String name;
		public String phone;
	}

	private static class MyHandler extends Handler {
		private Context mContext;

		public MyHandler(Context context) {
			mContext = context;
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				sendStateBroadcast(mContext, msg.arg1);
				break;
			case MESSAGE_WRITE:
				byte[] writeBuf = (byte[]) msg.obj;
				String writeMessage = new String(writeBuf);
				Log.i(TAG, "Text message sent: " + writeMessage);
				break;
			case MESSAGE_READ:
				byte[] readBuf = (byte[]) msg.obj;
				String readMessage = new String(readBuf, 0, msg.arg1);
				Log.i(TAG, "Message received: " + readMessage);
				handleCommand(mContext, readMessage);
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

	private static LocationListener mLocationListener = new LocationListener() {
		@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
		@Override
		public void onLocationChanged(Location location) {
			try {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("command", "location");
				jsonObject.put("altitude", location.getAltitude());
				if (android.os.Build.VERSION.SDK_INT >= 17) jsonObject.put("ert", location.getElapsedRealtimeNanos());
				jsonObject.put("latitude", location.getLatitude());
				jsonObject.put("longitude", location.getLongitude());
				jsonObject.put("time", location.getTime());
				jsonObject.put("accuracy", location.getAccuracy());
				jsonObject.put("bearing", location.getBearing());
				jsonObject.put("provider", location.getProvider());
				jsonObject.put("speed", location.getSpeed());
				btSendText(jsonObject.toString());
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {

		}

		@Override
		public void onProviderEnabled(String provider) {

		}

		@Override
		public void onProviderDisabled(String provider) {

		}
	};

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Broadcast message received");
			if (BROADCAST_ACTION.equals(intent.getAction())) {
				switch (intent.getIntExtra(BROADCAST_COMMAND, -1)) {
				case BROADCAST_CONNECT:
					connectDevice(intent.getStringExtra(BROADCAST_ADDRESS));
					break;
				case BROADCAST_DISCONNECT:
					mChatService.stop();
					break;
				}
			} else if (BROADCAST_SMS_RECEIVED.equals(intent.getAction())) {
				Bundle pudsBundle = intent.getExtras();
				Object[] pdus = (Object[]) pudsBundle.get("pdus");
				final SmsMessage[] messages = new SmsMessage[pdus.length];
				for (int i = 0; i < pdus.length; i++) {
					messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
				}
				StringBuffer content = new StringBuffer();
				for (SmsMessage sms : messages) {
					content.append(sms.getDisplayMessageBody());
				}

				String phone = messages[0].getDisplayOriginatingAddress();
				String text = content.toString();

				Log.d(TAG, "New SMS from " + phone + ": " + text);

				Contact contact = searchContactByPhone(getContacts(context), phone);

				try {
					JSONObject jsonObject = new JSONObject();
					jsonObject.put("command", "incoming_message");
					jsonObject.put("phone", phone);
					if (contact != null) jsonObject.put("name", contact.name);
					jsonObject.put("text", text);
					btSendText(jsonObject.toString());
				} catch (JSONException e) {
					e.printStackTrace();
				}

				if (contact != null) sendPhoto(context, contact.id);
			}
		}
	};

	class MyPhoneStateListener extends PhoneStateListener {
		public void onCallStateChanged(int state, String incomingNumber) {
			super.onCallStateChanged(state, incomingNumber);
			try {
				Contact contact = null;
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("command", "phone_state");
				switch (state) {
				case TelephonyManager.CALL_STATE_IDLE:
					jsonObject.put("type", "call_idle");
					break;
				case TelephonyManager.CALL_STATE_OFFHOOK:
					jsonObject.put("type", "call_offhook");
					break;
				case TelephonyManager.CALL_STATE_RINGING:
					jsonObject.put("type", "call_ringing");
					if (incomingNumber != null) {
						jsonObject.put("phone", incomingNumber);
						contact = searchContactByPhone(getContacts(MainService.this), incomingNumber);
						if (contact != null) {
							jsonObject.put("name", contact.name);
						}
					}
					break;
				}
				btSendText(jsonObject.toString());
				if (contact != null) sendPhoto(MainService.this, contact.id);
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

}
