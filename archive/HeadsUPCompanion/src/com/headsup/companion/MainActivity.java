package com.headsup.companion;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final String BROADCAST_ACTION = "com.headsup.companion.ActivityReceiver";
	public static final String BROADCAST_MESSAGE = "message";

	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private BluetoothAdapter mBluetoothAdapter;
	private Button mConnectButton;
	private TextView mStateView;
	private View mDisconnectButton;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
//		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?saddr=Tambov&daddr=Moscow"));
//		intent.setClassName("com.google.android.apps.maps", "com.google.android.maps.MapsActivity");
//		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//		intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
//		startActivity(intent);
		
//		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=Moscow"));
//		startActivity(intent);
//		
//		if(0>1) return;
		
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		mConnectButton = (Button) findViewById(R.id.connect);
		mConnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
				startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
			}
		});
		
		mDisconnectButton = (Button) findViewById(R.id.disconnect);
		mDisconnectButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainService.BROADCAST_ACTION);
				intent.putExtra(MainService.BROADCAST_COMMAND, MainService.BROADCAST_DISCONNECT);
				sendBroadcast(intent);
			}
		});

		mStateView = (TextView) findViewById(R.id.state);

		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}

		startService(new Intent(this, MainService.class));
	}

	@Override
	public void onStart() {
		super.onStart();
		IntentFilter filter = new IntentFilter(BROADCAST_ACTION);
		registerReceiver(mBroadcastReceiver, filter);
	}

	@Override
	public void onStop() {
		super.onStop();
		unregisterReceiver(mBroadcastReceiver);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			if (resultCode == Activity.RESULT_OK) {
				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				Intent intent = new Intent(MainService.BROADCAST_ACTION);
				intent.putExtra(MainService.BROADCAST_COMMAND, MainService.BROADCAST_CONNECT);
				intent.putExtra(MainService.BROADCAST_ADDRESS, address);
				sendBroadcast(intent);
			}
			break;
		case REQUEST_ENABLE_BT:
			if (resultCode != Activity.RESULT_OK) {
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
				finish();
			}
		}
	}

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			switch (intent.getIntExtra(BROADCAST_MESSAGE, -1)) {
			case BluetoothHelper.STATE_CONNECTED:
				mStateView.setText("Connected");
				mConnectButton.setEnabled(false);
				mDisconnectButton.setEnabled(true);
				break;
			case BluetoothHelper.STATE_CONNECTING:
				mStateView.setText("Connecting");
				mConnectButton.setEnabled(false);
				mDisconnectButton.setEnabled(true);
				break;
			case BluetoothHelper.STATE_LISTEN:
			case BluetoothHelper.STATE_NONE:
				mStateView.setText("Not connected");
				mConnectButton.setEnabled(true);
				mDisconnectButton.setEnabled(false);
				break;
			}
		}
	};

}
