package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Jednorazowa podpowiedź przy starcie strumienia (G6, 12.11.2026): pigułka z ikoną menu
// i tekstem „Menu: przycisk w rogu albo przeciągnij od góry”. Pokazuje ją ThumbcastSession po nawiązaniu połączenia,
// gdy flaga w ThumbcastSettings jeszcze nie jest ustawiona; znika po ok. 4 s albo przy pierwszym otwarciu menu.
// Czysty element UI: nie bierze dotyku ani fokusu, niczego nie wysyła do hosta (FAIR_PLAY: timer tylko chowa napis).

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limelight.R;

import java.util.List;

import app.thumbcast.android.overlay.PxRect;

@SuppressLint("ViewConstructor")
final class ThumbcastMenuHintView extends LinearLayout {

    private static final int FADE_MS = 200;

    private final float density;

    ThumbcastMenuHintView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(16), dp(10), dp(20), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(context.getColor(R.color.thumbcast_menu_surface));
        bg.setStroke(dp(1), context.getColor(R.color.thumbcast_divider));
        bg.setCornerRadius(dp(24));
        setBackground(bg);
        setElevation(dp(4));

        ImageView icon = new ImageView(context);
        icon.setImageDrawable(context.getDrawable(R.drawable.thumbcast_ic_game_menu));
        icon.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.thumbcast_accent)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(icon, new LayoutParams(dp(20), dp(20)));

        TextView text = new TextView(context);
        text.setText(R.string.thumbcast_game_menu_hint);
        text.setTextColor(context.getColor(R.color.thumbcast_text));
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.thumbcast_text_body_small));
        LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(dp(10));
        addView(text, tlp);

        setClickable(false);
        setFocusable(View.NOT_FOCUSABLE);
        // Komunikat dla TalkBack (raz, przy pokazaniu), bez fokusu i dotyku.
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    }

    /** Parametry w rodzicu StreamView: rozmiar wg treści, położenie przez translację ({@link #place}). */
    static FrameLayout.LayoutParams layoutParams() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
    }

    /** Podpowiedź nigdy nie bierze dotyku: zdarzenie idzie dalej do overlaya i strumienia. */
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
     * Na środku w poziomie, możliwie wysoko (pod wcięciem), ale pod przyciskami, które leżą w tym miejscu
     * ({@code obstacles} w px rodzica). Gdy w górnej połowie nie ma miejsca: pod górną krawędzią.
     */
    void place(List<PxRect> obstacles) {
        View parent = (View) getParent();
        if (parent == null || parent.getWidth() <= 0) {
            return;
        }
        int pw = parent.getWidth();
        int ph = parent.getHeight();
        measure(MeasureSpec.makeMeasureSpec(Math.max(0, pw - dp(32)), MeasureSpec.AT_MOST),
                MeasureSpec.makeMeasureSpec(ph, MeasureSpec.AT_MOST));
        int w = getMeasuredWidth();
        int h = getMeasuredHeight();
        float x = (pw - w) / 2f;
        float top = topInset(parent) + dp(16);
        float y = top;
        for (int guard = 0; guard < 16; guard++) {
            float bottomOfHit = -1f;
            for (PxRect r : obstacles) {
                if (r.getLeft() < x + w && r.getRight() > x && r.getTop() < y + h && r.getBottom() > y) {
                    bottomOfHit = Math.max(bottomOfHit, r.getBottom());
                }
            }
            if (bottomOfHit < 0f) {
                break;
            }
            y = bottomOfHit + dp(8);
        }
        if (y + h > ph / 2f) {
            y = top;
        }
        setTranslationX(Math.round(x));
        setTranslationY(Math.round(y));
    }

    void fadeIn() {
        setAlpha(0f);
        animate().alpha(1f).setDuration(FADE_MS).start();
    }

    @SuppressWarnings("deprecation")
    private static int topInset(View parent) {
        WindowInsets insets = parent.getRootWindowInsets();
        if (insets == null) {
            return 0;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).top;
        }
        int t = insets.getSystemWindowInsetTop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && insets.getDisplayCutout() != null) {
            t = Math.max(t, insets.getDisplayCutout().getSafeInsetTop());
        }
        return t;
    }

    private int dp(int value) {
        return Math.round(value * density);
    }
}
