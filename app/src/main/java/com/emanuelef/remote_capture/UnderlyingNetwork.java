/*
 * This file is part of PCAPdroid, licensed under the GNU GPL v3 or later.
 */
package com.emanuelef.remote_capture;

import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Select the real Internet network without inheriting a VPN's private DNS. */
@RequiresApi(Build.VERSION_CODES.M)
final class UnderlyingNetwork {
    static final class Candidate {
        final Network network;
        final NetworkCapabilities capabilities;
        final LinkProperties properties;

        Candidate(Network network, NetworkCapabilities capabilities, LinkProperties properties) {
            this.network = network;
            this.capabilities = capabilities;
            this.properties = properties;
        }
    }

    @Nullable
    static Candidate find(ConnectivityManager cm, Set<Network> lostNetworks, Network preferred) {
        Network active = cm.getActiveNetwork();
        List<Candidate> candidates = new ArrayList<>();
        for(Network network: cm.getAllNetworks()) {
            if(!lostNetworks.contains(network))
                candidates.add(new Candidate(network, cm.getNetworkCapabilities(network),
                        cm.getLinkProperties(network)));
        }
        return select(candidates, active, preferred);
    }

    @Nullable
    static Candidate select(List<Candidate> candidates, Network active, Network preferred) {
        Candidate selected = null;
        int bestScore = -1;
        for(Candidate candidate: candidates) {
            NetworkCapabilities caps = candidate.capabilities;
            // IMS networks and both the old VPN and our own VPN must never supply DNS.
            if(caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN))
                continue;

            int score = 0;
            if(candidate.network.equals(active))
                score += 10000;
            if(candidate.network.equals(preferred))
                score += 1000;
            if(caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                score += 100;
            // Before Android 12 provides best-matching callbacks, prefer a validated
            // Ethernet/WiFi connection over a simultaneously connected cellular network.
            if(caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
                score += 30;
            else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                score += 20;
            else if(caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                score += 10;

            if(score > bestScore) {
                selected = candidate;
                bestScore = score;
            }
        }
        return selected;
    }

    static String dns(Candidate selected, boolean useSystemDns, String fallback) {
        if(useSystemDns && selected != null && selected.properties != null) {
            for(InetAddress address: selected.properties.getDnsServers()) {
                // The native IPv4 DNS forwarder cannot use an IPv6 resolver. Private IPv4
                // resolvers are valid on carrier/WiFi networks, so do not filter by range.
                if(address instanceof Inet4Address && !address.isAnyLocalAddress()
                        && !address.isLoopbackAddress() && !address.isMulticastAddress())
                    return address.getHostAddress();
            }
        }
        return fallback;
    }
}
