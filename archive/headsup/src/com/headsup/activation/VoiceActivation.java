package com.headsup.activation;

import static edu.cmu.pocketsphinx.SpeechRecognizerSetup.defaultSetup;

import java.io.File;
import java.io.IOException;

import android.content.Context;
import android.util.Log;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;

public class VoiceActivation {

	private static final String KWS_SEARCH = "wakeup";
	private static final String KEYPHRASE = "okay headsup";
	private static final String TAG = "VoiceActivation";

	private Context mContext;
	private OnInitListener mOnInitListener;
	private OnActivatedListener mOnActivatedListener;

	private SpeechRecognizer mRecognizer;
	private boolean mIsListening = false;

	public VoiceActivation(Context context, OnInitListener listener) {
		mContext = context;
		mOnInitListener = listener;

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Assets assets = new Assets(mContext);
					File assetDir = assets.syncAssets();

					File modelsDir = new File(assetDir, "models");
					mRecognizer = defaultSetup().setAcousticModel(new File(modelsDir, "hmm/en-us-semi")).setDictionary(new File(modelsDir, "dict/cmu07a.dic")).setKeywordThreshold(1e-40f).getRecognizer();
					mRecognizer.addListener(new MyRecognitionListener());

					mRecognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);

					mRecognizer.stop();
					if (mOnInitListener != null) mOnInitListener.onSuccess();
				} catch (IOException e) {
					Log.e(TAG, "Failed to init recognizer");
					e.printStackTrace();
					if (mOnInitListener != null) mOnInitListener.onError();
				}
			}
		}).start();
	}

	public void setOnActivatedListener(OnActivatedListener listener) {
		mOnActivatedListener = listener;
	}

	public void listen() {
		if (mRecognizer != null) {
			mRecognizer.startListening(KWS_SEARCH);
			mIsListening = true;
		}
	}

	private class MyRecognitionListener implements RecognitionListener {

		@Override
		public void onBeginningOfSpeech() {
		}

		@Override
		public void onEndOfSpeech() {
		}

		@Override
		public void onPartialResult(Hypothesis hypothesis) {
			String text = hypothesis.getHypstr();
			if (text.equals(KEYPHRASE)) {
				mRecognizer.stop();
				mIsListening = false;
			}
		}

		@Override
		public void onResult(Hypothesis hypothesis) {
			if (hypothesis != null) {
				if (mOnActivatedListener != null) mOnActivatedListener.OnActivated();
			}
		}
	}

	public boolean isListening() {
		return mIsListening;
	}

	public void stop() {
		if (mRecognizer != null && mIsListening) {
			mRecognizer.stop();
			mIsListening = false;
		}
	}

}
