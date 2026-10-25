package com.dna.tools;

import android.content.SharedPreferences;

/** Tema + ukuran font. Nilai warna diisi ulang oleh {@link #apply(int)} sebelum UI dibuat. */
final class Skin {
    static final int DARK = 0, AMOLED = 1, LIGHT = 2;
    static final float LOG_DEF = 12f, EDITOR_DEF = 12.5f, MIN_SP = 8f, MAX_SP = 20f;

    static int mode = DARK;
    static boolean light;
    static float logSp = LOG_DEF, editorSp = EDITOR_DEF;

    static int BG, SURFACE, SURFACE2, ACCENT, ONACCENT, TXT, SUB, OK, ERR, RIPPLE, DIVIDER, LOGBG, LOGTXT;

    static {
        apply(DARK);
    }

    static void load(SharedPreferences p) {
        logSp = clamp(p.getFloat("font_log", LOG_DEF));
        editorSp = clamp(p.getFloat("font_editor", EDITOR_DEF));
        apply(p.getInt("theme", DARK));
    }

    static float clamp(float v) {
        return Math.max(MIN_SP, Math.min(MAX_SP, v));
    }

    static void apply(int m) {
        if (m < DARK || m > LIGHT) m = DARK;
        mode = m;
        light = m == LIGHT;
        OK = 0xFF66BB6A;
        ERR = 0xFFEF5350;
        TXT = 0xFFE9ECF3;
        SUB = 0xFF8A93A8;
        ACCENT = 0xFFFF8282;
        ONACCENT = 0xFF2B0A0A;
        RIPPLE = 0x33FFFFFF;
        DIVIDER = 0x24FFFFFF;
        LOGTXT = 0xFFD7DAE0;
        switch (m) {
            case AMOLED:
                BG = 0xFF000000; SURFACE = 0xFF101114; SURFACE2 = 0xFF1B1D22; LOGBG = 0xFF000000;
                break;
            case LIGHT:
                BG = 0xFFF4F6FA; SURFACE = 0xFFFFFFFF; SURFACE2 = 0xFFE6EAF2;
                TXT = 0xFF1B2133; SUB = 0xFF5F6B85;
                ACCENT = 0xFFD94A4A; ONACCENT = 0xFFFFFFFF;
                OK = 0xFF2E7D32; ERR = 0xFFD32F2F;
                RIPPLE = 0x22000000; DIVIDER = 0x1F000000;
                LOGBG = 0xFFECEFF5; LOGTXT = 0xFF1F2430;
                break;
            default:
                BG = 0xFF0C111E; SURFACE = 0xFF141B2D; SURFACE2 = 0xFF1B2438; LOGBG = 0xFF0A0C0F;
        }
        if (light) {
            Highlighter.KW = 0xFF7B3FC4; Highlighter.STR = 0xFF2E7D32; Highlighter.CMT = 0xFF8A93A8;
            Highlighter.NUM = 0xFFC2570C; Highlighter.TAG = 0xFF1E5FD6; Highlighter.ATTR = 0xFF9A6A00;
        } else {
            Highlighter.KW = 0xFFC792EA; Highlighter.STR = 0xFFC3E88D; Highlighter.CMT = 0xFF6B7794;
            Highlighter.NUM = 0xFFF78C6C; Highlighter.TAG = 0xFF82AAFF; Highlighter.ATTR = 0xFFFFCB6B;
        }
    }

    static int alertStyle() {
        return light ? android.R.style.Theme_Material_Light_Dialog_Alert : android.R.style.Theme_Material_Dialog_Alert;
    }

    static int sheetStyle() {
        return light ? android.R.style.Theme_Material_Light_Dialog_NoActionBar : android.R.style.Theme_Material_Dialog_NoActionBar;
    }
}
