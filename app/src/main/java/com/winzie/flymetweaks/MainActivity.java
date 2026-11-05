package com.winzie.flymetweaks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    // ---------- palette: Material You dynamic colors on Android 12+, blue fallback below ----------
    static int BG = 0xFF111418;
    static int CARD = 0xFF1E2227;
    static int CARD2 = 0xFF2F4156;
    static int PRIMARY = 0xFFA5C8FF;
    static int ACCENT = 0xFFA5C8FF;
    static int ON_PRIMARY = 0xFF00315E;
    static int ON_SURFACE = 0xFFE2E2E9;
    static int ON_VARIANT = 0xFFC3C6CF;
    static int DIVIDER = 0xFF3A3F47;
    static final int ERROR = 0xFFFFB4AB;
    static final int ON_ERROR = 0xFF690005;

    static void initPalette(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            BG = c.getColor(android.R.color.system_neutral1_900);
            CARD = c.getColor(android.R.color.system_neutral1_800);
            CARD2 = c.getColor(android.R.color.system_accent2_700);
            PRIMARY = c.getColor(android.R.color.system_accent1_200);
            ON_PRIMARY = c.getColor(android.R.color.system_accent1_800);
            ON_SURFACE = c.getColor(android.R.color.system_neutral1_100);
            ON_VARIANT = c.getColor(android.R.color.system_neutral2_200);
            DIVIDER = c.getColor(android.R.color.system_neutral2_700);
        }
        ACCENT = PRIMARY;
    }

    static int alpha(int color, int a) {
        return (a << 24) | (color & 0x00FFFFFF);
    }

    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final String LAUNCHER = "com.meizu.flyme.launcher";
    static final String ICON_KEY = "MzCommonUtils_Icon_Size_Progress";
    static final String PREFS_NAME = "MzCommonUtils_Common_Preferences.xml";
    static final int SEED_VERSION = 2;

    static class Toggle {
        String label, key, on = "", off = "", opts = "";
        boolean choice;
    }

    interface Cb {
        void done(int code, String out);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SharedPreferences sp;
    private final List<Toggle> toggles = new ArrayList<>();

    private ScrollView scroll;
    private final LinearLayout[] pages = new LinearLayout[4];
    private final LinearLayout[] tabs = new LinearLayout[4];
    private final ImageView[] tabIcons = new ImageView[4];
    private final TextView[] tabLabels = new TextView[4];
    private GlassBar bar;

    private LinearLayout statusCard, toggleBox;
    private TextView statusTitle, statusSub, flymeVer, logView;
    private EditText iconInput;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        initPalette(this);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        loadToggles();
        buildUi();
        selectTab(0);
        checkRoot();
        refreshToggles();
        su("getprop ro.build.display.id", new Cb() {
            public void done(int c, String out) {
                flymeVer.setText(out.isEmpty() ? "unknown" : out);
            }
        });
    }

    // =====================================================================
    // root helper
    // =====================================================================
    void su(final String cmd, final Cb cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();
                int code = -1;
                try {
                    Process p = (useMountMaster()
                            ? new ProcessBuilder("su", "-M", "-c", cmd)
                            : new ProcessBuilder("su", "-c", cmd)).redirectErrorStream(true).start();
                    BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String l;
                    while ((l = r.readLine()) != null) sb.append(l).append('\n');
                    code = p.waitFor();
                } catch (Exception e) {
                    sb.append(e.getMessage());
                }
                final int c = code;
                final String o = sb.toString().trim();
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (cb != null) cb.done(c, o);
                    }
                });
            }
        }).start();
    }

    // Root shells started by an app normally can't see other apps' data (Android data isolation).
    // "su -M" (mount-master) uses the global mount namespace so those paths are visible.
    static volatile int mmMode = -1;

    static synchronized boolean useMountMaster() {
        if (mmMode == -1) {
            mmMode = 0;
            try {
                Process p = new ProcessBuilder("su", "-M", "-c", "id").redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                p.waitFor();
                if (sb.toString().contains("uid=0")) mmMode = 1;
            } catch (Exception e) {
                // keep mode 0
            }
        }
        return mmMode == 1;
    }

    static String sq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    void checkRoot() {
        su("id", new Cb() {
            public void done(int c, String out) {
                boolean ok = c == 0 && out.contains("uid=0");
                statusCard.setBackground(round(ok ? PRIMARY : ERROR, 32));
                statusTitle.setTextColor(ok ? ON_PRIMARY : ON_ERROR);
                statusSub.setTextColor(ok ? ON_PRIMARY : ON_ERROR);
                if (ok) {
                    statusTitle.setText("Working");
                    statusSub.setText(mmMode == 1 ? "Root granted, global mount"
                            : "Root granted, isolated mount (icon size editing may fail)");
                } else {
                    statusTitle.setText("No root");
                    statusSub.setText("Grant su access to Flyme Tweaks, then reopen the app");
                }
            }
        });
    }

    // =====================================================================
    // storage + seeded toggles
    // =====================================================================
    void loadToggles() {
        toggles.clear();
        try {
            JSONArray a = new JSONArray(sp.getString("toggles", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Toggle t = new Toggle();
                t.label = o.getString("label");
                t.key = o.getString("key");
                t.on = o.optString("on", "");
                t.off = o.optString("off", "");
                t.choice = o.optBoolean("choice", false);
                t.opts = o.optString("opts", "");
                toggles.add(t);
            }
        } catch (Exception e) {
            // ignore, start empty
        }
        if (sp.getInt("seed", 0) < SEED_VERSION) {
            for (Toggle d : seedList()) {
                boolean have = false;
                for (Toggle t : toggles) if (t.key.equals(d.key)) have = true;
                if (!have) toggles.add(d);
            }
            saveToggles();
            sp.edit().putInt("seed", SEED_VERSION).apply();
        }
    }

    void saveToggles() {
        try {
            JSONArray a = new JSONArray();
            for (Toggle t : toggles) {
                JSONObject o = new JSONObject();
                o.put("label", t.label);
                o.put("key", t.key);
                o.put("on", t.on);
                o.put("off", t.off);
                o.put("choice", t.choice);
                o.put("opts", t.opts);
                a.put(o);
            }
            sp.edit().putString("toggles", a.toString()).apply();
        } catch (Exception e) {
            log("Save failed: " + e.getMessage());
        }
    }

    static Toggle sw(String label, String key, String on, String off) {
        Toggle t = new Toggle();
        t.label = label;
        t.key = key;
        t.on = on;
        t.off = off;
        return t;
    }

    static Toggle ch(String label, String key) {
        Toggle t = new Toggle();
        t.label = label;
        t.key = key;
        t.choice = true;
        t.opts = "Lite=basic\nFull=full";
        return t;
    }

    static List<Toggle> seedList() {
        List<Toggle> l = new ArrayList<>();
        l.add(sw("Parallel app open/close animation", "persist.wm.debug.flyme_recents_merge_animation", "true", "false"));
        l.add(ch("Blur: launcher", "persist.sys.launcher.blur.conf"));
        l.add(ch("Blur: recents", "persist.sys.launcher.recent.blur.conf"));
        l.add(ch("Blur: notification shade", "persist.sys.systemui.notification.blur.conf"));
        l.add(ch("Blur: assistant", "persist.sys.assistant.blur.conf"));
        l.add(ch("Blur: control center", "persist.sys.systemui.control.blur.conf"));
        l.add(ch("Blur: quick panel", "persist.sys.systemui.panel.blur.conf"));
        l.add(ch("Blur: lock screen bouncer", "persist.sys.systemui.bouncer.blur.conf"));
        l.add(sw("Blur: launcher AI search", "persist.sys.launcher.aisearch.blur.conf", "true", "false"));
        l.add(sw("Blur: windowed mode", "persist.sys.windowmode.blur.conf", "true", "false"));
        l.add(sw("Blur: cache", "persist.sys.enable_blur_cache", "true", "false"));
        l.add(ch("Anim: app opening", "persist.sys.launcher.app.opening.anim.conf"));
        l.add(ch("Anim: app closing", "persist.sys.launcher.app.closing.anim.conf"));
        l.add(ch("Anim: folder", "persist.sys.launcher.folder.anim.conf"));
        l.add(ch("Anim: widget opening", "persist.sys.launcher.widget.opening.anim.conf"));
        l.add(ch("Anim: capsule", "persist.sys.launcher.capsule.anim.conf"));
        l.add(ch("Anim: unlock", "persist.sys.launcher.unlockanim.conf"));
        l.add(ch("Anim: notification launch", "persist.sys.systemui.notification.launch.anim.conf"));
        l.add(ch("Anim: wake up", "persist.sys.systemui.wakeup.anim.conf"));
        l.add(ch("Anim: screenshot", "persist.sys.systemui.screenshot.anim.conf"));
        l.add(ch("Anim: control center", "persist.sys.systemui.control.anim.conf"));
        l.add(ch("Anim: wallpaper scale", "persist.sys.wallpaper.scale.anim.conf"));
        l.add(ch("Anim: windowed mode", "persist.sys.windowmode.anim.conf"));
        return l;
    }

    // =====================================================================
    // view helpers
    // =====================================================================
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    TextView tv(String text, int sp, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }

    TextView header(String text) {
        TextView h = tv(text, 14, ACCENT, true);
        h.setPadding(dp(8), dp(22), 0, dp(2));
        return h;
    }

    LinearLayout.LayoutParams mlp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(MATCH, WRAP);
        p.topMargin = dp(topDp);
        return p;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(CARD, 28));
        c.setPadding(dp(18), dp(14), dp(18), dp(14));
        return c;
    }

    LinearLayout newPage(String title) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(4), dp(8), dp(4), dp(8));
        TextView t = tv(title, 40, ON_SURFACE, false);
        t.setPadding(dp(8), dp(28), 0, dp(14));
        p.addView(t);
        return p;
    }

    TextView addInfo(LinearLayout page, String title, String sub) {
        LinearLayout c = card();
        c.addView(tv(title, 18, ON_SURFACE, false));
        TextView s = tv(sub, 14, ON_VARIANT, false);
        s.setPadding(0, dp(2), 0, 0);
        c.addView(s);
        page.addView(c, mlp(8));
        return s;
    }

    void addAction(LinearLayout page, String title, String sub, View.OnClickListener l) {
        LinearLayout c = card();
        c.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), round(CARD, 28), null));
        c.addView(tv(title, 18, ON_SURFACE, false));
        TextView s = tv(sub, 14, ON_VARIANT, false);
        s.setPadding(0, dp(2), 0, 0);
        c.addView(s);
        c.setClickable(true);
        c.setOnClickListener(l);
        page.addView(c, mlp(8));
    }

    TextView pill(String text, boolean primary, View.OnClickListener l) {
        TextView b = tv(text, 15, primary ? ON_PRIMARY : ON_SURFACE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(14), dp(16), dp(14));
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44FFFFFF),
                round(primary ? PRIMARY : CARD2, 28), null));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout pillRow(TextView... bs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (TextView b : bs) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            r.addView(b, lp);
        }
        return r;
    }

    static ColorStateList cs(int checked, int unchecked) {
        return new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{checked, unchecked});
    }

    // =====================================================================
    // layout: pages + glass bottom bar
    // =====================================================================
    void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setPadding(dp(16), 0, dp(16), dp(120));
        root.addView(scroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        pages[0] = buildHome();
        pages[1] = buildTogglesPage();
        pages[2] = buildLauncherPage();
        pages[3] = buildLogPage();

        bar = new GlassBar(this, scroll);
        String[] names = {"Home", "Toggles", "Launcher", "Log"};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_tune, R.drawable.ic_apps, R.drawable.ic_log};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);
            ImageView iv = new ImageView(this);
            iv.setImageResource(icons[i]);
            TextView t = tv(names[i], 11, ON_SURFACE, false);
            t.setGravity(Gravity.CENTER);
            tab.addView(iv, new LinearLayout.LayoutParams(dp(24), dp(24)));
            tab.addView(t, new LinearLayout.LayoutParams(WRAP, WRAP));
            tab.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    selectTab(idx);
                }
            });
            tabs[i] = tab;
            tabIcons[i] = iv;
            tabLabels[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, MATCH, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bar.row.addView(tab, lp);
        }
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(MATCH, dp(68), Gravity.BOTTOM);
        bl.setMargins(dp(24), 0, dp(24), dp(20));
        root.addView(bar, bl);

        setContentView(root);
    }

    void selectTab(int i) {
        scroll.removeAllViews();
        scroll.addView(pages[i], new ViewGroup.LayoutParams(MATCH, WRAP));
        scroll.scrollTo(0, 0);
        for (int j = 0; j < 4; j++) {
            boolean sel = j == i;
            tabs[j].setBackground(sel ? round(alpha(ON_SURFACE, 0x33), 30) : null);
            tabIcons[j].setColorFilter(sel ? ACCENT : ON_SURFACE);
            tabLabels[j].setTextColor(sel ? ACCENT : ON_SURFACE);
        }
        bar.refresh();
    }

    /** Frosted "glass" bar: blurs a snapshot of what scrolls behind it. */
    static class GlassBar extends FrameLayout {
        final ImageView blur;
        final LinearLayout row;
        final View source;
        Bitmap bmp;
        final int[] a = new int[2];
        final int[] b = new int[2];
        final Runnable tick = new Runnable() {
            public void run() {
                refresh();
                postDelayed(this, 250);
            }
        };

        GlassBar(Context ctx, View src) {
            super(ctx);
            source = src;
            final float d = ctx.getResources().getDisplayMetrics().density;
            blur = new ImageView(ctx);
            blur.setScaleType(ImageView.ScaleType.FIT_XY);
            if (Build.VERSION.SDK_INT >= 31) {
                blur.setRenderEffect(RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP));
            }
            addView(blur, new FrameLayout.LayoutParams(MATCH, MATCH));

            View tint = new View(ctx);
            GradientDrawable g = new GradientDrawable();
            g.setColor(MainActivity.alpha(MainActivity.ON_SURFACE, 0x2A));
            g.setCornerRadius(100 * d);
            g.setStroke((int) (1.5f * d), MainActivity.alpha(MainActivity.ON_SURFACE, 0x77));
            tint.setBackground(g);
            addView(tint, new FrameLayout.LayoutParams(MATCH, MATCH));

            row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
            addView(row, new FrameLayout.LayoutParams(MATCH, MATCH));

            setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getHeight() / 2f);
                }
            });
            setClipToOutline(true);

            src.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
                public void onScrollChanged() {
                    refresh();
                }
            });
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            post(tick);
        }

        @Override
        protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }

        void refresh() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0 || source.getWidth() <= 0) return;
            int sw = Math.max(1, w / 4);
            int sh = Math.max(1, h / 4);
            if (bmp == null || bmp.getWidth() != sw || bmp.getHeight() != sh) {
                bmp = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                blur.setImageDrawable(new BitmapDrawable(getResources(), bmp));
            }
            Canvas c = new Canvas(bmp);
            c.drawColor(MainActivity.BG);
            getLocationInWindow(a);
            source.getLocationInWindow(b);
            c.scale(sw / (float) w, sh / (float) h);
            c.translate(b[0] - a[0] - source.getScrollX(), b[1] - a[1] - source.getScrollY());
            source.draw(c);
            blur.invalidate();
        }
    }

    // =====================================================================
    // pages
    // =====================================================================
    LinearLayout buildHome() {
        LinearLayout p = newPage("Flyme Tweaks");

        statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(round(PRIMARY, 32));
        statusCard.setPadding(dp(24), dp(20), dp(24), dp(20));
        statusTitle = tv("Checking root...", 26, ON_PRIMARY, true);
        statusSub = tv("", 14, ON_PRIMARY, false);
        statusCard.addView(statusTitle);
        statusCard.addView(statusSub);
        p.addView(statusCard, mlp(4));

        p.addView(header("Device"));
        addInfo(p, "Device model", Build.MANUFACTURER + " " + Build.MODEL);
        addInfo(p, "Android version", Build.VERSION.RELEASE);
        flymeVer = addInfo(p, "Flyme version", "...");

        p.addView(header("Actions"));
        addAction(p, "Restart launcher", "Force-stop the Flyme launcher and return home", new View.OnClickListener() {
            public void onClick(View v) {
                runLogged("am force-stop " + LAUNCHER
                        + "; am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; echo 'Launcher restarted'");
            }
        });
        addAction(p, "Restart SystemUI", "Applies notification, control center and panel changes", new View.OnClickListener() {
            public void onClick(View v) {
                runLogged("killall com.android.systemui || pkill -f com.android.systemui; echo 'SystemUI restarted'");
            }
        });
        addAction(p, "Soft reboot", "Restart the Android framework (screen goes black briefly)", new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Restart the Android framework? The screen will go black for a moment.",
                        "setprop ctl.restart zygote");
            }
        });
        addAction(p, "Reboot", "Full device reboot", new View.OnClickListener() {
            public void onClick(View v) {
                confirm("Reboot the phone?", "reboot");
            }
        });
        return p;
    }

    LinearLayout buildTogglesPage() {
        LinearLayout p = newPage("Toggles");
        toggleBox = new LinearLayout(this);
        toggleBox.setOrientation(LinearLayout.VERTICAL);
        p.addView(toggleBox, new LinearLayout.LayoutParams(MATCH, WRAP));
        p.addView(header("Manage"));
        addAction(p, "Add toggle", "Create your own property toggle", new View.OnClickListener() {
            public void onClick(View v) {
                editDialog(null, "", "");
            }
        });
        addAction(p, "Scan props", "List blur / flyme / animation props on this phone", new View.OnClickListener() {
            public void onClick(View v) {
                scanProps();
            }
        });
        TextView hint = tv("Long-press a toggle to edit or delete it. Launcher and recents props: Restart launcher. "
                + "SystemUI props: Restart SystemUI. Anything else: Soft reboot.", 13, ON_VARIANT, false);
        hint.setPadding(dp(8), dp(12), dp(8), 0);
        p.addView(hint);
        return p;
    }

    LinearLayout buildLauncherPage() {
        LinearLayout p = newPage("Launcher");
        p.addView(header("Icon size"));
        LinearLayout c = card();
        c.addView(tv("Icon size progress", 18, ON_SURFACE, false));
        TextView s = tv("Values above 100 go past the stock slider limit. The launcher restarts after applying.",
                13, ON_VARIANT, false);
        s.setPadding(0, dp(2), 0, dp(6));
        c.addView(s);
        iconInput = new EditText(this);
        iconInput.setHint("e.g. 175");
        iconInput.setHintTextColor(ON_VARIANT);
        iconInput.setTextColor(ON_SURFACE);
        iconInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        iconInput.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        c.addView(iconInput, new LinearLayout.LayoutParams(MATCH, WRAP));
        p.addView(c, mlp(8));
        p.addView(pillRow(
                pill("Read current", false, new View.OnClickListener() {
                    public void onClick(View v) {
                        readIcon();
                    }
                }),
                pill("Apply", true, new View.OnClickListener() {
                    public void onClick(View v) {
                        applyIcon();
                    }
                })), mlp(12));
        return p;
    }

    LinearLayout buildLogPage() {
        LinearLayout p = newPage("Log");
        LinearLayout c = card();
        logView = tv("", 12, ON_SURFACE, false);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        c.addView(logView);
        p.addView(c, mlp(4));
        p.addView(pillRow(pill("Clear", false, new View.OnClickListener() {
            public void onClick(View v) {
                logView.setText("");
            }
        })), mlp(12));
        return p;
    }

    void log(String s) {
        String old = logView.getText().toString();
        String n = s + "\n" + old;
        if (n.length() > 5000) n = n.substring(0, 5000);
        logView.setText(n);
    }

    void runLogged(String cmd) {
        su(cmd, new Cb() {
            public void done(int c, String out) {
                log(out.isEmpty() ? "(exit " + c + ")" : out);
            }
        });
    }

    void confirm(String msg, final String cmd) {
        new AlertDialog.Builder(this).setMessage(msg)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        runLogged(cmd);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    // =====================================================================
    // toggles
    // =====================================================================
    static String groupOf(Toggle t) {
        if (t.label.startsWith("Blur:")) return "Blur";
        if (t.label.startsWith("Anim:")) return "Animations";
        return "General";
    }

    static String shortLabel(Toggle t) {
        int i = t.label.indexOf(':');
        if (!groupOf(t).equals("General") && i > 0) return t.label.substring(i + 1).trim();
        return t.label;
    }

    void refreshToggles() {
        toggleBox.removeAllViews();
        String[] groups = {"Blur", "Animations", "General"};
        for (String g : groups) {
            LinearLayout card = null;
            for (Toggle t : toggles) {
                if (!groupOf(t).equals(g)) continue;
                if (card == null) {
                    toggleBox.addView(header(g));
                    card = card();
                    toggleBox.addView(card, mlp(8));
                } else {
                    View div = new View(this);
                    div.setBackgroundColor(DIVIDER);
                    card.addView(div, new LinearLayout.LayoutParams(MATCH, 1));
                }
                if (t.choice) addChoiceRow(card, t);
                else addSwitchRow(card, t);
            }
        }
    }

    LinearLayout titleBlock(Toggle t, View.OnLongClickListener lc) {
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(tv(shortLabel(t), 17, ON_SURFACE, false));
        TextView k = tv(t.key, 11, ON_VARIANT, false);
        k.setTypeface(Typeface.MONOSPACE);
        texts.addView(k);
        texts.setOnLongClickListener(lc);
        return texts;
    }

    void addSwitchRow(LinearLayout card, final Toggle t) {
        View.OnLongClickListener lc = new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                itemMenu(t);
                return true;
            }
        };
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));
        row.setOnLongClickListener(lc);
        row.addView(titleBlock(t, lc), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final Switch sw = new Switch(this);
        sw.setThumbTintList(cs(ON_SURFACE, ON_VARIANT));
        sw.setTrackTintList(cs(PRIMARY, CARD2));
        sw.setEnabled(false);
        sw.setOnLongClickListener(lc);
        row.addView(sw, new LinearLayout.LayoutParams(WRAP, WRAP));
        card.addView(row);
        su("getprop " + t.key, new Cb() {
            public void done(int c, String out) {
                sw.setChecked(out.equals(t.on));
                sw.setEnabled(true);
                sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton b, boolean checked) {
                        setToggle(t, checked, sw);
                    }
                });
            }
        });
    }

    void addChoiceRow(LinearLayout card, final Toggle t) {
        final View.OnLongClickListener lc = new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                itemMenu(t);
                return true;
            }
        };
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(10), 0, dp(6));
        wrap.setOnLongClickListener(lc);
        wrap.addView(titleBlock(t, lc));
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final List<String> vals = new ArrayList<>();
        final List<RadioButton> rbs = new ArrayList<>();
        for (String line : t.opts.split("\n")) {
            int i = line.indexOf('=');
            if (i < 0) continue;
            RadioButton rb = new RadioButton(this);
            rb.setText(line.substring(0, i).trim());
            rb.setTextColor(ON_SURFACE);
            rb.setButtonTintList(cs(PRIMARY, ON_VARIANT));
            rb.setId(View.generateViewId());
            rb.setEnabled(false);
            rb.setOnLongClickListener(lc);
            rg.addView(rb, new RadioGroup.LayoutParams(WRAP, WRAP));
            rbs.add(rb);
            vals.add(line.substring(i + 1).trim());
        }
        wrap.addView(rg);
        card.addView(wrap);
        su("getprop " + t.key, new Cb() {
            public void done(int c, String out) {
                for (int i = 0; i < rbs.size(); i++) {
                    rbs.get(i).setEnabled(true);
                    if (vals.get(i).equals(out)) rg.check(rbs.get(i).getId());
                }
                if (!vals.contains(out)) log(shortLabel(t) + ": current value '" + out + "' matches no option");
                rg.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                    public void onCheckedChanged(RadioGroup g, int id) {
                        for (int i = 0; i < rbs.size(); i++) {
                            if (rbs.get(i).getId() == id) {
                                setChoice(t, vals.get(i));
                                return;
                            }
                        }
                    }
                });
            }
        });
    }

    void setChoice(final Toggle t, final String val) {
        su("setprop " + t.key + " " + sq(val) + "; getprop " + t.key, new Cb() {
            public void done(int c, String out) {
                log("setprop " + t.key + " = " + val + "  ->  now '" + out + "'"
                        + (out.equals(val) ? "" : "  (did not stick)"));
            }
        });
    }

    void setToggle(final Toggle t, boolean on, final Switch sw) {
        final String val = on ? t.on : t.off;
        su("setprop " + t.key + " " + sq(val) + "; getprop " + t.key, new Cb() {
            public void done(int c, String out) {
                log("setprop " + t.key + " = " + val + "  ->  now '" + out + "'");
                final boolean actual = out.equals(t.on);
                if (actual != sw.isChecked()) {
                    sw.setOnCheckedChangeListener(null);
                    sw.setChecked(actual);
                    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(CompoundButton b, boolean checked) {
                            setToggle(t, checked, sw);
                        }
                    });
                    log("Value did not stick (read-only prop or no root?)");
                }
            }
        });
    }

    void itemMenu(final Toggle t) {
        new AlertDialog.Builder(this).setTitle(t.label)
                .setItems(new CharSequence[]{"Edit", "Delete"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int i) {
                        if (i == 0) {
                            editDialog(t, "", "");
                        } else {
                            toggles.remove(t);
                            saveToggles();
                            refreshToggles();
                        }
                    }
                }).show();
    }

    EditText field(LinearLayout box, String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        box.addView(e);
        return e;
    }

    static String guessOff(String v) {
        if (v.equals("true")) return "false";
        if (v.equals("false")) return "true";
        return "0";
    }

    void editDialog(final Toggle existing, String scanKey, String scanVal) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(20);
        box.setPadding(p, p / 2, p, 0);
        String l = "", k = "", on = "", off = "";
        if (existing != null) {
            l = existing.label;
            k = existing.key;
            on = existing.on;
            off = existing.off;
        } else if (!scanKey.isEmpty()) {
            l = scanKey;
            k = scanKey;
            on = scanVal;
            off = guessOff(scanVal);
        }
        final EditText fl = field(box, "Name (e.g. Blur: notification)", l);
        final EditText fk = field(box, "Property key", k);
        final EditText fon = field(box, "Value when ON", on);
        final EditText foff = field(box, "Value when OFF", off);
        final Switch sc = new Switch(this);
        sc.setText("Choice mode (e.g. Lite / Full)");
        sc.setPadding(0, dp(10), 0, dp(4));
        box.addView(sc);
        final EditText fopts = new EditText(this);
        fopts.setHint("One option per line:\nLite=basic\nFull=full");
        fopts.setMinLines(3);
        fopts.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fopts.setText(existing != null ? existing.opts : "");
        box.addView(fopts);
        final boolean startChoice = existing != null && existing.choice;
        sc.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean c) {
                fon.setVisibility(c ? View.GONE : View.VISIBLE);
                foff.setVisibility(c ? View.GONE : View.VISIBLE);
                fopts.setVisibility(c ? View.VISIBLE : View.GONE);
            }
        });
        sc.setChecked(startChoice);
        fon.setVisibility(startChoice ? View.GONE : View.VISIBLE);
        foff.setVisibility(startChoice ? View.GONE : View.VISIBLE);
        fopts.setVisibility(startChoice ? View.VISIBLE : View.GONE);
        ScrollView dsv = new ScrollView(this);
        dsv.addView(box);
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add toggle" : "Edit toggle")
                .setView(dsv)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        String key = fk.getText().toString().trim();
                        if (!key.matches("[A-Za-z0-9._-]+")) {
                            log("Invalid property key");
                            return;
                        }
                        Toggle t = existing != null ? existing : new Toggle();
                        t.label = fl.getText().toString().trim().isEmpty() ? key : fl.getText().toString().trim();
                        t.key = key;
                        if (sc.isChecked()) {
                            String o = fopts.getText().toString().trim();
                            int n = 0;
                            for (String ln : o.split("\n")) if (ln.indexOf('=') > 0) n++;
                            if (n < 2) {
                                log("Choice mode needs at least 2 lines like Lite=basic");
                                return;
                            }
                            t.choice = true;
                            t.opts = o;
                            t.on = "";
                            t.off = "";
                        } else {
                            t.choice = false;
                            t.opts = "";
                            t.on = fon.getText().toString().trim();
                            t.off = foff.getText().toString().trim();
                        }
                        if (existing == null) toggles.add(t);
                        saveToggles();
                        refreshToggles();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    void scanProps() {
        su("getprop | grep -iE 'blur|flyme|anim|recents'", new Cb() {
            public void done(int c, String out) {
                final List<String> keys = new ArrayList<>();
                final List<String> vals = new ArrayList<>();
                Matcher m = Pattern.compile("\\[(.*?)\\]: \\[(.*?)\\]").matcher(out);
                while (m.find() && keys.size() < 300) {
                    keys.add(m.group(1));
                    vals.add(m.group(2));
                }
                if (keys.isEmpty()) {
                    log("No matching props found. Output: " + out);
                    return;
                }
                CharSequence[] items = new CharSequence[keys.size()];
                for (int i = 0; i < items.length; i++) items[i] = keys.get(i) + " = " + vals.get(i);
                new AlertDialog.Builder(MainActivity.this).setTitle("Tap a prop to add as toggle")
                        .setItems(items, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int i) {
                                editDialog(null, keys.get(i), vals.get(i));
                            }
                        }).show();
            }
        });
    }

    // =====================================================================
    // launcher icon size
    // =====================================================================
    // Finds the launcher prefs file and puts its path in $F
    static final String FIND_F = "F=''; for d in /data/user_de/0 /data/user/0 /data/data; do "
            + "if [ -f \"$d/" + LAUNCHER + "/shared_prefs/" + PREFS_NAME + "\" ]; then "
            + "F=\"$d/" + LAUNCHER + "/shared_prefs/" + PREFS_NAME + "\"; break; fi; done; "
            + "if [ -z \"$F\" ]; then F=$(find /data -maxdepth 6 -name " + PREFS_NAME
            + " -path '*" + LAUNCHER + "*' 2>/dev/null | head -n1); fi; "
            + "if [ -z \"$F\" ]; then echo 'Prefs file not found anywhere under /data'; exit 1; fi; ";

    void readIcon() {
        su(FIND_F + "echo \"File: $F\"; grep -o '" + ICON_KEY + "[^/]*' \"$F\"", new Cb() {
            public void done(int c, String out) {
                log(out.isEmpty() ? "Not found (exit " + c + ")" : out);
                Matcher m = Pattern.compile("value=\"([0-9.]+)\"").matcher(out);
                if (m.find()) iconInput.setText(m.group(1));
            }
        });
    }

    void applyIcon() {
        String s = iconInput.getText().toString().trim();
        if (!s.matches("[0-9]{1,4}")) {
            log("Enter a whole number");
            return;
        }
        String cmd = FIND_F
                + "U=$(stat -c %U \"$F\"); "
                + "am force-stop " + LAUNCHER + "; "
                + "sed -i 's/" + ICON_KEY + "\" value=\"[0-9.]*\"/" + ICON_KEY + "\" value=\"" + s + "\"/' \"$F\"; "
                + "chown $U:$U \"$F\"; "
                + "chmod 660 \"$F\"; "
                + "restorecon \"$F\"; "
                + "echo \"File: $F\"; grep -o '" + ICON_KEY + "[^/]*' \"$F\"; "
                + "am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1";
        runLogged(cmd);
    }
}
