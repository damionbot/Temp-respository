package com.Damion.Jarvis;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TimePicker;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int YELLOW = Color.parseColor("#FFD60A");
    private static final int DIM = Color.parseColor("#B8A56A");
    private static final int PANEL = Color.parseColor("#120E02");
    private static final int LINE = Color.parseColor("#5C4A00");
    private static final int ONLINE = Color.parseColor("#FFD60A");
    private static final int RED = Color.parseColor("#FF3B3B");
    private static final int DARK = Color.parseColor("#050505");

    private Handler ticker = new Handler(Looper.getMainLooper());
    private TextView netView, stateView, toggleBtn, feedView;
    private LinearLayout voiceCard, setupCard, aiCard;
    private TextToSpeech preview;
    private List<Voice> voices = new ArrayList<Voice>();
    private int voiceIdx = -1;

    private boolean setupActive = false;
    private long setupStart = 0;
    private long lastSetupCall = 0;

    private Runnable tick = new Runnable() {
        public void run() {
            if (JarvisService.active) addActiveSecond();
            refresh();
            ticker.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        preview = new TextToSpeech(this, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        touchStreak();
        loadActive();
        Smart.scheduleMorning(this);
        Smart.schedule(this);
        ticker.removeCallbacks(tick);
        ticker.post(tick);
        startTilt();
        startBreath();
        ticker.removeCallbacks(shineTick);
        ticker.postDelayed(shineTick, 1200);
        ticker.removeCallbacks(tipTick);
        ticker.postDelayed(tipTick, 3500);
        if (!countedUp) {
            countedUp = true;
            ticker.postDelayed(countUp, 900);
        }
        if (setupActive) continueSetup();
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        stopTilt();
        stopBreath();
        ticker.removeCallbacks(shineTick);
        ticker.removeCallbacks(tipTick);
        saveActive();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (setupActive) {
            ticker.postDelayed(new Runnable() {
                public void run() { continueSetup(); }
            }, 300);
        }
    }

    // ====== UI HELPERS ======

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable panel(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private TextView makeText(String s, int sp, int color, boolean mono) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (mono) t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    // ====== 3D LAYERED BUTTONS ======

    // A small vibration and a click sound the moment you touch a button.
    private void feel(View v) {
        v.setHapticFeedbackEnabled(true);
        v.setSoundEffectsEnabled(true);
        v.setCameraDistance(9000f * getResources().getDisplayMetrics().density);
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View view, android.view.MotionEvent e) {
                int a = e.getActionMasked();
                if (a == android.view.MotionEvent.ACTION_DOWN) {
                    view.animate().cancel();
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    view.playSoundEffect(android.view.SoundEffectConstants.CLICK);
                    view.setScaleX(0.97f);
                    view.setScaleY(0.97f);
                }
                if (a == android.view.MotionEvent.ACTION_DOWN || a == android.view.MotionEvent.ACTION_MOVE) {
                    // lean towards the spot you are pressing, like pushing a real slab
                    float hw = Math.max(1f, view.getWidth() / 2f), hh = Math.max(1f, view.getHeight() / 2f);
                    float nx = Math.max(-1f, Math.min(1f, (e.getX() - hw) / hw));
                    float ny = Math.max(-1f, Math.min(1f, (e.getY() - hh) / hh));
                    view.setRotationY(nx * 8f);
                    view.setRotationX(-ny * 8f);
                } else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
                    view.animate().rotationX(0f).rotationY(0f).scaleX(1f).scaleY(1f).setDuration(180).start();
                }
                return false;
            }
        });
    }

    private static final int RED_TOP = 0xFFFF5547;
    private static final int RED_BOT = 0xFFD10F0F;
    private static final int RED_EDGE = 0xFF6B0000;
    private static final int YEL_TOP = 0xFFFFE55C;
    private static final int YEL_BOT = 0xFFFFB800;
    private static final int YEL_EDGE = 0xFF8A5A00;

    // Three layers: dark edge underneath (the "thickness"), the coloured face,
    // and a glossy highlight. Pressing moves the face down so it looks pushed in.
    private LayerDrawable layers3d(int top, int bottom, int edge, int radiusDp, boolean pressed) {
        GradientDrawable e = new GradientDrawable();
        e.setColor(edge);
        e.setCornerRadius(dp(radiusDp));

        GradientDrawable f = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{top, bottom});
        f.setCornerRadius(dp(radiusDp));
        f.setStroke(dp(1), 0x88FFFFFF);

        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0x77FFFFFF, 0x00FFFFFF});
        g.setCornerRadius(dp(radiusDp));

        LayerDrawable ld = new LayerDrawable(new Drawable[]{e, f, g});
        int faceTop = pressed ? dp(4) : 0;
        int faceBottom = pressed ? dp(2) : dp(6);
        ld.setLayerInset(1, 0, faceTop, 0, faceBottom);
        ld.setLayerInset(2, dp(3), faceTop + dp(2), dp(3), faceBottom + dp(14));
        return ld;
    }

    private Drawable button3d(int top, int bottom, int edge, int radiusDp) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed},
            layers3d(top, bottom, edge, radiusDp, true));
        sl.addState(new int[]{}, layers3d(top, bottom, edge, radiusDp, false));
        return sl;
    }

    private Drawable redButton(int radiusDp) {
        return flatButton(0xFFFF5A4A, 0xFFC4161C, radiusDp);
    }

    private Drawable yellowButton(int radiusDp) {
        return flatButton(0xFFFFD86B, 0xFFFFA800, radiusDp);
    }

    private Drawable glass(int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{0xEE07131D, 0xEE03080D});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x6619E6FF);
        return g;
    }

    private static final int NEON = 0xFF19E6FF;
    private static final int GOLDC = 0xFFFFC24F;
    private static final int MUTED = 0xFF8FA6B2;

    private int withAlpha(int color, int a) {
        return (a << 24) | (color & 0xFFFFFF);
    }

    private int darken(int c, float f) {
        int r = (int) (((c >> 16) & 0xFF) * f);
        int g = (int) (((c >> 8) & 0xFF) * f);
        int b = (int) ((c & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private GradientDrawable flatLayer(int top, int bottom, int radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), 0x77FFFFFF);
        return g;
    }

    // A button with a thick dark edge underneath. When pressed, the face sinks onto the edge.
    private LayerDrawable slab(int top, int bottom, int radiusDp, boolean pressed) {
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(darken(bottom, 0.5f));
        edge.setCornerRadius(dp(radiusDp));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{edge, flatLayer(top, bottom, radiusDp)});
        ld.setLayerInset(1, 0, pressed ? dp(3) : 0, 0, pressed ? dp(1) : dp(4));
        return ld;
    }

    private Drawable flatButton(int top, int bottom, int radiusDp) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, slab(top, bottom, radiusDp, true));
        sl.addState(new int[]{}, slab(top, bottom, radiusDp, false));
        return sl;
    }

    private GradientDrawable glassLayer(int accent, int radiusDp, boolean pressed) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{withAlpha(accent, pressed ? 95 : 48), 0xDD07131D});
        g.setCornerRadius(dp(radiusDp));
        g.setStroke(dp(1), withAlpha(accent, pressed ? 255 : 150));
        return g;
    }

    // A thick glass slab: a bright face on top of a dark tinted edge.
    private LayerDrawable glassSlab(int accent, int radiusDp, boolean pressed) {
        GradientDrawable edge = new GradientDrawable();
        edge.setColor(darken(accent, 0.2f));
        edge.setCornerRadius(dp(radiusDp));
        edge.setStroke(dp(1), withAlpha(accent, 70));
        LayerDrawable ld = new LayerDrawable(new Drawable[]{edge, glassLayer(accent, radiusDp, pressed)});
        ld.setLayerInset(1, 0, pressed ? dp(3) : 0, 0, pressed ? dp(1) : dp(5));
        return ld;
    }

    private Drawable glassCard(int accent, int radiusDp) {
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, glassSlab(accent, radiusDp, true));
        sl.addState(new int[]{}, glassSlab(accent, radiusDp, false));
        return sl;
    }

    // A glass card: crisp icon, title and a line of small text. The small text is the card's tag,
    // so refresh() can change it later.
    private LinearLayout card(int kind, String title, String sub, int accent, View.OnClickListener l) {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        shineCards.add(c);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(21));
        c.setBackground(glassCard(accent, 18));
        c.addView(new IconView(this, kind, accent), new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView t = makeText(title, 14, 0xFFFFFFFF, false);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLetterSpacing(0.08f);
        t.setPadding(0, dp(12), 0, 0);
        TextView st = makeText(sub, 11, MUTED, false);
        st.setPadding(0, dp(3), 0, 0);
        c.addView(t);
        c.addView(st);
        c.setTag(st);
        c.setOnClickListener(l);
        feel(c);
        return c;
    }

    private LinearLayout makeCard(String title, String sub, boolean red, View.OnClickListener l) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(16), dp(14), dp(22));
        c.setBackground(red ? redButton(16) : yellowButton(16));
        TextView t = makeText(title, 14, red ? 0xFFFFFFFF : 0xFF1A1100, true);
        t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        TextView s = makeText(sub, 11, red ? 0xFFFFD9D2 : 0xFF5A4300, true);
        s.setPadding(0, dp(4), 0, 0);
        c.addView(t);
        c.addView(s);
        c.setTag(s);
        c.setOnClickListener(l);
        feel(c);
        return c;
    }

    // A card with a big symbol above the title, so the home screen reads at a glance.
    private LinearLayout tile(String glyph, String title, String sub, boolean red, View.OnClickListener l) {
        LinearLayout c = makeCard(title, sub, red, l);
        TextView g = makeText(glyph, 26, red ? 0xFFFFFFFF : 0xFF1A1100, false);
        g.setPadding(0, 0, 0, dp(6));
        c.addView(g, 0);
        return c;
    }

    private TextView subOf(LinearLayout card) {
        return (TextView) card.getTag();
    }

    private void addSingle(LinearLayout parent, LinearLayout card) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(5), dp(5), dp(5), dp(5));
        parent.addView(card, lp);
    }

    private void addRow(LinearLayout parent, LinearLayout a, LinearLayout b) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        la.setMargins(dp(5), dp(5), dp(5), dp(5));
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lb.setMargins(dp(5), dp(5), dp(5), dp(5));
        row.addView(a, la);
        row.addView(b, lb);
        parent.addView(row);
    }

    // ====== LIFE ON THE HOME SCREEN ======

    private static final int WHATS_NEW_ID = 1;     // raise this (and change the text below) after each update to show the card again
    private TextView statStreak, statBattery, statActive, statBatteryLabel;
    private int streakValue = 1, activeSecToday = 0, unsavedSec = 0, battPct = -1;
    private long activeDay = -1;
    private boolean battCharging = false, countedUp = false, statAnimating = false;

    private long dayNumber() {
        long now = System.currentTimeMillis();
        return (now + java.util.TimeZone.getDefault().getOffset(now)) / 86400000L;
    }

    // how many days in a row you have opened the app
    private void touchStreak() {
        SharedPreferences sp = getSharedPreferences("jarvis_home", 0);
        long today = dayNumber();
        long last = sp.getLong("lastDay", -1L);
        int streak = sp.getInt("streak", 0);
        if (last != today) {
            streak = (last == today - 1) ? streak + 1 : 1;
            sp.edit().putLong("lastDay", today).putInt("streak", streak).apply();
        }
        streakValue = Math.max(1, streak);
    }

    private void loadActive() {
        SharedPreferences sp = getSharedPreferences("jarvis_home", 0);
        long today = dayNumber();
        activeDay = today;
        activeSecToday = sp.getLong("activeDay", -1L) == today ? sp.getInt("activeSec", 0) : 0;
        unsavedSec = 0;
    }

    private void saveActive() {
        getSharedPreferences("jarvis_home", 0).edit()
            .putLong("activeDay", activeDay).putInt("activeSec", activeSecToday).apply();
        unsavedSec = 0;
    }

    private void addActiveSecond() {
        if (activeDay != dayNumber()) {
            activeDay = dayNumber();
            activeSecToday = 0;
        }
        activeSecToday++;
        if (++unsavedSec >= 15) saveActive();
    }

    private String activeText() {
        int m = activeSecToday / 60;
        return m < 60 ? m + "m" : (m / 60) + "h " + String.format(java.util.Locale.US, "%02d", m % 60) + "m";
    }

    private void readBattery() {
        Intent bi = registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi == null) return;
        int lv = bi.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
        int sc = bi.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, 100);
        battPct = sc > 0 && lv >= 0 ? lv * 100 / sc : -1;
        int st = bi.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
        battCharging = st == android.os.BatteryManager.BATTERY_STATUS_CHARGING
            || st == android.os.BatteryManager.BATTERY_STATUS_FULL;
    }

    private GlassCard statTile(String label, int accent, TextView[] out) {
        GlassCard c = new GlassCard(this, dp(14), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        c.setPadding(dp(6), dp(12), dp(6), dp(17));
        c.setBackground(glassCard(accent, 14));
        TextView v = makeText("--", 22, 0xFFFFFFFF, false);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        v.setGravity(Gravity.CENTER);
        v.setShadowLayer(dp(8), 0, 0, accent);
        TextView l = makeText(label, 8, MUTED, false);
        l.setLetterSpacing(0.2f);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, dp(2), 0, 0);
        c.addView(v);
        c.addView(l);
        out[0] = v;
        out[1] = l;
        return c;
    }

    private LinearLayout buildStatsRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView[] a = new TextView[2], b = new TextView[2], c = new TextView[2];
        GlassCard t1 = statTile("DAY STREAK", GOLDC, a);
        GlassCard t2 = statTile("BATTERY", NEON, b);
        GlassCard t3 = statTile("ACTIVE TODAY", NEON, c);
        statStreak = a[0];
        statBattery = b[0];
        statBatteryLabel = b[1];
        statActive = c[0];
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(5), 0, dp(5), 0);
        row.addView(t1, lp);
        row.addView(t2, new LinearLayout.LayoutParams(lp));
        row.addView(t3, new LinearLayout.LayoutParams(lp));
        return row;
    }

    // numbers count up from zero when you open the app
    private final Runnable countUp = new Runnable() {
        public void run() {
            statAnimating = true;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(900);
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    float f = (Float) a.getAnimatedValue();
                    statStreak.setText(String.valueOf(Math.round(streakValue * f)));
                    if (battPct >= 0) statBattery.setText(Math.round(battPct * f) + "%");
                    int m = Math.round(activeSecToday / 60f * f);
                    statActive.setText(m + "m");
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    statAnimating = false;
                }
            });
            va.start();
        }
    };

    // shown once after an update, until you tap GOT IT
    private View buildWhatsNew() {
        final GlassCard card = new GlassCard(this, dp(18), dp(5));
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(21));
        card.setBackground(glassCard(GOLDC, 18));
        TextView head = makeText("WHAT'S NEW", 11, GOLDC, false);
        head.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        head.setLetterSpacing(0.24f);
        card.addView(head);
        String[] items = {
            "Hologram Lab: finger skeleton hands, lit 3D shapes, colour themes, demo mode and a gesture guide",
            "New shapes: gear, turbine, orb, atom, DNA helix and a 4D tesseract",
            "Home screen: a living core with a battery ring, a daily streak and a clock",
            "The hand tracking model downloads itself the first time you open the lab"
        };
        for (String it : items) {
            TextView t = makeText("\u2022  " + it, 12, 0xFFDCEBF2, false);
            t.setPadding(0, dp(7), 0, 0);
            card.addView(t);
        }
        TextView ok = makeText("GOT IT", 12, 0xFF1A1100, false);
        ok.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        ok.setLetterSpacing(0.14f);
        ok.setBackground(yellowButton(16));
        ok.setPadding(dp(18), dp(8), dp(18), dp(10));
        feel(ok);
        ok.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                getSharedPreferences("jarvis_home", 0).edit().putInt("newSeen", WHATS_NEW_ID).apply();
                card.animate().alpha(0f).translationX(dp(80)).setDuration(260).withEndAction(new Runnable() {
                    public void run() { card.setVisibility(View.GONE); }
                }).start();
            }
        });
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        op.topMargin = dp(14);
        card.addView(ok, op);
        return card;
    }

    private int titleTaps = 0;
    private long lastTitleTap = 0;
    // ====== TODAY CARD, QUICK ACTIONS AND SCHEDULED CALLS ======

    private TextView nextBig, nextSub, mailLine;

    private View buildTodayCard() {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(20));
        c.setBackground(glassCard(NEON, 18));

        TextView head = makeText("NEXT CALL FROM JARVIS", 9, NEON, false);
        head.setLetterSpacing(0.24f);
        c.addView(head);

        nextBig = makeText("--", 30, 0xFFFFFFFF, false);
        nextBig.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        nextBig.setShadowLayer(dp(10), 0, 0, NEON);
        nextBig.setPadding(0, dp(4), 0, 0);
        c.addView(nextBig);

        nextSub = makeText("", 12, MUTED, false);
        nextSub.setPadding(0, dp(2), 0, dp(8));
        c.addView(nextSub);

        mailLine = makeText("", 12, 0xFFDCEBF2, false);
        mailLine.setPadding(0, dp(2), 0, dp(10));
        mailLine.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!MailListener.connected) Checks.openMail(MainActivity.this);
                else runQuick("YOUR EMAILS", "read my emails");
            }
        });
        c.addView(mailLine);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView add = makeText("+ NEW CALL", 12, 0xFF1A1100, false);
        add.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        add.setLetterSpacing(0.1f);
        add.setBackground(yellowButton(16));
        add.setPadding(dp(16), dp(8), dp(16), dp(11));
        feel(add);
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { newCallDialog(); }
        });
        TextView all = makeText("MY CALLS", 12, 0xFFFFFFFF, false);
        all.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        all.setLetterSpacing(0.1f);
        all.setBackground(panel(0x44000000, 0x8819E6FF, 16));
        all.setPadding(dp(16), dp(8), dp(16), dp(8));
        all.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showCalls(); }
        });
        row.addView(add);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ap.leftMargin = dp(10);
        row.addView(all, ap);
        c.addView(row);
        return c;
    }

    private void refreshToday() {
        if (nextBig == null) return;
        Calls.Call n = Calls.next(this);
        if (n == null) {
            nextBig.setText("No calls yet");
            nextSub.setText("Say \u201Ccall me at 6 AM\u201D, or tap + NEW CALL");
        } else {
            long now = System.currentTimeMillis();
            nextBig.setText(Calls.clock(n.hour, n.minute));
            String why = n.message.length() > 0 ? n.message : "your briefing";
            nextSub.setText("in " + Calls.countdownText(Calls.nextTime(n, now) - now) + "  \u00B7  " + why);
        }
        if (!MailListener.connected) {
            mailLine.setText("MAIL   tap here to let Jarvis see your emails  \u203A");
        } else {
            int unread = 0;
            synchronized (MailListener.emails) {
                for (String[] e : MailListener.emails) if (e[4].equals("0")) unread++;
            }
            mailLine.setText("MAIL   " + (unread == 0 ? "no new emails" : unread + (unread == 1 ? " unread email" : " unread emails")) + "  \u203A");
        }
    }

    private View buildQuickRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(4), 0, dp(4));
        String[][] actions = {
            {"BRIEF ME", "brief"}, {"WEATHER", "weather"}, {"NEWS", "news"}, {"EMAILS", "read my emails"},
            {"UPDATE", "update"}, {"NOTES", "read my notes"}, {"SPENDING", "how much did i spend today"}, {"WATER", "how much water have i had"}
        };
        for (final String[] a : actions) {
            TextView chip = makeText(a[0], 11, 0xFFFFFFFF, false);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            chip.setLetterSpacing(0.12f);
            chip.setBackground(panel(0x55000000, 0x8819E6FF, 18));
            chip.setPadding(dp(16), dp(9), dp(16), dp(9));
            feel(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { runQuick(a[0], a[1]); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        hs.addView(row);
        return hs;
    }

    // Runs a phrase the same way a voice command would and shows what Jarvis answers.
    private void runQuick(final String title, final String phrase) {
        toast("One moment, sir...");
        new Thread(new Runnable() {
            public void run() {
                String r = null;
                try {
                    if (phrase.equals("brief")) {
                        r = Briefing.build(MainActivity.this);
                    } else if (phrase.equals("update")) {
                        r = Daily.digest(MainActivity.this);
                    } else {
                        Brain b = new Brain();
                        r = b.handleLocal(MainActivity.this, phrase);
                        if (r == null) r = b.answerBeforeAi(MainActivity.this, phrase);
                    }
                } catch (Throwable t) {
                    r = null;
                }
                final String text = r == null ? "I couldn't get that just now, sir." : r;
                ticker.post(new Runnable() {
                    public void run() { showAnswer(title, text); }
                });
            }
        }).start();
    }

    private void showAnswer(String title, final String text) {
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle(title);
        b.setMessage(text);
        b.setPositiveButton("SPEAK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                if (JarvisService.instance != null) {
                    JarvisService.say(text);
                } else {
                    try {
                        Brain.applyVoice(MainActivity.this, preview);
                        preview.speak(text, TextToSpeech.QUEUE_FLUSH, null, "quick");
                    } catch (Throwable t) {
                        toast("I couldn't speak just now.");
                    }
                }
            }
        });
        b.setNegativeButton("CLOSE", null);
        b.show();
    }

    private void showCalls() {
        final ArrayList<Calls.Call> list = Calls.load(this);
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        sv.addView(box);
        if (list.isEmpty()) {
            TextView none = makeText("Nothing scheduled yet. Tap NEW CALL, or say \u201Ccall me at 6 AM\u201D.", 14, 0xFFDCEBF2, false);
            none.setPadding(0, dp(12), 0, dp(12));
            box.addView(none);
        }
        final AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        for (final Calls.Call c : list) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView big = makeText(c.kind == Calls.KIND_UPDATE ? "Update" : Calls.clock(c.hour, c.minute), 22, 0xFFFFFFFF, false);
            TextView small = makeText(Calls.describe(c), 12, MUTED, false);
            left.addView(big);
            left.addView(small);
            row.addView(left, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            Switch sw = new Switch(this);
            sw.setChecked(c.enabled);
            sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                    Calls.setEnabled(MainActivity.this, c.id, on);
                    refreshToday();
                }
            });
            row.addView(sw);
            TextView del = makeText("\u2715", 18, 0xFFFF6B6B, false);
            del.setPadding(dp(16), 0, 0, 0);
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Calls.remove(MainActivity.this, c.id);
                    refreshToday();
                    toast("Removed.");
                    showCalls();
                }
            });
            row.addView(del);
            box.addView(row);
        }
        b.setTitle("Scheduled calls");
        b.setView(sv);
        b.setPositiveButton("NEW CALL", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { newCallDialog(); }
        });
        b.setNegativeButton("CLOSE", null);
        b.show();
    }

    private void newCallDialog() {
        Calendar now = Calendar.getInstance();
        TimePickerDialog tp = new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
            new TimePickerDialog.OnTimeSetListener() {
                public void onTimeSet(TimePicker v, int h, int m) { askMessage(h, m); }
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
        tp.setTitle("What time should I call?");
        tp.show();
    }

    private void askMessage(final int h, final int m) {
        final EditText et = new EditText(this);
        et.setHint("What should I tell you? (optional)");
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF8FA6B2);
        et.setSingleLine(false);
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Call at " + Calls.clock(h, m));
        b.setMessage("Leave this empty and I'll give you the full briefing.");
        b.setView(et);
        b.setPositiveButton("NEXT", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { askDays(h, m, et.getText().toString().trim()); }
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    private void askDays(final int h, final int m, final String msg) {
        final String[] opts = {"Just once", "Every day", "Weekdays", "Weekends", "Pick days..."};
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("How often?");
        b.setItems(opts, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                if (which == 0) saveCall(h, m, 0, msg);
                else if (which == 1) saveCall(h, m, 127, msg);
                else if (which == 2) saveCall(h, m, 2 | 4 | 8 | 16 | 32, msg);
                else if (which == 3) saveCall(h, m, 1 | 64, msg);
                else pickDays(h, m, msg);
            }
        });
        b.show();
    }

    private void pickDays(final int h, final int m, final String msg) {
        final boolean[] on = new boolean[7];
        String[] names = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        AlertDialog.Builder b = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        b.setTitle("Which days?");
        b.setMultiChoiceItems(names, on, new DialogInterface.OnMultiChoiceClickListener() {
            public void onClick(DialogInterface d, int which, boolean checked) { on[which] = checked; }
        });
        b.setPositiveButton("SAVE", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) {
                int mask = 0;
                for (int i = 0; i < 7; i++) if (on[i]) mask |= 1 << i;
                saveCall(h, m, mask, msg);
            }
        });
        b.setNegativeButton("CANCEL", null);
        b.show();
    }

    private void saveCall(int h, int m, int days, String msg) {
        Calls.Call c = new Calls.Call();
        c.hour = h;
        c.minute = m;
        c.days = days;
        c.message = msg;
        c.brief = msg.length() == 0;
        if (days == 0) c.once = Calls.occurrence(h, m, 0, System.currentTimeMillis());
        Calls.add(this, c);
        String rep = Calls.daysText(days);
        toast("I'll call you at " + Calls.clock(h, m) + (rep.length() > 0 ? " " + rep : "") + ".");
        refreshToday();
    }

    // ====== PRIORITY PEOPLE, SMART CALLS AND WHAT YOU MISSED ======

    private Switch morningSwitch;
    private TextView morningTime, morningDaysChip, morningNext;
    private boolean morningBinding = false;

    private View buildMorningCard() {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(20));
        c.setBackground(glassCard(GOLDC, 18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = makeText("GOOD MORNING CALL", 14, 0xFFFFFFFF, false);
        t1.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t1.setLetterSpacing(0.08f);
        TextView t2 = makeText("Jarvis rings you with a brief of last night, then the weather, your day and the headlines.", 11, MUTED, false);
        t2.setPadding(0, dp(3), dp(10), 0);
        ht.addView(t1);
        ht.addView(t2);
        head.addView(ht, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        morningSwitch = new Switch(this);
        morningSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (morningBinding) return;
                Smart.setMorning(MainActivity.this, on, Smart.morningMinutes(MainActivity.this), Smart.morningDays(MainActivity.this));
                refreshMorning();
            }
        });
        head.addView(morningSwitch);
        c.addView(head);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(6));
        morningTime = settingChip("");
        morningTime.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int cur = Smart.morningMinutes(MainActivity.this);
                new TimePickerDialog(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                    new TimePickerDialog.OnTimeSetListener() {
                        public void onTimeSet(TimePicker tp, int h, int m) {
                            Smart.setMorning(MainActivity.this, true, h * 60 + m, Smart.morningDays(MainActivity.this));
                            refreshMorning();
                        }
                    }, cur / 60, cur % 60, false).show();
            }
        });
        morningDaysChip = settingChip("");
        morningDaysChip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int d = Smart.morningDays(MainActivity.this);
                int next = d == 127 ? (2 | 4 | 8 | 16 | 32) : (d == (2 | 4 | 8 | 16 | 32) ? (1 | 64) : 127);
                Smart.setMorning(MainActivity.this, Smart.morningOn(MainActivity.this), Smart.morningMinutes(MainActivity.this), next);
                refreshMorning();
            }
        });
        row.addView(morningTime);
        LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dp2.leftMargin = dp(8);
        row.addView(morningDaysChip, dp2);
        c.addView(row);

        morningNext = makeText("", 12, 0xFFDCEBF2, false);
        morningNext.setPadding(0, dp(2), 0, dp(10));
        c.addView(morningNext);

        TextView play = makeText("PLAY IT NOW", 12, 0xFF1A1100, false);
        play.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        play.setLetterSpacing(0.1f);
        play.setBackground(yellowButton(16));
        play.setPadding(dp(16), dp(8), dp(16), dp(11));
        feel(play);
        play.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast("Jarvis will call with your brief in a moment...");
                new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) {}
                        Smart.fireMorning(MainActivity.this, true, false);
                    }
                }).start();
            }
        });
        c.addView(play, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        refreshMorning();
        return c;
    }

    private void refreshMorning() {
        if (morningSwitch == null) return;
        boolean on = Smart.morningOn(this);
        morningBinding = true;
        if (morningSwitch.isChecked() != on) morningSwitch.setChecked(on);
        morningBinding = false;
        int m = Smart.morningMinutes(this);
        morningTime.setText(Smart.hhmm(m));
        styleChip(morningTime, on);
        morningDaysChip.setText(Smart.morningDaysText(this));
        styleChip(morningDaysChip, on);
        long t = Smart.nextMorning(this);
        if (!on || t == 0) {
            morningNext.setText("Off. Anything that comes in overnight will be told to you by a normal smart call instead.");
        } else {
            Calendar now = Calendar.getInstance();
            Calendar n = Calendar.getInstance();
            n.setTimeInMillis(t);
            boolean today = now.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == n.get(Calendar.YEAR);
            long mins = Math.max(0, (t - System.currentTimeMillis()) / 60000);
            String day = today ? "today" : (mins < 36 * 60 ? "tomorrow" : new java.text.SimpleDateFormat("EEEE", Locale.getDefault()).format(n.getTime()));
            morningNext.setText("Next: " + day + " at " + Smart.hhmm(m) + "  \u00B7  in " + Calls.countdownText(t - System.currentTimeMillis()));
        }
    }

    private LinearLayout vipRow, missedBox;
    private Switch smartSwitch;
    private TextView waitChip, quietChip;
    private TextView[] typeChips = new TextView[4];
    private String vipSig = "", missedSig = "";
    private boolean smartBinding = false;

    private int avatarColor(String s) {
        int[] palette = {0xFF1E88E5, 0xFF8E24AA, 0xFF00897B, 0xFFF4511E, 0xFF3949AB, 0xFFD81B60, 0xFF6D4C41, 0xFF039BE5};
        return palette[Math.abs(s.hashCode()) % palette.length];
    }

    private String ago(long t) {
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        if (m < 1440) return (m / 60) + "h ago";
        return (m / 1440) + "d ago";
    }

    private TextView avatar(String glyph, int color, int sizeDp, int ring) {
        TextView t = makeText(glyph, sizeDp > 40 ? 18 : 14, 0xFFFFFFFF, false);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{color, 0xFF0A1A2A});
        g.setShape(GradientDrawable.OVAL);
        g.setStroke(dp(2), ring);
        t.setBackground(g);
        t.setLayoutParams(new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return t;
    }

    private View buildPriorityRow() {
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        vipRow = new LinearLayout(this);
        vipRow.setOrientation(LinearLayout.HORIZONTAL);
        vipRow.setPadding(0, dp(4), 0, dp(4));
        hs.addView(vipRow);
        refreshVipRow(true);
        return hs;
    }

    private void refreshVipRow(boolean force) {
        if (vipRow == null) return;
        final ArrayList<String> vips = Smart.vips(this);
        final ArrayList<Smart.Item> pend = Smart.pending(this);
        StringBuilder sig = new StringBuilder();
        for (String v : vips) {
            int n = 0;
            for (Smart.Item it : pend) if (Smart.isVip(this, it.who) && Smart.norm(it.who).equals(Smart.norm(v))) n += it.count;
            sig.append(v).append(':').append(n).append(';');
        }
        if (!force && sig.toString().equals(vipSig)) return;
        vipSig = sig.toString();
        vipRow.removeAllViews();
        for (final String v : vips) {
            int n = 0;
            for (Smart.Item it : pend) if (Smart.norm(it.who).equals(Smart.norm(v)) || Smart.norm(it.who).startsWith(Smart.norm(v) + " ")) n += it.count;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(dp(6), 0, dp(6), 0);
            FrameLayout box = new FrameLayout(this);
            String n2 = Smart.norm(v);
            TextView av = avatar(n2.length() == 0 ? "?" : n2.substring(0, 1).toUpperCase(Locale.getDefault()), avatarColor(v), 52, n > 0 ? GOLDC : 0x8819E6FF);
            box.addView(av);
            if (n > 0) {
                TextView badge = makeText(String.valueOf(n), 10, 0xFF001018, false);
                badge.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
                badge.setGravity(Gravity.CENTER);
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(GOLDC);
                badge.setBackground(bg);
                FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(dp(20), dp(20));
                bl.gravity = Gravity.TOP | Gravity.END;
                box.addView(badge, bl);
            }
            cell.addView(box, new LinearLayout.LayoutParams(dp(52), dp(52)));
            String label = v.length() > 9 ? v.substring(0, 8) + "." : v;
            TextView name = makeText(label, 10, MUTED, false);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(4), 0, 0);
            cell.addView(name);
            cell.setOnClickListener(new View.OnClickListener() {
                public void onClick(View x) { showPerson(v); }
            });
            cell.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View x) {
                    new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle("Remove " + v + "?")
                        .setMessage("Jarvis will stop ringing you about their messages.")
                        .setPositiveButton("REMOVE", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int w) {
                                Smart.removeVip(MainActivity.this, v);
                                refreshVipRow(true);
                            }
                        })
                        .setNegativeButton("KEEP", null)
                        .show();
                    return true;
                }
            });
            vipRow.addView(cell);
        }
        // the + at the end
        LinearLayout addCell = new LinearLayout(this);
        addCell.setOrientation(LinearLayout.VERTICAL);
        addCell.setGravity(Gravity.CENTER_HORIZONTAL);
        addCell.setPadding(dp(6), 0, dp(6), 0);
        FrameLayout abox = new FrameLayout(this);
        abox.addView(avatar("+", 0xFF16303F, 52, 0x8819E6FF));
        addCell.addView(abox, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView an = makeText("ADD", 10, MUTED, false);
        an.setGravity(Gravity.CENTER);
        an.setPadding(0, dp(4), 0, 0);
        addCell.addView(an);
        addCell.setOnClickListener(new View.OnClickListener() {
            public void onClick(View x) { addVipDialog(); }
        });
        vipRow.addView(addCell);
    }

    private void addVipDialog() {
        final EditText et = new EditText(this);
        et.setHint("Name as it shows in WhatsApp or Messages");
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF8FA6B2);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Add an important person")
            .setMessage("If they message you and you don't see it, Jarvis will include it in his call.")
            .setView(et)
            .setPositiveButton("ADD", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String n = et.getText().toString().trim();
                    if (n.length() > 0) {
                        Smart.addVip(MainActivity.this, n);
                        refreshVipRow(true);
                    }
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void showPerson(String name) {
        ArrayList<Smart.Item> all = Smart.pending(this);
        all.addAll(Smart.history(this));
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (int i = all.size() - 1; i >= 0 && n < 5; i--) {
            Smart.Item it = all.get(i);
            String w = Smart.norm(it.who), v = Smart.norm(name);
            if (w.equals(v) || w.startsWith(v + " ") || v.startsWith(w + " ")) {
                sb.append(Smart.describeItem(it)).append("  (").append(ago(it.time)).append(")\n\n");
                n++;
            }
        }
        showAnswer(name, n == 0 ? "Nothing from " + name + " that I've caught recently, sir." : sb.toString().trim());
    }

    private void styleChip(TextView t, boolean on) {
        t.setTextColor(on ? 0xFFFFFFFF : 0xFF8FA6B2);
        t.setBackground(panel(on ? 0x5519E6FF : 0x33000000, on ? 0xCC19E6FF : 0x44FFFFFF, 18));
    }

    private TextView settingChip(String label) {
        TextView t = makeText(label, 11, 0xFFFFFFFF, false);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(13), dp(8), dp(13), dp(8));
        return t;
    }

    private View buildSmartCard() {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(20));
        c.setBackground(glassCard(GOLDC, 18));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView t1 = makeText("JARVIS CALLS YOU", 14, 0xFFFFFFFF, false);
        t1.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        t1.setLetterSpacing(0.08f);
        TextView t2 = makeText("Once, if something important is waiting and you haven't seen it. Then ask him anything.", 11, MUTED, false);
        t2.setPadding(0, dp(3), dp(10), 0);
        ht.addView(t1);
        ht.addView(t2);
        head.addView(ht, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        smartSwitch = new Switch(this);
        smartSwitch.setChecked(Smart.enabled(this));
        smartSwitch.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton v, boolean on) {
                if (smartBinding) return;
                Smart.setEnabled(MainActivity.this, on);
                refreshSmart();
            }
        });
        head.addView(smartSwitch);
        c.addView(head);

        // what to call about
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(4));
        String[] names = {"MESSAGES", "EMAILS", "MISSED CALLS", "ALERTS"};
        for (int i = 0; i < 4; i++) {
            final int type = i;
            TextView chip = settingChip(names[i]);
            typeChips[i] = chip;
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Smart.setTypeOn(MainActivity.this, type, !Smart.typeOn(MainActivity.this, type));
                    refreshSmart();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        hs.addView(row);
        c.addView(hs);

        // timing
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setPadding(0, dp(6), 0, dp(10));
        waitChip = settingChip("");
        waitChip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                int[] opts = {10, 20, 30, 45, 60, 90};
                int cur = Smart.waitMin(MainActivity.this);
                int next = opts[0];
                for (int i = 0; i < opts.length; i++) if (opts[i] > cur) { next = opts[i]; break; }
                Smart.setWaitMin(MainActivity.this, next);
                refreshSmart();
            }
        });
        quietChip = settingChip("");
        quietChip.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { pickQuiet(); }
        });
        quietChip.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                Smart.setQuiet(MainActivity.this, !Smart.quietOn(MainActivity.this), Smart.quietFrom(MainActivity.this), Smart.quietTo(MainActivity.this));
                refreshSmart();
                return true;
            }
        });
        row2.addView(waitChip);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        qp.leftMargin = dp(8);
        row2.addView(quietChip, qp);
        c.addView(row2);

        // try it
        LinearLayout row3 = new LinearLayout(this);
        row3.setOrientation(LinearLayout.HORIZONTAL);
        TextView test = makeText("TEST CALL", 12, 0xFF1A1100, false);
        test.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        test.setLetterSpacing(0.1f);
        test.setBackground(yellowButton(16));
        test.setPadding(dp(16), dp(8), dp(16), dp(11));
        feel(test);
        test.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast("Jarvis will call in a moment...");
                new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) {}
                        Smart.testCall(MainActivity.this);
                    }
                }).start();
            }
        });
        TextView now = makeText("CALL ME NOW", 12, 0xFFFFFFFF, false);
        now.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        now.setLetterSpacing(0.1f);
        now.setBackground(panel(0x44000000, 0x8819E6FF, 16));
        now.setPadding(dp(16), dp(8), dp(16), dp(8));
        now.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Smart.pending(MainActivity.this).isEmpty()) {
                    toast("You haven't missed anything important.");
                    return;
                }
                toast("Jarvis will call in a moment...");
                new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(1500); } catch (InterruptedException e) {}
                        Smart.fire(MainActivity.this, true, false);
                    }
                }).start();
            }
        });
        row3.addView(test);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        np.leftMargin = dp(10);
        row3.addView(now, np);
        c.addView(row3);
        refreshSmart();
        return c;
    }

    private void pickQuiet() {
        int from = Smart.quietFrom(this);
        new TimePickerDialog(this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
            new TimePickerDialog.OnTimeSetListener() {
                public void onTimeSet(TimePicker v, int h, int m) {
                    final int f = h * 60 + m;
                    int to = Smart.quietTo(MainActivity.this);
                    TimePickerDialog second = new TimePickerDialog(MainActivity.this, android.R.style.Theme_DeviceDefault_Dialog_Alert,
                        new TimePickerDialog.OnTimeSetListener() {
                            public void onTimeSet(TimePicker v2, int h2, int m2) {
                                Smart.setQuiet(MainActivity.this, true, f, h2 * 60 + m2);
                                refreshSmart();
                            }
                        }, to / 60, to % 60, false);
                    second.setTitle("No calls until...");
                    second.show();
                }
            }, from / 60, from % 60, false).show();
    }

    private View buildMissedCard() {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(20));
        c.setBackground(glassCard(NEON, 18));
        missedBox = new LinearLayout(this);
        missedBox.setOrientation(LinearLayout.VERTICAL);
        c.addView(missedBox);
        refreshMissed(true);
        return c;
    }

    private void refreshMissed(boolean force) {
        if (missedBox == null) return;
        ArrayList<Smart.Item> list = Smart.pending(this);
        StringBuilder sig = new StringBuilder();
        for (Smart.Item it : list) sig.append(it.key).append(it.count).append(it.text.hashCode()).append(';');
        if (!force && sig.toString().equals(missedSig)) return;
        missedSig = sig.toString();
        missedBox.removeAllViews();
        if (list.isEmpty()) {
            TextView none = makeText("You're all caught up. Nothing important is waiting.", 13, 0xFFDCEBF2, false);
            none.setPadding(0, dp(4), 0, dp(4));
            missedBox.addView(none);
            return;
        }
        int shown = 0;
        for (int i = list.size() - 1; i >= 0 && shown < 5; i--, shown++) {
            Smart.Item it = list.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(6), 0, dp(6));
            String glyph = it.type == Smart.T_MAIL ? "@" : (it.type == Smart.T_MISSED ? "\u2715" : (it.type == Smart.T_ALERT ? "!" : Smart.norm(it.who).length() > 0 ? Smart.norm(it.who).substring(0, 1).toUpperCase(Locale.getDefault()) : "?"));
            FrameLayout ab = new FrameLayout(this);
            ab.addView(avatar(glyph, it.type == Smart.T_MISSED ? 0xFFE53935 : (it.type == Smart.T_ALERT ? 0xFFFFA000 : avatarColor(it.who)), 38, it.urgent ? GOLDC : 0x8819E6FF));
            row.addView(ab, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setPadding(dp(12), 0, 0, 0);
            TextView who = makeText(it.who + (it.count > 1 ? "  \u00B7  " + it.count : ""), 13, 0xFFFFFFFF, false);
            who.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            who.setSingleLine(true);
            String body = it.type == Smart.T_MISSED ? "Missed call" : it.text;
            TextView what = makeText(body + "  \u00B7  " + ago(it.time), 11, MUTED, false);
            what.setSingleLine(true);
            what.setEllipsize(android.text.TextUtils.TruncateAt.END);
            col.addView(who);
            col.addView(what);
            row.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            missedBox.addView(row);
        }
        TextView clear = makeText("CLEAR ALL", 11, 0xFFFF7B72, false);
        clear.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        clear.setLetterSpacing(0.14f);
        clear.setPadding(0, dp(10), 0, 0);
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Smart.clearPending(MainActivity.this);
                refreshMissed(true);
                refreshVipRow(true);
            }
        });
        missedBox.addView(clear);
    }

    private void refreshSmart() {
        if (smartSwitch == null) return;
        boolean on = Smart.enabled(this);
        smartBinding = true;
        if (smartSwitch.isChecked() != on) smartSwitch.setChecked(on);
        smartBinding = false;
        for (int i = 0; i < 4; i++) if (typeChips[i] != null) styleChip(typeChips[i], on && Smart.typeOn(this, i));
        waitChip.setText("WAIT " + Smart.waitMin(this) + " MIN");
        styleChip(waitChip, on);
        quietChip.setText(Smart.quietOn(this) ? "QUIET " + Smart.hhmm(Smart.quietFrom(this)) + " - " + Smart.hhmm(Smart.quietTo(this)) : "QUIET HOURS OFF");
        styleChip(quietChip, on && Smart.quietOn(this));
        refreshVipRow(false);
        refreshMissed(false);
        refreshMorning();
    }

    // ====== MONEY ======

    private MoneyRing moneyRing;
    private Spark moneySpark;
    private TextView moneyEarned, moneySub, moneyMove, moneyDone;
    private String moneySig = "";
    private long moneySeenIncome = 0;

    private interface MoneyTask {
        String run();
    }

    private EditText moneyField(String hint, boolean number) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF8FA6B2);
        if (number) et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        return et;
    }

    private LinearLayout moneyForm(EditText... fields) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        for (EditText f : fields) box.addView(f);
        return box;
    }

    private View buildMoneyCard() {
        GlassCard c = new GlassCard(this, dp(18), dp(5));
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(18), dp(14), dp(18), dp(20));
        c.setBackground(glassCard(GOLDC, 18));

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        moneyRing = new MoneyRing(this);
        top.addView(moneyRing, new LinearLayout.LayoutParams(dp(104), dp(104)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), 0, 0, 0);
        TextView lab = makeText("EARNED THIS MONTH", 9, GOLDC, false);
        lab.setLetterSpacing(0.22f);
        moneyEarned = makeText("0", 30, 0xFFFFFFFF, false);
        moneyEarned.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        moneyEarned.setShadowLayer(dp(10), 0, 0, GOLDC);
        moneySub = makeText("", 12, MUTED, false);
        col.addView(lab);
        col.addView(moneyEarned);
        col.addView(moneySub);
        top.addView(col, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        top.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { goalDialog(); }
        });
        c.addView(top);

        moneySpark = new Spark(this);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        sp2.topMargin = dp(8);
        c.addView(moneySpark, sp2);

        // today's step of the plan
        LinearLayout moveRow = new LinearLayout(this);
        moveRow.setOrientation(LinearLayout.HORIZONTAL);
        moveRow.setGravity(Gravity.CENTER_VERTICAL);
        moveRow.setPadding(0, dp(10), 0, dp(4));
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        TextView ml = makeText("YOUR MONEY MOVE", 9, NEON, false);
        ml.setLetterSpacing(0.22f);
        moneyMove = makeText("", 13, 0xFFDCEBF2, false);
        mt.addView(ml);
        mt.addView(moneyMove);
        moveRow.addView(mt, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        moneyDone = makeText("DONE", 11, 0xFF1A1100, false);
        moneyDone.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        moneyDone.setLetterSpacing(0.12f);
        moneyDone.setBackground(yellowButton(14));
        moneyDone.setPadding(dp(14), dp(7), dp(14), dp(9));
        moneyDone.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Money.completeMove(MainActivity.this);
                moneyRing.burst();
                refreshMoney();
            }
        });
        moveRow.addView(moneyDone);
        c.addView(moveRow);

        // the tools
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, dp(4));
        String[] names = {"+ INCOME", "IDEAS", "SELL SOMETHING", "WRITE A PITCH", "SCAM CHECK", "MY LEADS", "MY PLAN"};
        for (int i = 0; i < names.length; i++) {
            final int which = i;
            TextView chip = makeText(names[i], 11, 0xFFFFFFFF, false);
            chip.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            chip.setLetterSpacing(0.1f);
            chip.setBackground(panel(0x55000000, 0x88FFC24F, 18));
            chip.setPadding(dp(15), dp(9), dp(15), dp(9));
            feel(chip);
            chip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { moneyAction(which); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            row.addView(chip, lp);
        }
        hs.addView(row);
        c.addView(hs);

        TextView note = makeText("Jarvis can't earn for you. He finds honest ways that cost nothing, writes the words, tracks your leads and keeps you on track.", 10, MUTED, false);
        note.setPadding(0, dp(8), 0, 0);
        c.addView(note);
        refreshMoney();
        return c;
    }

    private void moneyAction(int which) {
        if (which == 0) incomeDialog();
        else if (which == 1) ideasFlow();
        else if (which == 2) sellDialog();
        else if (which == 3) pitchDialog();
        else if (which == 4) scamDialog();
        else if (which == 5) leadsDialog();
        else planDialog();
    }

    private void runMoney(final String title, final MoneyTask task) {
        toast("One moment, sir...");
        new Thread(new Runnable() {
            public void run() {
                String r;
                try {
                    r = task.run();
                } catch (Throwable t) {
                    r = "I couldn't do that just now, sir.";
                }
                final String text = r;
                ticker.post(new Runnable() {
                    public void run() {
                        showAnswer(title, text);
                        refreshMoney();
                    }
                });
            }
        }).start();
    }

    private void incomeDialog() {
        final EditText amt = moneyField("Amount", true);
        final EditText src = moneyField("From what? (optional)", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Log what you earned")
            .setView(moneyForm(amt, src))
            .setPositiveButton("LOG IT", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        double v = Double.parseDouble(amt.getText().toString().trim());
                        if (v > 0) {
                            Money.addIncome(MainActivity.this, v, src.getText().toString().trim());
                            moneyRing.burst();
                            refreshMoney();
                            toast("Logged, sir.");
                        }
                    } catch (Exception e) {
                        toast("Type the amount as a number.");
                    }
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void goalDialog() {
        final EditText g = moneyField("Monthly goal, for example 500", true);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Your monthly income goal")
            .setView(moneyForm(g))
            .setPositiveButton("SET", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    try {
                        Money.setGoal(MainActivity.this, (int) Double.parseDouble(g.getText().toString().trim()));
                        refreshMoney();
                    } catch (Exception e) {
                        toast("Type the goal as a number.");
                    }
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void ideasFlow() {
        if (Money.hasProfile(this)) {
            runMoney("HONEST WAYS TO START FREE", new MoneyTask() {
                public String run() { return Money.makeIdeas(MainActivity.this); }
            });
            return;
        }
        final EditText skills = moneyField("What are you good at?", false);
        final EditText hours = moneyField("Hours a day you could spare", true);
        final EditText where = moneyField("Where are you based?", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Three quick questions")
            .setMessage("So the ideas fit you. Nothing here costs a thing.")
            .setView(moneyForm(skills, hours, where))
            .setPositiveButton("FIND IDEAS", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    int h = 1;
                    try { h = (int) Math.max(1, Double.parseDouble(hours.getText().toString().trim())); } catch (Exception e) {}
                    Money.setProfile(MainActivity.this, skills.getText().toString().trim().length() > 0 ? skills.getText().toString().trim() : "general skills", h, where.getText().toString().trim());
                    runMoney("HONEST WAYS TO START FREE", new MoneyTask() {
                        public String run() { return Money.makeIdeas(MainActivity.this); }
                    });
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void sellDialog() {
        final EditText item = moneyField("What do you want to sell?", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Sell something")
            .setMessage("I'll write the listing and copy it for you.")
            .setView(moneyForm(item))
            .setPositiveButton("WRITE IT", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final String it = item.getText().toString().trim();
                    if (it.length() == 0) return;
                    runMoney("LISTING (COPIED)", new MoneyTask() {
                        public String run() { return Money.makeListing(MainActivity.this, it); }
                    });
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void pitchDialog() {
        final EditText service = moneyField("What do you offer?", false);
        final EditText who = moneyField("Who is it for? (optional)", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Write a pitch")
            .setView(moneyForm(service, who))
            .setPositiveButton("WRITE IT", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final String sv = service.getText().toString().trim();
                    final String wh = who.getText().toString().trim();
                    if (sv.length() == 0) return;
                    runMoney("PITCH (COPIED)", new MoneyTask() {
                        public String run() { return Money.makePitch(MainActivity.this, sv, wh); }
                    });
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void scamDialog() {
        final EditText text = moneyField("Paste the message or offer here", false);
        text.setMinLines(3);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Is it a scam?")
            .setMessage("Never pay anything to receive money.")
            .setView(moneyForm(text))
            .setPositiveButton("CHECK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    final String t = text.getText().toString().trim();
                    if (t.length() == 0) return;
                    runMoney("SCAM CHECK", new MoneyTask() {
                        public String run() { return Money.scamCheck(MainActivity.this, t); }
                    });
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void leadsDialog() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Your leads")
            .setMessage(Money.pipelineText(this) + "\n\nSay, for example: I pitched Sam. Or: Sam said yes. I'll ring you to follow up.")
            .setPositiveButton("ADD A LEAD", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { addLeadDialog(); }
            })
            .setNegativeButton("CLOSE", null)
            .show();
    }

    private void addLeadDialog() {
        final EditText name = moneyField("Who?", false);
        final EditText what = moneyField("What do they need?", false);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("New lead")
            .setView(moneyForm(name, what))
            .setPositiveButton("ADD", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String n = name.getText().toString().trim();
                    if (n.length() > 0) {
                        Money.addLead(MainActivity.this, n, what.getText().toString().trim());
                        toast("Added.");
                    }
                }
            })
            .setNegativeButton("CANCEL", null)
            .show();
    }

    private void planDialog() {
        ArrayList<Money.Step> plan = Money.plan(this);
        if (plan.isEmpty()) {
            ideasFlow();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Money.Step st : plan) sb.append(st.done ? "\u2713  " : "\u25CB  ").append(st.text).append("\n\n");
        String ideas = Money.ideas(this);
        showAnswer("YOUR 7 DAY PLAN", (ideas.length() > 0 ? ideas + "\n\n" : "") + sb.toString().trim());
    }

    private void refreshMoney() {
        if (moneyRing == null) return;
        double earned = Money.earnedMonth(this), spent = Money.spentMonth(this);
        int goal = Money.goal(this);
        String move = Money.todaysMove(this);
        long lastIn = Money.lastIncomeAt(this);
        String sig = earned + "|" + spent + "|" + goal + "|" + move;
        if (lastIn > moneySeenIncome) {
            if (moneySeenIncome != 0) moneyRing.burst();
            moneySeenIncome = lastIn;
        }
        if (sig.equals(moneySig)) return;
        moneySig = sig;
        String cur = Money.cur(this);
        String e = earned == Math.floor(earned) ? String.valueOf((long) earned) : String.format(Locale.US, "%.2f", earned);
        moneyEarned.setText(e);
        float pct = goal > 0 ? (float) (earned / goal) : 0f;
        moneyRing.set(pct, goal > 0 ? Math.round(Math.min(999, pct * 100)) + "%" : "GOAL", goal > 0 ? "OF " + goal : "TAP TO SET");
        String net = earned - spent >= 0 ? "+" + (long) (earned - spent) : "-" + (long) (spent - earned);
        moneySub.setText(cur + "  \u00B7  spent " + (long) spent + "  \u00B7  net " + net);
        moneySpark.set(Money.last14(this));
        if (move.length() > 0) {
            moneyMove.setText(move.replaceAll("^Day \\d+:\\s*", ""));
            moneyDone.setVisibility(View.VISIBLE);
        } else {
            moneyMove.setText("No plan yet. Tap IDEAS and I'll make one that costs nothing.");
            moneyDone.setVisibility(View.GONE);
        }
    }

    private View glowRing;
    private GradientDrawable glowDrawable;
    private ValueAnimator glowBreath;
    private boolean glowWasActive = false;

    private View sectionLabel(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(16), dp(6), dp(4));
        TextView t = makeText(label, 9, NEON, false);
        t.setLetterSpacing(0.32f);
        row.addView(t);
        View rule = new View(this);
        rule.setBackgroundColor(0x3319E6FF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(1), 1f);
        lp.leftMargin = dp(10);
        row.addView(rule, lp);
        return row;
    }

    private void startBreath() {
        if (glowBreath != null) glowBreath.cancel();
        glowBreath = ValueAnimator.ofFloat(0.35f, 1f);
        glowBreath.setDuration(1400);
        glowBreath.setRepeatMode(ValueAnimator.REVERSE);
        glowBreath.setRepeatCount(ValueAnimator.INFINITE);
        glowBreath.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                if (glowRing != null) glowRing.setAlpha((Float) a.getAnimatedValue());
            }
        });
        glowBreath.start();
    }

    private void stopBreath() {
        if (glowBreath != null) {
            glowBreath.cancel();
            glowBreath = null;
        }
    }

    private CoreView coreView;
    private TextView clockView, dateView, tipView;
    private final ArrayList<GlassCard> shineCards = new ArrayList<GlassCard>();
    private int shineIdx = 0, tipIdx = 1;
    private static final String[] TIPS = {
        "say \u201CHey Jarvis, open the hologram\u201D",
        "say \u201Cspawn a tesseract\u201D in the hologram lab",
        "say \u201Ctheme red\u201D to recolour the holograms",
        "say \u201Cexplode it\u201D, then \u201Cput it back together\u201D",
        "say \u201Cdemo mode on\u201D and watch it show off",
        "say \u201Cadd a red cube\u201D in build mode",
        "say \u201Ctake a snapshot\u201D to save a picture",
        "hold your finger on the glowing core to switch Jarvis on",
        "say \u201Cwhat can you do?\u201D"
    };

    // every couple of seconds a bright band of light sweeps across the next card
    private final Runnable shineTick = new Runnable() {
        public void run() {
            if (!shineCards.isEmpty()) {
                final GlassCard g = shineCards.get(shineIdx++ % shineCards.size());
                ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
                va.setDuration(900);
                va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    public void onAnimationUpdate(ValueAnimator a) {
                        g.setShine((Float) a.getAnimatedValue());
                    }
                });
                va.addListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        g.setShine(-1f);
                    }
                });
                va.start();
            }
            ticker.postDelayed(this, 1800);
        }
    };

    private final Runnable tipTick = new Runnable() {
        public void run() {
            if (tipView != null) {
                tipView.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                    public void run() {
                        tipView.setText("TRY  \u203A  " + TIPS[tipIdx++ % TIPS.length]);
                        tipView.animate().alpha(1f).setDuration(250).start();
                    }
                }).start();
            }
            ticker.postDelayed(this, 4200);
        }
    };

    // ====== TILT WITH THE PHONE ======

    private View sceneView, backdropView;
    private float tiltAvgZ = 0f, tiltX = 0f, tiltY = 0f;
    private boolean tiltSeeded = false;
    private final SensorEventListener tiltListener = new SensorEventListener() {
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}

        public void onSensorChanged(SensorEvent e) {
            if (sceneView == null) return;
            float ax = e.values[0], az = e.values[2];
            if (!tiltSeeded) {
                tiltSeeded = true;
                tiltAvgZ = az;
            }
            // "flat" is however you normally hold the phone, learned slowly
            tiltAvgZ += (az - tiltAvgZ) * 0.004f;
            float tx = Math.max(-0.4f, Math.min(0.4f, ax / 9.81f));
            float ty = Math.max(-0.4f, Math.min(0.4f, (az - tiltAvgZ) / 9.81f));
            tiltX += (tx - tiltX) * 0.12f;
            tiltY += (ty - tiltY) * 0.12f;
            // only the faint background drifts now. The cards themselves stay perfectly still.
            if (backdropView != null) {
                backdropView.setTranslationX(tiltX * dp(14));
                backdropView.setTranslationY(-tiltY * dp(14));
            }
        }
    };

    private void startTilt() {
        try {
            SensorManager sm = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null) sm.registerListener(tiltListener, a, SensorManager.SENSOR_DELAY_GAME);
        } catch (Throwable t) {
            // no tilt effect on this phone
        }
    }

    private void stopTilt() {
        try {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).unregisterListener(tiltListener);
        } catch (Throwable t) {
            // ignore
        }
    }

    // ====== BUILD UI ======

    // Night sky: a painted background drawn once, with tiny lights drifting up through it,
    // a warm glow in one corner and a faint beam sweeping down.
    private static class BackdropView extends View {
        private static final int N = 56;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Shader auroraA, auroraB;
        private Bitmap base;
        private final float dens;
        private final float[] px = new float[N], py = new float[N], ps = new float[N], pr = new float[N], pph = new float[N];
        private long last = 0;
        private float t = 0f;
        private boolean seeded = false;

        BackdropView(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            if (w == 0 || h == 0) return;
            auroraA = new RadialGradient(0f, 0f, w * 0.7f, new int[]{0x2619E6FF, 0x0019E6FF}, null, Shader.TileMode.CLAMP);
            auroraB = new RadialGradient(0f, 0f, w * 0.6f, new int[]{0x22B26BFF, 0x00B26BFF}, null, Shader.TileMode.CLAMP);
            if (base != null) base.recycle();
            base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(base);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF010409, 0xFF05152A, 0xFF020810, 0xFF010409},
                new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            bc.drawRect(0, 0, w, h, bp);
            bp.setShader(new RadialGradient(w / 2f, h * 0.2f, w * 0.85f,
                new int[]{0x5519E6FF, 0x0019E6FF}, null, Shader.TileMode.CLAMP));
            bc.drawRect(0, 0, w, h, bp);
            bp.setShader(new RadialGradient(w * 0.95f, h * 0.75f, w * 0.7f,
                new int[]{0x33FFC24F, 0x00FFC24F}, null, Shader.TileMode.CLAMP));
            bc.drawRect(0, 0, w, h, bp);
            bp.setShader(null);
            bp.setStrokeWidth(1f);
            bp.setColor(0x0E19E6FF);
            float step = 28 * dens;
            for (float x = 0; x < w; x += step) bc.drawLine(x, 0, x, h, bp);
            for (float y = 0; y < h; y += step) bc.drawLine(0, y, w, y, bp);
            if (!seeded) {
                seeded = true;
                java.util.Random r = new java.util.Random(7);
                for (int i = 0; i < N; i++) {
                    px[i] = r.nextFloat() * w;
                    py[i] = r.nextFloat() * h;
                    ps[i] = (6 + r.nextFloat() * 22) * dens;
                    pr[i] = (0.6f + r.nextFloat() * 1.6f) * dens;
                    pph[i] = r.nextFloat() * 6.28f;
                }
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (base == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            t += dt;
            c.drawBitmap(base, 0, 0, null);
            int w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            // two slow, soft lights drifting around, one cyan and one purple
            c.save();
            c.translate(w * (0.5f + 0.35f * (float) Math.sin(t * 0.15f)), h * (0.3f + 0.2f * (float) Math.cos(t * 0.11f)));
            p.setShader(auroraA);
            c.drawRect(-w, -h, w, h, p);
            c.restore();
            c.save();
            c.translate(w * (0.5f + 0.4f * (float) Math.cos(t * 0.1f)), h * (0.7f + 0.2f * (float) Math.sin(t * 0.13f)));
            p.setShader(auroraB);
            c.drawRect(-w, -h, w, h, p);
            c.restore();
            p.setShader(null);
            for (int i = 0; i < N; i++) {
                py[i] -= ps[i] * dt;
                px[i] += (float) Math.sin(t * 0.6f + pph[i]) * 6f * dens * dt;
                if (py[i] < -10) py[i] = h + 10;
                if (px[i] < 0) px[i] = w;
                if (px[i] > w) px[i] = 0;
                float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 1.6f + pph[i]));
                p.setColor(((int) (150 * tw) << 24) | 0x7DF3FF);
                c.drawCircle(px[i], py[i], pr[i], p);
            }
            float by = ((t * 0.07f) % 1f) * h;
            p.setColor(0x1019E6FF);
            c.drawRect(0, by, w, by + 2 * dens, p);
            postInvalidateOnAnimation();
        }
    }

    // The living centrepiece: three rings spinning in 3D around a glowing core, with electrons,
    // a turning scale and breathing light. It speeds up and turns gold when Jarvis is active.
    private static class CoreView extends View {
        private static final int C_NEON = 0xFF19E6FF, C_GOLD = 0xFFFFC24F;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Shader glowCyan, glowGold;
        private float energy = 0f, target = 0f, t = 0f, ripple = -1f, boost = 0f;
        private int batt = -1;
        private boolean charging = false;
        private float voice = 0f, rainbowUntil = 0f;
        private final float[] ringX = new float[65], ringY = new float[65];
        private final Path wave = new Path();
        private long last = 0;
        private final float dens;

        void setBattery(int pct, boolean isCharging) {
            batt = pct;
            charging = isCharging;
        }

        void rainbow(float seconds) {
            rainbowUntil = t + seconds;
        }

        void pulse() {
            ripple = 0f;
            boost = 1f;
        }

        CoreView(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void setActive(boolean on) {
            target = on ? 1f : 0f;
        }

        private static int withA(int c, int a) {
            return (a << 24) | (c & 0xFFFFFF);
        }

        private static int mixc(int a, int b, float f) {
            int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return 0xFF000000 | ((int) (ar + (br - ar) * f) << 16) | ((int) (ag + (bg - ag) * f) << 8) | (int) (ab + (bb - ab) * f);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            float rad = Math.min(w, h) * 0.62f;
            glowCyan = new RadialGradient(w / 2f, h / 2f, rad, new int[]{0x7719E6FF, 0x0019E6FF}, null, Shader.TileMode.CLAMP);
            glowGold = new RadialGradient(w / 2f, h / 2f, rad, new int[]{0x77FFC24F, 0x00FFC24F}, null, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (glowCyan == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            energy += (target - energy) * Math.min(1f, dt * 3f);
            t += dt * (1f + energy * 1.6f + boost * 2.5f);
            boost = Math.max(0f, boost - dt * 1.2f);
            int W = getWidth(), H = getHeight();
            float cx = W / 2f, cy = H / 2f;
            float R = Math.min(W, H) * 0.335f;
            int col = mixc(C_NEON, C_GOLD, energy);
            if (t < rainbowUntil) {
                col = android.graphics.Color.HSVToColor(new float[]{(t * 120f) % 360f, 0.75f, 1f});
            }
            // how loud you are, from the microphone (only while Jarvis is listening)
            float lvTarget = target > 0.5f ? Math.max(0f, Math.min(1f, (JarvisService.micLevel + 2f) / 12f)) : 0f;
            voice += (lvTarget - voice) * Math.min(1f, dt * 10f);
            float pulse = 0.7f + 0.3f * (float) Math.sin(t * 2.2f);

            // breathing light
            p.setStyle(Paint.Style.FILL);
            p.setShader(glowCyan);
            p.setAlpha((int) (255 * pulse * (1f - energy)));
            c.drawRect(0, 0, W, H, p);
            p.setShader(glowGold);
            p.setAlpha((int) (255 * pulse * energy));
            c.drawRect(0, 0, W, H, p);
            p.setShader(null);

            // battery ring: a faint full circle, with the charge drawn over it
            if (batt >= 0) {
                float br = R * 1.36f;
                r.set(cx - br, cy - br, cx + br, cy + br);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * dens);
                p.setColor(withA(0xFFFFFFFF, 30));
                c.drawArc(r, -90f, 359.9f, false, p);
                int bc = batt > 50 ? 0xFF3DFF8A : (batt > 20 ? 0xFFFFC24F : 0xFFFF4D4D);
                float bp = charging ? 0.6f + 0.4f * (float) Math.sin(t * 4f) : 1f;
                p.setColor(withA(bc, (int) (230 * bp)));
                c.drawArc(r, -90f, 3.6f * batt, false, p);
            }

            // turning scale around the outside
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f * dens);
            for (int i = 0; i < 72; i++) {
                float a = (float) (Math.PI * 2 * i / 72) + t * 0.15f;
                boolean major = i % 6 == 0;
                float r0 = R * 1.16f, r1 = R * (major ? 1.28f : 1.22f);
                float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                p.setColor(withA(col, major ? 200 : 90));
                c.drawLine(cx + cs * r0, cy + sn * r0, cx + cs * r1, cy + sn * r1, p);
            }

            // three rings spinning in 3D: each squashes to a line and opens up again
            for (int k = 0; k < 3; k++) {
                float spin = t * (0.8f + 0.35f * k) + k * 1.9f;
                float ry = Math.max(R * 0.08f, R * Math.abs((float) Math.cos(spin)));
                c.save();
                c.translate(cx, cy);
                c.rotate(k * 60f + t * (10f + 6f * k) * (k == 1 ? -1f : 1f));
                r.set(-R, -ry, R, ry);
                p.setStrokeWidth(7 * dens);
                p.setColor(withA(col, 38));
                c.drawOval(r, p);
                p.setStrokeWidth(1.8f * dens);
                p.setColor(withA(col, 235));
                c.drawOval(r, p);
                float ea = t * (2.2f + k * 0.7f) + k * 2f;
                float ex = R * (float) Math.cos(ea), ey = ry * (float) Math.sin(ea);
                p.setStyle(Paint.Style.FILL);
                p.setColor(withA(col, 90));
                c.drawCircle(ex, ey, 7 * dens, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(ex, ey, 3 * dens, p);
                p.setStyle(Paint.Style.STROKE);
                c.restore();
            }

            // the core, with a shine on it
            float orbR = R * 0.30f * (1f + 0.06f * (float) Math.sin(t * 3f));
            p.setStyle(Paint.Style.FILL);
            p.setColor(withA(col, 40));
            c.drawCircle(cx, cy, orbR * 2.2f, p);
            p.setColor(withA(col, 90));
            c.drawCircle(cx, cy, orbR * 1.6f, p);
            p.setColor(withA(col, 170));
            c.drawCircle(cx, cy, orbR * 1.2f, p);
            p.setColor(0xF2FFFFFF);
            c.drawCircle(cx, cy, orbR * 0.75f, p);
            p.setColor(withA(col, 60));
            c.drawCircle(cx + orbR * 0.2f, cy + orbR * 0.22f, orbR * 0.5f, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(cx - orbR * 0.25f, cy - orbR * 0.28f, orbR * 0.2f, p);

            // two arcs going round the core
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.4f * dens);
            p.setColor(withA(col, 230));
            r.set(cx - R * 0.58f, cy - R * 0.58f, cx + R * 0.58f, cy + R * 0.58f);
            c.drawArc(r, t * 80f, 70f, false, p);
            c.drawArc(r, t * 80f + 180f, 70f, false, p);
            p.setStrokeWidth(1.2f * dens);
            p.setColor(withA(col, 150));
            r.set(cx - R * 0.72f, cy - R * 0.72f, cx + R * 0.72f, cy + R * 0.72f);
            c.drawArc(r, -t * 55f, 120f, false, p);

            // a ring of light that ripples with your voice
            if (voice > 0.04f) {
                float vr = R * 0.92f;
                wave.reset();
                for (int i = 0; i <= 64; i++) {
                    float a = (float) (Math.PI * 2 * i / 64);
                    float rr = vr + (float) Math.sin(a * 9f + t * 9f) * R * 0.09f * voice
                        + (float) Math.sin(a * 5f - t * 6f) * R * 0.07f * voice;
                    float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                    if (i == 0) wave.moveTo(x, y);
                    else wave.lineTo(x, y);
                }
                wave.close();
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(2.6f * dens);
                p.setColor(withA(col, (int) (120 + 120 * voice)));
                c.drawPath(wave, p);
            }

            // a shock wave when you touch it
            if (ripple >= 0f) {
                ripple += dt * 1.1f;
                if (ripple > 1.2f) {
                    ripple = -1f;
                } else {
                    for (int k = 0; k < 2; k++) {
                        float rr = ripple - k * 0.2f;
                        if (rr <= 0f || rr > 1f) continue;
                        p.setStrokeWidth(2.2f * dens);
                        p.setColor(withA(col, (int) (210 * (1f - rr))));
                        c.drawCircle(cx, cy, R * (0.3f + 1.6f * rr), p);
                    }
                }
            }

            postInvalidateOnAnimation();
        }
    }

    // A card that can run a bright band of light across itself.
    private static class GlassCard extends LinearLayout {
        private final float rad, thick;
        private float shine = -1f;
        private final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix m = new Matrix();
        private final Path clip = new Path();
        private final RectF rf = new RectF();
        private LinearGradient band;

        GlassCard(Context c, float radiusPx, float thickPx) {
            super(c);
            rad = radiusPx;
            thick = thickPx;
            setWillNotDraw(false);
        }

        void setShine(float s) {
            shine = s;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            band = new LinearGradient(0, 0, w * 0.45f, 0,
                new int[]{0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            sp.setShader(band);
            rf.set(0, 0, w, h - thick);
            clip.reset();
            clip.addRoundRect(rf, rad, rad, Path.Direction.CW);
        }

        @Override
        protected void dispatchDraw(Canvas c) {
            super.dispatchDraw(c);
            if (shine < 0f || shine > 1f || band == null) return;
            c.save();
            c.clipPath(clip);
            float w = getWidth();
            m.setTranslate(-w * 0.45f + shine * (w * 1.5f), 0);
            band.setLocalMatrix(m);
            c.drawRect(0, 0, w, getHeight(), sp);
            c.restore();
        }
    }

    // A tiny 4D cube that keeps turning through the fourth dimension, for the hologram card.
    private static class MiniTesseract extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] vx = new float[16], vy = new float[16];
        private float t = 0f;
        private long last = 0;
        private final float dens;

        MiniTesseract(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            t += dt;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, sc = Math.min(getWidth(), getHeight()) * 0.34f;
            float ca = (float) Math.cos(t * 0.8f), sa = (float) Math.sin(t * 0.8f);
            float cb = (float) Math.cos(t * 0.55f), sb = (float) Math.sin(t * 0.55f);
            float cg = (float) Math.cos(t * 0.4f), sg = (float) Math.sin(t * 0.4f);
            for (int i = 0; i < 16; i++) {
                float x = (i & 1) == 0 ? -1f : 1f, y = (i & 2) == 0 ? -1f : 1f;
                float z = (i & 4) == 0 ? -1f : 1f, w = (i & 8) == 0 ? -1f : 1f;
                float x1 = x * ca - w * sa, w1 = x * sa + w * ca;
                float z1 = z * cb - w1 * sb, w2 = z * sb + w1 * cb;
                float y1 = y * cg - z1 * sg, z2 = y * sg + z1 * cg;
                float k = 1.7f / (2.6f - w2 * 0.5f);
                float f = 1f / (1f - z2 * 0.12f);
                vx[i] = cx + x1 * k * sc * f * 0.8f;
                vy[i] = cy + y1 * k * sc * f * 0.8f;
            }
            for (int i = 0; i < 16; i++) {
                for (int bt = 1; bt <= 8; bt <<= 1) {
                    int j = i ^ bt;
                    if (j < i) continue;
                    boolean link = bt == 8;
                    p.setStrokeWidth((link ? 4.5f : 6f) * dens);
                    p.setColor(link ? 0x2AFFFFFF : 0x2619E6FF);
                    c.drawLine(vx[i], vy[i], vx[j], vy[j], p);
                    p.setStrokeWidth((link ? 1.1f : 1.7f) * dens);
                    p.setColor(link ? 0xCCFFFFFF : 0xFF19E6FF);
                    c.drawLine(vx[i], vy[i], vx[j], vy[j], p);
                }
            }
            postInvalidateOnAnimation();
        }
    }

    // A ring that fills as you get closer to your money goal, with a burst of gold when you log income.
    private static class MoneyRing extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private Shader sweep;
        private float target = 0f, shown = 0f, burstT = -1f;
        private String center = "", sub = "";
        private long last = 0;
        private final float dens;

        MoneyRing(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            p.setStrokeCap(Paint.Cap.ROUND);
        }

        void set(float t, String c, String s) {
            target = Math.max(0f, Math.min(1f, t));
            center = c;
            sub = s;
            invalidate();
        }

        void burst() {
            burstT = 0f;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            sweep = new SweepGradient(w / 2f, h / 2f, new int[]{0xFF19E6FF, 0xFF7DF3FF, 0xFFFFC24F, 0xFF19E6FF}, null);
        }

        @Override
        protected void onDraw(Canvas c) {
            if (sweep == null) return;
            long now = SystemClock.uptimeMillis();
            float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1000f);
            last = now;
            shown += (target - shown) * Math.min(1f, dt * 3f);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f, rad = Math.min(w, h) / 2f - 10 * dens;
            r.set(cx - rad, cy - rad, cx + rad, cy + rad);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(9 * dens);
            p.setColor(0x33FFFFFF);
            c.drawCircle(cx, cy, rad, p);
            if (shown > 0.003f) {
                c.save();
                c.rotate(-90f, cx, cy);
                p.setShader(sweep);
                c.drawArc(r, 0f, 360f * shown, false, p);
                p.setShader(null);
                // a bright dot at the end of the arc
                double a = Math.PI * 2 * shown;
                float ex = cx + (float) Math.cos(a) * rad, ey = cy + (float) Math.sin(a) * rad;
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x66FFC24F);
                c.drawCircle(ex, ey, 9 * dens, p);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(ex, ey, 4 * dens, p);
                c.restore();
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            p.setColor(0xFFFFFFFF);
            p.setTextSize(21 * dens);
            c.drawText(center, cx, cy + 3 * dens, p);
            p.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            p.setColor(0xFF8FA6B2);
            p.setTextSize(8.5f * dens);
            p.setLetterSpacing(0.14f);
            c.drawText(sub, cx, cy + 17 * dens, p);
            p.setLetterSpacing(0f);
            if (burstT >= 0f) {
                burstT += dt * 0.9f;
                if (burstT > 1f) {
                    burstT = -1f;
                } else {
                    for (int i = 0; i < 24; i++) {
                        float ang = (float) (Math.PI * 2 * i / 24 + i * 0.37);
                        float k = 0.55f + 0.45f * ((i * 7) % 5) / 4f;
                        float d = rad * (1f + 1.1f * burstT * k);
                        p.setColor(((int) (255 * (1f - burstT)) << 24) | (i % 3 == 0 ? 0xFFFFFF : 0xFFC24F));
                        c.drawCircle(cx + (float) Math.cos(ang) * d, cy + (float) Math.sin(ang) * d, (3.2f - 1.6f * burstT) * dens, p);
                    }
                }
            }
            if (Math.abs(target - shown) > 0.002f || burstT >= 0f) postInvalidateOnAnimation();
        }
    }

    // The last 14 days of income as a small glowing graph.
    private static class Spark extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path line = new Path();
        private final Path fill = new Path();
        private double[] data = new double[14];
        private Shader under;
        private final float dens;

        Spark(Context c) {
            super(c);
            dens = c.getResources().getDisplayMetrics().density;
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        void set(double[] d) {
            data = d;
            invalidate();
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            under = new LinearGradient(0, 0, 0, h, 0x66FFC24F, 0x00FFC24F, Shader.TileMode.CLAMP);
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float pad = 8 * dens, base = h - 6 * dens, top = 8 * dens;
            double max = 0;
            for (double v : data) max = Math.max(max, v);
            p.setStyle(Paint.Style.FILL);
            if (max <= 0) {
                p.setColor(0x44FFFFFF);
                for (float x = pad; x < w - pad; x += 9 * dens) c.drawCircle(x, base, 1.2f * dens, p);
                return;
            }
            line.reset();
            fill.reset();
            float lx = 0, ly = 0;
            for (int i = 0; i < data.length; i++) {
                float x = pad + i * (w - 2 * pad) / (data.length - 1);
                float y = base - (float) (data[i] / max) * (base - top);
                if (i == 0) {
                    line.moveTo(x, y);
                    fill.moveTo(x, base);
                    fill.lineTo(x, y);
                } else {
                    float mx = (lx + x) / 2f;
                    line.cubicTo(mx, ly, mx, y, x, y);
                    fill.cubicTo(mx, ly, mx, y, x, y);
                }
                lx = x;
                ly = y;
            }
            fill.lineTo(lx, base);
            fill.close();
            p.setShader(under);
            c.drawPath(fill, p);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(6 * dens);
            p.setColor(0x33FFC24F);
            c.drawPath(line, p);
            p.setStrokeWidth(2 * dens);
            p.setColor(0xFFFFC24F);
            c.drawPath(line, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x66FFC24F);
            c.drawCircle(lx, ly, 7 * dens, p);
            p.setColor(0xFFFFFFFF);
            c.drawCircle(lx, ly, 3 * dens, p);
        }
    }

    // Small line icons drawn in code, so they all look the same weight and colour.
    // kinds: 0 brain, 1 voice, 2 setup, 3 self test, 4 help, 5 hologram
    private static class IconView extends View {
        private final int kind, color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF();

        IconView(Context c, int kind, int color) {
            super(c);
            this.kind = kind;
            this.color = color;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight());
            if (s <= 0) return;
            float u = s / 24f;
            float sw = 1.8f * getResources().getDisplayMetrics().density;
            float ox = (getWidth() - s) / 2f, oy = (getHeight() - s) / 2f;
            for (int pass = 0; pass < 2; pass++) {
                p.setColor(pass == 0 ? ((color & 0xFFFFFF) | 0x44000000) : color);
                p.setStrokeWidth(pass == 0 ? sw * 3.2f : sw);
                drawIcon(c, ox, oy, u);
            }
        }

        private void drawIcon(Canvas c, float ox, float oy, float u) {
            if (kind == 0) {            // brain: a small network
                c.drawCircle(ox + 12 * u, oy + 12 * u, 3 * u, p);
                float[][] n = {{5, 6}, {19, 6}, {5, 18}, {19, 18}};
                for (float[] q : n) {
                    c.drawLine(ox + 12 * u, oy + 12 * u, ox + q[0] * u, oy + q[1] * u, p);
                    c.drawCircle(ox + q[0] * u, oy + q[1] * u, 1.8f * u, p);
                }
            } else if (kind == 1) {     // voice: sound bars
                float[] xs = {5, 8.5f, 12, 15.5f, 19};
                float[] hs = {3, 6, 9, 6, 3};
                for (int i = 0; i < xs.length; i++) {
                    c.drawLine(ox + xs[i] * u, oy + (12 - hs[i]) * u, ox + xs[i] * u, oy + (12 + hs[i]) * u, p);
                }
            } else if (kind == 2) {     // setup: a cog
                c.drawCircle(ox + 12 * u, oy + 12 * u, 3 * u, p);
                c.drawCircle(ox + 12 * u, oy + 12 * u, 7 * u, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * 2 * i / 8;
                    float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
                    c.drawLine(ox + (12 + cs * 7) * u, oy + (12 + sn * 7) * u,
                        ox + (12 + cs * 10) * u, oy + (12 + sn * 10) * u, p);
                }
            } else if (kind == 3) {     // self test: tick in a circle
                c.drawCircle(ox + 12 * u, oy + 12 * u, 9.5f * u, p);
                path.reset();
                path.moveTo(ox + 7.5f * u, oy + 12.5f * u);
                path.lineTo(ox + 10.5f * u, oy + 15.5f * u);
                path.lineTo(ox + 16.5f * u, oy + 9 * u);
                c.drawPath(path, p);
            } else if (kind == 4) {     // help: a list
                for (int i = 0; i < 3; i++) {
                    float y = oy + (7 + i * 5) * u;
                    c.drawLine(ox + 9 * u, y, ox + 20 * u, y, p);
                    c.drawCircle(ox + 4.5f * u, y, 0.6f * u, p);
                }
            } else {                    // hologram: rings in rings
                c.drawCircle(ox + 12 * u, oy + 12 * u, 9.5f * u, p);
                c.drawCircle(ox + 12 * u, oy + 12 * u, 5.5f * u, p);
                c.drawCircle(ox + 12 * u, oy + 12 * u, 1.6f * u, p);
                c.drawLine(ox + 12 * u, oy + 0.5f * u, ox + 12 * u, oy + 2.5f * u, p);
                c.drawLine(ox + 12 * u, oy + 21.5f * u, ox + 12 * u, oy + 23.5f * u, p);
            }
        }
    }

    private void buildUi() {
        getWindow().setStatusBarColor(0xFF010409);
        getWindow().setNavigationBarColor(0xFF010409);

        FrameLayout frame = new FrameLayout(this);
        backdropView = new BackdropView(this);
        backdropView.setScaleX(1.12f);    // a little oversize, so it can slide without showing its edges
        backdropView.setScaleY(1.12f);
        frame.addView(backdropView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        ScrollView sv = new ScrollView(this);
        sceneView = sv;
        sv.setCameraDistance(14000f * getResources().getDisplayMetrics().density);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        frame.addView(sv, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(32));
        sv.addView(root);

        // ---- header ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(new IconView(this, 5, NEON), new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(10), 0, 0, 0);
        TextView title = makeText("J.A.R.V.I.S.", 19, 0xFFFFFFFF, false);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        title.setLetterSpacing(0.16f);
        title.setShadowLayer(dp(10), 0, 0, NEON);
        titles.addView(title);
        title.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long now = SystemClock.uptimeMillis();
                titleTaps = now - lastTitleTap < 600 ? titleTaps + 1 : 1;
                lastTitleTap = now;
                if (titleTaps >= 7) {
                    titleTaps = 0;
                    coreView.rainbow(10f);
                    coreView.pulse();
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    toast("Party protocol engaged, sir.");
                }
            }
        });
        TextView sub = makeText("VOICE ASSISTANT  \u00B7  HOLOGRAM LAB", 9, MUTED, false);
        sub.setLetterSpacing(0.16f);
        titles.addView(sub);
        top.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        netView = makeText("", 10, MUTED, true);
        netView.setBackground(panel(0x66000000, 0x5519E6FF, 20));
        netView.setPadding(dp(12), dp(5), dp(12), dp(5));
        top.addView(netView);
        root.addView(top);

        // ---- clock, then the living core ----
        clockView = makeText("", 44, 0xFFFFFFFF, false);
        clockView.setTypeface(Typeface.create("sans-serif-thin", Typeface.NORMAL));
        clockView.setLetterSpacing(0.06f);
        clockView.setShadowLayer(dp(14), 0, 0, NEON);
        clockView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cvp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cvp.topMargin = dp(10);
        root.addView(clockView, cvp);
        dateView = makeText("", 10, MUTED, false);
        dateView.setLetterSpacing(0.2f);
        dateView.setGravity(Gravity.CENTER);
        root.addView(dateView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        coreView = new CoreView(this);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(230), dp(230));
        rp.gravity = Gravity.CENTER_HORIZONTAL;
        rp.topMargin = dp(2);
        root.addView(coreView, rp);
        coreView.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                coreView.pulse();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            }
        });
        coreView.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                coreView.pulse();
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                toggleBtn.performClick();
                return true;
            }
        });

        stateView = makeText("", 12, MUTED, true);
        stateView.setGravity(Gravity.CENTER);
        stateView.setLetterSpacing(0.12f);
        stateView.setBackground(panel(0x55000000, 0x6619E6FF, 22));
        stateView.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp2.gravity = Gravity.CENTER_HORIZONTAL;
        sp2.topMargin = dp(6);
        sp2.bottomMargin = dp(18);
        root.addView(stateView, sp2);

        // ---- main button ----
        toggleBtn = makeText("ACTIVATE", 17, DARK, false);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setPadding(0, dp(15), 0, dp(19));
        toggleBtn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        toggleBtn.setLetterSpacing(0.22f);
        toggleBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (JarvisService.active) {
                    getSharedPreferences("jarvis", 0).edit().putBoolean("enabled", false).apply();
                    stopService(new Intent(MainActivity.this, JarvisService.class));
                    toast("Jarvis stopped.");
                } else {
                    setupActive = true;
                    setupStart = SystemClock.uptimeMillis();
                    lastSetupCall = 0;
                    continueSetup();
                }
            }
        });
        feel(toggleBtn);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.setMargins(dp(5), 0, dp(5), dp(14));
        // a soft ring that breathes around the button
        FrameLayout btnWrap = new FrameLayout(this);
        glowDrawable = new GradientDrawable();
        glowDrawable.setCornerRadius(dp(36));
        glowDrawable.setStroke(dp(2), withAlpha(GOLDC, 210));
        glowDrawable.setColor(withAlpha(GOLDC, 28));
        glowRing = new View(this);
        glowRing.setBackground(glowDrawable);
        btnWrap.addView(glowRing, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams bp2 = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        bp2.setMargins(dp(6), dp(6), dp(6), dp(6));
        btnWrap.addView(toggleBtn, bp2);
        root.addView(btnWrap, tp);

        tipView = makeText("TRY  \u203A  " + TIPS[0], 11, MUTED, false);
        tipView.setGravity(Gravity.CENTER);
        tipView.setBackground(panel(0x44000000, 0x4419E6FF, 16));
        tipView.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams tvp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tvp.setMargins(dp(5), 0, dp(5), dp(14));
        root.addView(tipView, tvp);

        LinearLayout.LayoutParams sr = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sr.bottomMargin = dp(8);
        root.addView(buildStatsRow(), sr);

        root.addView(sectionLabel("TODAY"));
        LinearLayout.LayoutParams tcp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tcp.setMargins(dp(5), dp(6), dp(5), dp(6));
        root.addView(buildTodayCard(), tcp);
        root.addView(buildQuickRow(), tcp);

        root.addView(sectionLabel("MONEY"));
        root.addView(buildMoneyCard(), tcp);
        root.addView(sectionLabel("PRIORITY"));
        root.addView(buildPriorityRow(), tcp);
        root.addView(sectionLabel("MORNING BRIEF"));
        root.addView(buildMorningCard(), tcp);
        root.addView(sectionLabel("SMART CALLS"));
        root.addView(buildSmartCard(), tcp);
        root.addView(sectionLabel("MISSED"));
        root.addView(buildMissedCard(), tcp);

        if (getSharedPreferences("jarvis_home", 0).getInt("newSeen", 0) < WHATS_NEW_ID) {
            LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wp.setMargins(dp(5), dp(10), dp(5), dp(4));
            root.addView(buildWhatsNew(), wp);
        }

        root.addView(sectionLabel("HOLOGRAM"));

        // ---- hologram: the big one ----
        GlassCard hero = new GlassCard(this, dp(22), dp(5));
        shineCards.add(hero);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(20), dp(18), dp(16), dp(23));
        hero.setBackground(glassCard(NEON, 22));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        TextView hk = makeText("NEW  \u00B7  AUGMENTED REALITY", 9, NEON, false);
        hk.setLetterSpacing(0.18f);
        TextView h1 = makeText("HOLOGRAM LAB", 19, 0xFFFFFFFF, false);
        h1.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        h1.setLetterSpacing(0.06f);
        h1.setPadding(0, dp(4), 0, 0);
        TextView h2 = makeText("Camera, hands and 3D shapes. Build and move holograms with your fingers.", 11, MUTED, false);
        h2.setPadding(0, dp(4), 0, dp(10));
        TextView open = makeText("OPEN  \u203A", 12, 0xFF021018, false);
        open.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        open.setLetterSpacing(0.14f);
        open.setBackground(flatButton(0xFF8DF6FF, NEON, 16));
        open.setPadding(dp(16), dp(7), dp(16), dp(7));
        ht.addView(hk);
        ht.addView(h1);
        ht.addView(h2);
        ht.addView(open, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        hero.addView(ht, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        hero.addView(new MiniTesseract(this), new LinearLayout.LayoutParams(dp(84), dp(84)));
        hero.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HoloActivity.class));
            }
        });
        feel(hero);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.setMargins(dp(5), dp(5), dp(5), dp(5));
        root.addView(hero, hp);

        root.addView(sectionLabel("SYSTEMS"));

        // ---- the rest of the cards ----
        aiCard = card(0, "AI BRAIN", "", NEON, new View.OnClickListener() {
            public void onClick(View v) { AiSettings.show(MainActivity.this); }
        });
        addSingle(root, aiCard);

        voiceCard = card(1, "VOICE", "loading...", GOLDC, new View.OnClickListener() {
            public void onClick(View v) { nextVoice(); }
        });
        setupCard = card(2, "SETUP", "", NEON, new View.OnClickListener() {
            public void onClick(View v) { Dialogs.showSetup(MainActivity.this); }
        });
        addRow(root, voiceCard, setupCard);

        LinearLayout testCard = card(3, "SELF TEST", "check everything", NEON,
            new View.OnClickListener() {
                public void onClick(View v) {
                    Dialogs.selfTest(MainActivity.this, voices.size());
                }
            });
        LinearLayout helpCard = card(4, "HELP", "what I can do", GOLDC,
            new View.OnClickListener() {
                public void onClick(View v) { Dialogs.showCommands(MainActivity.this); }
            });
        addRow(root, testCard, helpCard);

        // ---- live feed, like a little terminal ----
        LinearLayout feedCard = new LinearLayout(this);
        feedCard.setOrientation(LinearLayout.VERTICAL);
        feedCard.setPadding(dp(16), dp(14), dp(16), dp(16));
        feedCard.setBackground(glass(18));

        LinearLayout feedHead = new LinearLayout(this);
        feedHead.setOrientation(LinearLayout.HORIZONTAL);
        feedHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView feedTitle = makeText("\u25CF  LIVE FEED", 11, NEON, true);
        feedTitle.setLetterSpacing(0.14f);
        feedHead.addView(feedTitle, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView copy = makeText("COPY", 11, 0xFF1A1100, false);
        copy.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        copy.setBackground(yellowButton(12));
        copy.setPadding(dp(14), dp(6), dp(14), dp(6));
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { copyLog(); }
        });
        feel(copy);
        feedHead.addView(copy);

        TextView clear = makeText("CLEAR", 11, 0xFFFFFFFF, false);
        clear.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        clear.setBackground(redButton(12));
        clear.setPadding(dp(14), dp(6), dp(14), dp(6));
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                JarvisLog.clear();
                refresh();
            }
        });
        feel(clear);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.leftMargin = dp(8);
        feedHead.addView(clear, clp);
        feedCard.addView(feedHead);

        feedView = makeText("", 12, 0xFFBFEFFF, true);
        feedView.setLineSpacing(0f, 1.15f);
        feedView.setPadding(0, dp(12), 0, 0);
        feedCard.addView(feedView);

        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.setMargins(dp(5), dp(14), dp(5), dp(5));
        root.addView(feedCard, fp);

        setContentView(frame);

        // as you scroll, the clock and core drift away more slowly than the cards
        final ScrollView scroller = sv;
        sv.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int ox, int oy) {
                float f = Math.min(1f, y / (float) dp(300));
                coreView.setTranslationY(y * 0.45f);
                coreView.setAlpha(1f - 0.8f * f);
                coreView.setScaleX(1f - 0.22f * f);
                coreView.setScaleY(1f - 0.22f * f);
                clockView.setAlpha(1f - f);
                dateView.setAlpha(1f - f);
            }
        });

        // everything slides up and fades in, one piece after another
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(dp(22));
            child.animate().alpha(1f).translationY(0f).setStartDelay(60L * i).setDuration(380).start();
        }
    }

    // ====== REFRESH ======

    private void refresh() {
        boolean active = JarvisService.active;
        if (coreView != null) coreView.setActive(active);
        readBattery();
        refreshToday();
        refreshSmart();
        refreshMoney();
        if (coreView != null) coreView.setBattery(battPct, battCharging);
        if (statBattery != null && !statAnimating) {
            statBattery.setText(battPct >= 0 ? battPct + "%" : "--");
            statBattery.setTextColor(battPct > 50 ? 0xFF3DFF8A : (battPct > 20 ? 0xFFFFC24F : (battPct >= 0 ? 0xFFFF4D4D : 0xFFFFFFFF)));
            statBatteryLabel.setText(battCharging ? "CHARGING" : "BATTERY");
            statStreak.setText(String.valueOf(streakValue));
            statActive.setText(activeText());
        }
        if (glowDrawable != null && active != glowWasActive) {
            glowWasActive = active;
            int gc = active ? 0xFFFF5A4A : GOLDC;
            glowDrawable.setStroke(dp(2), withAlpha(gc, 210));
            glowDrawable.setColor(withAlpha(gc, 28));
        }
        if (clockView != null) {
            java.util.Date nowDate = new java.util.Date();
            clockView.setText(android.text.format.DateFormat.getTimeFormat(this).format(nowDate));
            int hr = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            String hello = hr < 5 ? "GOOD NIGHT" : hr < 12 ? "GOOD MORNING" : hr < 18 ? "GOOD AFTERNOON" : "GOOD EVENING";
            dateView.setText(new java.text.SimpleDateFormat("EEEE d MMMM", java.util.Locale.getDefault())
                .format(nowDate).toUpperCase(java.util.Locale.getDefault()) + "  \u00B7  " + hello + ", SIR");
        }
        boolean online = Brain.isOnline(this);

        netView.setText(online ? "\u25CF ONLINE" : "\u25CF OFFLINE");
        netView.setTextColor(online ? 0xFF3DFF8A : RED);
        stateView.setText(active ? "ACTIVE - SAY \"HEY JARVIS\"" : "STANDBY");
        stateView.setTextColor(active ? NEON : MUTED);

        if (active) {
            toggleBtn.setText("DEACTIVATE");
            toggleBtn.setTextColor(0xFFFFFFFF);
            toggleBtn.setBackground(redButton(30));
        } else {
            toggleBtn.setText("ACTIVATE");
            toggleBtn.setTextColor(0xFF1A1100);
            toggleBtn.setBackground(yellowButton(30));
        }

        subOf(aiCard).setText(AiSettings.summary(this));
        subOf(setupCard).setText(Checks.readyCount(this) + "/7 ready");

        String feed = JarvisLog.recent(5);
        feedView.setText(feed.length() == 0 ? "No activity yet. Say \"Hey Jarvis\"." : feed);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private void copyLog() {
        String text = JarvisLog.recent(30);
        if (text.length() == 0) {
            toast("Nothing to copy yet.");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Jarvis log", text));
        toast("Log copied.");
    }

    // ====== SETUP FLOW (runs when you tap ACTIVATE) ======

    private void continueSetup() {
        if (!setupActive) return;
        long now = SystemClock.uptimeMillis();
        if (now - setupStart > 180000) {
            setupActive = false;
            return;
        }
        if (now - lastSetupCall < 800) return;
        lastSetupCall = now;

        SharedPreferences p = getSharedPreferences("jarvis", 0);

        // 1. Normal permissions, all in one prompt.
        List<String> missing = Checks.missingRuntime(this);
        boolean askedRuntime = p.getBoolean("askedRuntime", false);
        int micAsks = p.getInt("micAsks", 0);
        boolean needMic = !Checks.hasMic(this);
        if (missing.size() > 0 && (!askedRuntime || (needMic && micAsks < 2))) {
            p.edit().putBoolean("askedRuntime", true)
                .putInt("micAsks", needMic ? micAsks + 1 : micAsks).apply();
            requestPermissions(missing.toArray(new String[0]), 1);
            return;
        }
        if (needMic) {
            setupActive = false;
            toast("Jarvis needs the microphone. Turn on Microphone, then tap ACTIVATE.");
            Checks.openAppDetails(this);
            return;
        }

        // 2. Popup over other apps.
        if (!Checks.canOverlay(this) && !p.getBoolean("askedOverlay", false)) {
            p.edit().putBoolean("askedOverlay", true).apply();
            toast("Turn on \"Display over other apps\" for Jarvis, then come back.");
            Checks.openOverlay(this);
            return;
        }

        // 3. Email access.
        if (!Checks.isMailEnabled(this) && !p.getBoolean("askedMail", false)) {
            p.edit().putBoolean("askedMail", true).apply();
            askDialog("Read your emails aloud?",
                "Allow Notification Access so Jarvis can read your new email notifications. "
                + "Nothing is saved or sent anywhere.",
                new Runnable() {
                    public void run() { Checks.openMail(MainActivity.this); }
                });
            return;
        }

        // 4. Screen control.
        if (!Checks.isTouchEnabled(this) && !p.getBoolean("askedTouch", false)) {
            p.edit().putBoolean("askedTouch", true).apply();
            askDialog("Control your screen by voice?",
                "Allow Accessibility so Jarvis can tap, scroll and press buttons when you "
                + "ask. He only does this when you give a command.",
                new Runnable() {
                    public void run() { Checks.openTouch(MainActivity.this); }
                });
            return;
        }

        // 5. Stop Android from putting Jarvis to sleep.
        if (!Checks.isIgnoringBattery(this) && !p.getBoolean("askedBattery", false)) {
            p.edit().putBoolean("askedBattery", true).apply();
            toast("Allow this so Android doesn't stop Jarvis in the background.");
            Checks.openBattery(this);
            return;
        }

        // All done: start Jarvis.
        setupActive = false;
        launchService();
    }

    private void askDialog(String title, String message, final Runnable onYes) {
        new AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Allow", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) { onYes.run(); }
            })
            .setNegativeButton("Skip", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int which) {
                    lastSetupCall = 0;
                    continueSetup();
                }
            })
            .show();
    }

    private void launchService() {
        Intent svc = new Intent(this, JarvisService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
        toast("Jarvis is running. Say \"Hey Jarvis\" from anywhere.");
    }

    // ====== VOICE PICKER (offline-capable voices only) ======

    @Override
    public void onInit(int statusCode) {
        if (statusCode != TextToSpeech.SUCCESS) {
            subOf(voiceCard).setText("unavailable");
            return;
        }
        try {
            Set<Voice> all = preview.getVoices();
            if (all != null) {
                for (Voice v : all) {
                    if (v.getLocale().getLanguage().equals("en") && Brain.isOfflineVoice(v)) {
                        voices.add(v);
                    }
                }
            }
            Collections.sort(voices, new Comparator<Voice>() {
                public int compare(Voice a, Voice b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            String saved = getSharedPreferences("jarvis", 0).getString("voice", "");
            for (int i = 0; i < voices.size(); i++) {
                if (voices.get(i).getName().equals(saved)) voiceIdx = i;
            }
        } catch (Exception e) {
            // ignore
        }
        updateVoiceCard();
    }

    private void updateVoiceCard() {
        if (voices.size() == 0) {
            subOf(voiceCard).setText("none found");
        } else if (voiceIdx < 0) {
            subOf(voiceCard).setText("auto - tap to choose");
        } else {
            subOf(voiceCard).setText((voiceIdx + 1) + "/" + voices.size() + " "
                + voices.get(voiceIdx).getName());
        }
    }

    private void nextVoice() {
        if (voices.size() == 0) return;
        voiceIdx = (voiceIdx + 1) % voices.size();
        Voice v = voices.get(voiceIdx);
        preview.setVoice(v);
        preview.setPitch(Brain.PITCH);
        preview.setSpeechRate(Brain.RATE);
        preview.speak("Good evening, sir. How may I assist you?",
                      TextToSpeech.QUEUE_FLUSH, null, "preview");
        getSharedPreferences("jarvis", 0).edit().putString("voice", v.getName()).apply();
        updateVoiceCard();
        toast("Voice saved. Deactivate and activate Jarvis to apply it.");
    }

    @Override
    protected void onDestroy() {
        if (preview != null) {
            preview.stop();
            preview.shutdown();
        }
        super.onDestroy();
    }
}