package com.rookmeister.cleanmysalutetv;

import java.util.List;
import java.util.regex.Pattern;

/** The files ThemeProvider may write or delete under Config.dir, by the URI's path segments. */
final class ThemeFiles {
    static final String WALLPAPERS = "wallpapers";
    /** The names tools/theme.sh accepts for a collection image, and the types WallpaperRotation reads. */
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9_.-]+\\.(jpg|jpeg|png|webp)",
            Pattern.CASE_INSENSITIVE);

    /** An app icon the Mac app reads: a package name and .png. */
    private static final Pattern ICON = Pattern.compile("[A-Za-z0-9_.]+\\.png");

    private ThemeFiles() {}

    /** The path relative to the theme directory, e.g. "config.json" or "wallpapers/dusk.jpg", or null if refused. */
    static String path(List<String> segments) {
        if (segments == null) return null;
        if (segments.size() == 1) {
            String name = segments.get(0);
            return Config.FILE_NAME.equals(name) || "wallpaper.jpg".equals(name) || "theme_revision".equals(name)
                    ? name : null;
        }
        // CleanMySaluteTV: the Mac app's files (RemoteSettings): remote/state.json, remote/apply.json and the icons.
        if (segments.size() == 2 && RemoteSettings.DIR.equals(segments.get(0))
                && (RemoteSettings.STATE.equals(segments.get(1)) || RemoteSettings.APPLY.equals(segments.get(1)))) {
            return RemoteSettings.DIR + "/" + segments.get(1);
        }
        if (segments.size() == 3 && RemoteSettings.DIR.equals(segments.get(0)) && RemoteSettings.ICONS.equals(segments.get(1))
                && ICON.matcher(segments.get(2)).matches()) {
            return RemoteSettings.DIR + "/" + RemoteSettings.ICONS + "/" + segments.get(2);
        }
        if (segments.size() == 2 && WALLPAPERS.equals(segments.get(0))
                && (collectionImage(segments.get(1)) || WallpaperCaptions.FILE_NAME.equals(segments.get(1)))) {
            return WALLPAPERS + "/" + segments.get(1);
        }
        return null;
    }

    /** True for "wallpapers" alone: a delete of it clears the collection's images. */
    static boolean isCollection(List<String> segments) {
        return segments != null && segments.size() == 1 && WALLPAPERS.equals(segments.get(0));
    }

    static boolean collectionImage(String name) {
        return name != null && !name.startsWith(".") && IMAGE.matcher(name).matches();
    }
}
