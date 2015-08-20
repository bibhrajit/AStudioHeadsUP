package com.headsup.bluetooth;

public interface OnMessageListener {
	public void onConnected();
	public void onDisconnected();
	public void onTextMessage(String text);
}
