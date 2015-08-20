package com.headsup;

import java.io.File;
import java.util.Date;

import android.content.Context;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

@SuppressWarnings("deprecation")
public class VideoRecorder {

	private WindowManager windowManager;
	private SurfaceView surfaceView;
	private Camera camera = null;
	private MediaRecorder mediaRecorder = null;
	private boolean mIsRecording = false;

	public VideoRecorder(Context context) {
		windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
		surfaceView = new SurfaceView(context);
		LayoutParams layoutParams = new WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT);
		layoutParams.gravity = Gravity.START /* | Gravity.LEFT */| Gravity.TOP;
		windowManager.addView(surfaceView, layoutParams);
		surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
			@Override
			public void surfaceCreated(SurfaceHolder holder) {
				camera = Camera.open();
				mediaRecorder = new MediaRecorder();
				camera.unlock();

				mediaRecorder.setPreviewDisplay(holder.getSurface());
				mediaRecorder.setCamera(camera);
				mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
				//mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
				//mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));
				CamcorderProfile camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
				mediaRecorder.setOutputFormat(camcorderProfile.fileFormat);
				mediaRecorder.setVideoEncodingBitRate(camcorderProfile.videoBitRate);
				mediaRecorder.setVideoFrameRate(camcorderProfile.videoFrameRate);
				mediaRecorder.setVideoSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
		        mediaRecorder.setVideoEncoder(camcorderProfile.videoCodec);
		        
		        File directory=new File(Environment.getExternalStorageDirectory(),"HeadsUP Video");
		        directory.mkdirs();

				mediaRecorder.setOutputFile(new File(directory, DateFormat.format("yyyy-MM-dd_kk-mm-ss", new Date().getTime()) + ".mp4").getAbsolutePath());

				try {
					mediaRecorder.prepare();
				} catch (Exception e) {
				}
				mediaRecorder.start();
				mIsRecording = true;
			}

			@Override
			public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
			}

			@Override
			public void surfaceDestroyed(SurfaceHolder holder) {
			}
		});
	}
	
	public boolean isRecording() {
		return mIsRecording;
	}

	public void stop() {
		if (mIsRecording) {
			mIsRecording = false;
			mediaRecorder.stop();
			mediaRecorder.reset();
			mediaRecorder.release();

			camera.lock();
			camera.release();

			windowManager.removeView(surfaceView);
		}
	}

}
