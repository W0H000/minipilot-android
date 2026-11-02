/*
 * This file is part of PCAPdroid.
 *
 * PCAPdroid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PCAPdroid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PCAPdroid.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Copyright 2020-26 - Emanuele Faranda
 */

package com.emanuelef.remote_capture.activities.prefs;

import android.app.LocaleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.LocaleList;
import android.provider.Settings;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.DropDownPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import androidx.preference.SwitchPreference;

import com.emanuelef.remote_capture.Billing;
import com.emanuelef.remote_capture.CaptureService;
import com.emanuelef.remote_capture.Log;
import com.emanuelef.remote_capture.PCAPdroid;
import com.emanuelef.remote_capture.Utils;
import com.emanuelef.remote_capture.MitmAddon;
import com.emanuelef.remote_capture.VpnReconnectService;
import com.emanuelef.remote_capture.activities.BaseActivity;
import com.emanuelef.remote_capture.activities.MainActivity;
import com.emanuelef.remote_capture.activities.MitmSetupWizard;
import com.emanuelef.remote_capture.fragments.prefs.DnsSettings;
import com.emanuelef.remote_capture.fragments.prefs.GeoipSettings;
import com.emanuelef.remote_capture.fragments.prefs.Socks5Settings;
import com.emanuelef.remote_capture.interfaces.FragmentViewCreatedListener;
import com.emanuelef.remote_capture.model.Prefs;
import com.emanuelef.remote_capture.R;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Locale;

