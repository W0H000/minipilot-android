/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.emanuelef.remote_capture.model;

import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class ApiKeyAuth {
    public enum Result {
        VALID,
        INVALID,
        NOT_CONFIGURED,
        MISSING
    }

    private ApiKeyAuth() {}

    public static Result validate(SharedPreferences prefs, @Nullable String providedKey) {
        if((providedKey == null) || providedKey.isEmpty())
            return Result.MISSING;

        String configuredKey = Prefs.getApiKey(prefs);
        if(configuredKey.isEmpty())
            return Result.NOT_CONFIGURED;

        boolean matches = MessageDigest.isEqual(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                providedKey.getBytes(StandardCharsets.UTF_8));
        return matches ? Result.VALID : Result.INVALID;
    }
}
