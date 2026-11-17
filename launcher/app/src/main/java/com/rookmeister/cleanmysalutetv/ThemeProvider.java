package com.rookmeister.cleanmysalutetv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Lets tools/theme.sh write the theme files into {@link Config#dir} on firmware where the adb shell is
 * locked out of Android/data (TCL G07, Android 11). Only the files {@link ThemeFiles} allows, and only for
 * callers holding WRITE_SECURE_SETTINGS, which the adb shell has and ordinary apps cannot get.
 *
 *   content write --uri content://com.rookmeister.cleanmysalutetv.theme/config.json < /data/local/tmp/config.json
 *   content write --uri content://com.rookmeister.cleanmysalutetv.theme/wallpapers/dusk.jpg < /data/local/tmp/dusk.jpg
 *   content delete --uri content://com.rookmeister.cleanmysalutetv.theme/config.json
 *   content delete --uri content://com.rookmeister.cleanmysalutetv.theme/wallpapers      (every image in the collection)
 *
 * CleanMySaluteTV: the Mac app (RemoteSettings) also reads and writes remote/ here, and calls
 *   content call --uri content://com.rookmeister.cleanmysalutetv.theme --method export
 *   content call --uri content://com.rookmeister.cleanmysalutetv.theme --method apply
 */
public class ThemeProvider extends ContentProvider {

    private File file(Uri uri) throws FileNotFoundException {
        String path = ThemeFiles.path(uri.getPathSegments());
        File dir = Config.dir(getContext());
        if (dir == null || path == null) throw new FileNotFoundException(String.valueOf(uri));
        File f = new File(dir, path);
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new FileNotFoundException(String.valueOf(uri));
        return f;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = file(uri);
        if (mode.contains("w")) {
            return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_WRITE_ONLY
                    | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        File dir = Config.dir(getContext());
        if (dir != null && ThemeFiles.isCollection(uri.getPathSegments())) {
            File[] images = new File(dir, ThemeFiles.WALLPAPERS).listFiles((d, name) -> ThemeFiles.collectionImage(name));
            int n = 0;
            if (images != null) for (File f : images) if (f.delete()) n++;
            return n;
        }
        try {
            return file(uri).delete() ? 1 : 0;
        } catch (FileNotFoundException e) {
            return 0;
        }
    }

    /** CleanMySaluteTV: "export" and "apply" for the Mac app (RemoteSettings); "ok" true or an "error". */
    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // call() gets no permission check of its own, unlike the files: the adb shell's, as for those.
        if (getContext().checkCallingPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("WRITE_SECURE_SETTINGS required");
        }
        Bundle out = new Bundle();
        try {
            if ("export".equals(method)) RemoteSettings.export(getContext());
            else if ("apply".equals(method)) RemoteSettings.apply(getContext());
            else throw new IllegalArgumentException("unknown method " + method);
            out.putBoolean("ok", true);
        } catch (Exception e) {
            out.putBoolean("ok", false);
            out.putString("error", String.valueOf(e));
        }
        return out;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
