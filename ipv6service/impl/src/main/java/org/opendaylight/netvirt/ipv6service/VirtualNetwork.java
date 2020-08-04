/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.ipv6util.api.Ipv6Util;
import org.opendaylight.netvirt.ipv6service.api.IVirtualNetwork;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6ServiceConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yangtools.yang.common.Uint64;

public class VirtualNetwork implements IVirtualNetwork {
    private final Uuid networkUUID;
    private final ConcurrentMap<Uint64, DpnInterfaceInfo> dpnIfaceList = new ConcurrentHashMap<>();
    private volatile Long elanTag;
    private volatile int mtu = 0;

    public VirtualNetwork(Uuid networkUUID) {
        this.networkUUID = networkUUID;
    }

    @Override
    public Uuid getNetworkUuid() {
        return networkUUID;
    }

    public void updateDpnPortInfo(Uint64 dpnId, Long ofPort,Uuid portId, int addOrRemove) {
        if (dpnId == null) {
            return;
        }
        synchronized (networkUUID.getValue()) {
            DpnInterfaceInfo dpnInterface = dpnIfaceList.computeIfAbsent(dpnId, key -> new DpnInterfaceInfo(dpnId));
            if (addOrRemove == Ipv6ServiceConstants.ADD_ENTRY) {
                dpnInterface.updateofPortMap(ofPort, portId);
            } else {
                dpnInterface.removeOfPortFromMap(ofPort);
            }
        }
    }

    @Override
    public Long getElanTag() {
        return elanTag;
    }

    public void setElanTag(Long etag) {
        elanTag = etag;
    }

    @Override
    public List<Uint64> getDpnsHostingNetwork() {
        return dpnIfaceList.values().stream().flatMap(dpnInterfaceInfo -> Stream.of(dpnInterfaceInfo.getDpId()))
                .collect(Collectors.toList());
    }

    public Collection<DpnInterfaceInfo> getDpnIfaceList() {
        return dpnIfaceList.values();
    }

    @Nullable
    public DpnInterfaceInfo getDpnIfaceInfo(Uint64 dpId) {
        return dpId != null ? dpnIfaceList.get(dpId) : null;
    }

    public void setRSPuntFlowStatusOnDpnId(Uint64 dpnId, int action) {
        DpnInterfaceInfo dpnInterface = getDpnIfaceInfo(dpnId);
        if (null != dpnInterface) {
            dpnInterface.setRsFlowConfiguredStatus(action);
        }
    }

    public int getRSPuntFlowStatusOnDpnId(Uint64 dpnId) {
        DpnInterfaceInfo dpnInterface = getDpnIfaceInfo(dpnId);
        if (null != dpnInterface) {
            return dpnInterface.getRsFlowConfiguredStatus();
        }
        return Ipv6ServiceConstants.FLOWS_NOT_CONFIGURED;
    }

    public void clearDpnInterfaceList() {
        dpnIfaceList.clear();
    }

    @Override
    public String toString() {
        return "VirtualNetwork [networkUUID=" + networkUUID + " dpnIfaceList=" + dpnIfaceList + "]";
    }

    public void removeSelf() {
        synchronized (networkUUID.getValue()) {
            dpnIfaceList.values().forEach(dpnInterfaceInfo -> {
                dpnInterfaceInfo.clearPortList();
                dpnInterfaceInfo.clearNdTargetFlowInfo();
                dpnInterfaceInfo.clearsubnetCidrPuntFlowInfo();
                dpnInterfaceInfo.clearOvsNaResponderFlowConfigured();
            });

            clearDpnInterfaceList();
        }
    }

    public void setMtu(int mtu) {
        this.mtu = mtu;
    }

    @Override
    public int getMtu() {
        return this.mtu;
    }

    public static class DpnInterfaceInfo {
        private Uint64 dpId;
        private int rsPuntFlowConfigured = Ipv6ServiceConstants.FLOWS_NOT_CONFIGURED;
        private final Set<Uuid> subnetCidrPuntFlowList = ConcurrentHashMap.newKeySet();
        final Set<Ipv6Address> ndTargetFlowsPunted = ConcurrentHashMap.newKeySet();
        final ConcurrentMap<Long, Uuid> ofPortMap = new ConcurrentHashMap<>();
        private final ConcurrentMap<Uuid, Integer> ovsNaResponderFlowConfigured = new ConcurrentHashMap<>();

        DpnInterfaceInfo(Uint64 dpnId) {
            dpId = dpnId;
        }

        public void setDpId(Uint64 dpId) {
            this.dpId = dpId;
        }

        public Uint64 getDpId() {
            return dpId;
        }

        public void setRsFlowConfiguredStatus(int status) {
            this.rsPuntFlowConfigured = status;
        }

        public int getRsFlowConfiguredStatus() {
            return rsPuntFlowConfigured;
        }

        public void updateSubnetCidrFlowStatus(Uuid subnetUUID, int addOrRemove) {
            if (addOrRemove == Ipv6ServiceConstants.ADD_FLOW) {
                this.subnetCidrPuntFlowList.add(subnetUUID);
            } else {
                this.subnetCidrPuntFlowList.remove(subnetUUID);
            }
        }

        public boolean isSubnetCidrFlowAlreadyConfigured(Uuid subnetUUID) {
            return subnetCidrPuntFlowList.contains(subnetUUID);
        }

        public Set<Ipv6Address> getNDTargetFlows() {
            return ndTargetFlowsPunted;
        }

        public void updateNDTargetAddress(Ipv6Address ipv6Address, int addOrRemove) {
            Ipv6Address ipv6 = Ipv6Address.getDefaultInstance(Ipv6Util.getFormattedIpv6Address(ipv6Address));
            if (addOrRemove == Ipv6ServiceConstants.ADD_ENTRY) {
                this.ndTargetFlowsPunted.add(ipv6);
            } else {
                this.ndTargetFlowsPunted.remove(ipv6);
            }
        }

        public boolean isNdTargetFlowAlreadyConfigured(Ipv6Address ipv6Address) {
            Ipv6Address ipv6 = Ipv6Address.getDefaultInstance(Ipv6Util.getFormattedIpv6Address(ipv6Address));
            return this.ndTargetFlowsPunted.contains(ipv6);
        }

        public void clearNdTargetFlowInfo() {
            this.ndTargetFlowsPunted.clear();
        }

        public void setOvsNaResponderFlowConfiguredStatus(Uuid interfaceName, int lportTag, int addOrRemove) {
            if (addOrRemove == Ipv6ServiceConstants.ADD_ENTRY) {
                this.ovsNaResponderFlowConfigured.put(interfaceName, lportTag);
            } else {
                this.ovsNaResponderFlowConfigured.remove(interfaceName);
            }
        }

        public void clearOvsNaResponderFlowConfigured() {
            this.ovsNaResponderFlowConfigured.clear();
        }

        public void updateofPortMap(Long ofPort, Uuid portId) {
            this.ofPortMap.put(ofPort, portId);
        }

        public void removeOfPortFromMap(Long ofPort) {
            this.ofPortMap.remove(ofPort);
        }

        public void clearPortList() {
            this.ofPortMap.clear();
        }

        public void clearsubnetCidrPuntFlowInfo() {
            this.subnetCidrPuntFlowList.clear();
        }

        @Override
        public String toString() {
            return "DpnInterfaceInfo [dpId=" + dpId + " rsPuntFlowConfigured=" + rsPuntFlowConfigured
                    + "subnetCidrPuntFlowList=" + subnetCidrPuntFlowList + " ofPortMap ="
                    + ofPortMap  + "]";
        }
    }
}
