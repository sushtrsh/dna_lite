package com.dna.tools;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Editor teks sederhana: syntax highlight per ekstensi, tombol simbol, simpan lewat callback. */
final class EditorView extends LinearLayout {
    interface Done { void done(boolean ok); }

    interface Saver { void save(String text, Done done); }

    private static final int BG = 0xFF0C111E, SURFACE = 0xFF141B2D, SURFACE2 = 0xFF1B2438;
    private static final int ACCENT = 0xFFFF8282, TXT = 0xFFE9ECF3, SUB = 0xFF8A93A8;
    private static final int HIGHLIGHT_MAX = 120 * 1024;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Highlighter.Lang lang;
    private final EditText et;
    private final TextView info, saveBtn;
    private final Saver saver;
    private final String sizeLabel;
    private boolean dirty, loading = true, saving;
    private final Runnable highlightTask = this::highlight;

    EditorView(Context c, String fileName, String text, String sizeLabel, Saver saver) {
        super(c);
        this.saver = saver;
        this.sizeLabel = sizeLabel;
        this.lang = Highlighter.forFile(fileName);
        setOrientation(VERTICAL);
        setBackgroundColor(BG);

        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(6), dp(12), dp(6));
        info = label(c, "", 12, SUB);
        bar.addView(info, new LayoutParams(0, -2, 1f));
        saveBtn = label(c, "Save", 13, 0xFF2B0A0A);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setPadding(dp(18), dp(8), dp(18), dp(8));
        saveBtn.setOnClickListener(v -> save());
        bar.addView(saveBtn);
        addView(bar, new LayoutParams(-1, -2));

        et = new EditText(c);
        et.setTypeface(Typeface.MONOSPACE);
        et.setTextSize(12.5f);
        et.setTextColor(TXT);
        et.setBackgroundColor(SURFACE);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setHorizontallyScrolling(false);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setText(text);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }

            @Override public void afterTextChanged(Editable s) {
                if (loading) return;
                dirty = true;
                refresh();
                ui.removeCallbacks(highlightTask);
                ui.postDelayed(highlightTask, 350);
            }
        });
        addView(et, new LayoutParams(-1, 0, 1f));
        addView(symbolRow(c), new LayoutParams(-1, -2));

        loading = false;
        refresh();
        highlight();
    }

    boolean hasChanges() {
        return dirty;
    }

    void save() {
        if (saving || !dirty) return;
        saving = true;
        refresh();
        final String snapshot = et.getText().toString();
        saver.save(snapshot, ok -> {
            saving = false;
            if (ok && snapshot.equals(et.getText().toString())) dirty = false;
            refresh();
        });
    }

    private void refresh() {
        int lines = 1;
        CharSequence t = et.getText();
        for (int i = 0; i < t.length(); i++) if (t.charAt(i) == '\n') lines++;
        info.setText(lang.name + "  •  " + lines + " lines  •  " + sizeLabel + (dirty ? "  •  edited" : ""));
        boolean on = dirty && !saving;
        saveBtn.setText(saving ? "Saving…" : "Save");
        saveBtn.setEnabled(on);
        saveBtn.setAlpha(on ? 1f : 0.45f);
        saveBtn.setBackground(rect(ACCENT, 16));
    }

    private void highlight() {
        final Editable e = et.getText();
        for (ForegroundColorSpan s : e.getSpans(0, e.length(), ForegroundColorSpan.class)) e.removeSpan(s);
        if (e.length() > HIGHLIGHT_MAX) return;
        Highlighter.scan(e, lang, (start, end, color) -> {
            if (start >= 0 && end <= e.length() && start < end)
                e.setSpan(new ForegroundColorSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        });
    }

    private View symbolRow(Context c) {
        HorizontalScrollView sv = new HorizontalScrollView(c);
        sv.setBackgroundColor(SURFACE2);
        sv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(c);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        String[] keys = {"Tab", "{", "}", "(", ")", "[", "]", "<", ">", "\"", "'", ";", ":", "=", "/", "\\", "#", "$", "_", "|", "&"};
        for (String k : keys) {
            TextView t = label(c, k, 15, TXT);
            t.setTypeface(Typeface.MONOSPACE);
            t.setGravity(Gravity.CENTER);
            t.setMinWidth(dp(40));
            t.setPadding(dp(10), dp(8), dp(10), dp(8));
            t.setOnClickListener(v -> insert(k.equals("Tab") ? "    " : k));
            row.addView(t);
        }
        sv.addView(row);
        return sv;
    }

    private void insert(String s) {
        int a = Math.max(0, et.getSelectionStart()), b = Math.max(0, et.getSelectionEnd());
        et.getText().replace(Math.min(a, b), Math.max(a, b), s);
    }

    private TextView label(Context c, String s, int sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable rect(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
