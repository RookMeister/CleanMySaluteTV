package com.rookmeister.cleanmysalutetv;

/** CleanMySaluteTV control panel (QuickSettingsActivity): the arithmetic of its status lines, for the JVM tests. */
final class ControlPanel {
    private ControlPanel() {}

    /** Rounded share in percent, 0 to 100; 0 when there is no whole to take it of. */
    static int percent(long part, long whole) {
        if (whole <= 0) return 0;
        return (int) Math.max(0, Math.min(100, Math.round(part * 100.0 / whole)));
    }

    /** The network name without Android's quotes; empty when Android hides it ("<unknown ssid>", "0x"). */
    static String ssid(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\"", "").trim();
        return s.isEmpty() || s.contains("unknown") || s.equals("0x") ? "" : s;
    }
}
