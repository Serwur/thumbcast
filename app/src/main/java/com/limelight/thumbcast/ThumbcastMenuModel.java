package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Pozycje menu w grze (etykiety i akcje) bez widoku: ThumbcastMenuView je tylko rysuje,
// ThumbcastSession dostarcza stan i wykonuje akcje. Kolejność pozycji jak w dawnym AlertDialog.
// Od 12.11.2026 dwie strony: główna (build) i lista profili do zmiany w trakcie gry (buildProfiles).

import com.limelight.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ThumbcastMenuModel {

    /** Teksty z zasobów (Context.getString), żeby model nie zależał od widoku. */
    interface Strings {
        String get(int resId, Object... formatArgs);
    }

    /** Stan i akcje sesji, które menu pokazuje i wywołuje. */
    interface Target {
        void disconnect();

        boolean areControlsHidden();

        void toggleControls();

        /** Klawiatura ekranowa; wołane po zamknięciu menu. */
        void toggleKeyboard();

        int getScalePercent();

        /** Zmiana na żywo (overlay przelicza układ, menu zostaje otwarte). */
        void setScalePercent(int percent);

        boolean isHideOnGamepad();

        void setHideOnGamepad(boolean hide);

        /** Nazwa wyświetlana aktywnego profilu. */
        String getActiveProfileName();

        /** Strona z listą profili (menu zostaje otwarte). */
        void openProfiles();

        /** Powrót ze strony profili na stronę główną. */
        void showMainPage();

        /** Zmiana aktywnego profilu na żywo (wołane po zamknięciu menu); aktywny profil = nic. */
        void switchProfile(String fileName);

        /** Przełącznik „Gesty myszy” aktywnego profilu (stan efektywny). */
        boolean isMouseGestures();

        /** Zmiana na żywo i zapis dla pliku aktywnego profilu (menu zostaje otwarte). */
        void setMouseGestures(boolean enabled);
    }

    /**
     * Profil na stronie wyboru: plik (wartość preferencji list_thumbcast_profile) i nazwa; {@code active} = profil
     * tej sesji, {@code isDefault} = profil domyślny (preferencja), może być inny przy profilu aplikacji.
     */
    static final class ProfileChoice {
        final String fileName;
        final String name;
        final boolean active;
        final boolean isDefault;

        ProfileChoice(String fileName, String name, boolean active, boolean isDefault) {
            this.fileName = fileName;
            this.name = name;
            this.active = active;
            this.isDefault = isDefault;
        }
    }

    enum Kind {
        /** Wiersz-przycisk: jedna akcja. */
        BUTTON,
        /** Wiersz z etykietą i przyciskami − / +. */
        STEPPER
    }

    enum Style { NORMAL, DESTRUCTIVE, PRIMARY }

    static final class Item {
        final Kind kind;
        final Style style;
        final String label;
        /** BUTTON: true = najpierw zamknij menu, potem akcja; false = menu zostaje i odświeża etykiety. */
        final boolean closesMenu;
        /** BUTTON: akcja. */
        final Runnable action;
        /** STEPPER: krok w dół / w górę; null = przycisk nieaktywny (koniec zakresu). */
        final Runnable decrease;
        final Runnable increase;

        private Item(Kind kind, Style style, String label, boolean closesMenu, Runnable action,
                     Runnable decrease, Runnable increase) {
            this.kind = kind;
            this.style = style;
            this.label = label;
            this.closesMenu = closesMenu;
            this.action = action;
            this.decrease = decrease;
            this.increase = increase;
        }

        static Item button(Style style, String label, boolean closesMenu, Runnable action) {
            return new Item(Kind.BUTTON, style, label, closesMenu, action, null, null);
        }

        static Item stepper(String label, Runnable decrease, Runnable increase) {
            return new Item(Kind.STEPPER, Style.NORMAL, label, false, null, decrease, increase);
        }
    }

    private ThumbcastMenuModel() {
    }

    /** Pozycje menu dla bieżącego stanu. Po każdej akcji, która nie zamyka menu, sesja buduje listę od nowa. */
    static List<Item> build(Strings s, final Target t) {
        List<Item> items = new ArrayList<>();
        items.add(Item.button(Style.DESTRUCTIVE, s.get(R.string.thumbcast_menu_disconnect), true, t::disconnect));
        items.add(Item.button(Style.NORMAL, s.get(t.areControlsHidden()
                ? R.string.thumbcast_menu_show_controls : R.string.thumbcast_menu_hide_controls),
                true, t::toggleControls));
        items.add(Item.button(Style.NORMAL, s.get(R.string.thumbcast_menu_keyboard), true, t::toggleKeyboard));
        items.add(Item.button(Style.NORMAL, s.get(R.string.thumbcast_menu_profile, t.getActiveProfileName()),
                false, t::openProfiles));
        final boolean mouse = t.isMouseGestures();
        items.add(Item.button(Style.NORMAL, s.get(mouse
                ? R.string.thumbcast_menu_mouse_gestures_on : R.string.thumbcast_menu_mouse_gestures_off),
                false, () -> t.setMouseGestures(!mouse)));

        final int percent = t.getScalePercent();
        final int smaller = stepScale(percent, -1);
        final int larger = stepScale(percent, +1);
        items.add(Item.stepper(s.get(R.string.thumbcast_menu_control_size, percent),
                smaller != percent ? () -> t.setScalePercent(smaller) : null,
                larger != percent ? () -> t.setScalePercent(larger) : null));

        final boolean hide = t.isHideOnGamepad();
        items.add(Item.button(Style.NORMAL, s.get(hide
                ? R.string.thumbcast_menu_hide_on_gamepad_on : R.string.thumbcast_menu_hide_on_gamepad_off),
                false, () -> t.setHideOnGamepad(!hide)));
        items.add(Item.button(Style.PRIMARY, s.get(R.string.thumbcast_menu_resume), true, () -> { }));
        return Collections.unmodifiableList(items);
    }

    /**
     * Strona wyboru profilu: „Wstecz”, potem profile z biblioteki (presety, potem profile użytkownika). Aktywny jest
     * wyróżniony (PRIMARY, znacznik) i od niego startuje zaznaczenie z pada. Wybór zamyka menu i przełącza profil.
     */
    static List<Item> buildProfiles(Strings s, List<ProfileChoice> profiles, final Target t) {
        List<Item> items = new ArrayList<>();
        items.add(Item.button(Style.NORMAL, s.get(R.string.thumbcast_menu_back), false, t::showMainPage));
        for (final ProfileChoice p : profiles) {
            String label = p.active ? s.get(R.string.thumbcast_menu_profile_active, p.name) : p.name;
            if (p.isDefault) {
                label = s.get(R.string.thumbcast_menu_profile_default_marker, label);
            }
            items.add(Item.button(p.active ? Style.PRIMARY : Style.NORMAL, label,
                    true, () -> t.switchProfile(p.fileName)));
        }
        return Collections.unmodifiableList(items);
    }

    /**
     * Następna wartość z {@link ThumbcastSettings#SCALE_PERCENTS} w kierunku {@code direction} (-1 lub +1).
     * Wartość spoza listy (np. 90 z ręcznie zmienionych preferencji) idzie do najbliższej w tym kierunku.
     * Na końcu zakresu zwraca {@code current}.
     */
    static int stepScale(int current, int direction) {
        int[] values = ThumbcastSettings.SCALE_PERCENTS;
        if (direction > 0) {
            for (int v : values) {
                if (v > current) {
                    return v;
                }
            }
        } else if (direction < 0) {
            for (int i = values.length - 1; i >= 0; i--) {
                if (values[i] < current) {
                    return values[i];
                }
            }
        }
        return current;
    }
}
