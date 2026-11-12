package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Strony menu w grze (pozycje, sekcje, ikony i akcje) bez widoku: ThumbcastMenuView je tylko
// rysuje, ThumbcastSession dostarcza stan i wykonuje akcje.
//
// Od 12.11.2026 (przebudowa UI, pakiet P4, docs/ui-audit.md G1–G17) strona główna ma układ:
//   „Wróć do gry” (wypełniony przycisk, domyślne zaznaczenie z pada)
//   szybkie akcje: Klawiatura, Ukryj/Pokaż przyciski (kafle)
//   Sterowanie: Profil sterowania (→ strona profili), Gesty myszy (przełącznik + podpis zależny od profilu),
//               Rozmiar przycisków (− wartość +), Chowaj przyciski, gdy gram padem (przełącznik)
//   Obraz:      Statystyki (Wył. / Podstawowe / Szczegółowe; zapis bez zmian: dwie flagi StatsToggles)
//   Wyjście:    Wstrzymaj i wyjdź (rozłączenie, gra działa dalej), Zamknij grę na PC (→ potwierdzenie, /cancel)
// Strona profili: sekcje „Wbudowane” i „Moje”, aktywny z ikoną check, domyślny z gwiazdką.
// Potwierdzenie: ikona ostrzeżenia, tytuł i akapit (Page.intro*), „Anuluj” (domyślne zaznaczenie), „Zamknij grę”.

import com.limelight.R;

