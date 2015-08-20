package com.headsup.speaking;

public interface SpeakingListener {
	public void onReady();
	public void onStart();
	public void onDone();
	public void onError();
}
