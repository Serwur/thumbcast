package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Nakładka statystyk strumienia w trybie Thumbcast (12.11.2026; od przebudowy UI 12 sp, kolory
// z palety thumbcast_colors.xml, linia podstawowa „60 FPS · 23 ms”): mały tekst na półprzezroczystym
// czarnym tle w prawym górnym rogu obszaru bezpiecznego (bez wycięcia ekranu i pasków), przesuwana tak, żeby nie
// zasłaniała kontrolek (StatsPlacement z :control-android). Tekst i tryb (podstawowy / szczegółowy) ustawia
// ThumbcastSession. Widok nie bierze dotyku ani fokusu i leży pod overlayem kontrolek w kolejności rysowania.

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.List;

import app.thumbcast.android.overlay.PxRect;
import app.thumbcast.android.stats.StatsPlacement;

@SuppressLint({"ViewConstructor", "AppCompatCustomView"})
final class ThumbcastStatsView extends TextView {

    /** 12 sp (G14, 12.11.2026; wcześniej 11 sp). */
    private static final float TEXT_SP = 12f;
    private static final int PADDING_H_DP = 8;
    private static final int PADDING_V_DP = 4;
    private static final int CORNER_DP = 8;
    /** Odstęp od krawędzi obszaru bezpiecznego i od kontrolek. */
    private static final int MARGIN_DP = 6;

    private final float density;

    ThumbcastStatsView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setTextColor(context.getColor(R.color.thumbcast_text));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP);
        setTypeface(Typeface.DEFAULT);
        // Cyfry o stałej szerokości: zmieniające się liczby nie przesuwają ramki co sekundę.
        setFontFeatureSettings("tnum");
        setIncludeFontPadding(false);
        setGravity(Gravity.START);
        setPadding(dp(PADDING_H_DP), dp(PADDING_V_DP), dp(PADDING_H_DP), dp(PADDING_V_DP));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(context.getColor(R.color.thumbcast_scrim));
        bg.setCornerRadius(dp(CORNER_DP));
        setBackground(bg);
        setClickable(false);
        setLongClickable(false);
        setFocusable(View.NOT_FOCUSABLE);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /** Parametry do dodania w rodzicu StreamView: rozmiar wg treści, pozycja przez translację. */
    static FrameLayout.LayoutParams layoutParams() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
    }

    /** Nakładka nigdy nie bierze dotyku: zdarzenie idzie dalej do overlaya i strumienia. */
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return false;
    }

    /**
     * Nowy tekst i położenie. {@code obstacles} = prostokąty kontrolek (i innych napisów) w px rodzica.
     * Rozmiar liczony od razu (measure), więc położenie pasuje do nowego tekstu w tej samej klatce.
     */
    void show(CharSequence text, List<PxRect> obstacles) {
        if (!text.toString().contentEquals(getText())) {
            setText(text);
        }
        View parent = (View) getParent();
        if (parent == null || parent.getWidth() <= 0 || parent.getHeight() <= 0) {
            return;
        }
        int pw = parent.getWidth();
        int ph = parent.getHeight();
        measure(MeasureSpec.makeMeasureSpec(pw, MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(ph, MeasureSpec.AT_MOST));
        StatsPlacement.Position p = StatsPlacement.place(pw, ph, safeArea(parent, pw, ph),
                getMeasuredWidth(), getMeasuredHeight(), dp(MARGIN_DP), obstacles);
        setTranslationX(Math.round(p.getX()));
        setTranslationY(Math.round(p.getY()));
    }

    /**
     * Obszar bezpieczny w układzie rodzica: rodzic (FrameLayout treści aktywności) wypełnia okno, więc wcięcia okna
     * (wycięcie ekranu, widoczne paski systemowe) odejmujemy od jego krawędzi.
     */
    @SuppressWarnings("deprecation")
    private static PxRect safeArea(View parent, int pw, int ph) {
        WindowInsets insets = parent.getRootWindowInsets();
        int l = 0;
        int t = 0;
        int r = 0;
        int b = 0;
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets i = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft();
                t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight();
                b = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
                    l = Math.max(l, insets.getDisplayCutout().getSafeInsetLeft());
                    t = Math.max(t, insets.getDisplayCutout().getSafeInsetTop());
                    r = Math.max(r, insets.getDisplayCutout().getSafeInsetRight());
                    b = Math.max(b, insets.getDisplayCutout().getSafeInsetBottom());
                }
            }
        }
        return new PxRect(l, t, pw - r, ph - b);
    }

    private int dp(int value) {
        return Math.round(value * density);
    }
}
