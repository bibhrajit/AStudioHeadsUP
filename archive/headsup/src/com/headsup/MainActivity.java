package com.headsup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends FragmentActivity {

	public static final String BROADCAST_ACTION = "com.headsup.ActivityReceiver";
	public static final String BROADCAST_MESSAGE = "message";
	public static final String BROADCAST_TEXT = "text";
	public static final String BROADCAST_PHOTO = "photo";
	public static final String BROADCAST_FLAG = "flag";
	public static final int BM_OKHEADSUP_SCREEN = 0;
	public static final int BM_ERROR_SCREEN = 1;
	public static final int BM_CALLING_SCREEN = 2;
	public static final int BM_LISTEN_MESSAGE_SCREEN = 3;
	public static final int BM_SPEECH_SCREEN = 4;
	public static final int BM_CALLING_PHOTO = 5;
	public static final int BM_SEARCH_APP_SCREEN = 6;
	public static final int BM_INCALL_SCREEN = 7;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		findViewById(R.id.microphone).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MainService.BROADCAST_ACTION);
				intent.putExtra(MainService.BROADCAST_COMMAND, MainService.BROADCAST_ACTIVATE);
				sendBroadcast(intent);
			}
		});

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

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			TextView textView;
			switch (intent.getIntExtra(BROADCAST_MESSAGE, -1)) {
			case BM_OKHEADSUP_SCREEN:
				changeScreen(R.id.ok_headsup_screen);
				((TextView) findViewById(R.id.say_ok_headsup)).setTypeface(FontHelper.get(MainActivity.this));
				((TextView) findViewById(R.id.available_commands)).setTypeface(FontHelper.get(MainActivity.this));
				findViewById(R.id.video_recorder).setVisibility(intent.getBooleanExtra(BROADCAST_FLAG, false) ? View.VISIBLE : View.INVISIBLE);
				break;
			case BM_ERROR_SCREEN:
			case BM_LISTEN_MESSAGE_SCREEN:
			case BM_CALLING_SCREEN:
			case BM_SPEECH_SCREEN:
			case BM_SEARCH_APP_SCREEN:
				changeScreen(R.id.text_screen);
				textView = (TextView) findViewById(R.id.text);
				textView.setText(intent.getStringExtra(BROADCAST_TEXT));
				textView.setTypeface(FontHelper.get(MainActivity.this));
				break;
			case BM_CALLING_PHOTO:
				changeScreen(R.id.photo_screen);
				textView = (TextView) findViewById(R.id.text2);
				textView.setText(intent.getStringExtra(BROADCAST_TEXT));
				textView.setTypeface(FontHelper.get(MainActivity.this));
				setPhoto(intent.getStringExtra(BROADCAST_PHOTO), (ImageView) findViewById(R.id.photo));
				break;
			case BM_INCALL_SCREEN:
				changeScreen(R.id.incall_screen);
				textView = (TextView) findViewById(R.id.name);
				textView.setText(intent.getStringExtra(BROADCAST_TEXT));
				textView.setTypeface(FontHelper.get(MainActivity.this));
				ImageView imageView = (ImageView) findViewById(R.id.photo2);
				if (intent.hasExtra(BROADCAST_PHOTO)) setPhoto(intent.getStringExtra(BROADCAST_PHOTO), imageView);
				else {
					Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.unknown);
					imageView.setImageBitmap(getRoundedBitmap(bitmap));
				}
				break;
			}
		}
	};

	private void changeScreen(int screen) {
		int[] screens = new int[] { R.id.text_screen, R.id.ok_headsup_screen, R.id.photo_screen, R.id.incall_screen };
		for (int i = 0; i < screens.length; i++) {
			findViewById(screens[i]).setVisibility((screens[i] == screen) ? View.VISIBLE : View.GONE);
		}
	}

	private void setPhoto(String path, ImageView imageView) {
		try {
			FileInputStream fis = new FileInputStream(new File(path));
			Bitmap bitmap = getRoundedBitmap(BitmapFactory.decodeStream(fis));
			imageView.setImageBitmap(bitmap);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	private Bitmap getRoundedBitmap(Bitmap bitmap) {
		int radius = (bitmap.getWidth() > bitmap.getHeight()) ? bitmap.getHeight() : bitmap.getWidth();

		Bitmap output = Bitmap.createBitmap(radius, radius, Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff000000;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, output.getWidth(), output.getHeight());

		paint.setAntiAlias(true);
		paint.setFilterBitmap(true);
		paint.setDither(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);

		canvas.drawCircle(output.getWidth() / 2 + 0.7f, output.getHeight() / 2 + 0.7f, output.getWidth() / 2 + 0.1f, paint);
		paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));

		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}

	private static class FontHelper {
		private volatile static Typeface mFont = null;
		private static final Object mtx = new Object();

		public static Typeface get(Context context) {
			Typeface result = mFont;
			if (result == null) {
				synchronized (mtx) {
					result = mFont;
					if (result == null) {
						result = mFont = Typeface.createFromAsset(context.getAssets(), "Century-Schoolbook.ttf");
					}
				}
			}
			return result;
		}
	}

}