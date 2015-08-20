package com.headsup.recognition;

import java.util.ArrayList;

public interface CommandsListener {
	public void onResult(ArrayList<String> mResults);
	public void onError(int errorCode, String error);
}
