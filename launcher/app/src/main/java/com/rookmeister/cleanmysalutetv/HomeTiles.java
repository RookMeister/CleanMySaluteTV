package com.rookmeister.cleanmysalutetv;

import android.content.Context;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Home's tiles as they stood when Home was last left (home_tiles.txt, one tile key a line): the settings menu's Apps
 * list starts from them, so it shows the apps Home really shows, in Home's order, without working them out again.
 */
final class HomeTiles {
    private HomeTiles() {}

    private static AtomicFile file(Context c) {
        return new AtomicFile(new File(c.getFilesDir(), "home_tiles.txt"));
    }

    static void save(Context c, List<String> keys) {
        AtomicFile f = file(c);
        FileOutputStream out = null;
        try {
            out = f.startWrite();
            out.write(String.join("\n", keys).getBytes(StandardCharsets.UTF_8));
            f.finishWrite(out);
        } catch (IOException e) {
            if (out != null) f.failWrite(out);
        }
    }

    /** Empty when Home has not saved them yet. */
    static List<String> load(Context c) {
        List<String> keys = new ArrayList<>();
        try {
            for (String line : new String(file(c).readFully(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.trim().isEmpty()) keys.add(line.trim());
            }
        } catch (IOException e) {
            // none yet
        }
        return keys;
    }
}
