package com.dna.tools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Tokenizer ringan untuk syntax highlight. Tidak bergantung ke Android supaya mudah dites. */
final class Highlighter {
    static final int KW = 0xFFC792EA, STR = 0xFFC3E88D, CMT = 0xFF6B7794,
            NUM = 0xFFF78C6C, TAG = 0xFF82AAFF, ATTR = 0xFFFFCB6B;

    interface Sink { void span(int start, int end, int color); }

    static final class Lang {
        final String name;
        Set<String> kw = new HashSet<>();
        String lineCmt = "", lineCmt2 = "", blockStart = "", blockEnd = "";
        String quotes = "\"'";
        boolean triple, markup, keyed, ignoreCase, dollar, preproc, smali, atRules, hashColor, mdHead;

        Lang(String name) { this.name = name; }

        Lang words(String s) {
            for (String w : s.split(" ")) if (!w.isEmpty()) kw.add(ignoreCase ? w.toLowerCase(Locale.ROOT) : w);
            return this;
        }
    }

    private static final Lang PLAIN = new Lang("Plain text");
    private static final Map<String, Lang> BY_EXT = new HashMap<>();

    private static void reg(Lang l, String exts) {
        for (String e : exts.split(" ")) BY_EXT.put(e, l);
    }

    static {
        Lang java = new Lang("Java / Kotlin");
        java.lineCmt = "//"; java.blockStart = "/*"; java.blockEnd = "*/"; java.triple = true;
        java.words("abstract assert boolean break byte case catch char class const continue default do double else enum "
                + "extends final finally float for goto if implements import instanceof int interface long native new "
                + "package private protected public return short static strictfp super switch synchronized this throw "
                + "throws transient try void volatile while true false null var val fun object when is in as override "
                + "open sealed data lateinit companion suspend typealias internal def");
        reg(java, "java kt kts gradle groovy scala");

        Lang cstyle = new Lang("C / C++");
        cstyle.lineCmt = "//"; cstyle.blockStart = "/*"; cstyle.blockEnd = "*/"; cstyle.preproc = true;
        cstyle.words("auto break case char const continue default do double else enum extern float for goto if inline "
                + "int long register return short signed sizeof static struct switch typedef union unsigned void "
                + "volatile while class namespace template typename public private protected virtual new delete this "
                + "nullptr true false bool using override final constexpr include define ifdef ifndef endif pragma");
        reg(cstyle, "c h cc cpp cxx hpp hh");

        Lang other = new Lang("C-style (Go/Rust/C#/Dart/Swift)");
        other.lineCmt = "//"; other.blockStart = "/*"; other.blockEnd = "*/"; other.quotes = "\"'`";
        other.words("func fn let mut pub use mod struct enum impl trait match loop where type interface package import "
                + "var const return if else for while switch case default break continue go defer chan range map "
                + "class new this self super static final async await try catch throw throws true false null nil "
                + "namespace using public private protected void int string bool float double override abstract");
        reg(other, "go rs cs dart swift");

        Lang js = new Lang("JavaScript / TypeScript");
        js.lineCmt = "//"; js.blockStart = "/*"; js.blockEnd = "*/"; js.quotes = "\"'`";
        js.words("async await break case catch class const continue debugger default delete do else export extends "
                + "finally for function if import in instanceof let new of return static super switch this throw try "
                + "typeof var void while with yield true false null undefined interface type enum implements public "
                + "private protected readonly from as");
        reg(js, "js mjs cjs jsx ts tsx bp");

        Lang json = new Lang("JSON");
        json.quotes = "\""; json.words("true false null");
        reg(json, "json json5 webmanifest");

        Lang py = new Lang("Python");
        py.lineCmt = "#"; py.triple = true;
        py.words("False None True and as assert async await break class continue def del elif else except finally for "
                + "from global if import in is lambda nonlocal not or pass raise return try while with yield self");
        reg(py, "py pyw");

        Lang sh = new Lang("Shell");
        sh.lineCmt = "#"; sh.quotes = "\"'`"; sh.dollar = true;
        sh.words("if then else elif fi for while until do done case esac in function return exit local export "
                + "readonly unset shift break continue echo cd set source alias trap eval exec test true false");
        reg(sh, "sh bash zsh ksh rc mk");

        Lang smali = new Lang("Smali");
        smali.lineCmt = "#"; smali.quotes = "\""; smali.smali = true;
        smali.words(".class .super .implements .field .method .end .locals .registers .param .line .annotation "
                + ".prologue .source .local .restart .catch .catchall .packed-switch .sparse-switch .array-data "
                + "const const/4 const/16 const-string const-class invoke-virtual invoke-static invoke-direct "
                + "invoke-super invoke-interface move move-result move-result-object move-exception return "
                + "return-void return-object new-instance new-array iget iput sget sput iget-object iput-object "
                + "sget-object sput-object if-eq if-ne if-lt if-ge if-gt if-le if-eqz if-nez goto throw nop "
                + "check-cast instance-of public private protected static final constructor abstract synthetic");
        reg(smali, "smali");

        Lang xml = new Lang("XML / HTML");
        xml.markup = true; xml.blockStart = "<!--"; xml.blockEnd = "-->";
        reg(xml, "xml html htm xhtml svg plist");

        Lang css = new Lang("CSS");
        css.blockStart = "/*"; css.blockEnd = "*/"; css.atRules = true; css.hashColor = true;
        reg(css, "css scss less");

        Lang yaml = new Lang("YAML / Properties / INI");
        yaml.lineCmt = "#"; yaml.lineCmt2 = ";"; yaml.keyed = true;
        yaml.words("true false null yes no on off");
        reg(yaml, "yml yaml properties prop ini conf cfg toml cnf env");

        Lang sql = new Lang("SQL");
        sql.lineCmt = "--"; sql.blockStart = "/*"; sql.blockEnd = "*/"; sql.ignoreCase = true;
        sql.words("select from where insert into values update set delete create table drop alter add index join "
                + "left right inner outer on group by order having limit offset and or not null is in like as "
                + "distinct union all primary key foreign references default begin commit rollback");
        reg(sql, "sql");

        Lang md = new Lang("Markdown");
        md.mdHead = true; md.quotes = "`";
        reg(md, "md markdown");
    }

