/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.InterfaceMonitorMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorConfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfiles;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitoridKeyMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitoringStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629._interface.monitor.map.InterfaceMonitorEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629._interface.monitor.map.InterfaceMonitorEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profiles.MonitorProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profiles.MonitorProfileKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitorid.key.map.MonitoridKeyEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitorid.key.map.MonitoridKeyEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitoring.states.MonitoringState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitoring.states.MonitoringStateKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import com.google.common.primitives.UnsignedBytes;

public class AlivenessMonitorUtil {

    static InstanceIdentifier<MonitoringState> getMonitorStateId(String keyId) {
        return InstanceIdentifier.builder(MonitoringStates.class)
                .child(MonitoringState.class, new MonitoringStateKey(keyId)).build();
    }

    static InstanceIdentifier<MonitoringInfo> getMonitoringInfoId(Long monitorId) {
        return InstanceIdentifier.builder(MonitorConfigs.class)
                .child(MonitoringInfo.class, new MonitoringInfoKey(monitorId)).build();
    }

    static InstanceIdentifier<MonitorProfile> getMonitorProfileId(Long profileId) {
        return InstanceIdentifier.builder(MonitorProfiles.class)
                .child(MonitorProfile.class, new MonitorProfileKey(profileId)).build();
    }

    static InstanceIdentifier<MonitoridKeyEntry> getMonitorMapId(Long keyId) {
        return InstanceIdentifier.builder(MonitoridKeyMap.class)
                .child(MonitoridKeyEntry.class, new MonitoridKeyEntryKey(keyId)).build();
    }

    static InstanceIdentifier<InterfaceMonitorEntry> getInterfaceMonitorMapId(String interfaceName) {
        return InstanceIdentifier.builder(InterfaceMonitorMap.class)
                .child(InterfaceMonitorEntry.class, new InterfaceMonitorEntryKey(interfaceName)).build();
    }

    public static String toStringIpAddress(byte[] ipAddress)
    {
        String ip = "";
        if (ipAddress == null) {
            return ip;
        }

        try {
            ip = InetAddress.getByAddress(ipAddress).getHostAddress();
        } catch(UnknownHostException e) {  }

        return ip;
    }

    public static String toStringMacAddress(byte[] macAddress)
    {
        if (macAddress == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder(18);

        for (int i = 0; i < macAddress.length; i++) {
            sb.append(UnsignedBytes.toString(macAddress[i], 16).toUpperCase());
            sb.append(":");
        }

        sb.setLength(17);
        return sb.toString();
    }

    public static byte[] parseIpAddress(String ipAddress) {
        byte cur;

        String[] addressPart = ipAddress.split(".");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }

    public static byte[] parseMacAddress(String macAddress) {
        byte cur;

        String[] addressPart = macAddress.split(":");
        int size = addressPart.length;

        byte[] part = new byte[size];
        for (int i = 0; i < size; i++) {
            cur = UnsignedBytes.parseUnsignedByte(addressPart[i], 16);
            part[i] = cur;
        }

        return part;
    }
}
