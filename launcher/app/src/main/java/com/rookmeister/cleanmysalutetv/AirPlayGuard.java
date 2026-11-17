/*
 * Based on AirPlayGuard from Hume Launcher (https://github.com/petrobot-admin/hume-launcher),
 * Copyright 2026 Petrobot, licensed under the Apache License, Version 2.0 (licenses/hume-launcher-LICENSE.txt).
 * Changed for CleanMySaluteTV: the receiver counts as visible when an answer comes from this TV's own address, so
 * the check works without reading the receiver's name; the settings restart is used only with the DUMP grant that
 * AirPlay Server's settings receiver requires; the guard can be switched off in the settings menu.
 */
package com.rookmeister.cleanmysalutetv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Keeps the AirPlay receiver (AirPlay Server, io.github.jqssun.airplay) visible to Macs and iPhones. Only acts when
 * that app is installed and the guard is on. After long sleeps the app can keep running without announcing itself;
 * the launcher then asks the network the way a Mac does (an mDNS query) and, if this TV does not answer, restarts the
 * receiver: through the app's settings receiver when the launcher holds DUMP (install.sh with AIRPLAY=yes), otherwise
 * through the app's start-up receiver, which starts a stopped server when the app's own "start on boot" is on.
 * Nothing appears on screen. Checked on wake, when the network returns and every 15 minutes while Home is open.
 */
final class AirPlayGuard {
    static final String PKG = "io.github.jqssun.airplay";
    static final String RECEIVER = PKG + ".SettingsReceiver";
    static final String KEY = "airplay_guard";
    private static final String TAG = "CmstAirPlay";
    private static long lastCheck;
    private static boolean busy;

    private AirPlayGuard() {}

    static boolean installed(Context c) {
        try {
            c.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The settings menu's switch (OnTvChoices); on by default. */
    static boolean enabled(Context c) {
        return OnTvChoices.prefs(c).getBoolean(KEY, true);
    }

    /** DUMP is what AirPlay Server's settings receiver asks of its callers; only adb can grant it. */
    static boolean canRestart(Context c) {
        return c.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED;
    }

    /** @param force check now (wake, the settings menu) rather than at most every ten minutes */
    static void check(Context ctx, boolean force) {
        Context c = ctx.getApplicationContext();
        long now = System.currentTimeMillis();
        if (busy || !enabled(c) || !installed(c) || (!force && now - lastCheck < 10 * 60 * 1000L)) return;
        lastCheck = now;
        busy = true;
        if (canRestart(c)) learnName(c);
        Handler h = new Handler(Looper.getMainLooper());
        // After waking, give Wi-Fi a moment before asking the network.
        h.postDelayed(() -> new Thread(() -> {
            boolean visible = visibleOnNetwork(c);
            h.post(() -> {
                busy = false;
                if (visible) return;
                Log.i(TAG, "AirPlay not answering on the network; restarting it");
                if (canRestart(c)) restart(c);
                else startStopped(c);
                h.postDelayed(() -> new Thread(() -> {
                    if (!visibleOnNetwork(c)) h.post(() -> startStopped(c));
                    lastCheck = System.currentTimeMillis();
                }).start(), 8000);
            });
        }).start(), force ? 8000 : 0);
    }

    /**
     * Sends an mDNS query for AirPlay receivers (224.0.0.251:5353) and listens for 3 s for an answer from this TV
     * (one of its own addresses, or the receiver's name where known). True when unsure, so it never restarts on a guess.
     */
    static boolean visibleOnNetwork(Context c) {
        String name = c.getSharedPreferences("airplay", Context.MODE_PRIVATE).getString("name", null);
        Set<InetAddress> own = ownAddresses();
        if (own.isEmpty() && name == null) return true;
        WifiManager wm = (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wm == null ? null : wm.createMulticastLock("cmst_airplay_check");
        DatagramSocket sock = null;
        try {
            if (lock != null) lock.acquire();
            InetAddress group = InetAddress.getByName("224.0.0.251");
            // Query from an ordinary port: mDNS responders answer such "legacy" queries directly to the asker, so
            // there is no need to share port 5353 with the system's own responder.
            sock = new DatagramSocket();
            sock.setSoTimeout(400);
            byte[] q = query("_airplay._tcp.local");
            byte[] want = name == null ? null : name.getBytes(StandardCharsets.UTF_8);
            long end = System.currentTimeMillis() + 3000;
            int sent = 0;
            while (System.currentTimeMillis() < end) {
                if (sent < 3) {
                    sock.send(new DatagramPacket(q, q.length, group, 5353));
                    sent++;
                }
                byte[] buf = new byte[9000];
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                try {
                    sock.receive(p);
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (own.contains(p.getAddress()) || p.getAddress().isLoopbackAddress()) return true;
                if (want != null && contains(buf, p.getLength(), want)) return true;
            }
            Log.i(TAG, "AirPlay check: this TV did not answer");
            return false;
        } catch (Exception e) {
            Log.w(TAG, "AirPlay check failed: " + e);
            return true;
        } finally {
            if (sock != null) sock.close();
            if (lock != null && lock.isHeld()) lock.release();
        }
    }

    private static Set<InetAddress> ownAddresses() {
        Set<InetAddress> out = new HashSet<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                out.addAll(Collections.list(ni.getInetAddresses()));
            }
        } catch (Exception ignored) {
            // no addresses: the name, if known, still decides
        }
        return out;
    }

    /** A one-question DNS query (PTR, IN) for {@code qname}. */
    static byte[] query(String qname) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] header = {0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0};
        o.write(header, 0, header.length);
        for (String label : qname.split("\\.")) {
            byte[] b = label.getBytes(StandardCharsets.US_ASCII);
            o.write(b.length);
            o.write(b, 0, b.length);
        }
        byte[] tail = {0, 0, 12, 0, 1};
        o.write(tail, 0, tail.length);
        return o.toByteArray();
    }

