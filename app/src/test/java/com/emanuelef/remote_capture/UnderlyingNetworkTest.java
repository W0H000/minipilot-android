/* This file is part of PCAPdroid, licensed under the GNU GPL v3 or later. */
package com.emanuelef.remote_capture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.robolectric.Shadows.shadowOf;

import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetwork;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UnderlyingNetworkTest {
    private static final String FALLBACK = "1.1.1.1";

    private UnderlyingNetwork.Candidate network(int id, int transport, boolean validated,
                                               String... dns) throws Exception {
        NetworkCapabilities caps = new NetworkCapabilities();
        shadowOf(caps).addTransportType(transport);
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if(transport == NetworkCapabilities.TRANSPORT_VPN)
            shadowOf(caps).removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        else
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        if(validated)
            shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        LinkProperties properties = new LinkProperties();
        List<InetAddress> addresses = new ArrayList<>();
        for(String address: dns)
            addresses.add(InetAddress.getByName(address));
        properties.setDnsServers(addresses);
        return new UnderlyingNetwork.Candidate(ShadowNetwork.newInstance(id), caps, properties);
    }

    private UnderlyingNetwork.Candidate select(Network active, UnderlyingNetwork.Candidate... networks) {
        return UnderlyingNetwork.select(Arrays.asList(networks), active, null);
    }

    @Test
    public void replacingClashUsesCarrierDnsAndIgnoresIms() throws Exception {
        UnderlyingNetwork.Candidate clash = network(110, NetworkCapabilities.TRANSPORT_VPN,
                true, "172.19.0.2");
        UnderlyingNetwork.Candidate ims = network(107, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "240e:5a::6666");
        shadowOf(ims.capabilities).removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "240e:5a::6666", "218.2.2.2", "218.4.4.4");

        UnderlyingNetwork.Candidate selected = select(clash.network, clash, ims, mobile);
        assertSame(mobile, selected);
        assertEquals("218.2.2.2", UnderlyingNetwork.dns(selected, true, FALLBACK));
    }

    @Test
    public void ownVpnCannotReplacePhysicalDnsAfterEstablish() throws Exception {
        UnderlyingNetwork.Candidate ownVpn = network(115, NetworkCapabilities.TRANSPORT_VPN,
                true, "10.215.173.2");
        // Reject VPN transport even if an OEM erroneously also reports NOT_VPN.
        shadowOf(ownVpn.capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN);
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        assertSame(mobile, select(ownVpn.network, ownVpn, mobile));
    }

    @Test
    public void activePhysicalNetworkWinsOverOtherValidatedTransports() throws Exception {
        UnderlyingNetwork.Candidate wifi = network(111, NetworkCapabilities.TRANSPORT_WIFI,
                true, "192.168.89.1");
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        assertSame(mobile, select(mobile.network, wifi, mobile));
    }

    @Test
    public void systemBestMatchingNetworkWinsOverIdleWifi() throws Exception {
        UnderlyingNetwork.Candidate wifi = network(111, NetworkCapabilities.TRANSPORT_WIFI,
                true, "192.168.89.1");
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        assertSame(mobile, UnderlyingNetwork.select(Arrays.asList(wifi, mobile),
                ShadowNetwork.newInstance(115), mobile.network));
    }

    @Test
    public void preAndroid12PrefersValidatedWifiButNotCaptiveWifi() throws Exception {
        UnderlyingNetwork.Candidate wifi = network(111, NetworkCapabilities.TRANSPORT_WIFI,
                true, "192.168.89.1");
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        Network vpn = ShadowNetwork.newInstance(115);
        assertSame(wifi, select(vpn, mobile, wifi));
        shadowOf(wifi.capabilities).removeCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        assertSame(mobile, select(vpn, wifi, mobile));
    }

    @Test
    public void networkHandoffAndRecoveryDoNotKeepOldPrivateDns() throws Exception {
        UnderlyingNetwork.Candidate wifi = network(111, NetworkCapabilities.TRANSPORT_WIFI,
                true, "192.168.89.1");
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        assertEquals("192.168.89.1", UnderlyingNetwork.dns(select(wifi.network, wifi, mobile), true, FALLBACK));
        assertEquals("218.2.2.2", UnderlyingNetwork.dns(select(mobile.network, mobile), true, FALLBACK));
        assertNull(select(null));
        assertEquals(FALLBACK, UnderlyingNetwork.dns(select(null), true, FALLBACK));
        assertEquals("192.168.89.1", UnderlyingNetwork.dns(select(wifi.network, wifi), true, FALLBACK));
    }

    @Test
    public void linkPropertiesDnsChangesArePickedUpWithoutNewNetworkId() throws Exception {
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        mobile.properties.setDnsServers(Collections.singletonList(InetAddress.getByName("218.4.4.4")));
        assertEquals("218.4.4.4", UnderlyingNetwork.dns(select(mobile.network, mobile), true, FALLBACK));
    }

    @Test
    public void manualDnsIsPreservedAndIpv6OnlyLinkUsesConfiguredFallback() throws Exception {
        UnderlyingNetwork.Candidate mobile = network(114, NetworkCapabilities.TRANSPORT_CELLULAR,
                true, "218.2.2.2");
        assertEquals("9.9.9.9", UnderlyingNetwork.dns(mobile, false, "9.9.9.9"));
        mobile.properties.setDnsServers(Collections.singletonList(InetAddress.getByName("240e:5a::6666")));
        assertEquals(FALLBACK, UnderlyingNetwork.dns(mobile, true, FALLBACK));
    }

    @Test
    public void missingCapabilitiesAndDnsDoNotSelectVpnOrCrash() throws Exception {
        UnderlyingNetwork.Candidate unknown = new UnderlyingNetwork.Candidate(
                ShadowNetwork.newInstance(114), null, null);
        assertNull(select(unknown.network, unknown));
        UnderlyingNetwork.Candidate noProperties = new UnderlyingNetwork.Candidate(
                unknown.network, network(115, NetworkCapabilities.TRANSPORT_WIFI, true).capabilities, null);
        assertEquals(FALLBACK, UnderlyingNetwork.dns(noProperties, true, FALLBACK));
    }
}
