package com.dna.tools;

import static com.dna.tools.Skin.ACCENT;
import static com.dna.tools.Skin.BG;
import static com.dna.tools.Skin.DIVIDER;
import static com.dna.tools.Skin.ERR;
import static com.dna.tools.Skin.LOGBG;
import static com.dna.tools.Skin.LOGTXT;
import static com.dna.tools.Skin.OK;
import static com.dna.tools.Skin.ONACCENT;
import static com.dna.tools.Skin.RIPPLE;
import static com.dna.tools.Skin.SUB;
import static com.dna.tools.Skin.SURFACE;
import static com.dna.tools.Skin.SURFACE2;
import static com.dna.tools.Skin.TXT;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.view.KeyEvent;
import android.view.HapticFeedbackConstants;
import android.view.Window;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
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
import android.widget.ProgressBar;
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
        Runnable act;      // tombol aksi di empty state
        String actLabel = "";
    }

    private static final class Browse {
        boolean open, loading;
        int base;   // 0 = /sdcard/DNA/<project>, 1 = hasil unpack di /data/DNA/<project>
        String abs; // kalau diisi: folder yang di-pin (path absolut), base diabaikan
        final List<String> sub = new ArrayList<>();
        final Set<String> sel = new HashSet<>();   // multi-select
        boolean selMode;
        List<String[]> files = new ArrayList<>();
        // ---- state UI (untuk animasi in-place, tanpa rebuild seluruh list) ----
        FrameLayout host;                 // slot isi file di dalam kartu
        TextView arrow, selCnt, unpackChip;
        LinearLayout selBar;
        ValueAnimator anim, selAnim;
        float selP;                       // progres mode seleksi 0..1
        final List<Ref> refs = new ArrayList<>();
    }

    /** Referensi view satu baris file, supaya seleksi bisa diupdate tanpa rebuild. */
    private static final class Ref {
        String name = "";
        boolean sel;
        GradientDrawable bg;
        TextView ck;
        View acts;
    }

    private static final int VISIBLE_ROWS = 5;   // jumlah baris file yang tampil sekaligus di kartu

    /** Daftar file dengan tinggi maksimal N baris, sisanya digulir di dalam kartu (tetap bisa gulir list utama di ujung). */
    private static final class FilesScroll extends ScrollView {
        private final int maxH;
        private float lastY;

        FilesScroll(Context c, int maxH) {
            super(c);
            this.maxH = maxH;
            setOverScrollMode(OVER_SCROLL_NEVER);
            setVerticalScrollBarEnabled(true);
            setScrollbarFadingEnabled(true);
        }

        @Override protected void onMeasure(int w, int h) {
            super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
        }

        @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    lastY = e.getY();
                    getParent().requestDisallowInterceptTouchEvent(canScrollVertically(-1) || canScrollVertically(1));
                    break;
                case android.view.MotionEvent.ACTION_MOVE: {
                    float dy = e.getY() - lastY;
                    lastY = e.getY();
                    boolean atEdge = (dy > 0 && !canScrollVertically(-1)) || (dy < 0 && !canScrollVertically(1));
                    getParent().requestDisallowInterceptTouchEvent(!atEdge && (canScrollVertically(-1) || canScrollVertically(1)));
                    break;
                }
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
                default: break;
            }
            return super.dispatchTouchEvent(e);
        }
    }

    private static final Interpolator EASE = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private boolean popping;   // arah transisi layar: true = kembali

    /** Volume penyimpanan eksternal (micro SD / USB OTG). */
    private static final class Vol {
        String path = "", label = "";
        long total, avail;
    }

    private static final int EX_PIN = 0, EX_DEST = 1, EX_FILE = 2;

    interface PickCb { void pick(String path); }

    private static final class Sec {
        String title = "";
        final List<Menu.Node> items = new ArrayList<>();
        boolean open;
        boolean anim;   // item baru muncul dengan animasi
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
    private boolean busy, fg;
    private View runBar, logsBtn, settingsBtn;
    private ListView homeList;
    private String pendingOpen;
    private boolean pendingUnpacked;
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
        Skin.load(prefs());
        applyWindowSkin();
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
        TextView lg = text("Logs", 13, ACCENT, true);
        lg.setGravity(Gravity.CENTER);
        lg.setPadding(dp(12), 0, dp(12), 0);
        lg.setBackground(ripple(0x00000000, 20));
        lg.setOnClickListener(v -> openLogs());
        logsBtn = lg;
        TextView st = text("⚙", 22, ACCENT, false);
        st.setGravity(Gravity.CENTER);
        st.setBackground(ripple(0x00000000, 20));
        st.setOnClickListener(v -> openSettings());
        settingsBtn = st;
        row.addView(settingsBtn, new LinearLayout.LayoutParams(dp(40), dp(40)));
        row.addView(logsBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        row.addView(plus, new LinearLayout.LayoutParams(dp(44), dp(44)));

        View divider = new View(this);
        divider.setBackgroundColor(DIVIDER);

        ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pb.setIndeterminate(true);
        pb.getIndeterminateDrawable().setColorFilter(ACCENT, android.graphics.PorterDuff.Mode.SRC_IN);
        pb.setVisibility(View.GONE);
        runBar = pb;

        content = new FrameLayout(this);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        root.addView(runBar, new LinearLayout.LayoutParams(-1, dp(3)));
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
    protected void onPause() {
        fg = false;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fg = true;
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
        return new RippleDrawable(ColorStateList.valueOf(RIPPLE), rect(color, radiusDp), null);
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
        b.setTextColor(color == ACCENT ? ONACCENT : color == ERR ? Color.WHITE : TXT);
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
        v.animate().cancel();
        v.setAlpha(0f);
        v.setTranslationX(dp(popping ? -22 : 22));
        v.animate().alpha(1f).translationX(0f).setDuration(240).setInterpolator(EASE).withLayer().start();
        popping = false;
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
        logsBtn.setVisibility(home ? View.VISIBLE : View.GONE);
        settingsBtn.setVisibility(home ? View.VISIBLE : View.GONE);
        runBar.setVisibility(busy ? View.VISIBLE : View.GONE);
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
            popping = true;
            showTop();
        } else {
            super.onBackPressed();
        }
    }

    private void confirmDiscard() {
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        homeList = lv;
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
                t.setPadding(dp(24), dp(28), dp(24), dp(r.act != null ? 12 : 28));
                if (r.act == null) return t;
                LinearLayout ec = new LinearLayout(this);
                ec.setOrientation(LinearLayout.VERTICAL);
                ec.setGravity(Gravity.CENTER_HORIZONTAL);
                ec.addView(t, new LinearLayout.LayoutParams(-1, -2));
                Button eb = button(r.actLabel, ACCENT);
                eb.setFocusable(false);
                eb.setOnClickListener(v -> r.act.run());
                LinearLayout.LayoutParams ebl = new LinearLayout.LayoutParams(dp(200), dp(46));
                ebl.bottomMargin = dp(20);
                ec.addView(eb, ebl);
                return ec;
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
        if (r.b == null) {
            r.b = browse.get(r.name);
            if (r.b == null) {
                r.b = new Browse();
                browse.put(r.name, r.b);
            }
        }
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setDuplicateParentStateEnabled(true);
        card.setBackground(ripple(SURFACE, 18));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(14), dp(12), dp(10), dp(12));
        head.setBackground(ripple(0x00000000, 16));
        head.setOnClickListener(v -> toggleProject(r));
        head.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            if (pin) unpinDialog(r.name);
            else projectMenu(r.name);
            return true;
        });
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
        TextView arrow = text("  ▾", 12, SUB, false);
        arrow.setRotation(r.b.open ? 180f : 0f);
        r.b.arrow = arrow;
        nameRow.addView(arrow);
        if (active) {
            TextView t = text("ACTIVE", 9, ONACCENT, true);
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

        FrameLayout host = new FrameLayout(this);
        r.b.host = host;
        if (r.b.open) host.addView(filesView(r), new FrameLayout.LayoutParams(-1, -2));
        card.addView(host, new LinearLayout.LayoutParams(-1, -2));
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
                + "  c=0\n"
                + "  for x in \"$f\"/* \"$f\"/.[!.]* \"$f\"/..?*; do [ -e \"$x\" ] && c=$((c+1)); done\n"
                + "  echo \"D|$f|$c\"\n"
                + "done\n"
                + "fl=$(find -L . -maxdepth 1 ! -type d ! -name '.*' -exec stat -L -c 'F|%n|%s' {} + 2>/dev/null | sort)\n"
                + "room=$((80-n)); [ $room -lt 0 ] && room=0\n"
                + "if [ -n \"$fl\" ]; then\n"
                + "  t=$(printf '%s\\n' \"$fl\" | wc -l)\n"
                + "  printf '%s\\n' \"$fl\" | head -n $room\n"
                + "  [ $t -gt $room ] && echo \"M|more|0\"\n"
                + "fi\n"
                + "[ $n -gt 80 ] && echo \"M|more|0\"\n");
        List<String[]> res = new ArrayList<>();
        for (String line : out.split("\n")) {
            String[] p = line.split("\\|", 3);
            if (p.length == 3 && (p[0].equals("D") || p[0].equals("F") || p[0].equals("M"))) {
                if (p[0].equals("F") && p[1].startsWith("./")) p[1] = p[1].substring(2);
                res.add(p);
            }
        }
        b.files = res;
    }

    /** Baca ulang isi folder di background; tampilan lama tetap sampai data baru siap, lalu diganti dengan animasi. */
    private void reloadDir(Row r) {
        reloadDir(r, false);
    }

    private static boolean sameFiles(List<String[]> a, List<String[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }

    /** silent = tampilan sudah menampilkan isi (cache); kalau hasil baru sama, tidak perlu animasi ulang. */
    private void reloadDir(Row r, boolean silent) {
        final Browse b = r.b;
        if (!silent) {
            b.sel.clear();
            b.selMode = false;
        }
        new Thread(() -> {
            final List<String[]> before = b.files;
            loadDir(r.name, b);
            final boolean same = silent && sameFiles(before, b.files);
            ui.post(() -> {
                b.loading = false;
                if (!same) refreshFiles(r);
            });
        }).start();
    }

    private void toggleProject(Row r) {
        if (r.b == null) {
            r.b = new Browse();
            browse.put(r.name, r.b);
        }
        if (r.kind == Row.PIN) r.b.abs = r.name;
        final Browse b = r.b;
        if (b.open) {
            b.open = false;
            b.sel.clear();
            b.selMode = false;
            rotateArrow(b);
            final FrameLayout host = b.host;
            if (host == null || !host.isAttachedToWindow()) {
                homeAdapter.notifyDataSetChanged();
                return;
            }
            animateHeight(b, host, host.getHeight(), 0, host::removeAllViews);
            ui.postDelayed(() -> {
                if (!b.open && b.host != null && b.host.getChildCount() > 0) {
                    b.host.removeAllViews();
                    ViewGroup.LayoutParams lp = b.host.getLayoutParams();
                    lp.height = -2;
                    b.host.setLayoutParams(lp);
                }
            }, 600);
            return;
        }
        b.open = true;
        final boolean cached = !b.files.isEmpty();
        b.loading = !cached;
        refreshFiles(r);   // buka langsung (cache kalau ada), data terbaru menyusul
        reloadDir(r, cached);
    }

    private void rotateArrow(Browse b) {
        if (b.arrow != null) b.arrow.animate().rotation(b.open ? 180f : 0f).setDuration(240).setInterpolator(EASE).start();
    }

    private int measureH(View v, int width) {
        v.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return v.getMeasuredHeight();
    }

    /** Animasi tinggi view; kalau ada animasi sebelumnya, dibatalkan dan lanjut dari tinggi saat ini. */
    private void animateHeight(final Browse b, final View v, int from, final int to, final Runnable end) {
        if (b.anim != null) b.anim.cancel();
        final ViewGroup.LayoutParams lp0 = v.getLayoutParams();
        if (from == to) {
            lp0.height = -2;
            v.setLayoutParams(lp0);
            if (end != null) end.run();
            return;
        }
        lp0.height = from;
        v.setLayoutParams(lp0);
        ValueAnimator a = ValueAnimator.ofInt(from, to);
        a.setDuration(Math.min(360, 180 + Math.abs(to - from) / 8));
        a.setInterpolator(EASE);
        a.addUpdateListener(x -> {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            lp.height = (Integer) x.getAnimatedValue();
            v.setLayoutParams(lp);
        });
        a.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(Animator an) {
                if (cancelled) return;
                ViewGroup.LayoutParams lp = v.getLayoutParams();
                lp.height = -2;
                v.setLayoutParams(lp);
                if (end != null) end.run();
            }
        });
        b.anim = a;
        a.start();
    }

    /** Ganti isi kartu project in-place: konten baru fade-in, tinggi kartu beranimasi (tidak rebuild seluruh list). */
    private void refreshFiles(Row r) {
        final Browse b = r.b;
        if (b == null || !b.open) return;
        final FrameLayout host = b.host;
        if (host == null || !host.isAttachedToWindow() || host.getWidth() == 0) {
            homeAdapter.notifyDataSetChanged();
            return;
        }
        rotateArrow(b);
        if (b.selAnim != null) b.selAnim.cancel();
        final int from = host.getHeight();
        final View nv = filesView(r);
        host.removeAllViews();
        host.addView(nv, new FrameLayout.LayoutParams(-1, -2));
        final int to = measureH(nv, host.getWidth());
        nv.setAlpha(0f);
        nv.animate().alpha(1f).setDuration(240).setStartDelay(40).setInterpolator(EASE).start();
        animateHeight(b, host, from, to, null);
    }

    private boolean animating() {
        for (Browse b : browse.values()) if (b.anim != null && b.anim.isRunning()) return true;
        return false;
    }

    private TextView tab(String label, boolean on, Runnable click) {
        TextView t = text(label, 12, on ? ONACCENT : TXT, true);
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

        // bar seleksi selalu dibuat (disembunyikan saat tidak aktif) supaya bisa dianimasikan
        final boolean canUnpack = b.abs == null && b.base == 0 && b.sub.isEmpty();
        final boolean canRepack = b.abs == null && b.base == 1 && b.sub.isEmpty();
        final boolean canSuper = inOutFolder(b);
        b.refs.clear();
        b.selP = b.selMode ? 1f : 0f;
        b.unpackChip = null;
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        TextView cnt = text(b.sel.size() + " selected", 12, ACCENT, true);
        cnt.setPadding(0, dp(8), 0, dp(6));
        bar.addView(cnt);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout acts = new LinearLayout(this);
        List<String> labels = new ArrayList<>();
        List<Runnable> clicks = new ArrayList<>();
        labels.add("All");
        clicks.add(() -> {
            for (String[] f : b.files) if (!f[0].equals("M")) b.sel.add(f[1]);
            updateSel(r);
        });
        if (canUnpack) {
            labels.add("Unpack");
            clicks.add(() -> multiUnpack(r));
        } else if (canRepack) {
            labels.add("Repack");
            clicks.add(() -> multiRepack(r));
        } else if (canSuper) {
            labels.add("Repack to super");
            clicks.add(() -> multiRepackSuper(r));
        }
        labels.add("Copy");
        clicks.add(() -> openExplorerMulti(selectedPaths(r), false));
        labels.add("Move");
        clicks.add(() -> openExplorerMulti(selectedPaths(r), true));
        labels.add("Delete");
        clicks.add(() -> confirmDeleteSel(r));
        labels.add("✕");
        clicks.add(() -> {
            b.sel.clear();
            setSelMode(r, false);
        });
        for (int i = 0; i < labels.size(); i++) {
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(-2, -2);
            al.rightMargin = dp(8);
            TextView chipV = tab(labels.get(i), labels.get(i).equals("Delete"), clicks.get(i));
            String lbl = labels.get(i);
            if (lbl.equals("Unpack") || lbl.equals("Repack") || lbl.equals("Repack to super")) b.unpackChip = chipV;
            acts.addView(chipV, al);
        }
        hs.addView(acts);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.bottomMargin = dp(6);
        bar.addView(hs, hl);
        b.selBar = bar;
        b.selCnt = cnt;
        bar.setVisibility(b.selMode ? View.VISIBLE : View.GONE);
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        if (b.loading) {
            box.addView(text("Loading…", 12, SUB, false));
        } else if (b.files.isEmpty()) {
            boolean root0 = b.abs == null && b.base == 0 && b.sub.isEmpty();
            TextView e = text(root0 ? "Folder is empty.\nPut ROM files (zip, img, bin, br, dat) in this folder, then use Unzip ROM / Unpack in the project menu." : "Folder is empty", 12, SUB, false);
            e.setPadding(0, dp(8), 0, 0);
            box.addView(e);
        } else {
            LinearLayout items = new LinearLayout(this);
            items.setOrientation(LinearLayout.VERTICAL);
            for (String[] f : b.files) items.addView(entryRow(r, f));
            FilesScroll fs = new FilesScroll(this, dp(46) * VISIBLE_ROWS);
            fs.addView(items, new FrameLayout.LayoutParams(-1, -2));
            box.addView(fs, new LinearLayout.LayoutParams(-1, -2));
        }
        if (b.unpackChip != null) b.unpackChip.setAlpha(countActionable(b) > 0 ? 1f : 0.4f);
        return box;
    }

    private void switchBase(Row r, int base) {
        r.b.base = base;
        r.b.sub.clear();
        reloadDir(r);
    }

    private TextView fileBadge(String name) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        String lb = ext.isEmpty() ? "FILE" : ext.toUpperCase(Locale.ROOT);
        int c = 0xFF90A4AE;
        switch (ext) {
            case "img": c = 0xFF42A5F5; break;
            case "zip": case "7z": case "tar": case "gz": case "zst": case "zstd": c = 0xFFFFA726; break;
            case "br": case "dat": case "bin": c = 0xFFAB47BC; break;
            case "apk": c = 0xFF66BB6A; break;
            case "sh": c = 0xFF26A69A; break;
            case "patch": case "diff": c = 0xFFFF8282; break;
            default: break;
        }
        if (lb.length() > 4) lb = lb.substring(0, 4);
        TextView t = text(lb, 9, c, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rect((c & 0x00FFFFFF) | 0x33000000, 8));
        return t;
    }

    private void toggleSel(Row r, String name) {
        Browse b = r.b;
        if (!b.sel.remove(name)) b.sel.add(name);
        setSelMode(r, !b.sel.isEmpty());
    }

    private void setW(View v, int w) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp != null && lp.width != w) {
            lp.width = w;
            v.setLayoutParams(lp);
        }
    }

    private void applySelProgress(Browse b, float p) {
        for (Ref x : b.refs) {
            setW(x.ck, (int) (dp(28) * p));
            x.ck.setAlpha(p);
            setW(x.acts, (int) (dp(70) * (1f - p)));
            x.acts.setAlpha(1f - p);
        }
    }

    /** Update centang, warna baris, dan counter in-place (tanpa rebuild). */
    private void updateSel(Row r) {
        final Browse b = r.b;
        for (Ref x : b.refs) {
            boolean sl = b.sel.contains(x.name);
            if (sl == x.sel) continue;
            x.sel = sl;
            x.ck.setText(sl ? "☑" : "☐");
            x.ck.setTextColor(sl ? ACCENT : SUB);
            final GradientDrawable g = x.bg;
            ValueAnimator ca = ValueAnimator.ofArgb(sl ? 0x00FF8282 : 0x22FF8282, sl ? 0x22FF8282 : 0x00FF8282);
            ca.setDuration(180);
            ca.addUpdateListener(an -> g.setColor((Integer) an.getAnimatedValue()));
            ca.start();
            if (sl) {
                x.ck.setScaleX(0.6f);
                x.ck.setScaleY(0.6f);
                x.ck.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(new OvershootInterpolator(2f)).start();
            }
        }
        if (b.selCnt != null) b.selCnt.setText(b.sel.size() + " selected");
        if (b.unpackChip != null) b.unpackChip.setAlpha(countActionable(b) > 0 ? 1f : 0.4f);
    }

    /** Masuk/keluar mode seleksi: bar turun/naik, kolom centang & tombol aksi bergeser mulus. */
    private void setSelMode(Row r, boolean on) {
        final Browse b = r.b;
        updateSel(r);
        if (b.selMode == on) return;
        b.selMode = on;
        final LinearLayout bar = b.selBar;
        if (bar == null || b.host == null || !bar.isAttachedToWindow() || b.host.getWidth() == 0) {
            homeAdapter.notifyDataSetChanged();
            return;
        }
        if (b.selAnim != null) b.selAnim.cancel();
        bar.setVisibility(View.VISIBLE);
        final int barH = measureH(bar, b.host.getWidth() - dp(24));
        ValueAnimator a = ValueAnimator.ofFloat(b.selP, on ? 1f : 0f);
        a.setDuration(260);
        a.setInterpolator(EASE);
        a.addUpdateListener(x -> {
            float pr = (Float) x.getAnimatedValue();
            b.selP = pr;
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            lp.height = (int) (barH * pr);
            bar.setLayoutParams(lp);
            bar.setAlpha(pr);
            applySelProgress(b, pr);
        });
        a.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(Animator an) { cancelled = true; }
            @Override public void onAnimationEnd(Animator an) {
                if (cancelled) return;
                ViewGroup.LayoutParams lp = bar.getLayoutParams();
                lp.height = -2;
                bar.setLayoutParams(lp);
                if (!on) bar.setVisibility(View.GONE);
            }
        });
        b.selAnim = a;
        a.start();
    }

    /** Folder partisi hasil unpack (tab Unpacked) yang bisa di-repack; "config" bukan partisi. */
    private int countRepackable(Browse b) {
        int c = 0;
        for (String[] f : b.files) if (f[0].equals("D") && b.sel.contains(f[1]) && !f[1].equals("config")) c++;
        return c;
    }

    /** Folder out/ di dalam project (hasil repack disimpan di sini). */
    private static boolean inOutFolder(Browse b) {
        return b.abs == null && b.base == 0 && b.sub.size() == 1 && b.sub.get(0).equals("out");
    }

    /** Pack IMG terpilih di folder out menjadi super.img (pakai action "Repack super.img", diberi nama "Repack to super"). */
    private void multiRepackSuper(Row r) {
        final Browse b = r.b;
        final List<String> names = new ArrayList<>();
        boolean hasSuper = false;
        for (String[] f : b.files) {
            if (!f[0].equals("F") || !b.sel.contains(f[1]) || !"img".equals(unpackKind(f[1]))) continue;
            if (isSuperImg(f[1])) hasSuper = true;
            else if (!f[1].contains(",")) {
                names.add(f[1]);
            }
        }
        if (names.isEmpty()) {
            toast(hasSuper ? "super.img can't be packed into super" : "Select partition .img files to pack into super");
            return;
        }
        final String project = r.name;
        // Sama seperti menu Project: nama file polos, tool mencarinya di $DNA_PRO (root project).
        // IMG dari out dipindah sementara ke root selama pack lalu dikembalikan; super.img tetap jadi di out.
        final String joined = TextUtils.join(",", names);
        final String outDir = pathOf(r);
        final String desc = names.size() + " IMG: " + TextUtils.join(", ", names.size() > 6 ? names.subList(0, 6) : names)
                + (names.size() > 6 ? " +" + (names.size() - 6) + " more" : "");
        new Thread(() -> {
            runner.capture("echo " + Runner.q(project) + " > \"$TMPDIR/DNA.ini\"");
            Menu.Node found = null;
            try {
                List<Menu.Node> all = new ArrayList<>();
                Menu.flatten(Menu.parse(asset("menu/dna.xml")), all);
                for (Menu.Node x : all)
                    if (x.type == Menu.ACTION && "Repack super.img".equals(x.title)) { found = x; break; }
            } catch (Exception ignored) { }
            final Menu.Node node = found;
            if (node != null) {
                for (java.util.Iterator<Menu.Param> it = node.params.iterator(); it.hasNext(); )
                    if (it.next().name.equals("IMG_NAME")) it.remove();
                for (Menu.Param pr : node.params) {
                    if (!pr.valueSh.isEmpty()) {
                        String v = runner.capture(pr.valueSh).trim();
                        if (!v.isEmpty()) pr.value = v;
                    }
                }
                node.title = "Repack to super";
                node.desc = desc;
                StringBuilder lst = new StringBuilder();
                for (String nm : names) lst.append(' ').append(Runner.q(nm));
                String O = Runner.q(outDir);
                node.set = "O=" + O + "\n"
                    + "for n in" + lst + "; do\n"
                    + "  [ -f \"$DNA_PRO/$n\" ] && mv -f \"$DNA_PRO/$n\" \"$DNA_PRO/$n.dnabak\"\n"
                    + "  mv -f \"$O/$n\" \"$DNA_PRO/$n\"\n"
                    + "done\n"
                    + "back() {\n"
                    + "  for n in" + lst + "; do\n"
                    + "    [ -f \"$DNA_PRO/$n\" ] && mv -f \"$DNA_PRO/$n\" \"$O/$n\"\n"
                    + "    [ -f \"$DNA_PRO/$n.dnabak\" ] && mv -f \"$DNA_PRO/$n.dnabak\" \"$DNA_PRO/$n\"\n"
                    + "  done\n"
                    + "}\n"
                    + "trap 'back; exit 130' INT TERM\n"
                    + node.set + "\n"
                    + "rc=$?\n"
                    + "back\n"
                    + "exit $rc";
            }
            final Map<String, String> fixed = new LinkedHashMap<>();
            fixed.put("IMG_NAME", joined);
            ui.post(() -> {
                updateHeader();
                if (node == null) toast("Repack super menu not found");
                else paramDialog(node, fixed);
            });
        }).start();
    }

    private int countActionable(Browse b) {
        return b.base == 1 ? countRepackable(b) : countUnpackable(b);
    }

    private void multiRepack(Row r) {
        final Browse b = r.b;
        List<String> dirs = new ArrayList<>();
        for (String[] f : b.files)
            if (f[0].equals("D") && b.sel.contains(f[1]) && !f[1].equals("config") && !f[1].contains(",")) dirs.add(f[1]);
        if (dirs.isEmpty()) {
            toast("Select partition folders to repack");
            return;
        }
        repackParts(r.name, dirs);
    }

    /** Buka dialog "Repack single img partition" dengan folder partisi sudah terisi (tanpa picker). */
    private void repackParts(String project, List<String> dirs) {
        final String joined = TextUtils.join(",", dirs);
        new Thread(() -> {
            runner.capture("echo " + Runner.q(project) + " > \"$TMPDIR/DNA.ini\"");
            Menu.Node found = null;
            try {
                List<Menu.Node> all = new ArrayList<>();
                Menu.flatten(Menu.parse(asset("menu/dna.xml")), all);
                for (Menu.Node x : all)
                    if (x.type == Menu.ACTION && "Repack single img partition".equals(x.title)) { found = x; break; }
            } catch (Exception ignored) { }
            final Menu.Node node = found;
            if (node != null) {
                for (java.util.Iterator<Menu.Param> it = node.params.iterator(); it.hasNext(); )
                    if (it.next().name.equals("IMG_DIR")) it.remove();
                node.desc = dirs.size() == 1 ? "Partition: " + dirs.get(0) : dirs.size() + " partitions: " + TextUtils.join(", ", dirs);
            }
            final Map<String, String> fixed = new LinkedHashMap<>();
            fixed.put("IMG_DIR", joined);
            ui.post(() -> {
                updateHeader();
                if (node == null) toast("Repack menu not found");
                else paramDialog(node, fixed);
            });
        }).start();
    }

    private static boolean isSuperImg(String n) {
        return n.equalsIgnoreCase("super.img");
    }

    /** File .img terpilih yang bisa di-unpack massal (super.img & nama berkoma dilewati). */
    private int countUnpackable(Browse b) {
        int c = 0;
        for (String[] f : b.files)
            if (f[0].equals("F") && b.sel.contains(f[1]) && "img".equals(unpackKind(f[1])) && !isSuperImg(f[1]) && !f[1].contains(",")) c++;
        return c;
    }

    /** Unpack semua .img yang diceklis sekaligus. */
    private void multiUnpack(Row r) {
        final Browse b = r.b;
        List<String> imgs = new ArrayList<>();
        boolean hasSuper = false;
        for (String[] f : b.files) {
            if (!f[0].equals("F") || !b.sel.contains(f[1]) || !"img".equals(unpackKind(f[1]))) continue;
            if (isSuperImg(f[1])) hasSuper = true;
            else if (!f[1].contains(",")) imgs.add(f[1]);
        }
        if (imgs.isEmpty()) {
            toast(hasSuper ? "super.img is unpacked from its ⋮ menu (Unpack super)" : "Select .img files to unpack");
            return;
        }
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < imgs.size() && i < 6; i++) {
            if (i > 0) names.append(", ");
            names.append(imgs.get(i));
        }
        if (imgs.size() > 6) names.append(" +").append(imgs.size() - 6).append(" more");
        String desc = imgs.size() + " IMG selected: " + names + (hasSuper ? "\n(super.img skipped — use its ⋮ menu)" : "");
        quickUnpack(r.name, TextUtils.join(",", imgs), "img", desc);
    }

    private List<String> selectedPaths(Row r) {
        List<String> out = new ArrayList<>();
        for (String[] f : r.b.files) if (!f[0].equals("M") && r.b.sel.contains(f[1])) out.add(pathOf(r) + "/" + f[1]);
        return out;
    }

    private void confirmDeleteSel(Row r) {
        final Browse b = r.b;
        final List<String> names = new ArrayList<>();
        for (String[] f : b.files) if (!f[0].equals("M") && b.sel.contains(f[1])) names.add(f[1]);
        if (names.isEmpty()) return;
        confirmDialog("Delete " + names.size() + " items?", pathOf(r) + "\n\nThis cannot be undone.", "Delete", ERR, () -> {
            b.sel.clear();
            b.selMode = false;
            new Thread(() -> {
                StringBuilder sh = new StringBuilder("d=" + Runner.q(r.name) + "\n");
                for (String nm : names) {
                    if (nm.isEmpty() || nm.equals(".") || nm.equals("..") || nm.contains("/")) continue;
                    sh.append("rm -rf ").append(dirExpr(b)).append('/').append(Runner.q(nm)).append('\n');
                }
                runner.capture(sh.toString());
                loadDir(r.name, b);
                ui.post(() -> {
                    refreshFiles(r);
                    reloadHome();
                });
            }).start();
        });
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
        final boolean selected = b.sel.contains(f[1]);
        final float sp = b.selMode ? 1f : 0f;
        row.setPadding(dp(2), dp(3), 0, dp(3));
        GradientDrawable bgShape = rect(selected ? 0x22FF8282 : 0x00FF8282, 10);
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(RIPPLE), bgShape, null));
        // kolom centang selalu ada (lebar 0 saat tidak seleksi) agar bisa dianimasikan
        TextView ck = text(selected ? "☑" : "☐", 18, selected ? ACCENT : SUB, false);
        ck.setGravity(Gravity.CENTER);
        ck.setAlpha(sp);
        row.addView(ck, new LinearLayout.LayoutParams((int) (dp(28) * sp), -2));
        LinearLayout.LayoutParams mlp;
        if (dir) {
            ImageView ic = new ImageView(this);
            ic.setImageResource(R.drawable.ic_folder);
            mlp = new LinearLayout.LayoutParams(dp(24), dp(24));
            mlp.rightMargin = dp(8);
            row.addView(ic, mlp);
        } else {
            mlp = new LinearLayout.LayoutParams(dp(34), dp(22));
            mlp.rightMargin = dp(8);
            row.addView(fileBadge(f[1]), mlp);
        }
        TextView nm = text(f[1], 13, TXT, false);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
        String meta;
        long sz = 0;
        if (dir) {
            meta = f[2].trim() + " items";
        } else {
            try { sz = Long.parseLong(f[2].trim()); } catch (Exception ignored) { }
            meta = humanSize(sz);
        }
        final long fsz = sz;
        final String fmeta = meta;
        TextView mt = text(meta, 12, SUB, false);
        mt.setPadding(dp(8), 0, dp(4), 0);
        row.addView(mt);
        LinearLayout acts = new LinearLayout(this);
        acts.setGravity(Gravity.CENTER_VERTICAL);
        acts.setAlpha(1f - sp);
        TextView more = text("⋮", 18, SUB, true);
        more.setGravity(Gravity.CENTER);
        more.setOnClickListener(v -> entryMenu(r, f[1], dir, fmeta));
        acts.addView(more, new LinearLayout.LayoutParams(dp(30), dp(40)));
        ImageView del = new ImageView(this);
        del.setImageResource(R.drawable.ic_delete);
        del.setPadding(dp(10), dp(10), dp(10), dp(10));
        del.setOnClickListener(v -> confirmDelete(r, f[1], dir));
        acts.addView(del, new LinearLayout.LayoutParams(dp(40), dp(40)));
        row.addView(acts, new LinearLayout.LayoutParams((int) (dp(70) * (1f - sp)), dp(40)));

        Ref ref = new Ref();
        ref.name = f[1];
        ref.sel = selected;
        ref.bg = bgShape;
        ref.ck = ck;
        ref.acts = acts;
        b.refs.add(ref);

        row.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            toggleSel(r, f[1]);
            return true;
        });
        row.setOnClickListener(v -> {
            if (b.selMode) {
                toggleSel(r, f[1]);
            } else if (dir) {
                b.sub.add(f[1]);
                reloadDir(r);
            } else {
                openEditor(r, f[1], fsz);
            }
        });
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
                .setTitle("Delete " + (dir ? "folder" : "file") + "?")
                .setMessage(pathOf(r) + "/" + name + "\n\nThis cannot be undone.")
                .setPositiveButton("Delete", (x, w) -> new Thread(() -> {
                    runner.capture("d=" + Runner.q(r.name) + "\nrm -rf " + dirExpr(b) + "/" + Runner.q(name));
                    loadDir(r.name, b);
                    ui.post(() -> {
                        refreshFiles(r);
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
                e.text = "No projects yet.\nCreate one to start unpacking ROM files.";
                e.act = () -> newProjectDialog();
                e.actLabel = "Create project";
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
            ui.post(new Runnable() {
              @Override public void run() {
                if (animating()) {
                    ui.postDelayed(this, 90);
                    return;
                }
                rows.clear();
                rows.addAll(res);
                if (homeAdapter != null) homeAdapter.notifyDataSetChanged();
                if (pendingOpen != null) {
                    String nm = pendingOpen;
                    pendingOpen = null;
                    for (int i = 0; i < rows.size(); i++) {
                        Row pr = rows.get(i);
                        if (pr.kind == Row.PROJECT && pr.name.equals(nm) && pr.b != null) {
                            pr.b.base = pendingUnpacked && pr.hasUnpacked ? 1 : 0;
                            pr.b.sub.clear();
                            pr.b.open = true;
                            reloadDir(pr);
                            if (homeList != null) {
                                final int pos = i;
                                homeList.post(() -> homeList.setSelection(pos));
                            }
                            break;
                        }
                    }
                }
              }
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        final AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
                .setView(box).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.getWindow().setWindowAnimations(android.R.style.Animation_Toast);
        box.setAlpha(0f);
        box.setTranslationY(dp(14));
        d.setOnShowListener(x -> box.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(EASE).start());
        String ukRaw = (!dir && r.b != null && r.b.abs == null && r.b.base == 0 && r.b.sub.isEmpty()) ? unpackKind(name) : null;
        // IMG biasa tidak di-unpack dari sini (pakai multi-select > Unpack); menu ⋮ hanya untuk super.img
        if ("img".equals(ukRaw)) ukRaw = isSuperImg(name) ? "super" : null;
        final String uk = ukRaw;
        if (uk != null) {
            final boolean sup = uk.equals("super");
            addAction(box, R.drawable.ic_folder, sup ? "Unpack super" : "Unpack",
                    sup ? "Extract partitions from super.img" : "Extract this " + uk.toUpperCase(Locale.ROOT) + " into the project", () -> {
                d.dismiss();
                quickUnpack(r.name, name, uk);
            });
        }
        if (dir && r.b != null && r.b.abs == null && r.b.base == 1 && r.b.sub.isEmpty() && !name.equals("config")) {
            addAction(box, R.drawable.ic_folder, "Repack", "Pack this partition back into an IMG", () -> {
                d.dismiss();
                repackParts(r.name, Collections.singletonList(name));
            });
        }
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
        int idx = Math.max(0, box.getChildCount() - 2);
        row.setAlpha(0f);
        row.setTranslationY(dp(10));
        row.animate().alpha(1f).translationY(0f).setDuration(240).setStartDelay(60L + idx * 40L).setInterpolator(EASE).start();
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
        List<String> multi;   // beberapa sumber sekaligus (multi-select)
        int gen;   // hasil load yang sudah basi diabaikan
        String suffix = "";   // mode EX_FILE: hanya file dengan ekstensi ini yang bisa dipilih
        PickCb onPick;

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
            final boolean pickable = mode == EX_FILE && onPick != null
                    && (suffix.isEmpty() || name.toLowerCase(Locale.ROOT).endsWith("." + suffix.toLowerCase(Locale.ROOT)));
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(6), dp(9), dp(6), dp(9));
            LinearLayout.LayoutParams bl2 = new LinearLayout.LayoutParams(dp(34), dp(22));
            bl2.rightMargin = dp(8);
            row.addView(fileBadge(name), bl2);
            TextView nm = text(name, 13, pickable ? TXT : SUB, false);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(nm, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView sz = text(size, 11, SUB, false);
            sz.setPadding(dp(8), 0, 0, 0);
            row.addView(sz);
            if (pickable) {
                row.setBackground(ripple(0x00000000, 10));
                row.setOnClickListener(x -> onPick.pick(cur + "/" + name));
            } else {
                row.setAlpha(0.7f);
            }
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
                TextView hn = text(multi != null ? multi.size() + " items" : baseName(src), 15, TXT, true);
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
            TextView lbl = text(mode == EX_PIN ? "PICK A FOLDER TO ADD TO THE MENU"
                    : mode == EX_FILE ? "TAP A " + (suffix.isEmpty() ? "" : "." + suffix.toUpperCase(Locale.ROOT) + " ") + "FILE TO SELECT"
                    : "CHOOSE DESTINATION", 11, SUB, true);
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
            if (mode == EX_FILE) return;

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
                    if (multi != null) {
                        for (String sp : multi) {
                            if ((dest + "/").startsWith(sp + "/")) {
                                toast("Can't put a folder inside itself");
                                return;
                            }
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
                        transferMulti(multi, dest, move);
                        return;
                    }
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

    /** Dialog layar penuh untuk memilih satu file (dipakai param type="file", mis. Flash Image). */
    private void openFilePicker(String suffix, PickCb cb) {
        if (runner == null) return;
        final Dialog d = new Dialog(this, Skin.light ? android.R.style.Theme_Material_Light_NoActionBar
                : android.R.style.Theme_Material_NoActionBar);
        final Explorer ex = new Explorer(EX_FILE, null, false, null);
        ex.suffix = suffix == null ? "" : suffix;
        ex.onPick = path -> {
            int i = path.lastIndexOf('/');
            if (i > 0) prefs().edit().putString("pick_dir", path.substring(0, i)).apply();
            d.dismiss();
            cb.pick(path);
        };
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(BG);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(14), dp(8), dp(6));
        TextView ttl = text("Choose file", 19, TXT, true);
        TextView close = text("Close", 13, ACCENT, true);
        close.setPadding(dp(14), dp(10), dp(14), dp(10));
        close.setOnClickListener(v -> d.dismiss());
        bar.addView(ttl, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(close);
        col.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        col.addView(ex.box, new LinearLayout.LayoutParams(-1, 0, 1f));
        d.setContentView(col);
        Window w = d.getWindow();
        w.setLayout(-1, -1);
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        int lightBars = 0x2000 | 0x10;
        int f = w.getDecorView().getSystemUiVisibility();
        w.getDecorView().setSystemUiVisibility(Skin.light ? (f | lightBars) : (f & ~lightBars));
        d.setOnKeyListener((dd, code, ev) -> {
            if (code != KeyEvent.KEYCODE_BACK) return false;
            if (ev.getAction() == KeyEvent.ACTION_UP && !ex.up()) dd.dismiss();
            return true;
        });
        // mulai dari folder terakhir yang dipakai (kalau masih ada)
        String last = prefs().getString("pick_dir", "");
        if (!last.isEmpty()) {
            for (Vol vol : ex.roots) {
                if (last.equals(vol.path) || last.startsWith(vol.path + "/")) {
                    ex.root = vol.path;
                    ex.go(last);
                    break;
                }
            }
        }
        d.show();
    }

    private void openExplorerMulti(List<String> srcs, boolean move) {
        if (runner == null || srcs.isEmpty()) return;
        List<String> real = new ArrayList<>();
        for (String x : srcs) real.add(x.startsWith("/sdcard/") ? internalRoot() + x.substring(7) : x);
        Explorer ex = new Explorer(EX_DEST, real.get(0), move, null);
        ex.multi = real.size() > 1 ? real : null;
        push(move ? "Move to…" : "Copy to…", ex.box);
        stack.get(stack.size() - 1).explorer = ex;
        stack.get(stack.size() - 1).plain = true;
        ex.render();
        updateHeader();
    }

    private void transferMulti(List<String> srcs, String destDir, boolean move) {
        StringBuilder sb = new StringBuilder();
        for (String x : srcs) sb.append(x).append('\n');
        Map<String, String> env = new LinkedHashMap<>();
        env.put("SRCS", sb.toString());
        env.put("DST_DIR", destDir);
        run((move ? "Move " : "Copy ") + srcs.size() + " items", "samples/batch.sh " + (move ? "move" : "copy"), env, null, true);
    }

    // ---------- unpack cepat dari menu file ----------
    private static String unpackKind(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.equals("payload.bin")) return "bin";
        if (n.endsWith(".new.dat")) return "dat";
        if (n.endsWith(".img")) return "img";
        if (n.endsWith(".br")) return "br";
        if (n.endsWith(".zst") || n.endsWith(".zstd")) return "zst";
        return null;
    }

    private Menu.Param checkParam(String name, String label) {
        Menu.Param p = new Menu.Param();
        p.name = name;
        p.type = "checkbox";
        p.label = label;
        return p;
    }

    private void quickUnpack(String project, String name, String kind) {
        quickUnpack(project, name, kind, null);
    }

    /** Buka dialog Unpack super (pakai menu "Unpack super" dari dna.xml: pilih partisi, auto unpack, hapus sumber). */
    private void openSuperUnpack(String project) {
        new Thread(() -> {
            runner.capture("echo " + Runner.q(project) + " > \"$TMPDIR/DNA.ini\"");
            Menu.Node found = null;
            try {
                List<Menu.Node> all = new ArrayList<>();
                Menu.flatten(Menu.parse(asset("menu/dna.xml")), all);
                for (Menu.Node x : all)
                    if (x.type == Menu.ACTION && "Unpack super".equals(x.title)) { found = x; break; }
            } catch (Exception ignored) { }
            final Menu.Node node = found;
            ui.post(() -> {
                updateHeader();
                if (node == null) toast("Unpack super menu not found");
                else onNode(node);
            });
        }).start();
    }

    private void quickUnpack(String project, String name, String kind, String desc) {
        if (kind.equals("super")) {
            openSuperUnpack(project);
            return;
        }
        Menu.Node n = new Menu.Node(Menu.ACTION);
        if (desc != null) n.desc = desc;
        Map<String, String> fixed = new LinkedHashMap<>();
        switch (kind) {
            case "br": n.title = "Unpack br"; n.set = "dna extract $BR --delete $silence"; fixed.put("BR", name); break;
            case "dat": n.title = "Unpack dat"; n.set = "dna extract $DAT --delete $silence"; fixed.put("DAT", name); break;
            case "zst": n.title = "Unpack zstd"; n.set = "$START_DIR/samples/u_zstd_img.sh"; fixed.put("ZST", name); break;
            case "bin":
                n.title = "Unpack bin";
                n.set = "dna dumper --partition \"$img\" --delete $silence --auto $auto $DNA_PRO/payload.bin";
                break;
            default: n.title = "Unpack img"; n.set = "dna extract $IMG --delete $silence"; fixed.put("IMG", name); break;
        }
        n.params.add(checkParam("silence", "Delete source file"));
        if (kind.equals("bin")) n.params.add(checkParam("auto", "Automatically unpack extracted IMG"));
        new Thread(() -> {
            runner.capture("echo " + Runner.q(project) + " > \"$TMPDIR/DNA.ini\"");
            ui.post(() -> {
                updateHeader();
                paramDialog(n, fixed);
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
                r.sec.anim = r.sec.open;
                ad.rebuild();
                ad.notifyDataSetChanged();
                if (r.sec.anim) ui.postDelayed(() -> r.sec.anim = false, 450);
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
            View iv = itemView(r.node);
            if (r.sec != null && r.sec.anim) {
                int k = Math.min(Math.max(0, r.sec.items.indexOf(r.node)), 8);
                iv.setAlpha(0f);
                iv.setTranslationY(-dp(8));
                iv.animate().alpha(1f).translationY(0f).setDuration(200).setStartDelay(k * 20L).start();
            }
            return iv;
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        final AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
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
            launch(n, new LinkedHashMap<String, String>());
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
        paramDialog(n, null);
    }

    /** Bottom sheet berisi parameter; opsi tetap (fixed) ikut dikirim sebagai environment. */
    private void paramDialog(Menu.Node n, Map<String, String> fixed) {
        final int maxH = getResources().getDisplayMetrics().heightPixels * 6 / 10;
        ScrollView sv = new ScrollView(this) {
            @Override protected void onMeasure(int w, int h) {
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST));
            }
        };
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(box);
        final List<Getter> getters = new ArrayList<>();
        for (Menu.Param p : n.params) getters.add(addParam(box, p));

        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(dp(20), dp(10), dp(20), dp(16));
        float rd = dp(24);
        GradientDrawable sbg = new GradientDrawable();
        sbg.setColor(SURFACE);
        sbg.setCornerRadii(new float[]{rd, rd, rd, rd, 0, 0, 0, 0});
        sheet.setBackground(sbg);
        View handle = new View(this);
        handle.setBackground(rect(RIPPLE, 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(36), dp(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = dp(14);
        sheet.addView(handle, hp);
        sheet.addView(text(n.title, 19, TXT, true));
        if (!n.desc.isEmpty()) sheet.addView(text(n.desc, 12, SUB, false));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(4);
        sheet.addView(sv, sl);
        LinearLayout btns = new LinearLayout(this);
        Button cancel = button("Cancel", SURFACE2);
        Button go = button("Run", ACCENT);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, dp(48), 1f);
        cl.rightMargin = dp(8);
        btns.addView(cancel, cl);
        btns.addView(go, new LinearLayout.LayoutParams(0, dp(48), 1.4f));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
        bl.topMargin = dp(16);
        sheet.addView(btns, bl);

        final Dialog d = new Dialog(this, Skin.sheetStyle());
        d.setContentView(sheet);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        w.setGravity(Gravity.BOTTOM);
        w.setLayout(-1, -2);
        w.setWindowAnimations(android.R.style.Animation_InputMethod);
        w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        cancel.setOnClickListener(v -> d.dismiss());
        go.setOnClickListener(v -> {
            Map<String, String> env = new LinkedHashMap<>();
            if (fixed != null) env.putAll(fixed);
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
            launch(n, env);
        });
        d.show();
    }

    // ---------- dialog helper ----------
    private void confirmDialog(String title, CharSequence msg, String ok, int okColor, Runnable onOk) {
        AlertDialog.Builder bd = new AlertDialog.Builder(this, Skin.alertStyle())
                .setTitle(title).setMessage(msg);
        if (onOk != null) bd.setPositiveButton(ok, (x, w) -> onOk.run()).setNegativeButton("Cancel", null);
        else bd.setPositiveButton(ok, null);
        AlertDialog d = bd.create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(okColor);
        if (onOk != null) d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
    }

    private void dangerConfirm(String title, List<String[]> info, Runnable go) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(8), dp(22), dp(4));
        TextView warn = text("This writes directly to the device and cannot be undone. A wrong file can leave the phone unable to boot.", 13, TXT, false);
        warn.setBackground(rect(0x33EF5350, 12));
        warn.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(warn);
        for (String[] i : info) {
            TextView t = text(i[0] + ": " + i[1], 12, SUB, false);
            t.setTypeface(Typeface.MONOSPACE);
            t.setPadding(0, dp(6), 0, 0);
            box.addView(t);
        }
        CheckBox cb = new CheckBox(this);
        cb.setText("I understand, continue");
        cb.setTextColor(TXT);
        cb.setPadding(dp(6), dp(10), 0, dp(6));
        box.addView(cb);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog d = new AlertDialog.Builder(this, Skin.alertStyle())
                .setTitle(title).setView(sv)
                .setPositiveButton("Continue", (x, w) -> go.run()).setNegativeButton("Cancel", null).create();
        d.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
        d.show();
        Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
        pos.setTextColor(ERR);
        pos.setEnabled(false);
        d.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SUB);
        cb.setOnCheckedChangeListener((c, on) -> pos.setEnabled(on));
    }

    /** {basis folder, daftar nama, pemisah} untuk parameter file project, atau null. */
    private String[] fileSpec(Menu.Node job, Map<String, String> env) {
        for (String name : new String[]{"ZIP", "BR", "DAT", "ZST", "IMG"}) {
            String val = env.get(name);
            if (val == null || val.trim().isEmpty()) continue;
            if (name.equals("IMG") && val.startsWith("/")) continue;
            String sep = ",";
            if (job != null) for (Menu.Param q : job.params)
                if (q.name.equals(name) && q.multiple && !q.separator.isEmpty()) sep = q.separator;
            return new String[]{name.equals("ZIP") ? "$DNA_DIR" : "$DNA_PRO", val, sep};
        }
        return null;
    }

    /** Cek sebelum jalan: root untuk flash, konfirmasi bahaya, dan sisa storage untuk unpack. */
    private void launch(Menu.Node n, Map<String, String> env) {
        final String tl = n.title.toLowerCase(Locale.ROOT);
        final boolean flash = tl.contains("flash");
        final boolean unpack = tl.startsWith("unpack") || tl.equals("unzip rom");
        new Thread(() -> {
            if (flash && !runner.isRoot()) {
                ui.post(() -> confirmDialog("Root required", "Flashing a partition needs root access. Grant root to this app first.", "OK", ACCENT, null));
                return;
            }
            String warn = null;
            List<String[]> info = flash ? jobInfo(n, env) : null;
            String[] fs = unpack ? fileSpec(n, env) : null;
            if (fs != null) {
                StringBuilder sh = new StringBuilder("cd \"" + fs[0] + "\" 2>/dev/null || exit 0\nt=0\n");
                for (String f : fs[1].split(Pattern.quote(fs[2]) + "|\n")) {
                    f = f.trim();
                    if (f.isEmpty()) continue;
                    sh.append("s=$(stat -c %s ").append(Runner.q(f)).append(" 2>/dev/null); [ -n \"$s\" ] && t=$((t+s))\n");
                }
                String tgt = fs[0].equals("$DNA_DIR") ? "$DNA_DIR" : "$DNA_TMP";
                sh.append("mkdir -p \"").append(tgt).append("\" 2>/dev/null\n")
                  .append("set -- $(df -k \"").append(tgt).append("\" 2>/dev/null | tail -1)\necho \"$t $4\"\n");
                try {
                    String[] o = runner.capture(sh.toString()).trim().split("\\s+");
                    long total = Long.parseLong(o[0]);
                    long freeKb = Long.parseLong(o[1]);
                    long need = (long) (total * (env.containsKey("IMG") ? 1.2 : 3.0));
                    if (total > 0 && freeKb * 1024L < need) {
                        warn = "Free space: " + humanSize(freeKb * 1024L) + "\nNeeded (estimated): " + humanSize(need)
                                + "\n\nThe task may fail halfway. Continue anyway?";
                    }
                } catch (Exception ignored) { }
            }
            final String w = warn;
            final List<String[]> fi = info;
            ui.post(() -> {
                Runnable go = () -> run(n.title, n.set, env, n, false);
                if (flash) dangerConfirm("Confirm " + n.title, fi, go);
                else if (w != null) confirmDialog("Low storage", w, "Continue", ACCENT, go);
                else go.run();
            });
        }).start();
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
            if (p.options.size() > 3) {
                LinearLayout selRow = new LinearLayout(this);
                TextView all = text("Select all", 12, ACCENT, true);
                TextView none = text("Clear", 12, SUB, true);
                all.setPadding(0, dp(8), dp(16), dp(4));
                none.setPadding(0, dp(8), 0, dp(4));
                selRow.addView(all);
                selRow.addView(none);
                box.addView(selRow);
                all.setOnClickListener(v -> { for (CheckBox c : cbs) c.setChecked(true); });
                none.setOnClickListener(v -> { for (CheckBox c : cbs) c.setChecked(false); });
            }
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
            final int[] idx = {sel};
            final String[] arr = labels.toArray(new String[0]);
            TextView field = text(arr[sel] + "   ▾", 14, TXT, false);
            field.setBackground(ripple(SURFACE2, 12));
            field.setPadding(dp(14), dp(13), dp(14), dp(13));
            field.setSingleLine(true);
            field.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            field.setOnClickListener(v -> {
                AlertDialog dd = new AlertDialog.Builder(this, Skin.alertStyle())
                        .setItems(arr, (x, w) -> {
                            idx[0] = w;
                            field.setText(arr[w] + "   ▾");
                        }).create();
                dd.getWindow().setBackgroundDrawable(rect(SURFACE, 24));
                dd.show();
            });
            LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(-1, -2);
            flp.topMargin = dp(6);
            box.addView(field, flp);
            g = () -> p.options.get(idx[0]).value;
        } else if (!p.optionsSh.isEmpty()) {
            TextView none = text("No matching files found in this project.", 13, ERR, false);
            none.setBackground(rect(0x22EF5350, 12));
            none.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams nlp2 = new LinearLayout.LayoutParams(-1, -2);
            nlp2.topMargin = dp(6);
            box.addView(none, nlp2);
            g = () -> "";
        } else if (p.type.equals("file")) {
            final String[] chosen = {p.value == null ? "" : p.value.trim()};
            TextView field = text(chosen[0].isEmpty() ? "Tap to choose a file…" : chosen[0], 14,
                    chosen[0].isEmpty() ? SUB : TXT, false);
            field.setBackground(ripple(SURFACE2, 12));
            field.setPadding(dp(14), dp(13), dp(14), dp(13));
            field.setSingleLine(true);
            field.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            field.setOnClickListener(v -> openFilePicker(p.suffix, path -> {
                chosen[0] = path;
                field.setText(path);
                field.setTextColor(TXT);
            }));
            LinearLayout.LayoutParams flp2 = new LinearLayout.LayoutParams(-1, -2);
            flp2.topMargin = dp(6);
            box.addView(field, flp2);
            g = () -> chosen[0];
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
        final TextView copy = text("Copy", 12, ACCENT, true);
        final TextView share = text("Share", 12, ACCENT, true);
        final ScrollView sc = new ScrollView(MainActivity.this);
        final Button btn = button("Stop", ERR);
        final Button openBtn = button("Open files", SURFACE2);
        final String jobTitle;
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

        Console(String jobTitle) {
            this.jobTitle = jobTitle;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(14), dp(12), dp(14), dp(14));
            status.setText("● Running…");
            status.setTextColor(ACCENT);
            timer.setTypeface(Typeface.MONOSPACE);
            timer.setPadding(0, 0, dp(8), 0);
            for (TextView a : new TextView[]{copy, share}) {
                a.setPadding(dp(10), dp(6), dp(10), dp(6));
                a.setBackground(ripple(0x00000000, 14));
            }
            copy.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("DNA log", tv.getText().toString()));
                    Toast.makeText(MainActivity.this, "Log copied", Toast.LENGTH_SHORT).show();
                }
            });
            share.setOnClickListener(v -> shareText(jobTitle, tv.getText().toString()));
            tv.setTypeface(Typeface.MONOSPACE);
            tv.setTextSize(Skin.logSp);
            tv.setLineSpacing(0, 1.12f);
            tv.setTextColor(LOGTXT);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(14), dp(14), dp(14), dp(14));
            sc.setBackground(rect(LOGBG, 16));
            sc.addView(tv);
            LinearLayout bar = new LinearLayout(MainActivity.this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.addView(status);
            bar.addView(new View(MainActivity.this), new LinearLayout.LayoutParams(0, 1, 1f));
            bar.addView(timer);
            bar.addView(copy);
            bar.addView(share);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
            sl.bottomMargin = dp(10);
            root.addView(bar, sl);
            root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
            LinearLayout bottom = new LinearLayout(MainActivity.this);
            openBtn.setVisibility(View.GONE);
            LinearLayout.LayoutParams ol = new LinearLayout.LayoutParams(0, dp(52), 1f);
            ol.rightMargin = dp(8);
            bottom.addView(openBtn, ol);
            bottom.addView(btn, new LinearLayout.LayoutParams(0, dp(52), 1f));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(-1, -2);
            bl.topMargin = dp(12);
            root.addView(bottom, bl);
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
            } else if (line.matches("^[A-Z][A-Z0-9 ._\\-]{1,40}$") || line.matches("^\\[[0-9]+/[0-9]+\\]$") || line.startsWith("Running…") || (line.endsWith("…") && !line.startsWith(" "))) {
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

        void finish(int exit, boolean selfFooter, String openProject, boolean unpacked) {
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
            status.setScaleX(0.85f);
            status.setScaleY(0.85f);
            status.animate().scaleX(1f).scaleY(1f).setDuration(220).start();
            btn.setText("Close");
            btn.setTextColor(ONACCENT);
            btn.setBackground(ripple(ACCENT, 16));
            if (openProject != null) {
                openBtn.setVisibility(View.VISIBLE);
                openBtn.setOnClickListener(v -> openProjectFiles(openProject, unpacked));
            }
            haptic(ok);
            saveLog(jobTitle, tv.getText().toString());
            if (!fg) notifyDone(jobTitle, ok, took);
        }
    }

    private void haptic(boolean ok) {
        try {
            int c;
            if (Build.VERSION.SDK_INT >= 30) c = ok ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.REJECT;
            else c = ok ? HapticFeedbackConstants.KEYBOARD_TAP : HapticFeedbackConstants.LONG_PRESS;
            content.performHapticFeedback(c);
        } catch (Exception ignored) { }
    }

    private void notifyDone(String title, boolean ok, long ms) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel("dna_tasks", "Tasks", NotificationManager.IMPORTANCE_DEFAULT));
                nb = new Notification.Builder(this, "dna_tasks");
            } else {
                nb = new Notification.Builder(this);
            }
            Intent it = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            PendingIntent pi = PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_UPDATE_CURRENT);
            nb.setSmallIcon(R.drawable.ic_logo)
              .setContentTitle((ok ? "✓ " : "✕ ") + title)
              .setContentText((ok ? "Done in " : "Failed after ") + fmtDur(ms))
              .setContentIntent(pi).setAutoCancel(true);
            nm.notify(1001, nb.build());
        } catch (Exception ignored) { }
    }

    private void shareText(String title, String text) {
        try {
            String t = text.length() > 80000 ? text.substring(text.length() - 80000) : text;
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("text/plain");
            it.putExtra(Intent.EXTRA_SUBJECT, "DNA log: " + title);
            it.putExtra(Intent.EXTRA_TEXT, t);
            startActivity(Intent.createChooser(it, "Share log"));
        } catch (Exception e) {
            toast("Cannot share");
        }
    }

    /** Balik ke home dan buka folder project (tab Unpacked kalau habis unpack). */
    private void openProjectFiles(String name, boolean unpacked) {
        if (running != null) return;
        pendingOpen = name;
        pendingUnpacked = unpacked;
        while (stack.size() > 1) stack.remove(stack.size() - 1);
        showTop();
    }

    // ---------- riwayat log (/sdcard/DNA/logs) ----------
    private void saveLog(String title, String text) {
        new Thread(() -> {
            try {
                File tmp = File.createTempFile("log_", ".txt", getCacheDir());
                FileOutputStream o = new FileOutputStream(tmp);
                o.write(text.getBytes("UTF-8"));
                o.close();
                tmp.setReadable(true, false);
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String t = title.replaceAll("[^A-Za-z0-9.-]+", "_");
                if (t.length() > 40) t = t.substring(0, 40);
                String nm = stamp + "_" + t + ".log";
                runner.capture("mkdir -p \"$DNA_DIR/logs\"\ncp " + Runner.q(tmp.getAbsolutePath()) + " \"$DNA_DIR/logs/" + nm + "\"\n"
                        + "ls -t \"$DNA_DIR/logs\" | tail -n +41 | while read -r f; do rm -f \"$DNA_DIR/logs/$f\"; done\n");
                tmp.delete();
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------- pengaturan ----------
    private void applyWindowSkin() {
        Window w = getWindow();
        w.setBackgroundDrawable(new ColorDrawable(BG));
        w.setStatusBarColor(BG);
        w.setNavigationBarColor(BG);
        int f = w.getDecorView().getSystemUiVisibility();
        int lightBars = 0x2000 | 0x10; // LIGHT_STATUS_BAR | LIGHT_NAVIGATION_BAR
        f = Skin.light ? (f | lightBars) : (f & ~lightBars);
        w.getDecorView().setSystemUiVisibility(f);
    }

    private void openSettings() {
        push("Settings", settingsView());
        stack.get(stack.size() - 1).plain = true;
        updateHeader();
    }

    private TextView settingsLabel(String s) {
        TextView t = text(s, 12, ACCENT, true);
        t.setPadding(dp(4), dp(20), 0, dp(8));
        return t;
    }

    private View settingsView() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(0), dp(14), dp(28));
        sv.addView(col);

        col.addView(settingsLabel("THEME"));
        LinearLayout themeRow = new LinearLayout(this);
        String[] names = {"Dark", "AMOLED", "Light"};
        for (int i = 0; i < names.length; i++) {
            final int m = i;
            boolean on = Skin.mode == i;
            TextView c = text(names[i], 14, on ? ONACCENT : TXT, true);
            c.setGravity(Gravity.CENTER);
            c.setBackground(ripple(on ? ACCENT : SURFACE2, 14));
            c.setOnClickListener(v -> {
                if (Skin.mode == m) return;
                prefs().edit().putInt("theme", m).apply();
                recreate();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            if (i < names.length - 1) lp.rightMargin = dp(8);
            themeRow.addView(c, lp);
        }
        col.addView(themeRow, new LinearLayout.LayoutParams(-1, -2));

        col.addView(settingsLabel("TEXT SIZE"));
        col.addView(sizeCard("Log output", "font_log", Skin.logSp, true));
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, dp(10));
        col.addView(new View(this), gap);
        col.addView(sizeCard("Code editor", "font_editor", Skin.editorSp, false));

        col.addView(settingsLabel("BACKUP"));
        LinearLayout br = new LinearLayout(this);
        Button ex = button("Export", SURFACE2);
        Button im = button("Import", SURFACE2);
        ex.setOnClickListener(v -> exportSettings());
        im.setOnClickListener(v -> importSettings());
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
        l1.rightMargin = dp(8);
        br.addView(ex, l1);
        br.addView(im, new LinearLayout.LayoutParams(0, dp(48), 1f));
        col.addView(br, new LinearLayout.LayoutParams(-1, -2));
        TextView hint = text("Saved to /sdcard/DNA/dna_settings.txt", 12, SUB, false);
        hint.setPadding(dp(4), dp(8), 0, 0);
        col.addView(hint);

        Button reset = button("Reset to default", SURFACE2);
        reset.setOnClickListener(v -> confirmDialog("Reset settings?", "Theme and text sizes go back to default.", "Reset", ERR, () -> {
            prefs().edit().remove("theme").remove("font_log").remove("font_editor").apply();
            recreate();
        }));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, dp(48));
        rl.topMargin = dp(24);
        col.addView(reset, rl);
        return sv;
    }

    /** Kartu slider ukuran font dengan preview langsung; disimpan saat slider dilepas. */
    private View sizeCard(String label, String key, float value, boolean isLog) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rect(SURFACE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 15, TXT, true);
        TextView val = text(fmtSp(value), 13, ACCENT, true);
        head.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(val);
        card.addView(head);

        SeekBar sb = new SeekBar(this);
        int max = Math.round((Skin.MAX_SP - Skin.MIN_SP) * 2);
        sb.setMax(max);
        sb.setProgress(Math.round((value - Skin.MIN_SP) * 2));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(-1, -2);
        sl.topMargin = dp(6);
        card.addView(sb, sl);

        TextView pv = new TextView(this);
        pv.setTypeface(Typeface.MONOSPACE);
        pv.setText(isLog ? "✓ Packed to: /sdcard/DNA/test/out\n✓ Done in 3s" : "#!/system/bin/sh\necho \"hello\"  # 123");
        pv.setTextColor(isLog ? LOGTXT : TXT);
        pv.setTextSize(value);
        pv.setBackground(rect(isLog ? LOGBG : SURFACE2, 12));
        pv.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(-1, -2);
        pl.topMargin = dp(10);
        card.addView(pv, pl);

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                float v = Skin.MIN_SP + p / 2f;
                val.setText(fmtSp(v));
                pv.setTextSize(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) {
                float v = Skin.MIN_SP + s.getProgress() / 2f;
                prefs().edit().putFloat(key, v).apply();
                if (isLog) Skin.logSp = v; else Skin.editorSp = v;
            }
        });
        return card;
    }

    private static String fmtSp(float v) {
        return (v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v)) + " sp";
    }

    private void exportSettings() {
        final String body = "theme=" + Skin.mode + "\nfont_log=" + Skin.logSp + "\nfont_editor=" + Skin.editorSp + "\n";
        new Thread(() -> {
            String r = runner.capture("mkdir -p \"$DNA_DIR\"; printf %s " + Runner.q(body) + " > \"$DNA_DIR/dna_settings.txt\" && echo ok").trim();
            ui.post(() -> toast(r.endsWith("ok") ? "Exported to /sdcard/DNA/dna_settings.txt" : "Export failed"));
        }).start();
    }

    private void importSettings() {
        new Thread(() -> {
            String raw = runner.capture("cat \"$DNA_DIR/dna_settings.txt\" 2>/dev/null");
            SharedPreferences.Editor ed = prefs().edit();
            int found = 0;
            for (String line : raw.split("\n")) {
                String[] kv = line.trim().split("=", 2);
                if (kv.length != 2) continue;
                try {
                    switch (kv[0]) {
                        case "theme": {
                            int m = Integer.parseInt(kv[1].trim());
                            if (m >= Skin.DARK && m <= Skin.LIGHT) { ed.putInt("theme", m); found++; }
                            break;
                        }
                        case "font_log": ed.putFloat("font_log", Skin.clamp(Float.parseFloat(kv[1].trim()))); found++; break;
                        case "font_editor": ed.putFloat("font_editor", Skin.clamp(Float.parseFloat(kv[1].trim()))); found++; break;
                        default: break;
                    }
                } catch (NumberFormatException ignored) { }
            }
            final int n = found;
            if (n > 0) ed.apply();
            ui.post(() -> {
                if (n == 0) toast("No valid settings in /sdcard/DNA/dna_settings.txt");
                else recreate();
            });
        }).start();
    }

    private void openLogs() {
        if (runner == null) return;
        new Thread(() -> {
            String out = runner.capture("ls -t \"$DNA_DIR/logs\" 2>/dev/null");
            List<String> names = new ArrayList<>();
            for (String l : out.split("\n")) {
                l = l.trim();
                if (l.matches("[A-Za-z0-9._-]+\\.log")) names.add(l);
            }
            ui.post(() -> {
                push("Logs", logsView(names));
                stack.get(stack.size() - 1).plain = true;
                updateHeader();
            });
        }).start();
    }

    private View logsView(List<String> names) {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(10), dp(14), dp(24));
        sv.addView(col);
        if (names.isEmpty()) {
            TextView t = text("No logs yet.\nA log is saved to /sdcard/DNA/logs after every task.", 14, SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(24), dp(48), dp(24), dp(24));
            col.addView(t);
        }
        for (String nm : names) col.addView(logRow(nm));
        return sv;
    }

    private View logRow(String nm) {
        String base = nm.substring(0, nm.length() - 4);
        String when = "", what = base;
        if (base.length() > 16 && base.charAt(8) == '_') {
            try {
                Date dt = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).parse(base.substring(0, 15));
                when = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(dt);
                what = base.substring(16).replace('_', ' ');
            } catch (Exception ignored) { }
        }
        final String fw = what;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(13), dp(16), dp(13));
        card.setBackground(ripple(SURFACE, 16));
        card.addView(text(what, 15, TXT, true));
        if (!when.isEmpty()) card.addView(text(when, 12, SUB, false));
        card.setOnClickListener(v -> viewLog(nm, fw));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private void viewLog(String nm, String what) {
        new Thread(() -> {
            String raw = runner.capture("cat \"$DNA_DIR/logs/" + nm + "\" 2>/dev/null");
            final String text = raw.length() > 200000 ? raw.substring(raw.length() - 200000) : raw;
            ui.post(() -> {
                LinearLayout root = new LinearLayout(this);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setPadding(dp(14), dp(12), dp(14), dp(14));
                TextView tv = new TextView(this);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextSize(Skin.logSp);
                tv.setLineSpacing(0, 1.12f);
                tv.setTextColor(LOGTXT);
                tv.setTextIsSelectable(true);
                tv.setPadding(dp(14), dp(14), dp(14), dp(14));
                tv.setText(text);
                ScrollView sc = new ScrollView(this);
                sc.setBackground(rect(LOGBG, 16));
                sc.addView(tv);
                root.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1f));
                LinearLayout row = new LinearLayout(this);
                Button bc = button("Copy", SURFACE2), bs = button("Share", SURFACE2), bd = button("Delete", ERR);
                LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, dp(48), 1f);
                l1.rightMargin = dp(8);
                LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
                l2.rightMargin = dp(8);
                row.addView(bc, l1);
                row.addView(bs, l2);
                row.addView(bd, new LinearLayout.LayoutParams(0, dp(48), 1f));
                LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
                rl.topMargin = dp(12);
                root.addView(row, rl);
                bc.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("DNA log", text));
                        toast("Log copied");
                    }
                });
                bs.setOnClickListener(v -> shareText(what, text));
                bd.setOnClickListener(v -> confirmDialog("Delete log?", what, "Delete", ERR, () -> new Thread(() -> {
                    runner.capture("rm -f \"$DNA_DIR/logs/" + nm + "\"");
                    ui.post(() -> {
                        if (stack.size() > 1) stack.remove(stack.size() - 1);
                        if (stack.size() > 1) stack.remove(stack.size() - 1);
                        showTop();
                        openLogs();
                    });
                }).start()));
                push(what, root);
                stack.get(stack.size() - 1).plain = true;
                updateHeader();
            });
        }).start();
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
        if (running != null || busy) {
            toast("Another task is running");
            return;
        }
        final String proj = project();
        final boolean offerOpen = !selfHeader && !proj.equals("none") && !t.startsWith("Delete");
        final boolean unpacked = t.startsWith("Unpack");
        final Console c = new Console(t);
        c.btn.setOnClickListener(v -> {
            Runner.Started st = running;
            if (st != null) {
                st.proc.destroy();
                toast("Stopping…");
            } else {
                onBackPressed();
            }
        });
        busy = true;
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
                busy = false;
                c.finish(exit, selfHeader && exit == 0, offerOpen ? proj : null, unpacked);
                updateHeader();
            });
        }).start();
    }
}