    static boolean contains(byte[] buf, int len, byte[] want) {
        outer:
        for (int i = 0; i + want.length <= len; i++) {
            for (int j = 0; j < want.length; j++) if (buf[i + j] != want[j]) continue outer;
            return true;
        }
        return false;
    }

    /** Re-applies the current server name: the app restarts its server and announces itself again. Needs DUMP. */
    private static void restart(Context c) {
        Intent list = new Intent(PKG + ".LIST_SETTINGS").setClassName(PKG, RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        c.sendOrderedBroadcast(list, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String name = readName(getResultData());
                if (name != null) ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).edit().putString("name", name).apply();
                else name = ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).getString("name", null);
                if (name == null) name = "Android AirPlay";   // AirPlay Server's own default
                ctx.sendBroadcast(new Intent(PKG + ".SET_SETTING").setClassName(PKG, RECEIVER)
                        .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        .putExtra("key", "server_name").putExtra("value", name));
            }
        }, null, 0, null, null);
    }

    /** Reads the receiver's name without changing anything, so the network check knows it too. Needs DUMP. */
    private static void learnName(Context c) {
        Intent list = new Intent(PKG + ".LIST_SETTINGS").setClassName(PKG, RECEIVER)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        c.sendOrderedBroadcast(list, null, new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String n = readName(getResultData());
                if (n != null) ctx.getSharedPreferences("airplay", Context.MODE_PRIVATE).edit().putString("name", n).apply();
            }
        }, null, 0, null, null);
    }

    private static String readName(String json) {
        try {
            return new JSONObject(json).getJSONObject("server_name").getString("value");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * A fully stopped app: start it silently through its own start-up receiver (no screen). AirPlay Server starts its
     * server there only when its "start on boot" setting is on.
     */
    private static void startStopped(Context c) {
        Log.i(TAG, "AirPlay still hidden; starting it in the background");
        c.sendBroadcast(new Intent("android.intent.action.QUICKBOOT_POWERON").setClassName(PKG, PKG + ".BootReceiver")
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND));
    }
}
