package com.limelight.thumbcast;

// NOWY PLIK wstawiany do upstreamu moonlight-android (moduł :app).
// Delegacja fasady HostPadReporter (:control-android) do ControllerHandler.reportOscState(...).
//
// Retransmisja: wg planu (2.2) powtórka TEGO SAMEGO stanu po 25/50/75 ms jest w upstreamie
// w VirtualController.sendControllerInputContext(), a NIE w reportOscState(). Ponieważ nasz overlay
// nie używa VirtualController, powtarzamy ją tutaj w identycznej postaci: ten sam stan, nigdy nowe
// naciśnięcie, a każdy nowy stan anuluje zaległe powtórki. To jedyny wyjątek od zasady
// „zmiana wyjścia tylko z dotyku” dopuszczony przez ActionPolicy (plan 6.2 pkt 1).
// Do potwierdzenia na kodzie upstreamu (patches/APPLY.md, „Założenia”).

import android.os.Handler;
import android.os.Looper;

import com.limelight.binding.input.ControllerHandler;

import app.thumbcast.android.host.HostPadReporter;

public final class HostPadReporterImpl implements HostPadReporter {

    private static final long[] RETRANSMIT_DELAYS_MS = {25, 50, 75};

    private final ControllerHandler controllerHandler;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean retransmit;

    private int buttons;
    private short lsX, lsY, rsX, rsY;
    private byte lt, rt;

    private final Runnable resend = new Runnable() {
        @Override
        public void run() {
            send();
        }
    };

    public HostPadReporterImpl(ControllerHandler controllerHandler, boolean retransmit) {
        this.controllerHandler = controllerHandler;
        this.retransmit = retransmit;
    }

    public HostPadReporterImpl(ControllerHandler controllerHandler) {
        this(controllerHandler, true);
    }

    @Override
    public void reportOscState(int buttonFlags, short leftStickX, short leftStickY,
                               short rightStickX, short rightStickY,
                               byte leftTrigger, byte rightTrigger) {
        this.buttons = buttonFlags;
        this.lsX = leftStickX;
        this.lsY = leftStickY;
        this.rsX = rightStickX;
        this.rsY = rightStickY;
        this.lt = leftTrigger;
        this.rt = rightTrigger;

        handler.removeCallbacks(resend);
        send();
        if (retransmit) {
            for (long delay : RETRANSMIT_DELAYS_MS) {
                handler.postDelayed(resend, delay);
            }
        }
    }

    /** Wołać przy zakończeniu sesji (Game.onDestroy / stopConnection). */
    public void cancelPending() {
        handler.removeCallbacks(resend);
    }

    private void send() {
        controllerHandler.reportOscState(buttons, lsX, lsY, rsX, rsY, lt, rt);
    }
}
