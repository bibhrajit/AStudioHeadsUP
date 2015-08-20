package com.headsup;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.location.LocationServices;
import com.headsup.activation.OnActivatedListener;
import com.headsup.activation.OnInitListener;
import com.headsup.activation.VoiceActivation;
import com.headsup.bluetooth.BluetoothHelper;
import com.headsup.bluetooth.OnMessageListener;
import com.headsup.recognition.CommandsListener;
import com.headsup.recognition.CommandsRecognition;
import com.headsup.speaking.SpeakingHelper;
import com.headsup.speaking.SpeakingListener;
import com.headsup.statemachine.State;
import com.headsup.statemachine.StateMachine;

public class MainService extends Service {

	private static final String TAG = "HeadsUP service";
	private static final int NOTIFICATION_ID = 1;

	public static final String BROADCAST_ACTION = "com.headsup.ServiceReceiver";
	public static final String BROADCAST_COMMAND = "command";
	public static final int BROADCAST_ACTIVATE = 0;

	private static final int SM_WAITING_COMMAND = 0;
	private static final int SM_CALL_END = 1;
	private static final int SM_SPEAK = 2;
	private static final int SM_PHOTO_RECEIVED = 3;
	private static final int SM_CONTACT_ERROR = 4;
	private static final int SM_CONTACT_RECEIVED = 5;
	protected static final int SM_CALL_RINGING = 6;
	protected static final int SM_CALL_OFFHOOK = 7;

	private MyStateMachine mMyStateMachine;
	private NotificationManager mNotificationManager;
	private VoiceActivation mVoiceActivation;
	private BluetoothHelper mBluetoothHelper;
	private GoogleApiClient mGoogleApiClient;
	private VideoRecorder mVideoRecorder;

