package ir.nakhodi.cafeledger;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** Finds the local Wi-Fi / hotspot address of this phone (no internet, no Play Services). */
final class Net {

    private Net() {}

    static String localIp(Context ctx) {
        WifiManager wm = null;
        try {
            wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        } catch (Throwable ignored) {
        }
        if (wm != null) {
            try {
                if (!wm.isWifiEnabled()) wm.setWifiEnabled(true);   // harmless when already on
            } catch (Throwable ignored) {
            }
            try {
                int ip = wm.getConnectionInfo() == null ? 0 : wm.getConnectionInfo().getIpAddress();
                if (ip != 0) {
                    String s = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
                    if (!s.startsWith("0.") && !s.startsWith("127.")) return s;
                }
            } catch (Throwable ignored) {
            }
        }
        return interfaceIp();
    }

    /** Fallback used on hotspots (and on Android 10+ where the WifiManager address can read as 0). */
    static String interfaceIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni == null || !ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    static boolean looksLikeIp(String s) {
        if (s == null) return false;
        String[] p = s.split("\\.");
        if (p.length != 4) return false;
        for (String x : p) {
            try {
                int n = Integer.parseInt(x);
                if (n < 0 || n > 255) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
