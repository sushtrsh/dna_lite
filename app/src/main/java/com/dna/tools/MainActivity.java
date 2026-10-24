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
            + "  echo \"P|$n|$s|$c|$m\"\n"
            + "done\n"
            + "df -h /sdcard 2>/dev/null | tail -1 | awk '{print \"S|\"$4\"|\"$2\"|\"$5}'\n";

    private interface Getter { String get(); }

    private static final class Screen {
        String title;
        View view;
        boolean home;
        Runnable onShow;
    }

    private static final class Row {
        static final int STORAGE = 0, SECTION = 1, PROJECT = 2, EMPTY = 3, NODE = 4;
        int kind;
        String name = "", size = "", avail = "", total = "", text = "";
        int count, pct;
        long mtime;
        Menu.Node node;
        boolean expanded, loading;
        List<String[]> files;
    }

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
    private TextView title, back, projChip, rootChip;
    private View logoBox, spacer, plus, chipsRow;
    private FrameLayout content;
    private final List<Row> rows = new ArrayList<>();
    private BaseAdapter homeAdapter;
    private List<Menu.Node> toolNodes = new ArrayList<>();
    private Menu.Node unzipNode;
    private final Set<String> expandedSet = new HashSet<>();
    private final Map<String, List<String[]>> fileCache = new HashMap<>();
    private volatile Runner.Started running;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        back = text("←", 26, TXT, true);
        back.setPadding(0, 0, dp(14), 0);
        back.setVisibility(View.GONE);
        back.setOnClickListener(v -> onBackPressed());
        LinearLayout logo = new LinearLayout(this);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_logo);
        TextView na = text("na", 30, ACCENT, true);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.leftMargin = -dp(3);
        logo.addView(mark);
        logo.addView(na, nlp);
        logoBox = logo;
        title = text("", 20, TXT, true);
        title.setSingleLine(true);
        spacer = new View(this);
        ImageView pl = new ImageView(this);
        pl.setImageResource(R.drawable.ic_plus);
        pl.setPadding(dp(8), dp(8), dp(8), dp(8));
        pl.setBackground(ripple(0x00000000, 22));
        pl.setOnClickListener(v -> newProjectDialog());
        plus = pl;
        row.addView(back);
        row.addView(logoBox);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View divider = new View(this);
        divider.setBackgroundColor(Color.WHITE);

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(dp(16), dp(12), dp(16), dp(0));
        projChip = chip();
        rootChip = chip();
        chips.addView(projChip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.leftMargin = dp(8);
        chips.addView(rootChip, lp);
        chipsRow = chips;

        content = new FrameLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        root.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(2, (int) (1.5f * getResources().getDisplayMetrics().density))));
        root.addView(chipsRow, new LinearLayout.LayoutParams(-1, -2));
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
        if (!stack.isEmpty()) title.setText(stack.get(stack.size() - 1).title);
        back.setVisibility(home ? View.GONE : View.VISIBLE);
        logoBox.setVisibility(home ? View.VISIBLE : View.GONE);
        spacer.setVisibility(home ? View.VISIBLE : View.GONE);
        title.setVisibility(home ? View.GONE : View.VISIBLE);
        plus.setVisibility(home ? View.VISIBLE : View.GONE);
        chipsRow.setVisibility(home ? View.GONE : View.VISIBLE);
        projChip.setText("Project: " + display(project()));
        boolean r = runner != null && runner.isRoot();
        rootChip.setText(r ? "● Root" : "● No root");
        rootChip.setTextColor(r ? OK : ERR);
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
                if (r.kind == Row.PROJECT) return true;
                return r.kind == Row.NODE && (r.node.type == Menu.ACTION || r.node.type == Menu.PAGE);
            }
            @Override public View getView(int i, View cv, ViewGroup parent) { return rowView(rows.get(i)); }
        };
        lv.setAdapter(homeAdapter);
        lv.setOnItemClickListener((a, v, pos, id) -> {
            Row r = rows.get(pos);
            if (r.kind == Row.PROJECT) toggleProject(r);
            else if (r.kind == Row.NODE) onNode(r.node);
        });
        lv.setOnItemLongClickListener((a, v, pos, id) -> {
            Row r = rows.get(pos);
            if (r.kind != Row.PROJECT) return false;
            deleteDialog(r.name);
            return true;
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
            case Row.STORAGE:
                return storageCard(r);
            default:
                return projectCard(r);
        }
    }

    private View storageCard(Row r) {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rect(SURFACE, 18));
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.addView(text("INTERNAL STORAGE", 11, SUB, true));
        TextView big = text(r.avail + " free", 22, TXT, true);
        big.setPadding(0, dp(4), 0, dp(10));
        card.addView(big);
        LinearLayout bar = new LinearLayout(this);
        bar.setBackground(rect(SURFACE2, 4));
        View fill = new View(this);
        fill.setBackground(rect(r.pct > 90 ? ERR : ACCENT, 4));
        int pct = Math.max(2, Math.min(100, r.pct));
        bar.addView(fill, new LinearLayout.LayoutParams(0, dp(8), pct));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, dp(8), 100 - pct));
        card.addView(bar, new LinearLayout.LayoutParams(-1, dp(8)));
        boolean root = runner != null && runner.isRoot();
        TextView foot = text(r.pct + "% of " + r.total + " used  •  " + (root ? "Root ✓" : "No root"), 12, SUB, false);
        foot.setPadding(0, dp(10), 0, 0);
        card.addView(foot);
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View projectCard(Row r) {
        boolean active = r.name.equals(project());
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
        TextView nm = text(display(r.name), 16, TXT, true);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        nameRow.addView(nm, new LinearLayout.LayoutParams(-2, -2, 0f));
        TextView arrow = text(r.expanded ? "  ▴" : "  ▾", 12, SUB, false);
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
        String meta = (r.size.isEmpty() ? "–" : r.size) + "  •  " + r.count + " items" + (date.isEmpty() ? "" : "  •  " + date);
        col.addView(text(meta, 12, SUB, false));
        head.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView go = text("›", 32, ACCENT, true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(0, 0, 0, dp(4));
        go.setBackground(ripple(SURFACE2, 14));
        go.setOnClickListener(v -> openProject(r.name));
        head.addView(go, new LinearLayout.LayoutParams(dp(54), dp(54)));
        card.addView(head);

        if (r.expanded) card.addView(filesView(r));
        wrap.addView(card, new FrameLayout.LayoutParams(-1, -2));
        return wrap;
    }

    private View filesView(Row r) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), 0, dp(16), dp(12));
        View line = new View(this);
        line.setBackgroundColor(SURFACE2);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, 2);
        ll.bottomMargin = dp(8);
        box.addView(line, ll);
        if (r.loading) {
            box.addView(text("Loading…", 12, SUB, false));
        } else if (r.files == null || r.files.isEmpty()) {
            box.addView(text("Folder is empty", 12, SUB, false));
        } else {
            for (String[] f : r.files) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(5), 0, dp(5));
                if (f[0].equals("M")) {
                    row.addView(text("… and more", 12, SUB, false));
                } else {
                    boolean dir = f[0].equals("D");
                    TextView mark = text(dir ? "▸" : "•", 12, dir ? ACCENT : SUB, false);
                    mark.setWidth(dp(18));
                    row.addView(mark);
                    TextView nm = text(f[1], 13, TXT, false);
                    nm.setSingleLine(true);
                    nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
                    if (!dir) {
                        long sz = 0;
                        try { sz = Long.parseLong(f[2].trim()); } catch (Exception ignored) { }
                        TextView z = text(humanSize(sz), 12, SUB, false);
                        z.setPadding(dp(10), 0, 0, 0);
                        row.addView(z);
                    }
                }
                box.addView(row);
            }
        }
        return box;
    }

    private static String humanSize(long b) {
        if (b < 1024) return b + " B";
        double v = b / 1024.0;
        String[] u = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
        return String.format(Locale.US, "%.1f %s", v, u[i]);
    }

    /** Ambil isi folder project (maks 60 entri). Panggil dari background thread. */
    private List<String[]> fetchFiles(String name) {
        String out = runner.capture("d=" + Runner.q(name) + "\n"
                + "cd \"$DNA_DIR/$d\" 2>/dev/null || exit 0\n"
                + "n=0\n"
                + "for f in *; do\n"
                + "  [ -e \"$f\" ] || continue\n"
                + "  n=$((n+1))\n"
                + "  if [ $n -gt 60 ]; then echo \"M|more|0\"; break; fi\n"
                + "  if [ -d \"$f\" ]; then echo \"D|$f|0\"; else echo \"F|$f|$(stat -c %s \"$f\" 2>/dev/null)\"; fi\n"
                + "done\n");
        List<String[]> res = new ArrayList<>();
        for (String line : out.split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length == 3 && (p[0].equals("D") || p[0].equals("F") || p[0].equals("M"))) res.add(p);
        }
        return res;
    }

    private void toggleProject(Row r) {
        if (r.expanded) {
            r.expanded = false;
            expandedSet.remove(r.name);
            homeAdapter.notifyDataSetChanged();
            return;
        }
        r.expanded = true;
        expandedSet.add(r.name);
        if (fileCache.containsKey(r.name)) r.files = fileCache.get(r.name);
        r.loading = true;
        homeAdapter.notifyDataSetChanged();
        new Thread(() -> {
            List<String[]> f = fetchFiles(r.name);
            ui.post(() -> {
                fileCache.put(r.name, f);
                r.files = f;
                r.loading = false;
                homeAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void reloadHome() {
        if (runner == null) return;
        new Thread(() -> {
            String out = runner.capture(LOAD);
            List<Row> projects = new ArrayList<>();
            Row storage = null;
            for (String line : out.split("\n")) {
                String[] p = line.split("\\|", -1);
                try {
                    if (p[0].equals("P") && p.length >= 5) {
                        Row r = new Row();
                        r.kind = Row.PROJECT;
                        r.name = p[1];
                        r.size = p[2].trim();
                        r.count = Integer.parseInt(p[3].trim());
                        r.mtime = p[4].trim().isEmpty() ? 0 : Long.parseLong(p[4].trim());
                        projects.add(r);
                    } else if (p[0].equals("S") && p.length >= 4) {
                        storage = new Row();
                        storage.kind = Row.STORAGE;
                        storage.avail = p[1];
                        storage.total = p[2];
                        storage.pct = Integer.parseInt(p[3].replace("%", "").trim());
                    }
                } catch (NumberFormatException ignored) { }
            }
            for (Row r : projects) {
                if (expandedSet.contains(r.name)) {
                    r.expanded = true;
                    r.files = fetchFiles(r.name);
                    fileCache.put(r.name, r.files);
                }
            }
            Collections.sort(projects, new Comparator<Row>() {
                @Override public int compare(Row a, Row b) { return Long.compare(b.mtime, a.mtime); }
            });
            List<Row> res = new ArrayList<>();
            if (storage != null) res.add(storage);
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

    private void deleteDialog(String name) {
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Delete project?")
                .setMessage(display(name) + "\n\nAll files in this project will be permanently deleted.")
                .setPositiveButton("Delete", (x, w) -> {
                    Map<String, String> env = new LinkedHashMap<>();
                    env.put("TSS", name);
                    run("Delete " + display(name), "sh samples/project.sh SC", env);
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
            btn.setTextColor(0xFF2B0A0A);
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
