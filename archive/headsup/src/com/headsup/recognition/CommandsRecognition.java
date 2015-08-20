package com.headsup.recognition;

import java.util.ArrayList;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public class CommandsRecognition {

	private SpeechRecognizer mSpeechRecognizer;
	private Intent mRecognizerIntent;
	private CommandsListener mCommandsListener;

	public CommandsRecognition(Context context, boolean isCommand) {
		mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
		mSpeechRecognizer.setRecognitionListener(new MyRecognitionListener());
		mRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		//mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
		mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
		mRecognizerIntent.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[]{});
		mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
		mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		//mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 15);
		
		if(!isCommand) {
			mRecognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 10000);
			mRecognizerIntent.putExtra("android.speech.extra.GET_AUDIO_FORMAT", "audio/AMR");
			mRecognizerIntent.putExtra("android.speech.extra.GET_AUDIO", true);
		}
	}

	public void setCommandsListener(CommandsListener listener) {
		mCommandsListener = listener;
	}

	public void startListening() {
		mSpeechRecognizer.startListening(mRecognizerIntent);
	}

	public void stopListening() {
		mSpeechRecognizer.stopListening();
	}

	private class MyRecognitionListener implements RecognitionListener {

		@Override
		public void onError(int error) {
			String message;
			switch (error) {
			case SpeechRecognizer.ERROR_AUDIO:
				message = "Audio recording error";
				break;
			case SpeechRecognizer.ERROR_CLIENT:
				message = "Client side error";
				break;
			case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
				message = "Insufficient permissions";
				break;
			case SpeechRecognizer.ERROR_NETWORK:
				message = "Network error";
				break;
			case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
				message = "Network timeout";
				break;
			case SpeechRecognizer.ERROR_NO_MATCH:
				message = "No match";
				break;
			case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
				message = "RecognitionService busy";
				break;
			case SpeechRecognizer.ERROR_SERVER:
				message = "error from server";
				break;
			case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
				message = "No speech input";
				break;
			default:
				message = "Unspecified error";
			}
			mSpeechRecognizer.destroy();
			if (mCommandsListener != null) mCommandsListener.onError(error, message);
		}

		@Override
		public void onResults(Bundle results) {
			ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
			ArrayList<String> list = new ArrayList<String>();
			for (String result : matches) list.add(result);
			
			//Uri audioUri = results.getData();
		    //ContentResolver contentResolver = getContentResolver();
		    //InputStream filestream = contentResolver.openInputStream(audioUri);
			mSpeechRecognizer.destroy();
			if (mCommandsListener != null) mCommandsListener.onResult(list);
		}

		@Override
		public void onReadyForSpeech(Bundle params) {
		}

		@Override
		public void onBeginningOfSpeech() {
		}

		@Override
		public void onRmsChanged(float rmsdB) {
		}

		@Override
		public void onBufferReceived(byte[] buffer) {
		}

		@Override
		public void onEndOfSpeech() {
		}

		@Override
		public void onPartialResults(Bundle partialResults) {
		}

		@Override
		public void onEvent(int eventType, Bundle params) {
		}
	}

}
