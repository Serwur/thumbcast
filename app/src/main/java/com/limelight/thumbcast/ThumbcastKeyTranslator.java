package com.limelight.thumbcast;

// NOWY PLIK wstawiany do upstreamu moonlight-android (moduł :app).
// KeyTranslator (:control-android) delegujący do upstreamowego KeyboardTranslator, żeby nie
// duplikować tablic keycode Androida -> kod hosta.
//
// Zweryfikowane z upstreamem v12.2 (commit b48494cb96bf):
//   com.limelight.binding.input.KeyboardTranslator
//   @SuppressLint("InlinedApi") public short translate(int keycode, int deviceId)
// Metoda jest INSTANCYJNA i ma DWA parametry (nie ma scancode). Wynik to (0x80 << 8) | VK albo 0.
// deviceId = -1 pomija mapowanie per urządzenie: keycode jest traktowany jak QWERTY, tak samo jak
// w upstreamowym Game.keyboardEvent(boolean, short) (translate(keyCode, -1), flagi (byte) 0).

import com.limelight.binding.input.KeyboardTranslator;

import app.thumbcast.android.host.KeyTranslator;

public final class ThumbcastKeyTranslator implements KeyTranslator {

    private final KeyboardTranslator translator;

    public ThumbcastKeyTranslator(KeyboardTranslator translator) {
        this.translator = translator;
    }

    @Override
    public short toHostKeyCode(int androidKeyCode) {
        return translator.translate(androidKeyCode, -1);
    }
}
