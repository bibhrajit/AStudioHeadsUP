package com.headsup.statemachine;

public interface IState {

	void enter();
    void exit();
    void processMessage(int what, Object data);

}
