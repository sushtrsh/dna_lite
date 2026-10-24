package com.dna.tools;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Ekstrak assets/tools dan assets/samples ke filesDir (hanya saat install/update). */
final class Toolkit {
    static void install(Context c) throws Exception {
        File files = c.getFilesDir();
        File stamp = new File(files, ".installed");
        String key = String.valueOf(c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime);
        if (stamp.exists() && key.equals(read(stamp))) return;
        AssetManager am = c.getAssets();
        copy(am, "tools", new File(files, "tools"));
        copy(am, "samples", new File(files, "samples"));
        FileOutputStream o = new FileOutputStream(stamp);
        o.write(key.getBytes("UTF-8"));
        o.close();
    }

    private static String read(File f) {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] b = new byte[(int) f.length()];
            int n = in.read(b);
            in.close();
            return new String(b, 0, Math.max(n, 0), "UTF-8");
        } catch (IOException e) {
            return "";
        }
    }

    private static void copy(AssetManager am, String path, File dst) throws IOException {
        String[] list = am.list(path);
        if (list != null && list.length > 0) {
            dst.mkdirs();
            dst.setExecutable(true, false);
            dst.setReadable(true, false);
            for (String s : list) copy(am, path + "/" + s, new File(dst, s));
            return;
        }
        InputStream in = am.open(path);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
        in.close();
        dst.setReadable(true, false);
        dst.setExecutable(true, false);
    }
}