    static Lang forFile(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        if (n.equals("build.prop") || n.equals("default.prop")) return BY_EXT.get("prop");
        if (n.equals("androidmanifest.xml")) return BY_EXT.get("xml");
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : n;
        Lang l = BY_EXT.get(ext);
        return l != null ? l : PLAIN;
    }

    private static boolean idStart(char c) { return Character.isLetter(c) || c == '_'; }
    private static boolean idPart(char c) { return Character.isLetterOrDigit(c) || c == '_'; }

    private static boolean startsAt(CharSequence s, int i, String p) {
        int n = p.length();
        if (n == 0 || i + n > s.length()) return false;
        for (int k = 0; k < n; k++) if (s.charAt(i + k) != p.charAt(k)) return false;
        return true;
    }

    private static int indexOf(CharSequence s, String p, int from) {
        int end = s.length() - p.length();
        for (int i = from; i <= end; i++) if (startsAt(s, i, p)) return i;
        return -1;
    }

    private static int eol(CharSequence s, int i) {
        int n = s.length();
        while (i < n && s.charAt(i) != '\n') i++;
        return i;
    }

    static void scan(CharSequence s, Lang l, Sink out) {
        if (l == PLAIN) return;
        if (l.markup) { scanMarkup(s, l, out); return; }
        int n = s.length();
        int i = 0;
        boolean lineStart = true;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\n') { lineStart = true; i++; continue; }
            if (c == ' ' || c == '\t' || c == '\r') { i++; continue; }

            if (lineStart) {
                if (l.mdHead && c == '#') {
                    int e = eol(s, i);
                    out.span(i, e, TAG);
                    i = e;
                    continue;
                }
                if (l.keyed) {
                    int e = keyEnd(s, i);
                    if (e > i) {
                        out.span(i, e, s.charAt(i) == '[' ? TAG : ATTR);
                        i = e;
                        lineStart = false;
                        continue;
                    }
                }
            }
            lineStart = false;

            if (startsAt(s, i, l.lineCmt) || startsAt(s, i, l.lineCmt2)) {
                int e = eol(s, i);
                out.span(i, e, CMT);
                i = e;
                continue;
            }
            if (!l.blockStart.isEmpty() && startsAt(s, i, l.blockStart)) {
                int e = indexOf(s, l.blockEnd, i + l.blockStart.length());
                e = e < 0 ? n : e + l.blockEnd.length();
                out.span(i, e, CMT);
                i = e;
                continue;
            }
            if (l.quotes.indexOf(c) >= 0) {
                int e = stringEnd(s, i, l);
                out.span(i, e, STR);
                i = e;
                continue;
            }
            if (l.dollar && c == '$') {
                int e = i + 1;
                if (e < n && s.charAt(e) == '{') {
                    while (e < n && s.charAt(e) != '}' && s.charAt(e) != '\n') e++;
                    if (e < n && s.charAt(e) == '}') e++;
                } else {
                    while (e < n && (idPart(s.charAt(e)) || "@#?*!".indexOf(s.charAt(e)) >= 0)) e++;
                }
                out.span(i, e, ATTR);
                i = e;
                continue;
            }
            if (l.atRules && c == '@') {
                int e = i + 1;
                while (e < n && (idPart(s.charAt(e)) || s.charAt(e) == '-')) e++;
                out.span(i, e, KW);
                i = e;
                continue;
            }
            if (l.hashColor && c == '#') {
                int e = i + 1;
                while (e < n && Character.digit(s.charAt(e), 16) >= 0) e++;
                if (e > i + 1) out.span(i, e, NUM);
                i = Math.max(e, i + 1);
                continue;
            }
            if (l.preproc && c == '#') {
                int e = i + 1;
                while (e < n && idPart(s.charAt(e))) e++;
                if (e > i + 1) out.span(i, e, KW);
                i = Math.max(e, i + 1);
                continue;
            }
            if (l.smali && c == ':') {
                int e = i + 1;
                while (e < n && idPart(s.charAt(e))) e++;
                out.span(i, e, TAG);
                i = Math.max(e, i + 1);
                continue;
            }
            if (Character.isDigit(c)) {
                int e = i + 1;
                while (e < n && (Character.isLetterOrDigit(s.charAt(e)) || s.charAt(e) == '.')) e++;
                out.span(i, e, NUM);
                i = e;
                continue;
            }
            boolean start = idStart(c) || (l.smali && (c == '.' || c == '-') && i + 1 < n && idStart(s.charAt(i + 1)));
            if (start) {
                int e = i + 1;
                while (e < n) {
                    char d = s.charAt(e);
                    if (idPart(d) || (l.smali && (d == '-' || d == '/' || d == '.'))) e++;
                    else break;
                }
                String w = s.subSequence(i, e).toString();
                if (l.ignoreCase) w = w.toLowerCase(Locale.ROOT);
                if (l.kw.contains(w)) out.span(i, e, KW);
                i = e;
                continue;
            }
            i++;
        }
    }

    /** Akhir nama key di awal baris (YAML/properties/INI); 0 kalau bukan key. */
    private static int keyEnd(CharSequence s, int i) {
        int n = s.length();
        if (s.charAt(i) == '[') {
            int e = i + 1;
            while (e < n && s.charAt(e) != ']' && s.charAt(e) != '\n') e++;
            return e < n && s.charAt(e) == ']' ? e + 1 : i;
        }
        int e = i;
        if (e < n && s.charAt(e) == '-') {   // item list YAML: "- key: v"
            int k = e + 1;
            while (k < n && s.charAt(k) == ' ') k++;
            if (k > e + 1) e = k;
        }
        int ks = e;
        while (e < n) {
            char d = s.charAt(e);
            if (Character.isLetterOrDigit(d) || d == '_' || d == '.' || d == '-' || d == '/') e++;
            else break;
        }
        if (e == ks) return i;
        int k = e;
        while (k < n && s.charAt(k) == ' ') k++;
        if (k < n && (s.charAt(k) == ':' || s.charAt(k) == '=')) return e;
        return i;
    }

    private static int stringEnd(CharSequence s, int i, Lang l) {
        int n = s.length();
        char q = s.charAt(i);
        if (l.triple && i + 2 < n && s.charAt(i + 1) == q && s.charAt(i + 2) == q) {
            String t = "" + q + q + q;
            int e = indexOf(s, t, i + 3);
            return e < 0 ? n : e + 3;
        }
        int e = i + 1;
        while (e < n) {
            char d = s.charAt(e);
            if (d == '\\') { e += 2; continue; }
            if (d == q) return e + 1;
            if (d == '\n' && q != '`') return e;
            e++;
        }
        return n;
    }

    private static void scanMarkup(CharSequence s, Lang l, Sink out) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (startsAt(s, i, "<!--")) {
                int e = indexOf(s, "-->", i + 4);
                e = e < 0 ? n : e + 3;
                out.span(i, e, CMT);
                i = e;
                continue;
            }
            if (c != '<') { i++; continue; }
            int e = i + 1;
            while (e < n && "/?!".indexOf(s.charAt(e)) >= 0) e++;
            while (e < n && (idPart(s.charAt(e)) || ":.-".indexOf(s.charAt(e)) >= 0)) e++;
            out.span(i, e, TAG);
            i = e;
            while (i < n && s.charAt(i) != '>') {
                char d = s.charAt(i);
                if (d == '"' || d == '\'') {
                    int k = i + 1;
                    while (k < n && s.charAt(k) != d) k++;
                    k = Math.min(n, k + 1);
                    out.span(i, k, STR);
                    i = k;
                } else if (idStart(d)) {
                    int k = i + 1;
                    while (k < n && (idPart(s.charAt(k)) || ":.-".indexOf(s.charAt(k)) >= 0)) k++;
                    out.span(i, k, ATTR);
                    i = k;
                } else {
                    i++;
                }
            }
            if (i < n) {
                int from = i;
                if (i > 0 && "/?".indexOf(s.charAt(i - 1)) >= 0) from = i - 1;
                out.span(from, i + 1, TAG);
                i++;
            }
        }
    }
}
