/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.emanuelef.remote_capture;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import com.emanuelef.remote_capture.model.ApiKeyAuth;

public class ApiKeyValidationReceiver extends BroadcastReceiver {
    public static final String ACTION_VALIDATE_API_KEY =
            "com.emanuelef.remote_capture.action.VALIDATE_API_KEY";
    public static final String EXTRA_API_KEY = "api_key";
    public static final String EXTRA_PROTOCOL_VERSION = "protocol_version";
    public static final int PROTOCOL_VERSION = 1;

    public static final int RESULT_VALID = 100;
    public static final int RESULT_INVALID = 101;
    public static final int RESULT_NOT_CONFIGURED = 102;
    public static final int RESULT_MISSING = 103;
    public static final int RESULT_BAD_REQUEST = 104;

    public static final String DATA_VALID = "PCAPDROID_API_KEY_VALID";
    public static final String DATA_INVALID = "PCAPDROID_API_KEY_INVALID";
    public static final String DATA_NOT_CONFIGURED = "PCAPDROID_API_KEY_NOT_CONFIGURED";
    public static final String DATA_MISSING = "PCAPDROID_API_KEY_MISSING";
    public static final String DATA_BAD_REQUEST = "PCAPDROID_API_KEY_BAD_REQUEST";

    @Override
    public void onReceive(Context context, Intent intent) {
        if((intent == null) || !ACTION_VALIDATE_API_KEY.equals(intent.getAction())) {
            setValidationResult(RESULT_BAD_REQUEST, DATA_BAD_REQUEST);
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ApiKeyAuth.Result result = ApiKeyAuth.validate(prefs, intent.getStringExtra(EXTRA_API_KEY));

        switch(result) {
            case VALID:
                setValidationResult(RESULT_VALID, DATA_VALID);
                break;
            case INVALID:
                setValidationResult(RESULT_INVALID, DATA_INVALID);
                break;
            case NOT_CONFIGURED:
                setValidationResult(RESULT_NOT_CONFIGURED, DATA_NOT_CONFIGURED);
                break;
            case MISSING:
            default:
                setValidationResult(RESULT_MISSING, DATA_MISSING);
                break;
        }
    }

    private void setValidationResult(int code, String data) {
        Bundle extras = new Bundle();
        extras.putInt(EXTRA_PROTOCOL_VERSION, PROTOCOL_VERSION);
        setResult(code, data, extras);
    }
}
