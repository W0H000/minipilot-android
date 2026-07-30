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
public class ApiKeyAuthTest {
    private SharedPreferences mPrefs;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        mPrefs.edit().clear().commit();
    }

    @Test
    public void validatesMatchingKey() {
        mPrefs.edit().putString(Prefs.PREF_API_KEY, "configured-key").commit();
        assertEquals(ApiKeyAuth.Result.VALID, ApiKeyAuth.validate(mPrefs, "configured-key"));
    }

    @Test
    public void rejectsWrongKey() {
        mPrefs.edit().putString(Prefs.PREF_API_KEY, "configured-key").commit();
        assertEquals(ApiKeyAuth.Result.INVALID, ApiKeyAuth.validate(mPrefs, "wrong-key"));
    }

    @Test
    public void reportsMissingRequestKey() {
        mPrefs.edit().putString(Prefs.PREF_API_KEY, "configured-key").commit();
        assertEquals(ApiKeyAuth.Result.MISSING, ApiKeyAuth.validate(mPrefs, null));
        assertEquals(ApiKeyAuth.Result.MISSING, ApiKeyAuth.validate(mPrefs, ""));
    }

    @Test
    public void reportsUnconfiguredPcapdroidKey() {
        assertEquals(ApiKeyAuth.Result.NOT_CONFIGURED,
                ApiKeyAuth.validate(mPrefs, "provided-key"));
    }
}
