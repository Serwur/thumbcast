package com.limelight.thumbcast;

// NOWY PLIK wstawiany do upstreamu moonlight-android (moduł :app).
// KeyTranslator (:control-android) delegujący do upstreamowego KeyboardTranslator, żeby nie
// duplikować tablic keycode Androida -> kod hosta (plan 2.3 pkt 4).
//
// UWAGA [?]: dokładnej sygnatury KeyboardTranslator.translate w v12.2 NIE widziałem.
// Znane mi warianty historyczne:
//   (a) instancyjna: short translate(int keycode, int scancode, int deviceId)
//   (b) statyczna:   static short translate(int keycode, int deviceId)
// Poniżej wariant (a); przy (b) zamienić wywołanie w toHostKeyCode().
// scancode = 0 i deviceId = -1: klawisz wirtualny, bez fizycznego urządzenia (upstream w takiej
// sytuacji powinien użyć samego keycode; sprawdzić, czy nie odrzuca deviceId < 0).

import com.limelight.binding.input.KeyboardTranslator;

import app.thumbcast.android.host.KeyTranslator;

public final class ThumbcastKeyTranslator implements KeyTranslator {

    private final KeyboardTranslator translator;

    public ThumbcastKeyTranslator(KeyboardTranslator translator) {
        this.translator = translator;
    }

    @Override
    public short toHostKeyCode(int androidKeyCode) {
        return translator.translate(androidKeyCode, 0, -1);
    }
}
