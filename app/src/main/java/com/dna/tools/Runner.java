package com.dna.tools;

import android.content.Context;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;

/** Menjalankan shell (root kalau ada) dengan environment yang sama seperti executor lama. */
final class Runner {
    private final File files, cache;
    private volatile boolean root;
    private final Thread rootCheck;

    Runner(Context c) {
        files = c.getFilesDir();
        cache = c.getCacheDir();
        rootCheck = new Thread(() -> {
            try {
                java.lang.Process p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String l, all = "";
                while ((l = r.readLine()) != null) all += l;
                p.waitFor();
                root = all.contains("uid=0");
            } catch (Exception e) {
                root = false;
            }
        });
        rootCheck.start();
    }

    boolean isRoot() {
        return root;
    }

    void awaitRoot() {
        try { rootCheck.join(); } catch (InterruptedException ignored) { }
    }

    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /** "samples/x.sh arg" -> "sh samples/x.sh arg" (file lama tidak punya shebang/izin eksekusi). */
    static String norm(String cmd) {
        String c = cmd.trim();
        if (c.indexOf('\n') < 0 && (c.startsWith("samples/") || c.startsWith("$START_DIR/samples/"))) return "sh " + c;
        return c;
    }

    private String prologue(boolean chown) {
        String f = files.getAbsolutePath(), t = cache.getAbsolutePath();
        StringBuilder s = new StringBuilder("#!/system/bin/sh\n");
        s.append("export START_DIR=").append(q(f)).append('\n');
        s.append("export TEMP_DIR=").append(q(t)).append('\n');
        s.append("export TMPDIR=").append(q(t)).append('\n');
        s.append("export PATH=\"$START_DIR/tools:$PATH\"\n");
        s.append("export APP_USER_ID=").append(Process.myUid()).append('\n');
        s.append("export ROOT_PERMISSION=").append(root ? "true" : "false").append('\n');
        s.append("export DNA_DIR=/sdcard/DNA\n");
        s.append("[ -d \"$DNA_DIR\" ] || mkdir -p \"$DNA_DIR\"\n");
        s.append("if [ \"$ROOT_PERMISSION\" = true ]; then export DNA_TMP=/data/DNA; else export DNA_TMP=\"$START_DIR/DNA\"; fi\n");
        s.append("if [ -f \"$TMPDIR/DNA.ini\" ]; then\n");
        s.append("  project=$(cat \"$TMPDIR/DNA.ini\"); export project\n");
        s.append("  export DNA_PRO=\"$DNA_DIR/$project\"\n");
        s.append("  export DNA_DRO=\"$DNA_TMP/$project\"\n");
        s.append("fi\n");
        if (chown) s.append("[ \"$ROOT_PERMISSION\" = true ] && chown -R \"$APP_USER_ID:$APP_USER_ID\" \"$START_DIR\" \"$TMPDIR\" 2>/dev/null\n");
        s.append("cd \"$START_DIR\"\n");
        return s.toString();
    }

    /** Mulai proses. Panggil dari background thread. File script sementara dihapus oleh {@link #cleanup}. */
    Started start(String body, Map<String, String> env, boolean chown) throws IOException {
        awaitRoot();
        StringBuilder s = new StringBuilder(prologue(chown));
        if (env != null) {
            for (Map.Entry<String, String> e : env.entrySet()) {
                if (e.getKey().matches("[A-Za-z_][A-Za-z0-9_]*"))
                    s.append("export ").append(e.getKey()).append('=').append(q(e.getValue())).append('\n');
            }
        }
        s.append(norm(body)).append('\n');
        File f = File.createTempFile("dna_", ".sh", cache);
        FileOutputStream o = new FileOutputStream(f);
        o.write(s.toString().getBytes("UTF-8"));
        o.close();
        f.setReadable(true, false);
        ProcessBuilder pb = root
                ? new ProcessBuilder("su", "-c", "sh " + f.getAbsolutePath())
                : new ProcessBuilder("sh", f.getAbsolutePath());
        pb.redirectErrorStream(true);
        pb.directory(files);
        Started st = new Started();
        st.script = f;
        st.proc = pb.start();
        return st;
    }

    static final class Started {
        java.lang.Process proc;
        File script;

        void cleanup() {
            if (script != null) script.delete();
        }
    }

    /** Jalankan dan kembalikan stdout (untuk options-sh, value-sh, lock, config-sh). */
    String capture(String body) {
        try {
            Started st = start(body, null, false);
            BufferedReader r = new BufferedReader(new InputStreamReader(st.proc.getInputStream(), "UTF-8"));
            StringBuilder out = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) out.append(l).append('\n');
            st.proc.waitFor();
            st.cleanup();
            return out.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
