/* This file is part of PCAPdroid, licensed under the GNU GPL v3 or later. */
package com.emanuelef.remote_capture.model;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

@RunWith(RobolectricTestRunner.class)
public class CaptureSettingsTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setup() {
        context = ApplicationProvider.getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().clear().commit();
    }

    private Intent startIntent() {
        return new Intent().putExtra("action", "start");
    }

    @Test
    public void apiCaptureUsesSavedMtuAndRequestedApp() {
        prefs.edit()
                .putString(Prefs.PREF_VPN_MTU, "1400")
                .putStringSet(Prefs.PREF_APP_FILTER, Collections.singleton("com.other.app"))
                .putBoolean(Prefs.PREF_APP_FILTER_ENABLED, false)
                .commit();

        CaptureSettings settings = new CaptureSettings(context, startIntent()
                .putExtra(Prefs.PREF_APP_FILTER, "com.tencent.tmgp.sgame")
                .putExtra(Prefs.PREF_IP_MODE, "ipv4")
                .putExtra("pcap_dump_mode", "pcap_file")
                .putExtra("pcap_name", "minipilot.pcap"));

        assertEquals(1400, settings.vpn_mtu);
        assertEquals(Collections.singleton("com.tencent.tmgp.sgame"), settings.app_filter);
        assertEquals(Prefs.IpMode.IPV4_ONLY, settings.ip_mode);
        assertEquals(Prefs.DumpMode.PCAP_FILE, settings.dump_mode);
        assertEquals("minipilot.pcap", settings.pcap_name);
    }

    @Test
    public void apiMtuOverrideDoesNotChangeSavedPreference() {
        prefs.edit().putString(Prefs.PREF_VPN_MTU, "1400").commit();

        assertEquals(9000, new CaptureSettings(context, startIntent()
                .putExtra(Prefs.PREF_VPN_MTU, 9000)).vpn_mtu);
        assertEquals(1280, new CaptureSettings(context, startIntent()
                .putExtra(Prefs.PREF_VPN_MTU, "1280")).vpn_mtu);
        assertEquals(1400, new CaptureSettings(context, startIntent()
                .putExtra(Prefs.PREF_VPN_MTU, "invalid")).vpn_mtu);
        assertEquals(1400, new CaptureSettings(context, prefs).vpn_mtu);
    }

    @Test
    public void collectorHostSupportsLegacyIntentAndNewHostName() {
        Intent intent = startIntent().putExtra(Prefs.PREF_COLLECTOR_IP_KEY, "127.0.0.2");
        assertEquals("127.0.0.2", new CaptureSettings(context, intent).collector_address);

        intent.putExtra(Prefs.PREF_COLLECTOR_HOST_KEY, "collector.example.org");
        assertEquals("collector.example.org", new CaptureSettings(context, intent).collector_address);
    }
}