	@Override
	public void onCreate() {
		Log.d(TAG, "Service created");

		IntentFilter filter = new IntentFilter();
		filter.addAction(BROADCAST_ACTION);
		registerReceiver(mBroadcastReceiver, filter);

		mMyStateMachine = new MyStateMachine();

		NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(R.drawable.ic_launcher).setContentTitle(getString(R.string.app_name));
		builder.setOngoing(true);
		builder.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0));
		mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.notify(NOTIFICATION_ID, builder.build());

		mBluetoothHelper = new BluetoothHelper(this);
		mBluetoothHelper.setOnMessageListener(new OnMessageListener() {
			@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
			@Override
			public void onTextMessage(String message) {
				try {
					JSONObject jsonObject = new JSONObject(message);
					String command = jsonObject.getString("command");
					if (!TextUtils.isEmpty(command)) {
						if (command.equals("phone_state")) {
							String type = jsonObject.getString("type");
							if (!TextUtils.isEmpty(type)) {
								Log.d(TAG, "Phone state: " + type);
								if (type.equals("call_idle")) {
									mMyStateMachine.sendMessage(SM_CALL_END);
								} else if (type.equals("call_ringing")) {
									String phone = jsonObject.has("phone") ? jsonObject.getString("phone") : "";
									String name = jsonObject.has("name") ? jsonObject.getString("name") : "";
									// String from;
									// if (TextUtils.isEmpty(phone)) {
									// from = "Unknown";
									// } else {
									// if (TextUtils.isEmpty(name)) {
									// from = phone;
									// } else {
									// from = name;
									// }
									// }
									Log.d(TAG, "Incoming call (phone: " + phone + "; name: " + name + ")");
									mMyStateMachine.sendMessage(SM_CALL_RINGING, name);
								} else if (type.equals("call_offhook")) {
									mMyStateMachine.sendMessage(SM_CALL_OFFHOOK);
								}
							}
						} else if (command.equals("location")) {
							if (mGoogleApiClient.isConnected()) {
								Location newLocation = new Location(jsonObject.getString("provider"));
								newLocation.setAltitude(jsonObject.getDouble("altitude"));
								if (android.os.Build.VERSION.SDK_INT >= 17 && jsonObject.has("ert")) newLocation.setElapsedRealtimeNanos(jsonObject.getLong("ert"));
								newLocation.setLatitude(jsonObject.getDouble("latitude"));
								newLocation.setLongitude(jsonObject.getDouble("longitude"));
								newLocation.setTime(jsonObject.getLong("time"));
								newLocation.setAccuracy((float) jsonObject.getDouble("longitude"));
								newLocation.setBearing((float) jsonObject.getDouble("bearing"));
								newLocation.setSpeed((float) jsonObject.getDouble("speed"));
								LocationServices.FusedLocationApi.setMockLocation(mGoogleApiClient, newLocation);
							}
						} else if (command.equals("incoming_message")) {
							String text = "New message";
							if (jsonObject.has("name")) text += " from " + jsonObject.getString("name");
							if (jsonObject.has("text")) text += ": " + jsonObject.getString("text");
							mMyStateMachine.sendMessage(SM_SPEAK, text);
						} else if (command.equals("message_sent")) {
							String text = "Your message sent ";
							if (jsonObject.has("name")) text += " to " + jsonObject.getString("name");
							mMyStateMachine.sendMessage(SM_SPEAK, text);
						} else if (command.equals("contact")) {
							mMyStateMachine.sendMessage(SM_CONTACT_RECEIVED, jsonObject.getString("phone"));
						} else if (command.equals("contact_error")) {
							mMyStateMachine.sendMessage(SM_CONTACT_ERROR, jsonObject.getString("error"));
						} else if (command.equals("photo")) {
							try {
								byte[] data = Base64.decode(jsonObject.getString("photo"), Base64.DEFAULT);
								File file = new File(getCacheDir(), "photo.jpg");
								if (saveBitmap(file, BitmapFactory.decodeStream(new ByteArrayInputStream((byte[]) data)))) {
									mMyStateMachine.sendMessage(SM_PHOTO_RECEIVED, file.getAbsolutePath());
								}
							} catch (FileNotFoundException e) {
								e.printStackTrace();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onConnected() {
				mMyStateMachine.sendMessage(SM_SPEAK, "Bluetooth connected");
			}

			@Override
			public void onDisconnected() {
				mMyStateMachine.sendMessage(SM_SPEAK, "Bluetooth disconnected");
			}
		});

		mVoiceActivation = new VoiceActivation(this, new OnInitListener() {
			@Override
			public void onSuccess() {
				Log.d(TAG, "Voice activation: success");
				mMyStateMachine.start();
				// Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
				// intent.putExtra(MainActivity.BROADCAST_MESSAGE,
				// MainActivity.BM_VR_STARTED);
				// sendBroadcast(intent);
				// mVoiceActivation.listen();
			}

			@Override
			public void onError() {
				Log.d(TAG, "Voice activation: error");
				sendBroadcast(MainActivity.BM_ERROR_SCREEN, "Cannot start voice activation");
				// Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
				// intent.putExtra(MainActivity.BROADCAST_MESSAGE,
				// MainActivity.BM_VR_FAILED);
				// sendBroadcast(intent);
			}
		});

		mVoiceActivation.setOnActivatedListener(new OnActivatedListener() {
			@Override
			public void OnActivated() {
				Log.d(TAG, "Voice activation: activated");
				Intent intent = new Intent(MainService.this, MainActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
				startActivity(intent);
				// Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
				// intent.putExtra(MainActivity.BROADCAST_MESSAGE,
				// MainActivity.BM_VR_ACTIVATED);
				// sendBroadcast(intent);
				mMyStateMachine.sendMessage(SM_WAITING_COMMAND);
			}
		});

		mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API).addConnectionCallbacks(new ConnectionCallbacks() {
			@Override
			public void onConnected(Bundle arg0) {
				LocationServices.FusedLocationApi.setMockMode(mGoogleApiClient, true);
			}

			@Override
			public void onConnectionSuspended(int arg0) {
			}
		}).build();
		mGoogleApiClient.connect();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mBroadcastReceiver);
		mBluetoothHelper.destroy();
		if (mGoogleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.setMockMode(mGoogleApiClient, false);
			mGoogleApiClient.disconnect();
		}
		mNotificationManager.cancel(NOTIFICATION_ID);
	}

	private boolean saveBitmap(File file, Bitmap bitmap) throws FileNotFoundException, IOException {
		boolean r = false;
		FileOutputStream fos = new FileOutputStream(file);
		if (fos != null) {
			if (bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)) {
				r = true;
			}
			fos.flush();
			fos.close();
		}
		return r;
	}

	class MyStateMachine extends StateMachine {

		// private StateMachineEnterExitTransitionToTest mThisSm;
		private StateActivation mStateActivation = new StateActivation();
		private StateCommandWaiting mStateCommandWaiting = new StateCommandWaiting();
		private StateCommandHandling mStateCommandHandling = new StateCommandHandling();
		private StateCalling mStateCalling = new StateCalling();
		private StateSendMessage mStateSendMessage = new StateSendMessage();
		private StateDirection mStateDirection = new StateDirection();
		private StateSpeaking mStateSpeaking = new StateSpeaking();
		private StateInCall mStateInCall = new StateInCall();
		private StateInCallTalking mStateInCallTalking = new StateInCallTalking();

		private boolean mIsGpsStarted = false;

		MyStateMachine() {
			super();
			// mThisSm = this;
			// setDbg(true);

			addState(mStateActivation);
			addState(mStateCommandWaiting);
			addState(mStateCommandHandling);
			addState(mStateCalling);
			addState(mStateSendMessage);
			addState(mStateDirection);
			addState(mStateSpeaking);
			addState(mStateInCall);

			setInitialState(mStateActivation);
		}

		class MyState extends State {
			@Override
			public void processMessage(int what, Object data) {
				if (what == SM_CALL_RINGING) {
					Log.d(TAG, "Incoming call");
					mStateInCall.setFrom((String) data);
					transitionTo(mStateInCall);
				}
			}
		}

		class StateActivation extends MyState {

			@Override
			public void enter() {
				mVoiceActivation.listen();
				Log.d(TAG, "Waiting for \"Ok HeadsUP\"");
				Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
				intent.putExtra(MainActivity.BROADCAST_MESSAGE, MainActivity.BM_OKHEADSUP_SCREEN);
				intent.putExtra(MainActivity.BROADCAST_FLAG, (mVideoRecorder != null && mVideoRecorder.isRecording()));
				sendBroadcast(intent);
			}

			@Override
			public void processMessage(int what, Object data) {
				super.processMessage(what, data);
				if (what == SM_WAITING_COMMAND) {
					transitionTo(mStateCommandWaiting);
				} else if (what == SM_SPEAK) {
					mStateSpeaking.setText((String) data);
					transitionTo(mStateSpeaking);
				}
			}

			@Override
			public void exit() {
				if (mVoiceActivation.isListening()) {
					mVoiceActivation.stop();
				}
			}
		}

		class StateCommandWaiting extends MyState {
			@Override
			public void enter() {
				Log.d(TAG, "Listening your command");
				CommandsRecognition commandsRecognition = new CommandsRecognition(MainService.this, true);
				Log.d(TAG, "1");
				commandsRecognition.setCommandsListener(new CommandsListener() {
					@Override
					public void onResult(ArrayList<String> result) {
						Log.d(TAG, "Google caught: " + result.toString());

						if (mIsGpsStarted) {
							Log.d(TAG, "Stop GPS");
							try {
								JSONObject jsonObject = new JSONObject();
								jsonObject.put("command", "stop_gps");
								mBluetoothHelper.sendMessage(jsonObject.toString());
								mIsGpsStarted = false;
							} catch (JSONException e) {
								e.printStackTrace();
							}
						}

						mStateCommandHandling.setResults(result);
						transitionTo(mStateCommandHandling);
					}

					@Override
					public void onError(int errorCode, String error) {
						Log.d(TAG, "Error: " + error);
						transitionTo(mStateActivation);
					}
				});
				Log.d(TAG, "2");
				commandsRecognition.startListening();
				Log.d(TAG, "3");
			}
		}

		class StateCommandHandling extends MyState {
			private ArrayList<String> mCommands;

			public void setResults(ArrayList<String> commands) {
				mCommands = commands;
			}

			@Override
			public void enter() {
				Log.d(TAG, "Executing the command");
				boolean handled = false;
				ArrayList<Result> mAllApps = new ArrayList<Result>();
				for (int i = 0; i < mCommands.size(); i++) {
					String command = mCommands.get(i);
					if (command.startsWith("call")) {
						if (command.length() > 5) {
							String name = command.replaceAll("^call\\s+", "");
							if (name != null) {
								handled = true;
								mStateCalling.setName(name);
								transitionTo(mStateCalling);
								break;
							}
						}
					} else if (command.startsWith("start app") || command.startsWith("open app")) {
						if (command.length() > 9) {
							handled = true;
							sendBroadcast(MainActivity.BM_SEARCH_APP_SCREEN, "Search for app");
							String appName = command.replaceAll("^(start|open) app\\s+", "");
							if (mAllApps.isEmpty()) mAllApps = getApps();
							String app = searchResult(mAllApps, appName);
							if (app == null) {
								mStateSpeaking.setText("Cannot find app with name " + appName);
								transitionTo(mStateSpeaking);
							} else {
								Intent intent = new Intent();
								PackageManager manager = getPackageManager();
								intent = manager.getLaunchIntentForPackage(app);
								intent.addCategory(Intent.CATEGORY_LAUNCHER);
								startActivity(intent);
								mStateSpeaking.setText("Starting app " + appName);
								transitionTo(mStateSpeaking);
							}
							break;
						}
					} else if (command.startsWith("send message") || command.startsWith("send a text")) {
						if (command.length() > 11) {
							String name = command.replaceAll("^((send message)|(send a text))\\s+(to\\s+)?", "");
							if (name != null) {
								handled = true;
								mStateSendMessage.setName(name);
								transitionTo(mStateSendMessage);
								break;
							}
						}
					} else if (command.startsWith("direct me to ")) {
						if (command.length() > 13) {
							String city = command.substring(13);
							handled = true;
							mStateDirection.setCity(city);
							transitionTo(mStateDirection);
							break;
						}
					} else if (command.startsWith("enable vision")) {
						handled = true;
						Log.d(TAG, "Enable vision");
						if (mVideoRecorder == null || !mVideoRecorder.isRecording()) {
							Log.d(TAG, "Enabling vision");
							mVideoRecorder = new VideoRecorder(MainService.this);
						}
						mStateSpeaking.setText("Vision enabled");
						transitionTo(mStateSpeaking);
						break;
					} else if (command.startsWith("disable vision")) {
						handled = true;
						Log.d(TAG, "Disable vision");
						if (mVideoRecorder != null && mVideoRecorder.isRecording()) {
							Log.d(TAG, "Disabling vision");
							mVideoRecorder.stop();
						}
						mStateSpeaking.setText("Vision disabled");
						transitionTo(mStateSpeaking);
						break;
					}
				}
				if (!handled) {
					Log.d(TAG, "Unknown command");
					mStateSpeaking.setText("Unknown command");
					transitionTo(mStateSpeaking);
				}
			}
		}

		class StateCalling extends State {
			private String mName;

			public void setName(String name) {
				mName = name;
			}

			@Override
			public void enter() {
				if (mBluetoothHelper.isConnected()) {
					Log.d(TAG, "Calling " + mName);
					sendBroadcast(MainActivity.BM_CALLING_SCREEN, "Calling " + mName);
					try {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("command", "call");
						jsonObject.put("name", mName);
						mBluetoothHelper.sendMessage(jsonObject.toString());
					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					mStateSpeaking.setText("You need to connect your smartphone via Bluetooth before calling");
					transitionTo(mStateSpeaking);
				}
			}

			@Override
			public void processMessage(int what, Object data) {
				if (what == SM_CALL_END) {
					Log.d(TAG, "Call ended");
					transitionTo(mStateActivation);
				} else if (what == SM_CONTACT_ERROR) {
					Log.d(TAG, "Call error: " + ((String) data));
					mStateSpeaking.setText((String) data);
					transitionTo(mStateSpeaking);
				} else if (what == SM_PHOTO_RECEIVED) {
					Log.d(TAG, "Call, photo received");
					sendBroadcast(MainActivity.BM_CALLING_PHOTO, "Calling " + mName, (String) data);
				}
			}
		}

		class StateSendMessage extends MyState {
			private String mName;

			public void setName(String name) {
				mName = name;
			}

			@Override
			public void enter() {
				if (mBluetoothHelper.isConnected()) {
					Log.d(TAG, "Send message to " + mName);
					sendBroadcast(MainActivity.BM_CALLING_SCREEN, "Search for " + mName);
					try {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("command", "request_contact");
						jsonObject.put("name", mName);
						mBluetoothHelper.sendMessage(jsonObject.toString());
					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					mStateSpeaking.setText("You need to connect your smartphone via Bluetooth before messaging");
					transitionTo(mStateSpeaking);
				}
			}

			@Override
			public void processMessage(int what, Object data) {
				super.processMessage(what, data);
				if (what == SM_CONTACT_RECEIVED) {
					Log.d(TAG, "Listen message text");
					final String number = (String) data;
					sendBroadcast(MainActivity.BM_LISTEN_MESSAGE_SCREEN, "Listening for message text");
					CommandsRecognition commandsRecognition = new CommandsRecognition(MainService.this, false);
					commandsRecognition.setCommandsListener(new CommandsListener() {
						@Override
						public void onResult(ArrayList<String> result) {
							Log.d(TAG, "Message text: " + result.toString());
							if (!result.isEmpty()) {
								try {
									JSONObject jsonObject = new JSONObject();
									jsonObject.put("command", "send_message");
									jsonObject.put("name", mName);
									jsonObject.put("phone", number);
									jsonObject.put("text", result.get(0));
									mBluetoothHelper.sendMessage(jsonObject.toString());
									Log.d(TAG, "Message sent to companion");
								} catch (JSONException e) {
									e.printStackTrace();
								}
							}
							transitionTo(mStateActivation);
							// mStateSpeaking.setText("Message sent");
							// transitionTo(mStateSpeaking);
						}

						@Override
						public void onError(int errorCode, String error) {
							Log.d(TAG, "Error: " + error);
							transitionTo(mStateActivation);
						}
					});
					commandsRecognition.startListening();
				} else if (what == SM_CONTACT_ERROR) {
					Log.d(TAG, "Message error: " + ((String) data));
					mStateSpeaking.setText((String) data);
					transitionTo(mStateSpeaking);
				} else if (what == SM_PHOTO_RECEIVED) {
					Log.d(TAG, "Listening message, photo received");
					sendBroadcast(MainActivity.BM_CALLING_PHOTO, "Listening for message text", (String) data);
				}

			}
		}

		class StateDirection extends MyState {
			private String mCity;

			public void setCity(String city) {
				mCity = city;
			}

			@Override
			public void enter() {
				Log.d(TAG, "Direct to " + mCity);
				if (!mIsGpsStarted) {
					try {
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("command", "start_gps");
						mBluetoothHelper.sendMessage(jsonObject.toString());
						mIsGpsStarted = true;
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				// Intent intent = new Intent(Intent.ACTION_VIEW,
				// Uri.parse("http://maps.google.com/maps?daddr=" + mCity +
				// "&myl=saddr&dirflg=d"));
				// intent.setClassName("com.google.android.apps.maps",
				// "com.google.android.maps.MapsActivity");
				// intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				// intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
				// intent.putExtra("noconfirm", true);
				// startActivity(intent);

				Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + mCity));
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
				startActivity(intent);

				transitionTo(mStateActivation);
			}
		}

		class StateSpeaking extends MyState {
			private String mText;
			private SpeakingHelper mSpeakingHelper;

			public void setText(String text) {
				mText = text;
			}

			@Override
			public void enter() {
				Log.d(TAG, "Start speaking");
				mSpeakingHelper = new SpeakingHelper(MainService.this, new SpeakingListener() {
					@Override
					public void onReady() {
						mSpeakingHelper.speak(mText);
					}

					@Override
					public void onStart() {
						sendBroadcast(MainActivity.BM_SPEECH_SCREEN, mText);
					}

					@Override
					public void onDone() {
						transitionTo(mStateActivation);
					}

					@Override
					public void onError() {
						transitionTo(mStateActivation);
					}
				});
			}

			@Override
			public void processMessage(int what, Object data) {
				super.processMessage(what, data);
				if (what == SM_PHOTO_RECEIVED) {
					Log.d(TAG, "Speaking, photo received");
					sendBroadcast(MainActivity.BM_CALLING_PHOTO, mText, (String) data);
				}
			}

			@Override
			public void exit() {
				Log.d(TAG, "Stop speaking");
				mSpeakingHelper.destroy();
			}
		}

		class StateInCall extends State {
			private String mFrom;
			private String mPhoto;
			private SpeakingHelper mSpeakingHelper;

			public void setFrom(String from) {
				mFrom = from;
			}

			@Override
			public void enter() {
				Log.d(TAG, "Incoming call");
				sendBroadcast(MainActivity.BM_INCALL_SCREEN, mFrom);
				mSpeakingHelper = new SpeakingHelper(MainService.this, new SpeakingListener() {
					@Override
					public void onReady() {
						String text = "Incoming call";
						if (!TextUtils.isEmpty(mFrom)) text += " from " + mFrom;
						mSpeakingHelper.speak(text);
					}

					@Override
					public void onStart() {
					}

					@Override
					public void onDone() {
						mVoiceActivation.listen();
					}

					@Override
					public void onError() {
						mVoiceActivation.listen();
					}
				});
			}

			@Override
			public void processMessage(int what, Object data) {
				super.processMessage(what, data);
				if (what == SM_PHOTO_RECEIVED) {
					Log.d(TAG, "Incoming call, photo received");
					mPhoto = (String) data;
					sendBroadcast(MainActivity.BM_INCALL_SCREEN, mFrom, mPhoto);
				} else if (what == SM_CALL_OFFHOOK) {
					mStateInCallTalking.setFrom(mFrom);
					mStateInCallTalking.setPhoto(mPhoto);
					transitionTo(mStateInCallTalking);
				} else if (what == SM_CALL_END) {
					Log.d(TAG, "Incoming call ended");
					transitionTo(mStateActivation);
				} else if (what == SM_WAITING_COMMAND) {
					Log.d(TAG, "Incoming call, activated");
					CommandsRecognition commandsRecognition = new CommandsRecognition(MainService.this, true);
					commandsRecognition.setCommandsListener(new CommandsListener() {
						@Override
						public void onResult(ArrayList<String> mResults) {
							for (int i = 0; i < mResults.size(); i++) {
								String command = mResults.get(i);
								if (containsIgnoreCase(command, "accept") || containsIgnoreCase(command, "answer")) {
									try {
										JSONObject jsonObject = new JSONObject();
										jsonObject.put("command", "accept_call");
										mBluetoothHelper.sendMessage(jsonObject.toString());
									} catch (JSONException e) {
										e.printStackTrace();
									}
								} else if (containsIgnoreCase(command, "ignore") || containsIgnoreCase(command, "cancel") || containsIgnoreCase(command, "decline")) {
									try {
										JSONObject jsonObject = new JSONObject();
										jsonObject.put("command", "decline_call");
										mBluetoothHelper.sendMessage(jsonObject.toString());
									} catch (JSONException e) {
										e.printStackTrace();
									}
								} else {
									mVoiceActivation.listen();
								}
							}
						}

						private boolean containsIgnoreCase(String haystack, String needle) {
							try {
								return Pattern.compile(Pattern.quote(needle), Pattern.CASE_INSENSITIVE).matcher(haystack).find();
							} catch (PatternSyntaxException e) {
								e.printStackTrace();
							}
							return false;
						}

						@Override
						public void onError(int errorCode, String error) {
							mVoiceActivation.listen();
						}
					});
					commandsRecognition.startListening();
				}
			}

			@Override
			public void exit() {
				Log.d(TAG, "Stop incoming call");
			}
		}

		class StateInCallTalking extends State {
			private String mFrom;
			private String mPhoto;

			public void setFrom(String from) {
				mFrom = from;
			}

			public void setPhoto(String photo) {
				mPhoto = photo;
			}

			@Override
			public void enter() {
				Log.d(TAG, "Start talking");
				String label = "Call from " + mFrom;
				if (TextUtils.isEmpty(mPhoto)) sendBroadcast(MainActivity.BM_CALLING_SCREEN, label);
				else sendBroadcast(MainActivity.BM_CALLING_PHOTO, label, mPhoto);
			}

			@Override
			public void processMessage(int what, Object data) {
				if (what == SM_CALL_END) {
					Log.d(TAG, "Talking ended");
					transitionTo(mStateActivation);
				}
			}
		}

		// @Override
		// protected void onHalting() {
		// synchronized (mThisSm) {
		// mThisSm.notifyAll();
		// }
		// }

	}

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Broadcast message received");
			if (BROADCAST_ACTION.equals(intent.getAction())) {
				switch (intent.getIntExtra(BROADCAST_COMMAND, -1)) {
				case BROADCAST_ACTIVATE:
					mMyStateMachine.sendMessage(SM_WAITING_COMMAND);
					break;
				}
			}
		}
	};

	private void sendBroadcast(int stateActivation, String text) {
		sendBroadcast(stateActivation, text, null);
	}

	private void sendBroadcast(int stateActivation, String text, String photo) {
		Intent intent = new Intent(MainActivity.BROADCAST_ACTION);
		intent.putExtra(MainActivity.BROADCAST_MESSAGE, stateActivation);
		if (text != null) intent.putExtra(MainActivity.BROADCAST_TEXT, text);
		if (photo != null) intent.putExtra(MainActivity.BROADCAST_PHOTO, photo);
		sendBroadcast(intent);
	}

	private String searchResult(ArrayList<Result> results, String name) {
		for (int i = 0; i < results.size(); i++) {
			Result result = results.get(i);
			if (result.name.equals(name)) {
				return result.value;
			}
		}
		return null;
	}

	private ArrayList<Result> getApps() {
		ArrayList<Result> r = new ArrayList<Result>();
		List<PackageInfo> packages = getPackageManager().getInstalledPackages(0);
		for (int i = 0; i < packages.size(); i++) {
			PackageInfo packageInfo = packages.get(i);
			Result result = new Result();
			result.name = packageInfo.applicationInfo.loadLabel(getPackageManager()).toString();
			result.value = packageInfo.packageName;
			r.add(result);
		}
		return r;
	}

	private class Result {
		public String name, value;
	}

}
