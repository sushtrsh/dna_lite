package com.dna.tools;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.Base64;
import android.os.Handler;
import android.os.Looper;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int BG = 0xFF0C111E, SURFACE = 0xFF141B2D, SURFACE2 = 0xFF1B2438;
    private static final int ACCENT = 0xFFFF8282, TXT = 0xFFE9ECF3, SUB = 0xFF8A93A8;
    private static final int OK = 0xFF66BB6A, ERR = 0xFFEF5350;
    private static final String LOAD =
            "for d in \"$DNA_DIR\"/DNA_*/; do\n"
            + "  [ -d \"$d\" ] || continue\n"
            + "  n=$(basename \"$d\")\n"
            + "  s=$(du -sh \"$d\" 2>/dev/null | cut -f1)\n"
            + "  c=$(ls -1 \"$d\" 2>/dev/null | wc -l)\n"
            + "  m=$(stat -c %Y \"$d\" 2>/dev/null)\n"
            + "  u=0; uc=0\n"
            + "  if [ -d \"$DNA_TMP/$n\" ]; then u=1; uc=$(ls -1 \"$DNA_TMP/$n\" 2>/dev/null | wc -l); fi\n"
            + "  echo \"P|$n|$s|$c|$m|$u|$uc\"\n"
            + "done\n";

    private interface Getter { String get(); }

    private static final class Screen {
        String title;
        View view;
        boolean home;
        Runnable onShow;
        EditorView editor;
        Explorer explorer;
        boolean plain;   // layar tanpa subjudul Project/Root (explorer, console)
    }

    private static final class Row {
        static final int STORAGE = 0, SECTION = 1, PROJECT = 2, EMPTY = 3, NODE = 4, PIN = 5, ADD = 6, DISKS = 7;
        int kind;
        String name = "", size = "", avail = "", total = "", text = "", title = "";
        boolean missing;
        final List<Row> disks = new ArrayList<>();
        int count, pct;
        long mtime;
        Menu.Node node;
        Browse b;
        boolean hasUnpacked;
        int uCount;
        long freeBytes;
    }

    private static final class Browse {
        boolean open, loading;
        int base;   // 0 = /sdcard/DNA/<project>, 1 = hasil unpack di /data/DNA/<project>
        String abs; // kalau diisi: folder yang di-pin (path absolut), base diabaikan
        final List<String> sub = new ArrayList<>();
        List<String[]> files = new ArrayList<>();
    }

    /** Volume penyimpanan eksternal (micro SD / USB OTG). */
    private static final class Vol {
        String path = "", label = "";
        long total, avail;
    }

    private static final int EX_PIN = 0, EX_DEST = 1;

    private static final class Sec {
        String title = "";
        final List<Menu.Node> items = new ArrayList<>();
        boolean open;
    }

    private static final class VRow {
        static final int TOP = 0, HEADER = 1, ITEM = 2;
        int kind;
        Menu.Node node;
        Sec sec;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<Screen> stack = new ArrayList<>();
    private Runner runner;
    private TextView title, back, sub;
    private View logoBox, spacer, plus, titleBox;
    private FrameLayout content;
    private final List<Row> rows = new ArrayList<>();
    private BaseAdapter homeAdapter;
    private List<Menu.Node> toolNodes = new ArrayList<>();
    private Menu.Node unzipNode;
    private final Map<String, Browse> browse = new HashMap<>();
    private volatile Runner.Started running;
    private volatile List<Vol> sdCache = new ArrayList<>();
    private final BroadcastReceiver mediaRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            // kasih waktu mount selesai sebelum cek ulang
            ui.postDelayed(() -> {
                if (runner != null && stack.size() == 1 && stack.get(0).home) reloadHome();
            }, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(12), dp(8));
        back = text("←", 24, TXT, true);
        back.setGravity(Gravity.CENTER);
        back.setBackground(ripple(0x00000000, 22));
        back.setVisibility(View.GONE);
        back.setOnClickListener(v -> onBackPressed());
        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.setPadding(dp(8), 0, 0, 0);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_logo);
        TextView na = text("na", 30, ACCENT, true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = -dp(3);
        logo.addView(mark);
        logo.addView(na, nlp);
        logoBox = logo;
        title = text("", 19, TXT, true);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        sub = text("", 12, SUB, false);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout tb = new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        tb.setPadding(dp(4), 0, dp(8), 0);
        tb.addView(title);
        tb.addView(sub);
        titleBox = tb;
        spacer = new View(this);
        ImageView pl = new ImageView(this);
        pl.setImageResource(R.drawable.ic_plus);
        pl.setPadding(dp(8), dp(8), dp(8), dp(8));
        pl.setBackground(ripple(0x00000000, 22));
        pl.setOnClickListener(v -> newProjectDialog());
        plus = pl;
        row.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        row.addView(logoBox);
        row.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View divider = new View(this);
        divider.setBackgroundColor(0x24FFFFFF);

        content = new FrameLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        updateHeader();

        IntentFilter mf = new IntentFilter();
        mf.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mf.addAction(Intent.ACTION_MEDIA_EJECT);
        mf.addAction(Intent.ACTION_MEDIA_REMOVED);
        mf.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        mf.addDataScheme("file");
        registerReceiver(mediaRx, mf);

        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 30) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        message("Preparing tools…");
        new Thread(() -> {
            try {
                Toolkit.install(this);
                runner = new Runner(this);
                List<Menu.Node> homeNodes = new ArrayList<>(Menu.parse(asset("menu/home.xml")));
                List<Menu.Node> flat = new ArrayList<>();
                Menu.flatten(homeNodes, flat);
                for (Menu.Node n : flat) {
                    if (n.type == Menu.ACTION && n.title.equals("Unzip ROM")) unzipNode = n;
                }
                toolNodes = new ArrayList<>(Menu.parse(asset("menu/more.xml")));
                for (Menu.Node n : flat) if (n.type == Menu.TEXT) toolNodes.add(n);
                ui.post(() -> {
                    content.removeAllViews();
                    View hv = homeView();
                    push("DNA", hv);
                    stack.get(0).home = true;
                    stack.get(0).onShow = this::reloadHome;
                    reloadHome();
                });
                runner.awaitRoot();
                ui.post(() -> {
                    updateHeader();
                    if (!stack.isEmpty() && stack.get(0).home) reloadHome();
                });
            } catch (Exception e) {
                ui.post(() -> message("Error: " + e));
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (runner != null && stack.size() == 1 && stack.get(0).home) reloadHome();
    }

    @Override
    protected void onDestroy() {
        try { unregisterReceiver(mediaRx); } catch (Exception ignored) { }
        super.onDestroy();
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
        b.setTextColor(color == ACCENT ? 0xFF2B0A0A : Color.WHITE);
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
        Runnable r = stack.get(stack.size() - 1).onShow;
        if (r != null) r.run();
    }

    private void updateHeader() {
        boolean home = stack.size() <= 1;
        Screen top = stack.isEmpty() ? null : stack.get(stack.size() - 1);
        if (top != null) title.setText(top.title);
        back.setVisibility(home ? View.GONE : View.VISIBLE);
        logoBox.setVisibility(home ? View.VISIBLE : View.GONE);
        spacer.setVisibility(home ? View.VISIBLE : View.GONE);
        titleBox.setVisibility(home ? View.GONE : View.VISIBLE);
        plus.setVisibility(home ? View.VISIBLE : View.GONE);
        boolean showSub = !home && top != null && !top.plain;
        sub.setVisibility(showSub ? View.VISIBLE : View.GONE);
        if (showSub) {
            String pr = project();
            boolean r = runner != null && runner.isRoot();
            String head = pr.equals("none") ? "No project" : display(pr);
            String dot = r ? "● Root" : "● No root";
            SpannableStringBuilder sb = new SpannableStringBuilder(head + "   " + dot);
            sb.setSpan(new ForegroundColorSpan(r ? OK : ERR), head.length() + 3, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sub.setText(sb);
        }
    }

    private static String display(String name) {
        return name.startsWith("DNA_") ? name.substring(4) : name;
    }

    @Override
    public void onBackPressed() {
        if (running != null) {
            toast("Task is still running. Press Stop first.");
            return;
        }
        if (stack.size() > 1) {
            Screen top = stack.get(stack.size() - 1);
            if (top.editor != null && top.editor.hasChanges()) {
                confirmDiscard();
                return;
            }
            if (top.explorer != null && top.explorer.up()) return;
            stack.remove(stack.size() - 1);
            showTop();
        } else {
            super.onBackPressed();
        }
    }

    private void confirmDiscard() {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Discard changes?")
                .setMessage("This file has unsaved changes.")
                .setPositiveButton("Discard", (x, w) -> {
                    if (stack.size() > 1) {
                        stack.remove(stack.size() - 1);
                        showTop();
                    }
                })
                .setNegativeButton("Keep editing", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ERR);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
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
                return nodeView(items.get(i));
            }
        });
        lv.setOnItemClickListener((a, v, pos, id) -> onNode(items.get(pos)));
        return lv;
    }

    private View nodeView(Menu.Node n) {
        if (n.type == Menu.GROUP) {
            TextView h = text(n.title.toUpperCase(), 12, ACCENT, true);
            h.setLetterSpacing(0.08f);
            h.setPadding(dp(24), dp(22), dp(24), dp(6));
            return h;
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setDuplicateParentStateEnabled(true);
        if (n.type == Menu.TEXT) {
            card.setBackground(rect(SURFACE, 16));
            card.addView(text(n.title.trim(), 12, SUB, false));
        } else {
            card.setBackground(ripple(SURFACE, 16));
            String ch = n.title.isEmpty() ? "•" : n.title.substring(0, 1).toUpperCase();
            TextView badge = text(ch, 16, ACCENT, true);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rect(0x22FF8282, 14));
            card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
            LinearLayout col = new LinearLayout(this);
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

    // ---------- home: daftar folder project ----------
    private View homeView() {
        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPadding(0, dp(6), 0, dp(24));
        homeAdapter = new BaseAdapter() {
            @Override public int getCount() { return rows.size(); }
            @Override public Object getItem(int i) { return rows.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public boolean isEnabled(int i) {
                Row r = rows.get(i);
                if (r.kind == Row.PROJECT || r.kind == Row.PIN || r.kind == Row.ADD) return true;
                return r.kind == Row.NODE && (r.node.type == Menu.ACTION || r.node.type == Menu.PAGE);
            }
            @Override public View getView(int i, View cv, ViewGroup parent) { return rowView(rows.get(i)); }
        };
        lv.setAdapter(homeAdapter);
        lv.setOnItemClickListener((a, v, pos, id) -> {
            Row r = rows.get(pos);
            if (r.kind == Row.PROJECT || r.kind == Row.PIN) toggleProject(r);
            else if (r.kind == Row.ADD) openExplorer(EX_PIN, null, false, null);
            else if (r.kind == Row.NODE) onNode(r.node);
        });
        lv.setOnItemLongClickListener((a, v, pos, id) -> {
            Row r = rows.get(pos);
            if (r.kind == Row.PROJECT) {
                projectMenu(r.name);
                return true;
            }
            if (r.kind == Row.PIN) {
                unpinDialog(r.name);
                return true;
            }
            return false;
        });
        return lv;
    }

    private View rowView(Row r) {
        switch (r.kind) {
            case Row.NODE:
                return nodeView(r.node);
            case Row.SECTION: {
                TextView h = text(r.text.toUpperCase(), 12, ACCENT, true);
                h.setLetterSpacing(0.08f);
                h.setPadding(dp(24), dp(20), dp(24), dp(6));
                return h;
            }
            case Row.EMPTY: {
                TextView t = text(r.text, 14, SUB, false);
                t.setGravity(Gravity.CENTER);
                t.setPadding(dp(24), dp(28), dp(24), dp(28));
                return t;
            }
            case Row.DISKS:
                return disksView(r);
            case Row.ADD:
                return addCard();
            default:
                return projectCard(r);
        }
    }

    /** Internal + SD/USB: dua kartu per baris (kiri-kanan). Kartu tunggal memenuhi lebar. */
    private View disksView(Row r) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(4), dp(14), dp(4));
        int n = r.disks.size();
        for (int i = 0; i < n; i += 2) {
            LinearLayout line = new LinearLayout(this);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) ll.topMargin = dp(10);
            for (int j = i; j < Math.min(n, i + 2); j++) {
                LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1f);
                if (n > 1) {
                    if (j % 2 == 0) cp.rightMargin = dp(5);
                    else cp.leftMargin = dp(5);
                }
                line.addView(diskCard(r.disks.get(j)), cp);
            }
            if (n > 1 && n - i == 1) {
                LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(0, 1, 1f);
                sl.leftMargin = dp(5);
                line.addView(new View(this), sl);
            }
            col.addView(line, ll);
        }
        return col;
    }

    private View diskCard(Row r) {
        final boolean ext = !r.name.isEmpty();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rect(SURFACE, 18));
        card.setPadding(dp(14), dp(14), dp(14), dp(14));

        TextView ttl = text(r.title, 11, SUB, true);
        ttl.setSingleLine(true);
        ttl.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(ttl);

        LinearLayout free = new LinearLayout(this);
        free.setPadding(0, dp(4), 0, dp(10));
        free.addView(text(r.avail, 20, TXT, true));
        free.addView(text(" free", 12, SUB, false));
        card.addView(free);

        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(rect(SURFACE2, 4));
        View fill = new View(this);
        fill.setBackground(rect(!ext && r.freeBytes < 5L * 1024 * 1024 * 1024 ? ERR : ACCENT, 4));
        int pct = Math.max(2, Math.min(100, r.pct));
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(6), pct));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(6), 100 - pct));
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(6)));

        TextView foot = text(r.text + " / " + r.total + " used", 11, SUB, false);
        foot.setPadding(0, dp(10), 0, 0);
        card.addView(foot);

        if (ext) {
            TextView path = text(r.name, 11, SUB, false);
            path.setSingleLine(true);
            path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            path.setPadding(0, dp(4), 0, 0);
            card.addView(path);
        } else {
            boolean root = runner != null && runner.isRoot();
            TextView rt = text(root ? "● Root" : "● No root", 11, root ? OK : ERR, false);
            rt.setPadding(0, dp(4), 0, 0);
            card.addView(rt);
            if (r.freeBytes < 10L * 1024 * 1024 * 1024) {
                TextView warn = text("Low space for ROM work", 11, ERR, false);
                warn.setPadding(0, dp(2), 0, 0);
                card.addView(warn);
            }
        }
        return card;
    }

    private View projectCard(Row r) {
        final boolean pin = r.kind == Row.PIN;
        boolean active = !pin && r.name.equals(project());
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setDuplicateParentStateEnabled(true);
        card.setBackground(ripple(SURFACE, 18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(12), dp(10), dp(12));
        head.setDuplicateParentStateEnabled(true);
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(rect(0x22FF8282, 14));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_folder);
        badge.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        head.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm = text(pin ? r.text : display(r.name), 16, TXT, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(nm, new LinearLayout.LayoutParams(-2, -2, 0f));
        TextView arrow = text(r.b != null && r.b.open ? "  ▴" : "  ▾", 12, SUB, false);
        nameRow.addView(arrow);
        if (active) {
            TextView t = text("ACTIVE", 9, 0xFF2B0A0A, true);
            t.setBackground(rect(ACCENT, 8));
            t.setPadding(dp(6), dp(2), dp(6), dp(2));
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(-2, -2);
            alp.leftMargin = dp(6);
            nameRow.addView(t, alp);
        }
        col.addView(nameRow);
        String date = r.mtime > 0
                ? new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(r.mtime * 1000L)) : "";
        String meta = pin
                ? (r.missing ? "Not available (SD card removed?)" : r.count + " items")
                : (r.size.isEmpty() ? "–" : r.size) + "  •  " + r.count + " items" + (date.isEmpty() ? "" : "  •  " + date);
        col.addView(text(meta, 12, r.missing ? ERR : SUB, false));
        if (pin) {
            TextView pt = text(r.name, 11, SUB, false);
            pt.setTypeface(Typeface.MONOSPACE);
            pt.setSingleLine(true);
            pt.setEllipsize(TextUtils.TruncateAt.START);
            col.addView(pt);
        }
        head.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!pin) {
            TextView mn = text("⋮", 20, SUB, true);
            mn.setGravity(Gravity.CENTER);
            mn.setOnClickListener(v -> projectMenu(r.name));
            head.addView(mn, new LinearLayout.LayoutParams(dp(34), dp(54)));
        }
        TextView go = text(pin ? "×" : "›", 32, ACCENT, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, 0, 0, dp(4));
        go.setBackground(ripple(SURFACE2, 14));
        if (pin) go.setOnClickListener(v -> unpinDialog(r.name));
        else go.setOnClickListener(v -> openProject(r.name));
        head.addView(go, new LinearLayout.LayoutParams(dp(54), dp(54)));
        card.addView(head);

        if (r.b != null && r.b.open) card.addView(filesView(r));
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private Row storageRow() {
        try {
            StatFs sf = new StatFs(
                    Environment.getDataDirectory().getAbsolutePath()
            );

            long total = sf.getTotalBytes();
            long available = sf.getAvailableBytes();

            if (total <= 0 || available < 0 || available > total) {
                return null;
            }

            Row r = new Row();
            r.kind = Row.STORAGE;
            r.title = "INTERNAL STORAGE";
            r.freeBytes = available;
            r.avail = humanSize(available);
            r.total = humanSize(total);
            r.text = humanSize(total - available);
            r.pct = (int) ((total - available) * 100L / total);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    // ---------- file manager di dalam kartu project ----------
    private String baseExpr(Browse b) {
        return b.base == 0 ? "$DNA_DIR" : "$DNA_TMP";
    }

    /** Ekspresi shell menuju folder yang sedang dibuka. Variabel $d harus sudah di-set. */
    private String dirExpr(Browse b) {
        StringBuilder sb = new StringBuilder(b.abs != null ? Runner.q(b.abs) : "\"" + baseExpr(b) + "/$d\"");
        for (String seg : b.sub) sb.append('/').append(Runner.q(seg));
        return sb.toString();
    }

    private String pathOf(Row r) {
        Browse b = r.b;
        StringBuilder sb = new StringBuilder();
        if (b.abs != null) sb.append(b.abs);
        else {
            if (b.base == 0) sb.append("/sdcard/DNA/");
            else sb.append(runner != null && runner.isRoot() ? "/data/DNA/" : getFilesDir().getAbsolutePath() + "/DNA/");
            sb.append(r.name);
        }
        for (String seg : b.sub) sb.append('/').append(seg);
        return sb.toString();
    }

    /** Baca isi folder (maks 80 entri, folder dulu). Panggil dari background thread. */
    private void loadDir(String name, Browse b) {
        String out = runner.capture("d=" + Runner.q(name) + "\n"
                + "cd " + dirExpr(b) + " 2>/dev/null || exit 0\n"
                + "n=0\n"
                + "for f in *; do\n"
                + "  [ -d \"$f\" ] || continue\n"
                + "  n=$((n+1)); [ $n -le 80 ] || break\n"
                + "  echo \"D|$f|$(ls -A \"$f\" 2>/dev/null | wc -l | tr -d ' ')\"\n"
                + "done\n"
                + "for f in *; do\n"
                + "  [ -d \"$f\" ] && continue\n"
                + "  [ -e \"$f\" ] || continue\n"
                + "  n=$((n+1)); [ $n -le 80 ] || break\n"
                + "  echo \"F|$f|$(stat -c %s \"$f\" 2>/dev/null)\"\n"
                + "done\n"
                + "[ $n -gt 80 ] && echo \"M|more|0\"\n");
        List<String[]> res = new ArrayList<>();
        for (String line : out.split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length == 3 && (p[0].equals("D") || p[0].equals("F") || p[0].equals("M"))) res.add(p);
        }
        b.files = res;
    }

    private void reloadDir(Row r) {
        final Browse b = r.b;
        b.loading = true;
        homeAdapter.notifyDataSetChanged();
        new Thread(() -> {
            loadDir(r.name, b);
            ui.post(() -> {
                b.loading = false;
                homeAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void toggleProject(Row r) {
        if (r.b == null) {
            r.b = new Browse();
            browse.put(r.name, r.b);
        }
        if (r.kind == Row.PIN) r.b.abs = r.name;
        r.b.open = !r.b.open;
        if (!r.b.open) {
            homeAdapter.notifyDataSetChanged();
            return;
        }
        reloadDir(r);
    }

    private TextView tab(String label, boolean on, Runnable click) {
        TextView t = text(label, 12, on ? 0xFF2B0A0A : TXT, true);
        t.setPadding(dp(14), dp(7), dp(14), dp(7));
        t.setBackground(rect(on ? ACCENT : SURFACE2, 16));
        t.setOnClickListener(v -> click.run());
        return t;
    }

    private View filesView(Row r) {
        final Browse b = r.b;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(8), dp(12));
        box.setOnClickListener(v -> { });   // tap area kosong jangan menutup kartu

        View line = new View(this);
        line.setBackgroundColor(SURFACE2);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 2);
        ll.bottomMargin = dp(10);
        box.addView(line, ll);

        if (b.abs == null) {
            LinearLayout tabs = new LinearLayout(this);
            tabs.addView(tab("Project", b.base == 0, () -> switchBase(r, 0)));
            if (r.hasUnpacked) {
                LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
                tl.leftMargin = dp(8);
                tabs.addView(tab("Unpacked (" + r.uCount + ")", b.base == 1, () -> switchBase(r, 1)), tl);
            }
            box.addView(tabs);
        }

        TextView path = text(pathOf(r), 11, SUB, false);
        path.setTypeface(Typeface.MONOSPACE);
        path.setSingleLine(true);
        path.setEllipsize(TextUtils.TruncateAt.START);
        path.setPadding(0, dp(10), dp(8), dp(4));
        box.addView(path);

        if (!b.sub.isEmpty()) {
            LinearLayout up = new LinearLayout(this);
            up.setGravity(Gravity.CENTER_VERTICAL);
            up.setPadding(0, dp(8), 0, dp(8));
            up.setBackground(ripple(0x00000000, 10));
            TextView m = text("▴", 14, ACCENT, false);
            m.setWidth(dp(20));
            up.addView(m);
            up.addView(text("..", 13, TXT, false));
            up.setOnClickListener(v -> {
                b.sub.remove(b.sub.size() - 1);
                reloadDir(r);
            });
            box.addView(up);
        }

        if (b.loading) {
            box.addView(text("Loading…", 12, SUB, false));
        } else if (b.files.isEmpty()) {
            TextView e = text("Folder is empty", 12, SUB, false);
            e.setPadding(0, dp(8), 0, 0);
            box.addView(e);
        } else {
            for (String[] f : b.files) box.addView(entryRow(r, f));
        }
        return box;
    }

    private void switchBase(Row r, int base) {
        r.b.base = base;
        r.b.sub.clear();
        reloadDir(r);
    }

    private View entryRow(Row r, String[] f) {
        final Browse b = r.b;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (f[0].equals("M")) {
            TextView t = text("… and more (showing first 80)", 12, SUB, false);
            t.setPadding(0, dp(8), 0, dp(4));
            row.addView(t);
            return row;
        }
        final boolean dir = f[0].equals("D");
        row.setPadding(0, dp(2), 0, dp(2));
        row.setBackground(ripple(0x00000000, 10));
        TextView mark = text(dir ? "▸" : "•", 14, dir ? ACCENT : SUB, false);
        mark.setWidth(dp(20));
        row.addView(mark);
        TextView nm = text(f[1], 13, TXT, false);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        String meta;
        if (dir) {
            meta = f[2].trim() + " items";
        } else {
            long sz = 0;
            try { sz = Long.parseLong(f[2].trim()); } catch (Exception ignored) { }
            meta = humanSize(sz);
        }
        TextView mt = text(meta, 12, SUB, false);
        mt.setPadding(dp(8), 0, 0, 0);
        row.addView(mt);
        TextView more = text("⋮", 18, SUB, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> entryMenu(r, f[1], dir, meta));
        row.addView(more, new LinearLayout.LayoutParams(dp(30), dp(40)));
        row.setOnLongClickListener(v -> {
            entryMenu(r, f[1], dir, meta);
            return true;
        });
        ImageView del = new ImageView(this);
        del.setImageResource(R.drawable.ic_delete);
        del.setPadding(dp(10), dp(10), dp(10), dp(10));
        del.setOnClickListener(v -> confirmDelete(r, f[1], dir));
        row.addView(del, new LinearLayout.LayoutParams(dp(40), dp(40)));
        if (dir) {
            row.setOnClickListener(v -> {
                b.sub.add(f[1]);
                reloadDir(r);
            });
        } else {
            long sz = 0;
            try { sz = Long.parseLong(f[2].trim()); } catch (Exception ignored) { }
            final long fsz = sz;
            row.setOnClickListener(v -> openEditor(r, f[1], fsz));
        }
        return row;
    }

    private static final String B64 = "B=base64; command -v base64 >/dev/null 2>&1 || B='busybox base64'\n";

    private void openEditor(Row r, String name, long size) {
        if (size > 512 * 1024) {
            toast("File too large to edit (max 512 KB)");
            return;
        }
        if (runner == null) return;
        final String path = pathOf(r) + "/" + name;
        toast("Opening " + name + "…");
        new Thread(() -> {
            byte[] data = null;
            try {
                String b64 = runner.capture(B64 + "$B < " + Runner.q(path) + "\n");
                data = Base64.decode(b64, Base64.DEFAULT);
            } catch (Exception ignored) { }
            final byte[] d = data;
            ui.post(() -> {
                if (d == null || (d.length == 0 && size > 0)) {
                    toast("Cannot read file");
                    return;
                }
                for (int i = 0; i < Math.min(d.length, 4096); i++) {
                    if (d[i] == 0) {
                        toast("Binary file, can't open in editor");
                        return;
                    }
                }
                String text;
                try { text = new String(d, "UTF-8"); } catch (Exception e) { text = new String(d); }
                final EditorView ev = new EditorView(this, name, text, humanSize(d.length),
                        (t, done) -> saveFile(path, t, done));
                push(name, ev);
                stack.get(stack.size() - 1).editor = ev;
            });
        }).start();
    }

    private void saveFile(String path, String text, EditorView.Done done) {
        new Thread(() -> {
            boolean ok = false;
            File in = null, out = null;
            try {
                in = File.createTempFile("ed_", ".b64", getCacheDir());
                out = new File(in.getAbsolutePath() + ".out");
                FileOutputStream o = new FileOutputStream(in);
                o.write(Base64.encode(text.getBytes("UTF-8"), Base64.NO_WRAP));
                o.close();
                in.setReadable(true, false);
                // decode ke file sementara dulu, supaya file asli tidak terpotong kalau decode gagal
                String res = runner.capture(B64 + "$B -d < " + Runner.q(in.getAbsolutePath())
                        + " > " + Runner.q(out.getAbsolutePath())
                        + " && cat " + Runner.q(out.getAbsolutePath()) + " > " + Runner.q(path)
                        + " && echo SAVED_OK\n");
                ok = res.contains("SAVED_OK");
            } catch (Exception ignored) {
            } finally {
                if (in != null) in.delete();
                if (out != null) out.delete();
            }
            final boolean fok = ok;
            ui.post(() -> {
                toast(fok ? "Saved" : "Save failed");
                done.done(fok);
            });
        }).start();
    }

    private void confirmDelete(Row r, String name, boolean dir) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) return;
        final Browse b = r.b;
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete " + (dir ? "folder" : "file") + "?")
                .setMessage(pathOf(r) + "/" + name + "\n\nThis cannot be undone.")
                .setPositiveButton("Delete", (x, w) -> new Thread(() -> {
                    runner.capture("d=" + Runner.q(r.name) + "\nrm -rf " + dirExpr(b) + "/" + Runner.q(name));
                    loadDir(r.name, b);
                    ui.post(() -> {
                        homeAdapter.notifyDataSetChanged();
                        reloadHome();
                    });
                }).start())
                .setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ERR);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
    }

    private void reloadHome() {
        if (runner == null) return;
        new Thread(() -> {
            String out = runner.capture(LOAD);
            List<Row> projects = new ArrayList<>();
            Row storage = storageRow();
            List<Vol> sd = findSdCards();
            sdCache = sd;
            for (String line : out.split("\n")) {
                String[] p = line.split("\\|", -1);
                try {
                    if (p[0].equals("P") && p.length >= 7) {
                        Row r = new Row();
                        r.kind = Row.PROJECT;
                        r.name = p[1];
                        r.size = p[2].trim();
                        r.count = Integer.parseInt(p[3].trim());
                        r.mtime = p[4].trim().isEmpty() ? 0 : Long.parseLong(p[4].trim());
                        r.hasUnpacked = "1".equals(p[5].trim());
                        r.uCount = p[6].trim().isEmpty() ? 0 : Integer.parseInt(p[6].trim());
                        projects.add(r);
                    }
                } catch (NumberFormatException ignored) { }
            }
            for (Row r : projects) {
                Browse b = browse.get(r.name);
                if (b == null) {
                    b = new Browse();
                    browse.put(r.name, b);
                }
                if (!r.hasUnpacked && b.base == 1) {
                    b.base = 0;
                    b.sub.clear();
                }
                r.b = b;
                if (b.open) loadDir(r.name, b);
            }
            Collections.sort(projects, new Comparator<Row>() {
                @Override public int compare(Row a, Row b) { return Long.compare(b.mtime, a.mtime); }
            });
            List<Row> res = new ArrayList<>();
            Row dk = new Row();
            dk.kind = Row.DISKS;
            if (storage != null) dk.disks.add(storage);
            for (Vol v : sd) {
                Row sr = new Row();
                sr.kind = Row.STORAGE;
                sr.name = v.path;
                sr.title = v.label.toUpperCase(Locale.getDefault());
                sr.freeBytes = v.avail;
                sr.avail = humanSize(v.avail);
                sr.total = humanSize(v.total);
                sr.text = humanSize(v.total - v.avail);
                sr.pct = (int) ((v.total - v.avail) * 100L / v.total);
                dk.disks.add(sr);
            }
            if (!dk.disks.isEmpty()) res.add(dk);
            Row sec = new Row();
            sec.kind = Row.SECTION;
            sec.text = "Projects (" + projects.size() + ")";
            res.add(sec);
            if (projects.isEmpty()) {
                Row e = new Row();
                e.kind = Row.EMPTY;
                e.text = "No projects yet.\nTap + to create your first one.";
                res.add(e);
            }
            res.addAll(projects);

            List<String> pins = loadPins();
            Row fsec = new Row();
            fsec.kind = Row.SECTION;
            fsec.text = "Folders (" + pins.size() + ")";
            res.add(fsec);
            Row add = new Row();
            add.kind = Row.ADD;
            res.add(add);
            if (!pins.isEmpty()) {
                StringBuilder sh = new StringBuilder();
                for (String pp : pins) {
                    sh.append("p=").append(Runner.q(pp)).append('\n')
                      .append("if [ -d \"$p\" ]; then c=$(ls -A \"$p\" 2>/dev/null | wc -l | tr -d ' '); echo \"K|1|$c|$p\"; ")
                      .append("else echo \"K|0|0|$p\"; fi\n");
                }
                Map<String, String[]> info = new HashMap<>();
                for (String line : runner.capture(sh.toString()).split("\n")) {
                    String[] q = line.split("\\|", 4);
                    if (q.length == 4 && q[0].equals("K")) info.put(q[3], q);
                }
                for (String pp : pins) {
                    Row pr = new Row();
                    pr.kind = Row.PIN;
                    pr.name = pp;
                    pr.text = pinLabel(pp, sd);
                    String[] q = info.get(pp);
                    pr.missing = q == null || !q[1].equals("1");
                    try { pr.count = q == null ? 0 : Integer.parseInt(q[2].trim()); } catch (NumberFormatException ignored) { }
                    Browse pb = browse.get(pp);
                    if (pb == null) {
                        pb = new Browse();
                        browse.put(pp, pb);
                    }
                    pb.abs = pp;
                    pr.b = pb;
                    if (pb.open && !pr.missing) loadDir(pp, pb);
                    res.add(pr);
                }
            }
            List<Menu.Node> flat = new ArrayList<>();
            Menu.flatten(toolNodes, flat);
            for (Menu.Node n : flat) {
                Row r = new Row();
                r.kind = Row.NODE;
                r.node = n;
                res.add(r);
            }
            ui.post(() -> {
                rows.clear();
                rows.addAll(res);
                if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    // ---------- storage eksternal (micro SD / USB OTG) ----------
    private String internalRoot() {
        try {
            File e = Environment.getExternalStorageDirectory();
            if (e != null && e.getPath().startsWith("/storage/")) return e.getPath();
        } catch (Exception ignored) { }
        return "/storage/emulated/0";
    }

    private static String canon(String p) {
        try { return new File(p).getCanonicalPath(); } catch (IOException e) { return p; }
    }

    private static String baseName(String p) {
        int i = p.lastIndexOf('/');
        return i >= 0 && i < p.length() - 1 ? p.substring(i + 1) : p;
    }

    private static String parentOf(String p) {
        int i = p.lastIndexOf('/');
        return i > 0 ? p.substring(0, i) : "/";
    }

    /** {total, available} dalam byte, atau null kalau path tidak bisa dibaca. */
    private long[] statOf(String path) {
        try {
            StatFs sf = new StatFs(path);
            return new long[]{sf.getTotalBytes(), sf.getAvailableBytes()};
        } catch (Exception ignored) { }
        if (runner != null) {
            try {
                String out = runner.capture("df -k " + Runner.q(path) + " 2>/dev/null | tail -n 1").trim();
                String[] t = out.split("\\s+");
                // dari belakang: mount, use%, avail, used, 1K-blocks
                if (t.length >= 5) {
                    return new long[]{Long.parseLong(t[t.length - 5]) * 1024, Long.parseLong(t[t.length - 3]) * 1024};
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    /** Cari micro SD / drive removable yang sedang ter-mount. Panggil dari background thread. */
    private List<Vol> findSdCards() {
        final String in = canon(internalRoot());
        final LinkedHashMap<String, String> cand = new LinkedHashMap<>();   // path -> label
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                StorageManager sm = (StorageManager) getSystemService(Context.STORAGE_SERVICE);
                for (android.os.storage.StorageVolume sv : sm.getStorageVolumes()) {
                    if (sv.isPrimary() || !sv.isRemovable() || sv.getUuid() == null) continue;
                    String st = sv.getState();
                    if (!Environment.MEDIA_MOUNTED.equals(st) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(st)) continue;
                    String d = sv.getDescription(this);
                    cand.put("/storage/" + sv.getUuid(), d == null || d.trim().isEmpty() ? "SD card" : d.trim());
                }
            } catch (Exception ignored) { }
        }
        // cadangan (Android < 7 atau ROM aneh): scan /storage
        List<String> names = new ArrayList<>();
        String[] ls = new File("/storage").list();
        if (ls != null) {
            Collections.addAll(names, ls);
        } else if (runner != null) {
            for (String l : runner.capture("ls -1 /storage 2>/dev/null").split("\n")) {
                if (!l.trim().isEmpty()) names.add(l.trim());
            }
        }
        for (String n : names) {
            if (n.equals("emulated") || n.equals("self")) continue;
            String p = "/storage/" + n;
            if (!cand.containsKey(p)) cand.put(p, "SD card");
        }
        long[] base = statOf("/storage");
        List<Vol> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Map.Entry<String, String> e : cand.entrySet()) {
            String c = canon(e.getKey());
            if (c.equals(in) || c.startsWith("/storage/emulated") || c.startsWith("/data/media") || !seen.add(c)) continue;
            long[] st = statOf(e.getKey());
            if (st == null || st[0] <= 0 || st[1] < 0 || st[1] > st[0]) continue;
            // mount point kosong (kartu sudah dicabut) membaca statistik /storage, bukan kartunya
            if (base != null && st[0] == base[0]) continue;
            Vol v = new Vol();
            v.path = e.getKey();
            v.label = e.getValue();
            v.total = st[0];
            v.avail = st[1];
            out.add(v);
        }
        return out;
    }

    // ---------- folder yang di-pin ke menu ----------
    private SharedPreferences prefs() {
        return getSharedPreferences("dna_ui", MODE_PRIVATE);
    }

    private List<String> loadPins() {
        List<String> l = new ArrayList<>();
        for (String p : prefs().getString("pins", "").split("\n")) {
            if (!p.trim().isEmpty() && !l.contains(p)) l.add(p);
        }
        return l;
    }

    private void savePins(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String p : l) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(p);
        }
        prefs().edit().putString("pins", sb.toString()).apply();
    }

    private String pinLabel(String p, List<Vol> vols) {
        if (p.equals(internalRoot())) return "Internal storage";
        for (Vol v : vols) if (p.equals(v.path)) return v.label;
        return baseName(p);
    }

    private void unpinDialog(String path) {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Remove from menu?")
                .setMessage(path + "\n\nThe folder itself is not deleted.")
                .setPositiveButton("Remove", (x, w) -> {
                    List<String> pins = loadPins();
                    pins.remove(path);
                    savePins(pins);
                    browse.remove(path);
                    reloadHome();
                })
                .setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ERR);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
    }

    private View addCard() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setDuplicateParentStateEnabled(true);
        card.setBackground(ripple(SURFACE, 16));
        TextView badge = text("+", 20, ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(rect(0x22FF8282, 14));
        card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(8), 0);
        col.addView(text("Add folder", 16, TXT, false));
        col.addView(text("Explore internal storage / SD card and pin a folder here", 12, SUB, false));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(text("›", 22, SUB, false));
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    // ---------- salin / pindah ----------
    private void entryMenu(Row r, String name, boolean dir, String meta) {
        if (name.isEmpty() || name.equals(".") || name.equals("..") || name.contains("/")) return;
        final String path = pathOf(r) + "/" + name;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(20), dp(18), dp(14));
        TextView nm = text(name, 17, TXT, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(nm);
        TextView mt = text((dir ? "Folder" : "File") + "  •  " + meta, 12, SUB, false);
        mt.setPadding(0, dp(2), 0, dp(12));
        box.addView(mt);
        final AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setView(box).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        addAction(box, R.drawable.ic_edit, "Rename", "Change the " + (dir ? "folder" : "file") + " name", () -> {
            d.dismiss();
            renameDialog(path, name, dir);
        });
        addAction(box, R.drawable.ic_copy, "Copy to…", "Keep the original where it is", () -> {
            d.dismiss();
            openExplorer(EX_DEST, path, false, null);
        });
        addAction(box, R.drawable.ic_move, "Move to…", "Take it out of this folder", () -> {
            d.dismiss();
            openExplorer(EX_DEST, path, true, null);
        });
        d.show();
    }

    private void addAction(LinearLayout box, int icon, String title, String desc, Runnable click) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackground(ripple(SURFACE2, 14));
        FrameLayout badge = new FrameLayout(this);
        badge.setBackground(rect(0x22FF8282, 12));
        ImageView ic = new ImageView(this);
        ic.setImageResource(icon);
        badge.addView(ic, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        row.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), 0, dp(6), 0);
        col.addView(text(title, 15, TXT, true));
        col.addView(text(desc, 12, SUB, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        row.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(8);
        box.addView(row, lp);
    }

    private void transfer(String src, String destDir, boolean move) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SRC", src);
        env.put("DST_DIR", destDir);
        run((move ? "Move " : "Copy ") + baseName(src), "samples/transfer.sh " + (move ? "move" : "copy"), env, null, true);
    }

    // ---------- explorer folder (pilih folder untuk di-pin / tujuan salin-pindah) ----------
    private void openExplorer(int mode, String src, boolean move, String startRoot) {
        if (runner == null) return;
        Explorer ex = new Explorer(mode, src, move, startRoot);
        push(mode == EX_PIN ? "Add folder" : (move ? "Move to…" : "Copy to…"), ex.box);
        stack.get(stack.size() - 1).explorer = ex;
        stack.get(stack.size() - 1).plain = true;
        updateHeader();
    }

    private final class Explorer {
        final int mode;
        final String src;
        final boolean move;
        final List<Vol> roots = new ArrayList<>();
        final LinearLayout box = new LinearLayout(MainActivity.this);
        String root, cur;
        boolean loading, failed;
        List<String> dirs = new ArrayList<>();
        List<String[]> files = new ArrayList<>();
        int gen;   // hasil load yang sudah basi diabaikan

        Explorer(int mode, String src, boolean move, String startRoot) {
            this.mode = mode;
            this.move = move;
            final String in = internalRoot();
            String s = src;
            if (s != null && s.startsWith("/sdcard/")) s = in + s.substring(7);
            this.src = s;
            Vol iv = new Vol();
            iv.path = in;
            iv.label = "Internal";
            roots.add(iv);
            roots.addAll(sdCache);
            String start = in;
            for (Vol vol : roots) {
                boolean hit = startRoot != null ? vol.path.equals(startRoot) : (s != null && s.startsWith(vol.path + "/"));
                if (hit) start = vol.path;
            }
            root = start;
            box.setOrientation(LinearLayout.VERTICAL);
            go(start);
        }

        void go(String path) {
            cur = path;
            loading = true;
            failed = false;
            dirs = new ArrayList<>();
            files = new ArrayList<>();
            final int g = ++gen;
            render();
            new Thread(() -> {
                String out = runner.capture("cd " + Runner.q(path) + " 2>/dev/null || { echo X; exit 0; }\n"
                        + "n=0\n"
                        + "for f in * .[!.]* ..?*; do\n"
                        + "  [ -d \"$f\" ] || continue\n"
                        + "  n=$((n+1)); [ $n -le 400 ] || break\n"
                        + "  echo \"D|$f\"\n"
                        + "done\n"
                        + "n=0\n"
                        + "for f in * .[!.]* ..?*; do\n"
                        + "  [ -d \"$f\" ] && continue\n"
                        + "  [ -e \"$f\" ] || [ -L \"$f\" ] || continue\n"
                        + "  n=$((n+1)); [ $n -le 400 ] || break\n"
                        + "  echo \"F|$f|$(stat -c %s \"$f\" 2>/dev/null)\"\n"
                        + "done\n");
                final List<String> res = new ArrayList<>();
                final List<String[]> fres = new ArrayList<>();
                boolean bad = false;
                for (String l : out.split("\n")) {
                    if (l.equals("X")) bad = true;
                    else if (l.startsWith("D|") && l.length() > 2) res.add(l.substring(2));
                    else if (l.startsWith("F|") && l.length() > 2) {
                        String[] fp = l.substring(2).split("\\|(?=[0-9]*$)", 2);
                        long sz = 0;
                        try { sz = Long.parseLong(fp.length > 1 ? fp[1].trim() : "0"); } catch (Exception ignored) { }
                        if (!fp[0].isEmpty()) fres.add(new String[]{fp[0], humanSize(sz)});
                    }
                }
                Collections.sort(res, String.CASE_INSENSITIVE_ORDER);
                Collections.sort(fres, (x, y) -> x[0].compareToIgnoreCase(y[0]));
                final boolean fb = bad;
                ui.post(() -> {
                    if (g != gen) return;
                    loading = false;
                    failed = fb;
                    dirs = res;
                    files = fres;
                    render();
                });
            }).start();
        }

        /** Naik satu folder. false kalau sudah di root volume. */
        boolean up() {
            if (cur == null || cur.equals(root)) return false;
            String parent = parentOf(cur);
            go(parent.length() < root.length() ? root : parent);
            return true;
        }

        private View row(String name, boolean upRow, Runnable click) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(11), dp(6), dp(11));
            row.setBackground(ripple(0x00000000, 10));
            TextView m = text(upRow ? "▴" : "▸", 14, ACCENT, false);
            m.setWidth(dp(22));
            row.addView(m);
            TextView nm = text(name, 14, TXT, false);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            row.setOnClickListener(x -> click.run());
            return row;
        }

        /** File ditampilkan redup (hanya info, tidak bisa dibuka) supaya isi folder kelihatan. */
        private View fileRow(String name, String size) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(9), dp(6), dp(9));
            TextView m = text("•", 14, SUB, false);
            m.setWidth(dp(22));
            row.addView(m);
            TextView nm = text(name, 13, SUB, false);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView sz = text(size, 11, SUB, false);
            sz.setPadding(dp(8), 0, 0, 0);
            row.addView(sz);
            row.setAlpha(0.7f);
            return row;
        }

        private View note(String s) {
            TextView t = text(s, 13, SUB, false);
            t.setPadding(dp(6), dp(12), dp(6), dp(12));
            return t;
        }

        void render() {
            box.removeAllViews();
            box.setPadding(dp(14), dp(6), dp(14), dp(14));
            if (mode == EX_DEST) {
                LinearLayout hc = new LinearLayout(MainActivity.this);
                hc.setGravity(Gravity.CENTER_VERTICAL);
                hc.setBackground(rect(SURFACE, 16));
                hc.setPadding(dp(14), dp(12), dp(14), dp(12));
                FrameLayout badge = new FrameLayout(MainActivity.this);
                badge.setBackground(rect(0x22FF8282, 12));
                ImageView ic = new ImageView(MainActivity.this);
                ic.setImageResource(move ? R.drawable.ic_move : R.drawable.ic_copy);
                badge.addView(ic, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
                hc.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
                LinearLayout hcol = new LinearLayout(MainActivity.this);
                hcol.setOrientation(LinearLayout.VERTICAL);
                hcol.setPadding(dp(14), 0, 0, 0);
                hcol.addView(text(move ? "MOVING" : "COPYING", 10, ACCENT, true));
                TextView hn = text(baseName(src), 15, TXT, true);
                hn.setSingleLine(true);
                hn.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                hcol.addView(hn);
                TextView hf = text("from " + parentOf(src), 11, SUB, false);
                hf.setTypeface(Typeface.MONOSPACE);
                hf.setSingleLine(true);
                hf.setEllipsize(TextUtils.TruncateAt.START);
                hcol.addView(hf);
                hc.addView(hcol, new LinearLayout.LayoutParams(0, -2, 1f));
                LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
                hp.bottomMargin = dp(14);
                box.addView(hc, hp);
            }
            TextView lbl = text(mode == EX_PIN ? "PICK A FOLDER TO ADD TO THE MENU" : "CHOOSE DESTINATION", 11, SUB, true);
            lbl.setPadding(dp(2), 0, 0, dp(8));
            box.addView(lbl);
            if (roots.size() > 1) {
                HorizontalScrollView hs = new HorizontalScrollView(MainActivity.this);
                hs.setHorizontalScrollBarEnabled(false);
                LinearLayout chips = new LinearLayout(MainActivity.this);
                for (Vol vol : roots) {
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                    lp.rightMargin = dp(8);
                    chips.addView(tab(vol.label, vol.path.equals(root), () -> {
                        root = vol.path;
                        go(vol.path);
                    }), lp);
                }
                hs.addView(chips);
                box.addView(hs);
            }
            TextView p = text(cur, 11, SUB, false);
            p.setTypeface(Typeface.MONOSPACE);
            p.setSingleLine(true);
            p.setEllipsize(TextUtils.TruncateAt.START);
            p.setPadding(dp(2), dp(10), 0, dp(6));
            box.addView(p);

            ScrollView sv = new ScrollView(MainActivity.this);
            sv.setBackground(rect(SURFACE, 16));
            LinearLayout list = new LinearLayout(MainActivity.this);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(dp(10), dp(8), dp(10), dp(8));
            sv.addView(list);
            if (!cur.equals(root)) list.addView(row("..", true, () -> up()));
            if (loading) list.addView(note("Loading…"));
            else if (failed) list.addView(note("Cannot open this folder"));
            else if (dirs.isEmpty() && files.isEmpty()) list.addView(note("Empty folder"));
            else {
                for (String d : dirs) list.addView(row(d, false, () -> go(cur + "/" + d)));
                if (dirs.isEmpty()) list.addView(note("No subfolders"));
                for (String[] f : files) list.addView(fileRow(f[0], f[1]));
            }
            box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

            Button b;
            if (mode == EX_PIN) {
                boolean exists = loadPins().contains(cur);
                b = button(exists ? "Already in menu" : "Add this folder to menu", exists ? SURFACE2 : ACCENT);
                b.setEnabled(!exists && !loading && !failed);
                b.setOnClickListener(x -> {
                    final String target = cur;
                    if (target.indexOf('\n') >= 0) {
                        toast("Unsupported folder name");
                        return;
                    }
                    List<String> pins = loadPins();
                    if (!pins.contains(target)) {
                        pins.add(target);
                        savePins(pins);
                    }
                    toast("Added to menu: " + pinLabel(target, sdCache));
                    if (stack.size() > 1) {
                        stack.remove(stack.size() - 1);
                        showTop();
                    }
                });
            } else {
                b = button(move ? "Move here" : "Copy here", ACCENT);
                b.setEnabled(!loading && !failed);
                b.setOnClickListener(x -> {
                    final String dest = cur;
                    if ((dest + "/").startsWith(src + "/")) {
                        toast("Can't put a folder inside itself");
                        return;
                    }
                    if (move && dest.equals(parentOf(src))) {
                        toast("Already in this folder");
                        return;
                    }
                    if (running != null) {
                        toast("Another task is running");
                        return;
                    }
                    if (!stack.isEmpty() && stack.get(stack.size() - 1).explorer == this) stack.remove(stack.size() - 1);
                    transfer(src, dest, move);
                });
            }
            b.setAlpha(b.isEnabled() ? 1f : 0.5f);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(52));
            bl.topMargin = dp(12);
            box.addView(b, bl);
        }
    }

    private void openProject(String name) {
        new Thread(() -> {
            runner.capture("echo " + Runner.q(name) + " > \"$TMPDIR/DNA.ini\"");
            try {
                List<Menu.Node> top = new ArrayList<>();
                Menu.Node path = new Menu.Node(Menu.TEXT);
                path.title = "/sdcard/DNA/" + name;
                top.add(path);
                if (unzipNode != null) top.add(unzipNode);
                List<Menu.Node> body = Menu.parse(asset("menu/dna.xml"));
                ui.post(() -> push(display(name), sectionList(top, body)));
            } catch (Exception e) {
                ui.post(() -> toast("Error: " + e));
            }
        }).start();
    }

    // ---------- menu project: section dropdown ----------
    private View sectionList(List<Menu.Node> top, List<Menu.Node> body) {
        List<Menu.Node> flat = new ArrayList<>();
        Menu.flatten(body, flat);
        List<Menu.Node> pinned = new ArrayList<>(top);
        List<Sec> secs = new ArrayList<>();
        Sec cur = null;
        for (Menu.Node n : flat) {
            if (n.type == Menu.GROUP) {
                cur = new Sec();
                cur.title = n.title;
                secs.add(cur);
            } else if (cur == null) {
                pinned.add(n);
            } else {
                cur.items.add(n);
            }
        }
        for (int i = secs.size() - 1; i >= 0; i--) if (secs.get(i).items.isEmpty()) secs.remove(i);
        if (!secs.isEmpty()) secs.get(0).open = true;

        ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setDividerHeight(0);
        lv.setSelector(new ColorDrawable(Color.TRANSPARENT));
        lv.setClipToPadding(false);
        lv.setPadding(0, dp(6), 0, dp(24));
        final SecAdapter ad = new SecAdapter(pinned, secs);
        lv.setAdapter(ad);
        lv.setOnItemClickListener((a, v, pos, id) -> {
            VRow r = ad.vis.get(pos);
            if (r.kind == VRow.HEADER) {
                r.sec.open = !r.sec.open;
                ad.rebuild();
                ad.notifyDataSetChanged();
            } else {
                onNode(r.node);
            }
        });
        return lv;
    }

    private final class SecAdapter extends BaseAdapter {
        final List<Menu.Node> top;
        final List<Sec> secs;
        final List<VRow> vis = new ArrayList<>();

        SecAdapter(List<Menu.Node> top, List<Sec> secs) {
            this.top = top;
            this.secs = secs;
            rebuild();
        }

        void rebuild() {
            vis.clear();
            for (Menu.Node n : top) {
                VRow r = new VRow();
                r.kind = VRow.TOP;
                r.node = n;
                vis.add(r);
            }
            for (Sec s : secs) {
                VRow h = new VRow();
                h.kind = VRow.HEADER;
                h.sec = s;
                vis.add(h);
                if (!s.open) continue;
                for (Menu.Node n : s.items) {
                    VRow r = new VRow();
                    r.kind = VRow.ITEM;
                    r.node = n;
                    r.sec = s;
                    vis.add(r);
                }
            }
        }

        @Override public int getCount() { return vis.size(); }
        @Override public Object getItem(int i) { return vis.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public boolean isEnabled(int i) {
            VRow r = vis.get(i);
            if (r.kind == VRow.HEADER) return true;
            return r.node.type == Menu.ACTION || r.node.type == Menu.PAGE;
        }

        @Override public View getView(int i, View cv, ViewGroup parent) {
            VRow r = vis.get(i);
            if (r.kind == VRow.TOP) return nodeView(r.node);
            if (r.kind == VRow.HEADER) return headerView(r.sec);
            return itemView(r.node);
        }
    }

    private View headerView(Sec s) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setDuplicateParentStateEnabled(true);
        card.setBackground(ripple(SURFACE, 16));
        TextView t = text(s.title, 15, TXT, true);
        card.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView cnt = text(String.valueOf(s.items.size()), 12, SUB, false);
        cnt.setBackground(rect(SURFACE2, 10));
        cnt.setPadding(dp(9), dp(2), dp(9), dp(2));
        card.addView(cnt);
        TextView chev = text(s.open ? "▴" : "▾", 14, ACCENT, true);
        chev.setPadding(dp(12), 0, 0, 0);
        card.addView(chev);
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View itemView(Menu.Node n) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(30), dp(3), dp(14), dp(3));
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setDuplicateParentStateEnabled(true);
        card.setBackground(ripple(SURFACE2, 12));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(text(n.type == Menu.TEXT ? n.title.trim() : n.title, 14, TXT, false));
        if (!n.desc.isEmpty()) col.addView(text(n.desc, 12, SUB, false));
        card.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        if (n.type == Menu.PAGE) card.addView(text("›", 20, SUB, false));
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private void newProjectDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));
        EditText et = input();
        et.setHint("Project name (a-z, 0-9, _ -)");
        et.setSingleLine(true);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("Folder will be created in /sdcard/DNA as DNA_<name>", 12, SUB, false);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("New project").setView(box)
                .setPositiveButton("Create", null).setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = et.getText().toString().trim().replaceAll("[^A-Za-z0-9._-]", "_");
            if (name.isEmpty()) {
                toast("Enter a project name");
                return;
            }
            d.dismiss();
            createProject(name);
        });
    }

    private void createProject(String name) {
        new Thread(() -> {
            runner.capture("export T=" + Runner.q(name) + "\nsh samples/project.sh XJ");
            String active = project();
            ui.post(() -> {
                reloadHome();
                openProject(active);
            });
        }).start();
    }

    // ---------- rename ----------
    private EditText nameInput(String initial, boolean selectStem) {
        EditText et = input();
        et.setSingleLine(true);
        et.setText(initial);
        int dot = initial.lastIndexOf('.');
        et.setSelection(0, selectStem && dot > 0 ? dot : initial.length());
        return et;
    }

    private void renameDialog(String path, String oldName, boolean dir) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));
        EditText et = nameInput(oldName, !dir);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text(path.substring(0, Math.max(0, path.lastIndexOf('/'))), 11, SUB, false);
        hint.setTypeface(Typeface.MONOSPACE);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Rename " + (dir ? "folder" : "file")).setView(box)
                .setPositiveButton("Rename", null).setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nn = et.getText().toString().trim();
            if (nn.isEmpty() || nn.equals(".") || nn.equals("..") || nn.contains("/")) {
                toast("Invalid name");
                return;
            }
            if (nn.equals(oldName)) {
                d.dismiss();
                return;
            }
            d.dismiss();
            Map<String, String> env = new LinkedHashMap<>();
            env.put("SRC", path);
            env.put("NEW", nn);
            run("Rename " + oldName, "samples/rename.sh item", env, null, true);
        });
    }

    private void renameProjectDialog(String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(8));
        EditText et = nameInput(display(name), false);
        box.addView(et, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("Folder in /sdcard/DNA will be renamed to DNA_<name>", 12, SUB, false);
        hint.setPadding(0, dp(8), 0, 0);
        box.addView(hint);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Rename project").setView(box)
                .setPositiveButton("Rename", null).setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nn = et.getText().toString().trim().replaceAll("[^A-Za-z0-9._-]", "_");
            if (nn.isEmpty()) {
                toast("Enter a project name");
                return;
            }
            String full = "DNA_" + nn;
            if (full.equals(name)) {
                d.dismiss();
                return;
            }
            d.dismiss();
            Map<String, String> env = new LinkedHashMap<>();
            env.put("SRC", name);
            env.put("NEW", full);
            run("Rename " + display(name), "samples/rename.sh project", env, null, true);
        });
    }

    private void projectMenu(String name) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(20), dp(18), dp(14));
        TextView nm = text(display(name), 17, TXT, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        box.addView(nm);
        TextView mt = text("Project  •  /sdcard/DNA/" + name, 12, SUB, false);
        mt.setPadding(0, dp(2), 0, dp(12));
        box.addView(mt);
        final AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setView(box).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        addAction(box, R.drawable.ic_edit, "Rename", "Change the project name", () -> {
            d.dismiss();
            renameProjectDialog(name);
        });
        addAction(box, R.drawable.ic_delete, "Delete", "Remove the project and its files", () -> {
            d.dismiss();
            deleteDialog(name);
        });
        d.show();
    }

    private void deleteDialog(String name) {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete project?")
                .setMessage(display(name) + "\n\nAll files in this project will be permanently deleted.")
                .setPositiveButton("Delete", (x, w) -> {
                    Map<String, String> env = new LinkedHashMap<>();
                    env.put("TSS", name);
                    run("Delete " + display(name), "sh samples/project.sh SC", env, null, false);
                })
                .setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ERR);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
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
            run(n.title, n.set, new LinkedHashMap<String, String>(), n, false);
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
            run(n.title, n.set, env, n, false);
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

    // ---------- console (gaya log "Copy": judul, detail, Running…, Done in Xs) ----------
    private static final Map<String, String> LABELS = new HashMap<>();
    static {
        LABELS.put("IMG", "Image");
        LABELS.put("BR", "File");
        LABELS.put("DAT", "File");
        LABELS.put("ZST", "File");
        LABELS.put("ZIP", "File");
        LABELS.put("img", "Partition");
        LABELS.put("TSL", "Project");
        LABELS.put("TSS", "Project");
        LABELS.put("T", "Name");
        LABELS.put("TO", "Command");
        LABELS.put("Brush_in", "Flash file");
        LABELS.put("file", "File");
        LABELS.put("sub", "Plugin");
    }

    private static final Pattern STEP_START = Pattern.compile("^(Unpacking|Packing|Extracting)\\b.*");
    private static final String[][] ZH = {
            {"开始分解", "Unpacking"}, {"分解完成", "Unpacked"}, {"开始打包", "Packing"}, {"打包完成", "Packed"},
            {"开始解压", "Extracting"}, {"解压完成", "Extracted"}, {"开始提取", "Extracting"}, {"提取完成", "Extracted"},
            {"开始转换", "Converting"}, {"转换完成", "Converted"}, {"开始", "Start"}, {"完成", "Done"},
            {"成功", "success"}, {"失败", "failed"}, {"错误", "Error"}, {"警告", "Warning"},
            {"不存在", "not found"}, {"文件位于", "files at"}, {"文件", "file"}, {"删除", "Delete"},
            {"正在", "Working on"}, {"分解", "Unpack"}, {"打包", "Pack"}, {"解压", "Extract"},
    };

    static String fmtDur(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s < 60) return s + "s";
        return (s / 60) + "m " + (s % 60) + "s";
    }

    static String fmtClock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }

    private final class Console {
        final LinearLayout root = new LinearLayout(MainActivity.this);
        final TextView tv = new TextView(MainActivity.this);
        final TextView status = chip();
        final TextView timer = text("00:00", 12, SUB, false);
        final TextView copy = text("Copy log", 12, ACCENT, true);
        final ScrollView sc = new ScrollView(MainActivity.this);
        final Button btn = button("Stop", ERR);
        final long t0 = System.currentTimeMillis();
        private final StringBuilder pending = new StringBuilder();
        private boolean scheduled;
        private volatile boolean done;
        private long stepStart;
        private final Runnable tick = new Runnable() {
            @Override public void run() {
                if (done) return;
                timer.setText(fmtClock(System.currentTimeMillis() - t0));
                ui.postDelayed(this, 1000);
            }
        };

        Console() {
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(14), dp(12), dp(14), dp(14));
            status.setText("● Running…");
            status.setTextColor(ACCENT);
            timer.setTypeface(Typeface.MONOSPACE);
            timer.setPadding(0, 0, dp(12), 0);
            copy.setPadding(dp(10), dp(6), dp(10), dp(6));
            copy.setBackground(ripple(0x00000000, 14));
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("DNA log", tv.getText().toString()));
                    Toast.makeText(MainActivity.this, "Log copied", Toast.LENGTH_SHORT).show();
                }
            });
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(12);
            tv.setLineSpacing(0, 1.12f);
            tv.setTextColor(0xFFD7DAE0);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            sc.setBackground(rect(0xFF0A0C0F, 16));
            sc.addView(tv);
            LinearLayout bar = new LinearLayout(MainActivity.this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(status);
            bar.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, 1, 1f));
            bar.addView(timer);
            bar.addView(copy);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.bottomMargin = dp(10);
            root.addView(bar, sl);
            root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, dp(52));
            bl.topMargin = dp(12);
            root.addView(btn, bl);
            ui.postDelayed(tick, 1000);
        }

        /** Terjemahkan output Mandarin dari binary dna + hitung durasi per langkah unpack. */
        String tr(String line) {
            String s = line;
            if (s.startsWith("> ")) s = s.substring(2);
            else if (s.startsWith(">")) s = s.substring(1);
            s = s.replaceAll("分解完成[，,]\\s*文件位于[：:]\\s*", "✓ Unpacked to: ");
            s = s.replaceAll("打包完成[，,]\\s*文件位于[：:]\\s*", "✓ Packed to: ");
            for (String[] z : ZH) s = s.replace(z[0], z[1]);
            s = s.replace("：", ": ").replace("，", ", ").replace("。", ".").replace("（", " (").replace("）", ")");
            s = s.replaceAll(": {2,}", ": ").replaceAll("\\s+$", "");
            long now = System.currentTimeMillis();
            if (STEP_START.matcher(s).matches()) {
                stepStart = now;
            } else if (stepStart > 0 && s.startsWith("✓ ") && (s.contains("Unpacked") || s.contains("Packed") || s.contains("Extracted"))) {
                s += "  (" + fmtDur(now - stepStart) + ")";
                stepStart = 0;
            }
            return s;
        }

        void line(String l) {
            append(tr(l) + "\n");
        }

        void append(String s) {
            synchronized (pending) {
                pending.append(s);
                if (scheduled) return;
                scheduled = true;
            }
            ui.postDelayed(this::flush, 80);
        }

        private CharSequence style(String line) {
            SpannableStringBuilder sb = new SpannableStringBuilder(line);
            if (line.isEmpty()) return sb;
            if (line.startsWith("✓")) {
                sb.setSpan(new ForegroundColorSpan(OK), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.startsWith("✕") || line.startsWith("!") || line.startsWith("！")) {
                sb.setSpan(new ForegroundColorSpan(ERR), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (line.matches("^[A-Z][A-Z0-9 ._\\-]{1,40}$") || line.startsWith("Running…") || line.endsWith("…") && !line.startsWith(" ")) {
                sb.setSpan(new ForegroundColorSpan(ACCENT), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                Matcher m = Pattern.compile("^(\\s+[^:\\n]{1,16}?\\s*:)(\\s.*)?$").matcher(line);
                if (m.matches()) sb.setSpan(new ForegroundColorSpan(SUB), 0, m.end(1), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            return sb;
        }

        void flush() {
            String s;
            synchronized (pending) {
                s = pending.toString();
                pending.setLength(0);
                scheduled = false;
            }
            if (!s.isEmpty()) {
                SpannableStringBuilder out = new SpannableStringBuilder();
                String[] parts = s.split("\n", -1);
                for (int i = 0; i < parts.length; i++) {
                    if (i == parts.length - 1 && parts[i].isEmpty()) break;
                    out.append(style(parts[i]));
                    if (i < parts.length - 1) out.append('\n');
                }
                tv.append(out);
            }
            if (tv.length() > 300000) {
                CharSequence t = tv.getText();
                tv.setText(t.subSequence(150000, t.length()));
            }
            sc.post(() -> sc.fullScroll(View.FOCUS_DOWN));
        }

        void finish(int exit, boolean selfFooter) {
            done = true;
            long took = System.currentTimeMillis() - t0;
            boolean ok = exit == 0;
            boolean stopped = exit == 143 || exit == 137 || exit == 130;
            if (!selfFooter) {
                append("\n" + (ok ? "✓ Done in " + fmtDur(took)
                        : stopped ? "✕ Stopped after " + fmtDur(took)
                        : "✕ Failed (exit " + exit + ") after " + fmtDur(took)) + "\n");
            }
            flush();
            timer.setText(fmtClock(took));
            status.setText(ok ? "✓ Done" : stopped ? "✕ Stopped" : "✕ Failed");
            status.setTextColor(ok ? OK : ERR);
            btn.setText("Close");
            btn.setTextColor(0xFF2B0A0A);
            btn.setBackground(ripple(ACCENT, 16));
        }
    }

    private static String shortList(String v, String sep, boolean keepPath) {
        String[] parts = v.split(Pattern.quote(sep.isEmpty() ? "," : sep) + "|\\n");
        StringBuilder sb = new StringBuilder();
        for (String x : parts) {
            x = x.trim();
            if (x.isEmpty()) continue;
            if (!keepPath) x = baseName(x);
            if (sb.length() > 0) sb.append(", ");
            sb.append(x);
        }
        return sb.toString();
    }

    /** Detail pekerjaan (Project / Item / From / Size / opsi) untuk header log. Panggil dari background thread. */
    private List<String[]> jobInfo(Menu.Node job, Map<String, String> env) {
        List<String[]> info = new ArrayList<>();
        boolean hasProject = false;
        String fileNames = "", fileBase = "";
        List<String[]> opts = new ArrayList<>();
        for (Map.Entry<String, String> e : env.entrySet()) {
            String name = e.getKey(), val = e.getValue() == null ? "" : e.getValue();
            Menu.Param p = null;
            if (job != null) for (Menu.Param q : job.params) if (q.name.equals(name)) p = q;
            boolean check = p != null && (p.type.equals("checkbox") || p.type.equals("switch"));
            if (check) {
                String lb = !p.label.isEmpty() ? p.label : name;
                opts.add(new String[]{lb, val.equals("1") ? "Yes" : "No"});
                continue;
            }
            if (val.trim().isEmpty()) continue;
            String label = LABELS.containsKey(name) ? LABELS.get(name) : name;
            boolean full = name.equals("Brush_in") || name.equals("file") || name.equals("TO");
            String shown = full ? val : shortList(val, p != null ? p.separator : ",", false);
            if (label.equals("Project")) {
                hasProject = true;
                shown = display(shown);
                info.add(0, new String[]{label, shown});
                continue;
            }
            info.add(new String[]{label, shown});
            if (!full && (name.equals("ZIP") || name.equals("BR") || name.equals("DAT") || name.equals("ZST")
                    || (name.equals("IMG") && !val.startsWith("/")))) {
                fileNames = val;
                fileBase = name.equals("ZIP") ? "$DNA_DIR" : "$DNA_PRO";
            }
        }
        if (!hasProject && !env.containsKey("TSS")) {
            String pr = project();
            if (!pr.equals("none")) info.add(0, new String[]{"Project", display(pr)});
        }
        if (!fileNames.isEmpty()) {
            String sep = ",";
            if (job != null) for (Menu.Param q : job.params) if (q.multiple && !q.separator.isEmpty() && fileNames.contains(q.separator)) sep = q.separator;
            StringBuilder cmd = new StringBuilder("cd \"" + fileBase + "\" 2>/dev/null || exit 0\ndu -ch --");
            int cnt = 0;
            for (String f : fileNames.split(Pattern.quote(sep) + "|\\n")) {
                if (f.trim().isEmpty()) continue;
                cmd.append(' ').append(Runner.q(f.trim()));
                cnt++;
            }
            String sz = cnt == 0 ? "" : runner.capture(cmd + " 2>/dev/null | tail -1 | cut -f1").trim();
            String from = fileBase.equals("$DNA_DIR") ? "/sdcard/DNA" : "/sdcard/DNA/" + project();
            info.add(new String[]{"From", from});
            if (!sz.isEmpty()) info.add(new String[]{"Size", sz});
        }
        info.addAll(opts);
        return info;
    }

    private static String header(String title, List<String[]> info) {
        int w = 4;
        for (String[] i : info) w = Math.max(w, i[0].length());
        StringBuilder sb = new StringBuilder(title.toUpperCase(Locale.ROOT)).append('\n');
        for (String[] i : info) {
            StringBuilder lb = new StringBuilder(i[0]);
            while (lb.length() < w) lb.append(' ');
            sb.append("  ").append(lb).append(" : ").append(i[1]).append('\n');
        }
        return sb.append('\n').toString();
    }

    /**
     * @param job        node aksi (untuk label parameter), boleh null
     * @param selfHeader true kalau script sudah mencetak header + "Done in" sendiri (copy / move / rename)
     */
    private void run(String t, String body, Map<String, String> env, Menu.Node job, boolean selfHeader) {
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
        stack.get(stack.size() - 1).plain = true;
        updateHeader();
        new Thread(() -> {
            int code = -1;
            Runner.Started st = null;
            try {
                if (!selfHeader) {
                    c.append(header(t, jobInfo(job, env)) + "Running…\n");
                }
                st = runner.start(body, env, true);
                running = st;
                BufferedReader r = new BufferedReader(new InputStreamReader(st.proc.getInputStream(), "UTF-8"));
                String l;
                while ((l = r.readLine()) != null) c.line(l);
                code = st.proc.waitFor();
            } catch (Exception e) {
                c.append("✕ " + e + "\n");
            } finally {
                if (st != null) st.cleanup();
                running = null;
            }
            final int exit = code;
            ui.post(() -> {
                c.finish(exit, selfHeader && exit == 0);
                updateHeader();
            });
        }).start();
    }
}