import app.thumbcast.android.stats.StatsMode;
import app.thumbcast.android.stats.StatsToggles;
import app.thumbcast.model.MouseGesturesTransform;

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
        /** „Wstrzymaj i wyjdź”: strumień zatrzymany, aplikacja na PC działa dalej (wznowienie z listy). */
        void disconnect();

        /** Strona potwierdzenia „Zamknij grę na PC” (menu zostaje otwarte). */
        void openEndSession();

        /** „Zamknij grę”: strumień zatrzymany, potem aplikacja na PC zamknięta (/cancel; wołane po zamknięciu menu). */
        void endSession();

        /** Stan nakładki statystyk (zapisany globalnie). */
        StatsToggles getStatsToggles();

        /** Zmiana na żywo i zapis (menu zostaje otwarte). */
        void setStatsToggles(StatsToggles toggles);

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

        /** Powrót z podstrony na stronę główną. */
        void showMainPage();

        /** Zmiana aktywnego profilu na żywo (wołane po zamknięciu menu); aktywny profil = nic. */
        void switchProfile(String fileName);

        /** Przełącznik „Gesty myszy” aktywnego profilu (stan efektywny). */
        boolean isMouseGestures();

        /** Rodzaj gestów aktywnego profilu (podpis pod przełącznikiem). */
        MouseGesturesTransform.Style getMouseGesturesStyle();

        /** Zmiana na żywo i zapis dla pliku aktywnego profilu (menu zostaje otwarte). */
        void setMouseGestures(boolean enabled);
    }

    /**
     * Profil na stronie wyboru: plik (wartość preferencji list_thumbcast_profile) i nazwa; {@code active} = profil
     * tej sesji, {@code isDefault} = profil domyślny (preferencja), może być inny przy profilu aplikacji;
     * {@code builtIn} = preset dostarczony z aplikacją (sekcja „Wbudowane”).
     */
    static final class ProfileChoice {
        final String fileName;
        final String name;
        final boolean active;
        final boolean isDefault;
        final boolean builtIn;

        ProfileChoice(String fileName, String name, boolean active, boolean isDefault, boolean builtIn) {
            this.fileName = fileName;
            this.name = name;
            this.active = active;
            this.isDefault = isDefault;
            this.builtIn = builtIn;
        }
    }

    /** Rodzaj wiersza: wygląd i to, co robią A, lewo i prawo z pada. */
    enum Kind {
        /** Wypełniony przycisk w akcencie (Wróć do gry). */
        PRIMARY,
        /** Przycisk z obrysem (Anuluj). */
        SECONDARY,
        /** Wypełniony przycisk w kolorze błędu (Zamknij grę). */
        DANGER,
        /** Kafel szybkiej akcji; kolejne kafle leżą w jednym rzędzie, lewo/prawo przechodzi między nimi. */
        TILE,
        /** Wiersz z ikoną, tytułem i opcjonalnym podpisem; {@link Item#danger} = kolor błędu. */
        ROW,
        /** Wiersz prowadzący na podstronę (wartość w podpisie, strzałka). */
        NAV,
        /** Przełącznik: A przełącza, lewo = wył., prawo = wł. */
        SWITCH,
        /** Wartość z przyciskami − / +: lewo/prawo zmienia. */
        STEPPER,
        /** Jedna z kilku opcji: lewo/prawo wybiera sąsiednią, A następną (z zawijaniem). */
        SEGMENTED,
        /** Profil na stronie profili ({@link Item#selected} = aktywny, {@link Item#starred} = domyślny). */
        CHOICE
    }

    /** Wybór opcji wiersza SEGMENTED. */
    interface OptionSelector {
        void select(int index);
    }

    static final class Item {
        final Kind kind;
        /** Sekcja (nagłówek nad pierwszym wierszem sekcji); null = bez sekcji (góra strony). */
        final String section;
        final int icon;
        final String title;
        /** Podpis pod tytułem (NAV: wartość); null = brak. */
        final String subtitle;
        /** ROW: kolor błędu (akcja nieodwracalna). */
        final boolean danger;
        /** true = najpierw zamknij menu, potem akcja; false = menu zostaje i odświeża wiersze. */
        final boolean closesMenu;
        /** Wiersz zaznaczany jako pierwszy przy nawigacji padem. */
        final boolean defaultFocus;
        /** Akcja (A, dotyk); SWITCH: przełączenie. */
        final Runnable action;
        /** SWITCH: stan; CHOICE: aktywny. */
        final boolean checked;
        /** CHOICE: profil domyślny (gwiazdka). */
        final boolean starred;
        /** STEPPER: wartość w pigułce; krok w dół / w górę, null = koniec zakresu. */
        final String value;
        final Runnable decrease;
        final Runnable increase;
        final String decreaseDescription;
        final String increaseDescription;
        /** SEGMENTED: opcje, wybrana i wybór. */
        final String[] options;
        final int selectedOption;
        final OptionSelector selector;

        private Item(Builder b) {
            kind = b.kind;
            section = b.section;
            icon = b.icon;
            title = b.title;
            subtitle = b.subtitle;
            danger = b.danger;
            closesMenu = b.closesMenu;
            defaultFocus = b.defaultFocus;
            action = b.action;
            checked = b.checked;
            starred = b.starred;
            value = b.value;
            decrease = b.decrease;
            increase = b.increase;
            decreaseDescription = b.decreaseDescription;
            increaseDescription = b.increaseDescription;
            options = b.options;
            selectedOption = b.selectedOption;
            selector = b.selector;
        }
    }

    /** Budowanie wierszy: tylko w tym pliku. */
    private static final class Builder {
        final Kind kind;
        final String title;
        String section;
        int icon;
        String subtitle;
        boolean danger;
        boolean closesMenu;
        boolean defaultFocus;
        Runnable action;
        boolean checked;
        boolean starred;
        String value;
        Runnable decrease;
        Runnable increase;
        String decreaseDescription;
        String increaseDescription;
        String[] options;
        int selectedOption;
        OptionSelector selector;

        Builder(Kind kind, String title) {
            this.kind = kind;
            this.title = title;
        }

        Builder section(String s) {
            section = s;
            return this;
        }

        Builder icon(int res) {
            icon = res;
            return this;
        }

        Builder subtitle(String s) {
            subtitle = s;
            return this;
        }

        Builder action(boolean closes, Runnable r) {
            closesMenu = closes;
            action = r;
            return this;
        }

        Item build() {
            return new Item(this);
        }
    }

    /** Strona menu: nagłówek, opcjonalny blok wstępu (potwierdzenie) i wiersze. */
    static final class Page {
        final String title;
        /** true = w nagłówku strzałka wstecz (podstrona); false = ikona zamknięcia (strona główna). */
        final boolean subPage;
        /** Blok nad wierszami (ikona, tytuł, akapit); introTitle null = brak. */
        final int introIcon;
        final String introTitle;
        final String introBody;
        final List<Item> items;

        Page(String title, boolean subPage, int introIcon, String introTitle, String introBody, List<Item> items) {
            this.title = title;
            this.subPage = subPage;
            this.introIcon = introIcon;
            this.introTitle = introTitle;
            this.introBody = introBody;
            this.items = Collections.unmodifiableList(items);
        }
    }

    private ThumbcastMenuModel() {
    }

    /** Strona główna dla bieżącego stanu. Po każdej akcji, która nie zamyka menu, sesja buduje ją od nowa. */
    static Page build(Strings s, String title, final Target t) {
        List<Item> items = new ArrayList<>();
        Builder resume = new Builder(Kind.PRIMARY, s.get(R.string.thumbcast_game_resume))
                .icon(R.drawable.thumbcast_ic_play_arrow).action(true, () -> { });
        resume.defaultFocus = true;
        items.add(resume.build());

        items.add(new Builder(Kind.TILE, s.get(R.string.thumbcast_game_keyboard))
                .icon(R.drawable.thumbcast_ic_keyboard).action(true, t::toggleKeyboard).build());
        boolean hidden = t.areControlsHidden();
        items.add(new Builder(Kind.TILE, s.get(hidden ? R.string.thumbcast_game_show_buttons
                : R.string.thumbcast_game_hide_buttons))
                .icon(hidden ? R.drawable.thumbcast_ic_visibility : R.drawable.thumbcast_ic_visibility_off)
                .action(true, t::toggleControls).build());

        String controls = s.get(R.string.thumbcast_game_section_controls);
        items.add(new Builder(Kind.NAV, s.get(R.string.thumbcast_game_profile)).section(controls)
                .icon(R.drawable.thumbcast_ic_sports_esports).subtitle(t.getActiveProfileName())
                .action(false, t::openProfiles).build());

        final boolean mouse = t.isMouseGestures();
        Builder mouseRow = new Builder(Kind.SWITCH, s.get(R.string.thumbcast_game_mouse_gestures)).section(controls)
                .icon(R.drawable.thumbcast_ic_mouse)
                .subtitle(s.get(mouseGesturesSubtitle(mouse, t.getMouseGesturesStyle())))
                .action(false, () -> t.setMouseGestures(!mouse));
        mouseRow.checked = mouse;
        items.add(mouseRow.build());

        final int percent = t.getScalePercent();
        final int smaller = stepScale(percent, -1);
        final int larger = stepScale(percent, +1);
        Builder size = new Builder(Kind.STEPPER, s.get(R.string.thumbcast_game_button_size)).section(controls)
                .icon(R.drawable.thumbcast_ic_format_size);
        size.value = s.get(R.string.thumbcast_game_button_size_value, percent);
        size.decrease = smaller != percent ? () -> t.setScalePercent(smaller) : null;
        size.increase = larger != percent ? () -> t.setScalePercent(larger) : null;
        size.decreaseDescription = s.get(R.string.thumbcast_game_button_smaller);
        size.increaseDescription = s.get(R.string.thumbcast_game_button_larger);
        items.add(size.build());

        final boolean hide = t.isHideOnGamepad();
        Builder hideRow = new Builder(Kind.SWITCH, s.get(R.string.thumbcast_game_hide_on_gamepad)).section(controls)
                .icon(R.drawable.thumbcast_ic_gamepad).action(false, () -> t.setHideOnGamepad(!hide));
        hideRow.checked = hide;
        items.add(hideRow.build());

        final StatsToggles stats = t.getStatsToggles();
        Builder statsRow = new Builder(Kind.SEGMENTED, s.get(R.string.thumbcast_game_stats))
                .section(s.get(R.string.thumbcast_game_section_display)).icon(R.drawable.thumbcast_ic_speed);
        statsRow.options = new String[]{s.get(R.string.thumbcast_game_stats_off),
                s.get(R.string.thumbcast_game_stats_basic), s.get(R.string.thumbcast_game_stats_detailed)};
        statsRow.selectedOption = statsIndex(stats.getMode());
        statsRow.selector = index -> t.setStatsToggles(stats.withMode(statsMode(index)));
        items.add(statsRow.build());

        String exit = s.get(R.string.thumbcast_game_section_exit);
        items.add(new Builder(Kind.ROW, s.get(R.string.thumbcast_game_pause_exit)).section(exit)
                .icon(R.drawable.thumbcast_ic_logout).subtitle(s.get(R.string.thumbcast_game_pause_exit_subtitle))
                .action(true, t::disconnect).build());
        Builder quit = new Builder(Kind.ROW, s.get(R.string.thumbcast_game_quit)).section(exit)
                .icon(R.drawable.thumbcast_ic_power_settings_new)
                .subtitle(s.get(R.string.thumbcast_game_quit_subtitle)).action(false, t::openEndSession);
        quit.danger = true;
        items.add(quit.build());
        return new Page(title, false, 0, null, null, items);
    }

    /**
     * Strona wyboru profilu: sekcje „Wbudowane” (presety) i „Moje” (profile użytkownika), w kolejności z biblioteki.
     * Aktywny ma ikonę check i od niego startuje zaznaczenie z pada; domyślny ma gwiazdkę. Wybór zamyka menu
     * i przełącza profil (wybór aktywnego tylko zamyka menu).
     */
    static Page buildProfiles(Strings s, String title, List<ProfileChoice> profiles, final Target t) {
        List<Item> items = new ArrayList<>();
        String builtIn = s.get(R.string.thumbcast_game_profiles_builtin);
        String mine = s.get(R.string.thumbcast_game_profiles_mine);
        for (int pass = 0; pass < 2; pass++) {
            boolean wantBuiltIn = pass == 0;
            for (final ProfileChoice p : profiles) {
                if (p.builtIn != wantBuiltIn) {
                    continue;
                }
                Builder b = new Builder(Kind.CHOICE, p.name).section(wantBuiltIn ? builtIn : mine)
                        .icon(p.active ? R.drawable.thumbcast_ic_check : 0)
                        .subtitle(profileSubtitle(s, p)).action(true, () -> t.switchProfile(p.fileName));
                b.checked = p.active;
                b.starred = p.isDefault;
                b.defaultFocus = p.active;
                items.add(b.build());
            }
        }
        return new Page(title, true, 0, null, null, items);
    }

    /**
     * Potwierdzenie „Zamknij grę na PC”: ostrzeżenie w bloku wstępu, „Anuluj” z domyślnym zaznaczeniem (przypadkowe
     * A nie zamyka gry), „Zamknij grę” zamyka menu i kończy sesję.
     */
    static Page buildEndSession(Strings s, final Target t) {
        List<Item> items = new ArrayList<>();
        Builder cancel = new Builder(Kind.SECONDARY, s.get(R.string.thumbcast_game_quit_cancel))
                .action(false, t::showMainPage);
        cancel.defaultFocus = true;
        items.add(cancel.build());
        items.add(new Builder(Kind.DANGER, s.get(R.string.thumbcast_game_quit_confirm))
                .icon(R.drawable.thumbcast_ic_power_settings_new).action(true, t::endSession).build());
        return new Page(s.get(R.string.thumbcast_game_quit), true, R.drawable.thumbcast_ic_warning,
                s.get(R.string.thumbcast_game_quit_title), s.get(R.string.thumbcast_game_quit_body), items);
    }

    private static String profileSubtitle(Strings s, ProfileChoice p) {
        if (p.active && p.isDefault) {
            return s.get(R.string.thumbcast_game_profile_in_use_default);
        }
        if (p.active) {
            return s.get(R.string.thumbcast_game_profile_in_use);
        }
        return p.isDefault ? s.get(R.string.thumbcast_game_profile_default) : null;
    }

    /** Krótki podpis przełącznika „Gesty myszy” (zamiast długiego komunikatu, G7). */
    static int mouseGesturesSubtitle(boolean enabled, MouseGesturesTransform.Style style) {
        switch (style) {
            case AT_POINT:
                return enabled ? R.string.thumbcast_game_mouse_camera_on : R.string.thumbcast_game_mouse_camera_off;
            case TOUCHPAD:
                return enabled ? R.string.thumbcast_game_mouse_touchpad_on : R.string.thumbcast_game_mouse_off;
            default:
                return enabled ? R.string.thumbcast_game_mouse_direct_on : R.string.thumbcast_game_mouse_off;
        }
    }

    /** Kolejność segmentów „Statystyki”: Wył., Podstawowe, Szczegółowe. */
    static int statsIndex(StatsMode mode) {
        switch (mode) {
            case BASIC:
                return 1;
            case ADVANCED:
                return 2;
            default:
                return 0;
        }
    }

    static StatsMode statsMode(int index) {
        switch (index) {
            case 1:
                return StatsMode.BASIC;
            case 2:
                return StatsMode.ADVANCED;
            default:
                return StatsMode.OFF;
        }
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
