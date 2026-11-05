package com.winzie.flymetweaks;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

    static final String LAUNCHER = "com.meizu.flyme.launcher";
    static final String PREFS_XML = "/data/data/" + LAUNCHER
            + "/shared_prefs/MzCommonUtils_Common_Preferences.xml";
    static final String ICON_KEY = "MzCommonUtils_Icon_Size_Progress";
    static final String DEFAULTS = "[{\"label\":\"Parallel app open/close animation\","
            + "\"key\":\"persist.wm.debug.flyme_recents_merge_animation\","
            + "\"on\":\"true\",\"off\":\"false\"}]";

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
    private LinearLayout toggleBox;
    private TextView logView, rootStatus;
    private EditText iconInput;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        loadToggles();
        buildUi();
        checkRoot();
        refreshToggles();
    }

    // ---------- root helper ----------
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
                rootStatus.setText(c == 0 && out.contains("uid=0")
                        ? "Root: granted" + (mmMode == 1 ? " (global mount)" : "")
                        : "Root: NOT available. Grant su access to this app.");
            }
        });
    }

    // ---------- storage ----------
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

    static final int SEED_VERSION = 2;

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
        // Blur (Lite / Full)
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
        // Animations (Lite / Full)
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

    // ---------- UI ----------
    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    TextView header(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(18);
        v.setTypeface(null, Typeface.BOLD);
        v.setPadding(0, dp(22), 0, dp(6));
        return v;
    }

    Button btn(String t, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout row(Button... bs) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : bs) {
            r.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        return r;
    }

    void buildUi() {
        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        TextView title = new TextView(this);
        title.setText("Flyme Tweaks");
        title.setTextSize(26);
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title);

        rootStatus = new TextView(this);
        rootStatus.setText("Root: checking...");
        root.addView(rootStatus);

        root.addView(header("Toggles"));
        toggleBox = new LinearLayout(this);
        toggleBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(toggleBox);
        root.addView(row(
                btn("Add toggle", new View.OnClickListener() {
                    public void onClick(View v) {
                        editDialog(null, "", "");
                    }
                }),
                btn("Scan props", new View.OnClickListener() {
                    public void onClick(View v) {
                        scanProps();
                    }
                })));
        TextView hint = new TextView(this);
        hint.setText("Long-press a toggle to edit or delete it. Launcher/recents props: Restart launcher. SystemUI props: Restart SystemUI. Others: Soft reboot.");
        hint.setTextSize(12);
        root.addView(hint);

        root.addView(header("Launcher icon size"));
        iconInput = new EditText(this);
        iconInput.setHint("Icon size progress (e.g. 120)");
        iconInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(iconInput);
        root.addView(row(
                btn("Read current", new View.OnClickListener() {
                    public void onClick(View v) {
                        readIcon();
                    }
                }),
                btn("Apply", new View.OnClickListener() {
                    public void onClick(View v) {
                        applyIcon();
                    }
                })));

        root.addView(header("Actions"));
        root.addView(row(
                btn("Restart launcher", new View.OnClickListener() {
                    public void onClick(View v) {
                        runLogged("am force-stop " + LAUNCHER
                                + "; am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; echo ok");
                    }
                }),
                btn("Restart SystemUI", new View.OnClickListener() {
                    public void onClick(View v) {
                        runLogged("killall com.android.systemui || pkill -f com.android.systemui; echo ok");
                    }
                })));
        root.addView(row(
                btn("Soft reboot", new View.OnClickListener() {
                    public void onClick(View v) {
                        confirm("Restart the Android framework? The screen will go black for a moment.",
                                "setprop ctl.restart zygote");
                    }
                }),
                btn("Reboot", new View.OnClickListener() {
                    public void onClick(View v) {
                        confirm("Reboot the phone?", "reboot");
                    }
                })));

        root.addView(header("Log"));
        logView = new TextView(this);
        logView.setTextIsSelectable(true);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextSize(12);
        root.addView(logView);

        setContentView(sv);
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
                .setPositiveButton("Yes", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        runLogged(cmd);
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    // ---------- toggles ----------
    void refreshToggles() {
        toggleBox.removeAllViews();
        for (final Toggle t : toggles) {
            if (t.choice) {
                addChoice(t);
                continue;
            }
            final Switch sw = new Switch(this);
            sw.setText(t.label + "\n" + t.key + "  (on=" + t.on + ", off=" + t.off + ")");
            sw.setPadding(0, dp(10), 0, dp(10));
            sw.setEnabled(false);
            sw.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    itemMenu(t);
                    return true;
                }
            });
            toggleBox.addView(sw);
            su("getprop " + t.key, new Cb() {
                public void done(int c, String out) {
                    sw.setChecked(out.equals(t.on));
                    sw.setEnabled(true);
                    sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
                            setToggle(t, checked, sw);
                        }
                    });
                }
            });
        }
    }

    void addChoice(final Toggle t) {
        final View.OnLongClickListener lc = new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                itemMenu(t);
                return true;
            }
        };
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, dp(10), 0, dp(10));
        TextView tv = new TextView(this);
        tv.setText(t.label + "\n" + t.key);
        tv.setOnLongClickListener(lc);
        wrap.addView(tv);
        final RadioGroup rg = new RadioGroup(this);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        final List<String> vals = new ArrayList<>();
        final List<RadioButton> rbs = new ArrayList<>();
        for (String line : t.opts.split("\n")) {
            int i = line.indexOf('=');
            if (i < 0) continue;
            RadioButton rb = new RadioButton(this);
            rb.setText(line.substring(0, i).trim());
            rb.setId(View.generateViewId());
            rb.setEnabled(false);
            rb.setOnLongClickListener(lc);
            rg.addView(rb);
            rbs.add(rb);
            vals.add(line.substring(i + 1).trim());
        }
        wrap.addView(rg);
        toggleBox.addView(wrap);
        su("getprop " + t.key, new Cb() {
            public void done(int c, String out) {
                for (int i = 0; i < rbs.size(); i++) {
                    rbs.get(i).setEnabled(true);
                    if (vals.get(i).equals(out)) rg.check(rbs.get(i).getId());
                }
                if (!vals.contains(out)) log(t.label + ": current value '" + out + "' matches no option");
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
                    sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
                        public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) {
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
                .setItems(new CharSequence[]{"Edit", "Delete"}, new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int i) {
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
        final EditText fl = field(box, "Name (e.g. Blur notification)", l);
        final EditText fk = field(box, "Property key", k);
        final EditText fon = field(box, "Value when ON", on);
        final EditText foff = field(box, "Value when OFF", off);
        final Switch sc = new Switch(this);
        sc.setText("Choice mode (e.g. Lite / Full)");
        sc.setPadding(0, dp(10), 0, dp(4));
        box.addView(sc);
        final EditText fopts = new EditText(this);
        fopts.setHint("One option per line:\nLite=0\nFull=1");
        fopts.setMinLines(3);
        fopts.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        fopts.setText(existing != null ? existing.opts : "");
        box.addView(fopts);
        final boolean startChoice = existing != null && existing.choice;
        sc.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(android.widget.CompoundButton b, boolean c) {
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
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
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
                                log("Choice mode needs at least 2 lines like Lite=0");
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
                        .setItems(items, new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int i) {
                                editDialog(null, keys.get(i), vals.get(i));
                            }
                        }).show();
            }
        });
    }

    // ---------- launcher icon size ----------
    static final String PREFS_NAME = "MzCommonUtils_Common_Preferences.xml";
    // Finds the launcher prefs file (it may live in device-protected storage) and puts its path in $F
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