public class SettingsActivity extends BaseActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        FragmentManager.OnBackStackChangedListener,
        FragmentViewCreatedListener {
    private static final String TAG = "SettingsActivity";
    private static final String ACTION_LANG_RESTART = "lang_restart";
    public static final String TARGET_PREF_EXTRA = "target_pref";
    private WindowInsetsCompat mInsets = null;
    private OnBackPressedCallback mLangRestartCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.title_activity_settings); // note: setting via manifest does not honor custom locale
        displayBackAction();
        setContentView(R.layout.fragment_activity);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment, new SettingsFragment(), "root")
                .commit();

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        // After a locale change the parent activity stack is stale, so back must re-launch MainActivity.
        // Only relevant while the root SettingsFragment is showing — nested settings fragments pop normally.
        Intent intent = getIntent();
        if ((intent != null) && ACTION_LANG_RESTART.equals(intent.getAction())) {
            mLangRestartCallback = new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    Intent restart = new Intent(SettingsActivity.this, MainActivity.class);
                    restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(restart);
                    finish();
                }
            };
            getOnBackPressedDispatcher().addCallback(this, mLangRestartCallback);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@Nullable View parent, @NonNull String name, @NonNull Context context, @NonNull AttributeSet attrs) {
        View view = super.onCreateView(parent, name, context, attrs);
        if (view != null)
            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                mInsets = windowInsets;
                return windowInsets;
            });

        return view;
    }

    @Override
    public void onFragmentViewCreated(@NonNull View view) {
        // necessary, otherwise insets are not dispatched after fragment replace
        if (mInsets != null)
            ViewCompat.dispatchApplyWindowInsets(view, mInsets);
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller, @NonNull Preference pref) {
        PreferenceFragmentCompat targetFragment = null;
        String prefKey = pref.getKey();

        Log.d(TAG, "startFragment: " + prefKey);

        if(prefKey.equals("geolocation")) {
            targetFragment = new GeoipSettings();
            setTitle(R.string.geolocation);
        } else if(prefKey.equals("dns_settings")) {
            targetFragment = new DnsSettings();
            setTitle(R.string.dns_servers);
        } else if(prefKey.equals("socks5_settings")) {
            targetFragment = new Socks5Settings();
            setTitle(R.string.socks5_proxy);
        }

        if(targetFragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment, targetFragment, pref.getKey())
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .addToBackStack(pref.getKey())
                    .commit();
            return true;
        }

        return false;
    }

    @Override
    public void onBackStackChanged() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.fragment);
        boolean atRoot = f instanceof SettingsFragment;

        if(atRoot) {
            setTitle(R.string.title_activity_settings);

            var view = f.getView();
            if ((mInsets != null) && (view != null))
                ViewCompat.dispatchApplyWindowInsets(view, mInsets);
        }

        if (mLangRestartCallback != null)
            mLangRestartCallback.setEnabled(atRoot);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private SwitchPreference mTlsDecryption;
        private SwitchPreference mFullPayloadEnabled;
        private SwitchPreference mRootCaptureEnabled;
        private SwitchPreference mAutoBlockPrivateDNS;
        private EditTextPreference mMitmproxyOpts;
        private DropDownPreference mIpMode;
        private DropDownPreference mCapInterface;
        private DropDownPreference mBlockQuic;
        private DropDownPreference mConnectionsLogSize;
        private Preference mVpnExceptions;
        private Preference mSocks5Settings;
        private Preference mDnsSettings;
        private Preference mPortMapping;
        private Preference mMitmWizard;
        private SwitchPreference mMalwareDetectionEnabled;
        private SwitchPreference mPcapngEnabled;
        private SwitchPreference mDumpExtensions;
        private SwitchPreference mRestartOnDisconnect;
        private Billing mIab;
        private SettingsBackupHandler mBackupHandler;
        private boolean mHasStartedMitmWizard;
        private boolean mRootDecryptionNoticeShown = false;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);
            mIab = Billing.newInstance(requireContext());
            mBackupHandler = new SettingsBackupHandler(this);

            setupExporterPrefs();
            setupHttpServerPrefs();
            setupTrafficInspectionPrefs();
            setupCapturePrefs();
            setupSecurityPrefs();
            setupOtherPrefs();
            setupBackupPrefs();

            socks5ProxyHideShow(mTlsDecryption.isChecked(), rootCaptureEnabled());
            mBlockQuic.setVisible(!rootCaptureEnabled());
            rootCaptureHideShow(rootCaptureEnabled());

            Intent intent = requireActivity().getIntent();
            if(intent != null) {
                String target_pref = intent.getStringExtra(TARGET_PREF_EXTRA);
                if(target_pref != null)
                    scrollToPreference(target_pref);
            }
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onViewCreated(view, savedInstanceState);

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() |
                        WindowInsetsCompat.Type.displayCutout());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);

                return WindowInsetsCompat.CONSUMED;
            });
        }

        @Override
        public boolean onPreferenceTreeClick(@NonNull Preference preference) {
            Intent intent = preference.getIntent();
            if(intent != null) {
                Utils.startActivity(requireContext(), intent);
                return true;
            }

            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onResume() {
            super.onResume();

            if(mHasStartedMitmWizard && !MitmAddon.needsSetup(requireContext())) {
                Log.d(TAG, "mitm setup complete, enabling");
                mTlsDecryption.setChecked(true);
                mFullPayloadEnabled.setChecked(true);
            }
            mHasStartedMitmWizard = false;
        }

        private @NonNull <T extends Preference> T requirePreference(String key) {
            T pref = findPreference(key);
            if(pref == null)
                throw new IllegalStateException();
            return pref;
        }

        @SuppressWarnings("deprecation")
        private void setupExporterPrefs() {
            /* Collector host validation (IP address or domain name) */
            EditTextPreference mRemoteCollectorHost = requirePreference(Prefs.PREF_COLLECTOR_HOST_KEY);
            mRemoteCollectorHost.setOnPreferenceChangeListener((preference, newValue) -> Utils.validateHostOrIp(newValue.toString()));

            /* Collector port validation */
            EditTextPreference mRemoteCollectorPort = requirePreference(Prefs.PREF_COLLECTOR_PORT_KEY);
            mRemoteCollectorPort.setOnBindEditTextListener(editText -> editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED));
            mRemoteCollectorPort.setOnPreferenceChangeListener((preference, newValue) -> Utils.validatePort(newValue.toString()));
        }

        private void setupHttpServerPrefs() {
            /* HTTP Server port validation */
            EditTextPreference mHttpServerPort = requirePreference(Prefs.PREF_HTTP_SERVER_PORT);
            mHttpServerPort.setOnPreferenceChangeListener((preference, newValue) -> Utils.validatePort(newValue.toString()));
        }

        private boolean rootCaptureEnabled() {
            return Utils.isRootAvailable() && mRootCaptureEnabled.isChecked();
        }

        private boolean isPcapngEnabled() {
            return mIab.isPurchased(Billing.PCAPNG_SKU) && mPcapngEnabled.isChecked();
        }

        private void dumpExtensionsHideShow(boolean pcapngEnabled) {
            // the PCAPdroid extensions are implicitly enabled with the pcapng format
            mDumpExtensions.setVisible(!pcapngEnabled);
        }

        private void refreshInterfaces() {
            ArrayList<String> labels = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();

            labels.add(getString(R.string.internet));
            values.add("@inet");
            labels.add(getString(R.string.all_interfaces));
            values.add("any");

            try {
                Enumeration<NetworkInterface> ifaces = Utils.getNetworkInterfaces();

                while (ifaces.hasMoreElements()) {
                    NetworkInterface iface = ifaces.nextElement();
                    if(!iface.isUp())
                        continue;

                    String name = iface.getName();
                    labels.add(name);
                    values.add(name);
                }
            } catch (SocketException e) {
                e.printStackTrace();
            }

            mCapInterface.setEntryValues(values.toArray(new String[0]));
            mCapInterface.setEntries(labels.toArray(new String[0]));
        }

        private void setupCapturePrefs() {
            mCapInterface = requirePreference(Prefs.PREF_CAPTURE_INTERFACE);
            refreshInterfaces();

            Preference vpnMtu = requirePreference(Prefs.PREF_VPN_MTU);
            updateVpnMtuSummary(vpnMtu);
            vpnMtu.setOnPreferenceClickListener(preference -> {
                showVpnMtuDialog(preference);
                return true;
            });

            mRootCaptureEnabled = requirePreference(Prefs.PREF_ROOT_CAPTURE);
            if(Utils.isRootAvailable()) {
                mRootCaptureEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                    rootCaptureHideShow((Boolean) newValue);
                    return checkDecrpytionWithRoot((Boolean) newValue, mTlsDecryption.isChecked());
                });
            } else
                mRootCaptureEnabled.setVisible(false);

            mRestartOnDisconnect = requirePreference(Prefs.PREF_RESTART_ON_DISCONNECT);
            mRestartOnDisconnect.setVisible(VpnReconnectService.isAvailable());

            mDnsSettings = requirePreference("dns_settings");;
            mVpnExceptions = requirePreference(Prefs.PREF_VPN_EXCEPTIONS);
            mVpnExceptions.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), VpnExemptionsActivity.class);
                startActivity(intent);
                return true;
            });
        }

        private void updateVpnMtuSummary(Preference preference) {
            int mtu = Prefs.getVpnMtu(PreferenceManager.getDefaultSharedPreferences(requireContext()));

            if(mtu == 1500)
                preference.setSummary(R.string.vpn_mtu_summary_standard);
            else if(mtu == 1280)
                preference.setSummary(R.string.vpn_mtu_summary_compatibility);
            else if(mtu == 10000)
                preference.setSummary(R.string.vpn_mtu_summary_throughput);
            else
                preference.setSummary(getString(R.string.vpn_mtu_summary_custom, mtu));
        }

        private void showVpnMtuDialog(Preference preference) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
            int currentMtu = Prefs.getVpnMtu(prefs);
            int checkedItem;

            if(currentMtu == 1500)
                checkedItem = 0;
            else if(currentMtu == 1280)
                checkedItem = 1;
            else if(currentMtu == 10000)
                checkedItem = 2;
            else
                checkedItem = 3;

            CharSequence[] choices = {
                    getText(R.string.vpn_mtu_option_standard),
                    getText(R.string.vpn_mtu_option_compatibility),
                    getText(R.string.vpn_mtu_option_throughput),
                    getString(R.string.vpn_mtu_option_custom, currentMtu)
            };
            int[] selectedItem = {checkedItem};
            TextView notice = new TextView(requireContext());
            notice.setText(R.string.vpn_mtu_change_notice);
            TextViewCompat.setTextAppearance(notice, android.R.style.TextAppearance_Material_Subhead);

            if(CaptureService.isServiceActive()) {
                SpannableString activeNotice = new SpannableString(
                        getText(R.string.vpn_mtu_change_notice_active));
                activeNotice.setSpan(new ForegroundColorSpan(
                                ContextCompat.getColor(requireContext(), R.color.warning)),
                        0, activeNotice.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                notice.setText(activeNotice);
            }

            float density = getResources().getDisplayMetrics().density;
            int horizontalPadding = (int) (24 * density);
            int verticalPadding = (int) (12 * density);
            int optionPadding = (int) (8 * density);
            LinearLayout content = new LinearLayout(requireContext());
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0);
            notice.setPadding(0, 0, 0, verticalPadding);
            content.addView(notice);

            RadioGroup options = new RadioGroup(requireContext());
            int[] optionIds = new int[choices.length];
            for(int i = 0; i < choices.length; i++) {
                RadioButton option = new RadioButton(requireContext());
                optionIds[i] = View.generateViewId();
                option.setId(optionIds[i]);
                option.setText(choices[i]);
                option.setPadding(0, optionPadding, 0, optionPadding);
                options.addView(option);
            }
            content.addView(options);

            EditText customInput = new EditText(requireContext());
            customInput.setInputType(InputType.TYPE_CLASS_NUMBER);
            customInput.setHint(R.string.vpn_mtu_custom_hint);
            customInput.setText(Integer.toString(currentMtu));
            customInput.setSelectAllOnFocus(true);
            customInput.setSingleLine(true);
            customInput.setVisibility(checkedItem == 3 ? View.VISIBLE : View.GONE);
            content.addView(customInput);

            options.check(optionIds[checkedItem]);
            options.setOnCheckedChangeListener((group, checkedId) -> {
                for(int i = 0; i < optionIds.length; i++) {
                    if(optionIds[i] != checkedId)
                        continue;

                    selectedItem[0] = i;
                    boolean isCustom = (i == 3);
                    customInput.setVisibility(isCustom ? View.VISIBLE : View.GONE);
                    if(isCustom) {
                        customInput.post(() -> {
                            customInput.requestFocus();
                            customInput.selectAll();
                            InputMethodManager keyboard = ContextCompat.getSystemService(
                                    requireContext(), InputMethodManager.class);
                            if(keyboard != null)
                                keyboard.showSoftInput(customInput, InputMethodManager.SHOW_IMPLICIT);
                        });
                    }
                    break;
                }
            });

            ScrollView scrollView = new ScrollView(requireContext());
            scrollView.setFillViewport(true);
            scrollView.addView(content);

            AlertDialog dialog = new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.vpn_mtu_dialog_title)
                    .setView(scrollView)
                    .setNegativeButton(R.string.cancel_action, null)
                    .setPositiveButton(R.string.ok, null)
                    .create();
            dialog.setOnShowListener(ignored ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                        int selected = selectedItem[0];
                        int mtu;

                        if(selected == 3) {
                            try {
                                mtu = Integer.parseInt(customInput.getText().toString());
                            } catch(NumberFormatException e) {
                                customInput.setError(getString(R.string.vpn_mtu_invalid));
                                return;
                            }

                            if((mtu < Prefs.MIN_VPN_MTU) || (mtu > Prefs.MAX_VPN_MTU)) {
                                customInput.setError(getString(R.string.vpn_mtu_invalid));
                                return;
                            }
                        } else {
                            mtu = (selected == 0) ? 1500 : ((selected == 1) ? 1280 : 10000);
                        }

                        prefs.edit()
                                .putString(Prefs.PREF_VPN_MTU, Integer.toString(mtu))
                                .apply();
                        updateVpnMtuSummary(preference);
                        dialog.dismiss();
                    }));
            dialog.show();
        }

        private void setupSecurityPrefs() {
            mMalwareDetectionEnabled = requirePreference(Prefs.PREF_MALWARE_DETECTION);

            if(!mIab.isPurchased(Billing.MALWARE_DETECTION_SKU) && !mIab.isAvailable(Billing.MALWARE_DETECTION_SKU)) {
                getPreferenceScreen().removePreference(requirePreference("security"));
                return;
            }

            // Billing code here
        }

        @SuppressWarnings("deprecation")
        private void setupTrafficInspectionPrefs() {
            mAutoBlockPrivateDNS = requirePreference("auto_block_private_dns");

            mTlsDecryption = requirePreference(Prefs.PREF_TLS_DECRYPTION_KEY);
            if(Utils.isPlaystore() && !MitmAddon.isInstalled(requireContext())) {
                // the addon cannot be installed from the app, see InstallAddon
                mTlsDecryption.setChecked(false);
                mTlsDecryption.setVisible(false);
            } else if(!MitmAddon.isSupportedTarget()) {
                // The mitm addon dropped support for armv7/x86 and Android < 7
                mTlsDecryption.setChecked(false);
                mTlsDecryption.setEnabled(false);
                mTlsDecryption.setSummary(R.string.tls_decryption_unsupported_target);
            } else
                mTlsDecryption.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean enabled = (boolean) newValue;
                    Context ctx = requireContext();

                    if(!checkDecrpytionWithRoot(rootCaptureEnabled(), (boolean) newValue))
                        return false;

                    if(enabled && MitmAddon.needsSetup(ctx)) {
                        mHasStartedMitmWizard = true;
                        Intent intent = new Intent(ctx, MitmSetupWizard.class);
                        startActivity(intent);
                        return false;
                    }

                    mMitmWizard.setVisible((boolean) newValue);
                    mMitmproxyOpts.setVisible((boolean) newValue);
                    socks5ProxyHideShow((boolean) newValue, rootCaptureEnabled());
                    return true;
                });

            mPcapngEnabled = requirePreference(Prefs.PREF_PCAPNG_ENABLED);
            mDumpExtensions = requirePreference(Prefs.PREF_DUMP_EXTENSIONS);

            if(mIab.isPurchased(Billing.PCAPNG_SKU) || mIab.isAvailable(Billing.PCAPNG_SKU)) {
                mPcapngEnabled.setOnPreferenceClickListener((preference -> {
                    // Billing code here

                    return false;
                }));
                if(!mIab.isPurchased(Billing.PCAPNG_SKU))
                    mPcapngEnabled.setChecked(false);
            } else
                mPcapngEnabled.setVisible(false);

            mPcapngEnabled.setOnPreferenceChangeListener((preference, newValue) -> {
                dumpExtensionsHideShow((boolean) newValue);
                return true;
            });

            dumpExtensionsHideShow(isPcapngEnabled());

            mFullPayloadEnabled = requirePreference(Prefs.PREF_FULL_PAYLOAD);
            mBlockQuic = requirePreference(Prefs.PREF_BLOCK_QUIC);
            mMitmproxyOpts = requirePreference(Prefs.PREF_MITMPROXY_OPTS);
            mMitmproxyOpts.setVisible(mTlsDecryption.isChecked());
            mMitmWizard = requirePreference("mitm_setup_wizard");
            mMitmWizard.setVisible(mTlsDecryption.isChecked());
            mMitmWizard.setOnPreferenceClickListener(preference -> {
                mHasStartedMitmWizard = true;
                Intent intent = new Intent(requireContext(), MitmSetupWizard.class);
                startActivity(intent);
                return true;
            });

            mSocks5Settings = requirePreference("socks5_settings");

            setupConnectionsLogSizePref();
        }

        private void setupConnectionsLogSizePref() {
            mConnectionsLogSize = requirePreference(Prefs.PREF_CONNECTIONS_LOG_SIZE);

            int maxSupported = Prefs.getMaxConnectionsLogSize();
            ArrayList<CharSequence> labels = new ArrayList<>();
            ArrayList<CharSequence> values = new ArrayList<>();

            for (int v = Prefs.MIN_CONNECTIONS_LOG_SIZE; v <= maxSupported; v <<= 1) {
                int mb = (int) (((long) v * 2 /* KB per conn */) / 1024);
                labels.add(getString(R.string.connections_log_size_entry, Integer.toString(v), mb));
                values.add(Integer.toString(v));
            }

            mConnectionsLogSize.setEntries(labels.toArray(new CharSequence[0]));
            mConnectionsLogSize.setEntryValues(values.toArray(new CharSequence[0]));

            // Clamp the stored value if the device-supported max shrank (e.g. moved profile / smaller heap).
            String current = mConnectionsLogSize.getValue();
            if ((current == null) || !values.contains(current))
                mConnectionsLogSize.setValue(Integer.toString(Prefs.getConnectionsLogSize(
                        PreferenceManager.getDefaultSharedPreferences(requireContext()))));
        }

        private void socks5ProxyHideShow(boolean tlsDecryption, boolean rootEnabled) {
            mSocks5Settings.setVisible(!tlsDecryption && !rootEnabled);
        }

        private void setupAppLanguagePref() {
            DropDownPreference appLang = requirePreference(Prefs.PREF_APP_LANGUAGE);
            Preference appLangExternal = requirePreference("app_language_external");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // On Android 33+, app language is configurable from the system settings
                appLang.setVisible(false);
                appLangExternal.setVisible(true);

                LocaleList locales = requireContext().getSystemService(LocaleManager.class)
                        .getApplicationLocales();
                if (locales.equals(LocaleList.getEmptyLocaleList()))
                    appLangExternal.setSummary(getString(R.string.system_default));
                else if (!locales.isEmpty())
                    appLangExternal.setSummary(locales.get(0).getDisplayName());

                appLangExternal.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Settings.ACTION_APP_LOCALE_SETTINGS);
                    intent.setData(Uri.fromParts("package", requireContext().getPackageName(), null));
                    startActivity(intent);
                    return true;
                });
            } else {
                // Populate supported languages dynamically from locales_config.xml
                String[] supportedLocales = Utils.getSupportedLocales(requireContext());
                String[] entryValues = new String[supportedLocales.length + 1];
                CharSequence[] entryLabels = new CharSequence[supportedLocales.length + 1];

                entryValues[0] = "system";
                entryLabels[0] = getString(R.string.system_default);

                for (int i = 0; i < supportedLocales.length; i++) {
                    Locale locale = Locale.forLanguageTag(supportedLocales[i]);
                    entryValues[i + 1] = supportedLocales[i];
                    String name = locale.getDisplayName(locale);
                    entryLabels[i + 1] = name.substring(0, 1).toUpperCase(locale) + name.substring(1);
                }

                appLang.setEntries(entryLabels);
                appLang.setEntryValues(entryValues);

                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

                if (SettingsActivity.ACTION_LANG_RESTART.equals(requireActivity().getIntent().getAction()))
                    scrollToPreference(appLang);

                // Current locale applied via BaseActivity.attachBaseContext
                appLang.setOnPreferenceChangeListener((preference, newValue) -> {
                    if (prefs.edit().putString(Prefs.PREF_APP_LANGUAGE, newValue.toString()).commit()) {
                        // Restart the activity to apply the language change
                        Intent intent = new Intent(requireContext(), SettingsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        intent.setAction(SettingsActivity.ACTION_LANG_RESTART);
                        startActivity(intent);

                        Runtime.getRuntime().exit(0);
                    }

                    return false;
                });
            }
        }

        private void setupOtherPrefs() {
            setupAppLanguagePref();

            mPortMapping = requirePreference(Prefs.PREF_PORT_MAPPING);
            mPortMapping.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), PortMapActivity.class);
                startActivity(intent);
                return true;
            });

            mIpMode = requirePreference(Prefs.PREF_IP_MODE);

            Preference ctrlPerm = requirePreference("control_permissions");
            ctrlPerm.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), EditCtrlPermissions.class);
                startActivity(intent);
                return true;
            });
        }

        private void setupBackupPrefs() {
            Preference exportSettings = requirePreference("export_settings");
            exportSettings.setOnPreferenceClickListener(preference -> {
                mBackupHandler.startExport();
                return true;
            });

            Preference importSettings = requirePreference("import_settings");
            importSettings.setOnPreferenceClickListener(preference -> {
                mBackupHandler.startImport();
                return true;
            });
        }

        private void rootCaptureHideShow(boolean enabled) {
            if(enabled) {
                mAutoBlockPrivateDNS.setVisible(false);
                mBlockQuic.setVisible(false);
                mSocks5Settings.setVisible(false);
            } else {
                mAutoBlockPrivateDNS.setVisible(true);
                mBlockQuic.setVisible(true);
                socks5ProxyHideShow(mTlsDecryption.isChecked(), false);
            }

            if (VpnReconnectService.isAvailable())
                mRestartOnDisconnect.setVisible(!enabled);

            mIpMode.setVisible(!enabled);
            mCapInterface.setVisible(enabled);
            mVpnExceptions.setVisible(!enabled);
            mDnsSettings.setVisible(!enabled);
            mPortMapping.setVisible(!enabled);
        }

        private boolean checkDecrpytionWithRoot(boolean rootEnabled, boolean tlsDecryption) {
            if(mRootDecryptionNoticeShown || !rootEnabled || !tlsDecryption)
                return true;

            new AlertDialog.Builder(requireContext())
                    .setMessage(R.string.tls_decryption_with_root_msg)
                    .setPositiveButton(R.string.ok, (dialog, whichButton) -> {
                        mRootCaptureEnabled.setChecked(true);
                        mTlsDecryption.setChecked(true);

                        mRootDecryptionNoticeShown = true;
                    })
                    .show();

            return false;
        }
    }
}
