package com.limelight.thumbcast;

// NOWY PLIK wstawiany do upstreamu moonlight-android (moduł :app).
// Delegacja 1:1 fasady HostConnection (:control-android) do NvConnection.
// Sygnatury NvConnection wg planu, sekcja 2.2 [Z:6i]; NIE sprawdzone na kodzie w tej sesji.

import com.limelight.nvstream.NvConnection;

import app.thumbcast.android.host.HostConnection;

public final class HostConnectionImpl implements HostConnection {

    private final NvConnection conn;

    public HostConnectionImpl(NvConnection conn) {
        this.conn = conn;
    }

    @Override
    public void sendKeyboardInput(short keyMap, byte keyDirection, byte modifier, byte flags) {
        conn.sendKeyboardInput(keyMap, keyDirection, modifier, flags);
    }

    @Override
    public void sendMouseMove(short deltaX, short deltaY) {
        conn.sendMouseMove(deltaX, deltaY);
    }

    @Override
    public void sendMousePosition(short x, short y, short referenceWidth, short referenceHeight) {
        conn.sendMousePosition(x, y, referenceWidth, referenceHeight);
    }

    @Override
    public void sendMouseButtonDown(byte mouseButton) {
        conn.sendMouseButtonDown(mouseButton);
    }

    @Override
    public void sendMouseButtonUp(byte mouseButton) {
        conn.sendMouseButtonUp(mouseButton);
    }

    @Override
    public void sendMouseHighResScroll(short scrollAmount) {
        conn.sendMouseHighResScroll(scrollAmount);
    }
}
