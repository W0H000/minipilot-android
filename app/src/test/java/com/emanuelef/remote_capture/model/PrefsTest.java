/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.emanuelef.remote_capture.model;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class PrefsTest {
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.edit().clear().commit();
    }

    @Test
    public void vpnMtuDefaultsToStandardValue() {
        assertEquals(1500, Prefs.getVpnMtu(mPrefs));
    }

    @Test
    public void vpnMtuParsesConfiguredValue() {
        mPrefs.edit().putString(Prefs.PREF_VPN_MTU, "1500").commit();
        assertEquals(1500, Prefs.getVpnMtu(mPrefs));
    }

    @Test
    public void vpnMtuHandlesInvalidAndEmptyValues() {
        mPrefs.edit().putString(Prefs.PREF_VPN_MTU, "abc").commit();
        assertEquals(1500, Prefs.getVpnMtu(mPrefs));

        mPrefs.edit().putString(Prefs.PREF_VPN_MTU, "").commit();
        assertEquals(1500, Prefs.getVpnMtu(mPrefs));
    }

    @Test
    public void vpnMtuClampsOutOfRangeValues() {
        mPrefs.edit().putString(Prefs.PREF_VPN_MTU, "100").commit();
        assertEquals(1280, Prefs.getVpnMtu(mPrefs));

        mPrefs.edit().putString(Prefs.PREF_VPN_MTU, "50000").commit();
        assertEquals(10000, Prefs.getVpnMtu(mPrefs));
    }

    @Test
    public void vpnMtuHandlesLegacyIntegerValue() {
        mPrefs.edit().putInt(Prefs.PREF_VPN_MTU, 1500).commit();
        assertEquals(1500, Prefs.getVpnMtu(mPrefs));
    }

    @Test
    public void vpnMtuInvalidOverrideUsesProvidedFallback() {
        assertEquals(1500, Prefs.getVpnMtu("invalid", 1500));
        assertEquals(1280, Prefs.getVpnMtu("100", 1500));
        assertEquals(10000, Prefs.getVpnMtu("50000", 1500));
    }
}
