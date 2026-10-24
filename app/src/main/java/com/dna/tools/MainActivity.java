package com.dna.tools;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0F1115, SURFACE = 0xFF181B21, SURFACE2 = 0xFF20242C;
    private static final int ACCENT = 0xFF5EEAD4, TXT = 0xFFE6E8EB, SUB = 0xFF8B93A1;
    private static final int OK = 0xFF66BB6A, ERR = 0xFFEF5350;

    private interface Getter { String get(); }

    private static final class Screen {
        String title;
        View view;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<Screen> stack = new ArrayList<>();
    private Runner runner;
    private TextView title, back, projChip, rootChip;
    private FrameLayout content;
    private volatile Runner.Started running;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(20), dp(16), dp(20), dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        back = text("←", 26, TXT, true);
        back.setPadding(0, 0, dp(16), 0);
        back.setVisibility(View.GONE);
        back.setOnClickListener(v -> onBackPressed());
        title = text("DNA Lite", 26, TXT, true);
        row.addView(back);
        row.addView(title);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, dp(12), 0, 0);
        projChip = chip();
        rootChip = chip();
        chips.addView(projChip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        chips.addView(rootChip, lp);
        top.addView(row);
        top.addView(chips);

        content = new FrameLayout(this);
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        updateHeader();

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        message("Preparing tools…");
        new Thread(() -> {
            try {
                Toolkit.install(this);
                runner = new Runner(this);
                List<Menu.Node> home = new ArrayList<>(Menu.parse(asset("menu/home.xml")));
                home.addAll(Menu.parse(asset("menu/more.xml")));
                ui.post(() -> {
                    content.removeAllViews();
                    push("DNA Lite", list(home));
                });
                runner.awaitRoot();
                ui.post(this::updateHeader);
            } catch (Exception e) {
                ui.post(() -> message("Error: " + e));
            }
        }).start();
    }

    // ---------- util & styling ----------
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private GradientDrawable rect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private Drawable ripple(int color, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), rect(color, radiusDp), null);
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private TextView chip() {
        TextView t = text("", 12, TXT, false);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackground(rect(SURFACE2, 20));
        return t;
    }

    private Button button(String s, int color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(color == ACCENT ? 0xFF062925 : Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(ripple(color, 16));
        return b;
    }

    private EditText input() {
        EditText e = new EditText(this);
        e.setTextColor(TXT);
        e.setHintTextColor(SUB);
        e.setTextSize(14);
        e.setBackground(rect(SURFACE2, 12));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private String asset(String path) throws IOException {
        InputStream in = getAssets().open(path);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    private String project() {
        try {
            File f = new File(getCacheDir(), "DNA.ini");
            if (!f.exists()) return "none";
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String l = r.readLine();
            r.close();
            return l == null || l.trim().isEmpty() ? "none" : l.trim();
        } catch (IOException e) {
            return "none";
        }
    }

    private void message(String s) {
        content.removeAllViews();
        TextView t = text(s, 15, SUB, false);
        t.setGravity(Gravity.CENTER);
        content.addView(t, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------- navigasi ----------
    private void push(String t, View v) {
        Screen s = new Screen();
        s.title = t;
        s.view = v;
        stack.add(s);
        showTop();
    }

    private void showTop() {
        if (stack.isEmpty()) return;
        View v = stack.get(stack.size() - 1).view;
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(160).start();
        updateHeader();
    }

    private void updateHeader() {
        if (!stack.isEmpty()) title.setText(stack.get(stack.size() - 1).title);
        back.setVisibility(stack.size() > 1 ? View.VISIBLE : View.GONE);
        projChip.setText("Project: " + project());
        boolean r = runner != null && runner.isRoot();
        rootChip.setText(r ? "● Root" : "● No root");
        rootChip.setTextColor(r ? OK : ERR);
    }

    @Override
    public void onBackPressed() {
        if (running != null) {
            toast("Task is still running. Press Stop first.");
            return;
        }
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            showTop();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- daftar menu ----------
    private View list(List<Menu.Node> nodes) {
        final List<Menu.Node> items = new ArrayList<>();
        Menu.flatten(nodes, items);
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPadding(0, 0, 0, dp(24));
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int i) { return items.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public boolean isEnabled(int i) {
                int t = items.get(i).type;
                return t == Menu.ACTION || t == Menu.PAGE;
            }

            @Override public View getView(int i, View cv, ViewGroup parent) {
                Menu.Node n = items.get(i);
                if (n.type == Menu.GROUP) {
                    TextView h = text(n.title.toUpperCase(), 12, ACCENT, true);
                    h.setLetterSpacing(0.08f);
                    h.setPadding(dp(24), dp(22), dp(24), dp(6));
                    return h;
                }
                FrameLayout wrap = new FrameLayout(MainActivity.this);
                wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(14), dp(14), dp(14), dp(14));
                card.setDuplicateParentState(true);
                if (n.type == Menu.TEXT) {
                    card.setBackground(rect(SURFACE, 16));
                    card.addView(text(n.title.trim(), 12, SUB, false));
                } else {
                    card.setBackground(ripple(SURFACE, 16));
                    String ch = n.title.isEmpty() ? "•" : n.title.substring(0, 1).toUpperCase();
                    TextView badge = text(ch, 16, ACCENT, true);
                    badge.setGravity(Gravity.CENTER);
                    badge.setBackground(rect(0x225EEAD4, 14));
                    card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
                    LinearLayout col = new LinearLayout(MainActivity.this);
                    col.setOrientation(LinearLayout.VERTICAL);
                    col.setPadding(dp(14), 0, dp(8), 0);
                    col.addView(text(n.title, 16, TXT, false));
                    if (!n.desc.isEmpty()) col.addView(text(n.desc, 12, SUB, false));
                    card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (n.type == Menu.PAGE) card.addView(text("›", 22, SUB, false));
                }
                wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
                return wrap;
            }
        });
        lv.setOnItemClickListener((a, v, pos, id) -> onNode(items.get(pos)));
        return lv;
    }

    private void onNode(Menu.Node n) {
        if (n.type == Menu.ACTION) guard(n, () -> openAction(n));
        else if (n.type == Menu.PAGE) guard(n, () -> openPage(n));
    }

    private void guard(Menu.Node n, Runnable go) {
        if (n.lockSh.isEmpty()) {
            go.run();
            return;
        }
        new Thread(() -> {
            String out = runner.capture(n.lockSh).trim();
            ui.post(() -> {
                if (out.equals("unlocked")) go.run();
                else toast(out.isEmpty() ? "Locked" : out);
            });
        }).start();
    }

    private void openPage(Menu.Node n) {
        if (!n.link.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(n.link)));
            } catch (Exception e) {
                toast("Cannot open link");
            }
            return;
        }
        if (n.config.isEmpty() && n.configSh.isEmpty()) return;
        new Thread(() -> {
            try {
                String xml = !n.config.isEmpty() ? asset("menu/" + n.config) : runner.capture(n.configSh);
                List<Menu.Node> nodes = new ArrayList<>(Menu.parse(xml));
                if (n.fileOption && !n.handler.isEmpty()) {
                    Menu.Node imp = new Menu.Node(Menu.ACTION);
                    imp.title = "Import plugin (.zip2)";
                    imp.desc = "Enter the absolute path of a .zip2 file";
                    imp.set = n.handler;
                    Menu.Param p = new Menu.Param();
                    p.name = "file";
                    p.label = "File path";
                    p.placeholder = "/sdcard/…/plugin.zip2";
                    p.required = true;
                    imp.params.add(p);
                    nodes.add(0, imp);
                }
                ui.post(() -> push(n.title, list(nodes)));
            } catch (Exception e) {
                ui.post(() -> toast("Error: " + e));
            }
        }).start();
    }

    // ---------- action + parameter ----------
    private void openAction(Menu.Node n) {
        if (n.params.isEmpty()) {
            run(n.title, n.set, new LinkedHashMap<String, String>());
            return;
        }
        new Thread(() -> {
            for (Menu.Param p : n.params) {
                if (!p.optionsSh.isEmpty()) {
                    p.options.clear();
                    for (String line : runner.capture(p.optionsSh).split("\n")) {
                        if (line.trim().isEmpty()) continue;
                        Menu.Opt o = new Menu.Opt();
                        int i = line.indexOf('|');
                        o.value = i < 0 ? line.trim() : line.substring(0, i);
                        o.label = i < 0 ? line.trim() : line.substring(i + 1);
                        p.options.add(o);
                    }
                }
                if (!p.valueSh.isEmpty()) {
                    String v = runner.capture(p.valueSh).trim();
                    if (!v.isEmpty()) p.value = v;
                }
            }
            ui.post(() -> paramDialog(n));
        }).start();
    }

    private void paramDialog(Menu.Node n) {
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));
        sv.addView(box);
        final List<Getter> getters = new ArrayList<>();
        for (Menu.Param p : n.params) getters.add(addParam(box, p));

        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(n.title).setView(sv)
                .setPositiveButton("Run", null).setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String, String> env = new LinkedHashMap<>();
            for (int i = 0; i < n.params.size(); i++) {
                Menu.Param p = n.params.get(i);
                String val = getters.get(i).get();
                if (p.required && val.trim().isEmpty()) {
                    toast((p.label.isEmpty() ? (p.title.isEmpty() ? p.name : p.title) : p.label) + " is required");
                    return;
                }
                if (!p.name.isEmpty()) env.put(p.name, val);
            }
            d.dismiss();
            run(n.title, n.set, env);
        });
    }

    private Getter addParam(LinearLayout box, Menu.Param p) {
        boolean check = p.type.equals("checkbox") || p.type.equals("switch");
        if (!check && !p.label.isEmpty()) {
            TextView t = text(p.label, 15, TXT, true);
            t.setPadding(0, dp(16), 0, dp(2));
            box.addView(t);
        }
        if (!check && !p.title.isEmpty()) box.addView(text(p.title, 13, SUB, false));

        Getter g;
        if (check) {
            CheckBox cb = new CheckBox(this);
            cb.setText(!p.label.isEmpty() ? p.label : p.title);
            cb.setTextColor(TXT);
            cb.setChecked(p.value.equals("1") || p.value.equals("true"));
            cb.setPadding(dp(6), dp(8), 0, dp(8));
            box.addView(cb);
            g = () -> cb.isChecked() ? "1" : "0";
        } else if (p.type.equals("seekbar")) {
            int min = p.min, max = Math.max(p.max, p.min + 1);
            SeekBar sb = new SeekBar(this);
            sb.setMax(max - min);
            int v;
            try { v = Integer.parseInt(p.value.trim()); } catch (Exception e) { v = min; }
            sb.setProgress(Math.max(0, Math.min(max - min, v - min)));
            TextView val = text(String.valueOf(min + sb.getProgress()), 13, ACCENT, true);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar s, int pr, boolean u) { val.setText(String.valueOf(min + pr)); }
                @Override public void onStartTrackingTouch(SeekBar s) { }
                @Override public void onStopTrackingTouch(SeekBar s) { }
            });
            box.addView(val);
            box.addView(sb);
            g = () -> String.valueOf(min + sb.getProgress());
        } else if (!p.options.isEmpty() && p.multiple) {
            List<CheckBox> cbs = new ArrayList<>();
            for (Menu.Opt o : p.options) {
                CheckBox cb = new CheckBox(this);
                cb.setText(o.label);
                cb.setTextColor(TXT);
                cb.setChecked(o.selected);
                box.addView(cb);
                cbs.add(cb);
            }
            g = () -> {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cbs.size(); i++) {
                    if (!cbs.get(i).isChecked()) continue;
                    if (sb.length() > 0) sb.append(p.separator);
                    sb.append(p.options.get(i).value);
                }
                return sb.toString();
            };
        } else if (!p.options.isEmpty()) {
            List<String> labels = new ArrayList<>();
            int sel = 0;
            for (int i = 0; i < p.options.size(); i++) {
                Menu.Opt o = p.options.get(i);
                labels.add(o.label);
                if (o.selected || (!p.value.isEmpty() && o.value.equals(p.value))) sel = i;
            }
            Spinner sp = new Spinner(this);
            sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
            sp.setSelection(sel);
            box.addView(sp);
            g = () -> p.options.get(sp.getSelectedItemPosition()).value;
        } else {
            EditText et = input();
            et.setText(p.value);
            et.setHint(p.placeholder);
            et.setSingleLine(!p.name.equals("TO"));
            if (p.type.equals("float") || p.type.equals("number") || p.type.equals("int")) {
                int flags = InputType.TYPE_CLASS_NUMBER;
                if (p.type.equals("float")) flags |= InputType.TYPE_NUMBER_FLAG_DECIMAL;
                et.setInputType(flags);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = dp(6);
            box.addView(et, lp);
            g = () -> et.getText().toString().trim();
        }
        if (!p.desc.isEmpty()) {
            TextView d = text(p.desc, 12, SUB, false);
            d.setPadding(0, dp(4), 0, 0);
            box.addView(d);
        }
        return g;
    }

    // ---------- console ----------
    private final class Console {
        final LinearLayout root = new LinearLayout(MainActivity.this);
        final TextView tv = new TextView(MainActivity.this);
        final TextView status = chip();
        final ScrollView sc = new ScrollView(MainActivity.this);
        final Button btn = button("Stop", ERR);
        private final StringBuilder pending = new StringBuilder();
        private boolean scheduled;

        Console() {
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(14), dp(4), dp(14), dp(14));
            status.setText("● Running…");
            status.setTextColor(ACCENT);
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(12);
            tv.setTextColor(0xFFD7DAE0);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            sc.setBackground(rect(0xFF0A0C0F, 16));
            sc.addView(tv);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-2, -2);
            sl.bottomMargin = dp(10);
            root.addView(status, sl);
            root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(52));
            bl.topMargin = dp(12);
            root.addView(btn, bl);
        }

        void append(String s) {
            synchronized (pending) {
                pending.append(s);
                if (scheduled) return;
                scheduled = true;
            }
            ui.postDelayed(this::flush, 80);
        }

        void flush() {
            String s;
            synchronized (pending) {
                s = pending.toString();
                pending.setLength(0);
                scheduled = false;
            }
            tv.append(s);
            if (tv.length() > 300000) {
                CharSequence t = tv.getText();
                tv.setText(t.subSequence(150000, t.length()));
            }
            sc.post(() -> sc.fullScroll(View.FOCUS_DOWN));
        }

        void finish(int exit) {
            flush();
            boolean ok = exit == 0;
            status.setText(ok ? "✓ Done" : "✕ Failed (exit " + exit + ")");
            status.setTextColor(ok ? OK : ERR);
            btn.setText("Close");
            btn.setTextColor(0xFF062925);
            btn.setBackground(ripple(ACCENT, 16));
        }
    }

    private void run(String t, String body, Map<String, String> env) {
        if (running != null) {
            toast("Another task is running");
            return;
        }
        final Console c = new Console();
        c.btn.setOnClickListener(v -> {
            Runner.Started st = running;
            if (st != null) {
                st.proc.destroy();
                toast("Stopping…");
            } else {
                onBackPressed();
            }
        });
        push(t, c.root);
        new Thread(() -> {
            int code = -1;
            Runner.Started st = null;
            try {
                st = runner.start(body, env, true);
                running = st;
                BufferedReader r = new BufferedReader(new InputStreamReader(st.proc.getInputStream(), "UTF-8"));
                String l;
                while ((l = r.readLine()) != null) c.append(l + "\n");
                code = st.proc.waitFor();
            } catch (Exception e) {
                c.append("! " + e + "\n");
            } finally {
                if (st != null) st.cleanup();
                running = null;
            }
            final int exit = code;
            ui.post(() -> {
                c.finish(exit);
                updateHeader();
            });
        }).start();
    }
}
