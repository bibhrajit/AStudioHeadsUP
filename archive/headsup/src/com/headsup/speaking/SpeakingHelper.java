package com.headsup.speaking;

import java.util.HashMap;
import java.util.Locale;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

public class SpeakingHelper {

	private TextToSpeech mTextToSpeech;
	private boolean mIsInit = false;

	public SpeakingHelper(Context context, final SpeakingListener listener) {
		mTextToSpeech = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
			@Override
			public void onInit(int status) {
				if (status == TextToSpeech.SUCCESS) {
					int result = mTextToSpeech.setLanguage(Locale.US);
					if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
						Log.e("SpeakingHelper", "This Language is not supported");
						if (listener != null) listener.onError();
					} else {
						mIsInit = true;
						if (listener != null) listener.onReady();
					}
				} else {
					Log.e("SpeakingHelper", "Unable init TextToSpeech. Status: " + status);
					if (listener != null) listener.onError();
				}
			}
		});
		mTextToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
			@Override
			public void onStart(String utteranceId) {
				if (listener != null) listener.onStart();
			}

			@Override
			public void onDone(String utteranceId) {
				if (listener != null) listener.onDone();
			}

			@Override
			public void onError(String utteranceId) {
				if (listener != null) listener.onError();
			}
		});
	}

	@SuppressWarnings("deprecation")
	public void speak(String text) {
		if (mIsInit) {
			HashMap<String, String> map = new HashMap<String, String>();
			map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "UtteranceId");
			mTextToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, map);
		}
	}
	
	public void destroy() {
		if (mIsInit) {
			mTextToSpeech.shutdown();
		}
	}

}
