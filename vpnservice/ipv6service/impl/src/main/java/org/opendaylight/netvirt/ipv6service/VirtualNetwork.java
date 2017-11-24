/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.ipv6service;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.opendaylight.netvirt.ipv6service.api.IVirtualNetwork;
import org.opendaylight.netvirt.ipv6service.utils.Ipv6Constants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class VirtualNetwork implements IVirtualNetwork {
    private final Uuid networkUUID;
    private final ConcurrentMap<BigInteger, DpnInterfaceInfo> dpnIfaceList = new ConcurrentHashMap<>();
    private volatile Long elanTag;

    public VirtualNetwork(Uuid networkUUID) {
        this.networkUUID = networkUUID;
    }

    @Override
    public Uuid getNetworkUuid() {
        return networkUUID;
    }

    public void updateDpnPortInfo(BigInteger dpnId, Long ofPort, int addOrRemove) {
        DpnInterfaceInfo dpnInterface = dpnIfaceList.computeIfAbsent(dpnId, key -> new DpnInterfaceInfo(dpnId));
        if (addOrRemove == Ipv6Constants.ADD_ENTRY) {
            dpnInterface.updateofPortList(ofPort);
        } else {
            dpnInterface.removeOfPortFromList(ofPort);
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
    public List<BigInteger> getDpnsHostingNetwork() {
        return dpnIfaceList.values().stream().flatMap(dpnInterfaceInfo -> Stream.of(dpnInterfaceInfo.getDpId()))
                .collect(Collectors.toList());
    }

    public Collection<DpnInterfaceInfo> getDpnIfaceList() {
        return dpnIfaceList.values();
    }

    public DpnInterfaceInfo getDpnIfaceInfo(BigInteger dpId) {
        return dpnIfaceList.get(dpId);
    }

    public void setRSPuntFlowStatusOnDpnId(BigInteger dpnId, int action) {
        DpnInterfaceInfo dpnInterface = dpnIfaceList.get(dpnId);
        if (null != dpnInterface) {
            dpnInterface.setRsFlowConfiguredStatus(action);
        }
    }

    public int getRSPuntFlowStatusOnDpnId(BigInteger dpnId) {
        DpnInterfaceInfo dpnInterface = dpnIfaceList.get(dpnId);
        if (null != dpnInterface) {
            return dpnInterface.getRsFlowConfiguredStatus();
        }
        return Ipv6Constants.FLOWS_NOT_CONFIGURED;
    }

    public void clearDpnInterfaceList() {
        dpnIfaceList.clear();
    }

    @Override
    public String toString() {
        return "VirtualNetwork [networkUUID=" + networkUUID + " dpnIfaceList=" + dpnIfaceList + "]";
    }

    public void removeSelf() {
        dpnIfaceList.values().forEach(dpnInterfaceInfo -> {
            dpnInterfaceInfo.clearOfPortList();
            dpnInterfaceInfo.clearNdTargetFlowInfo();
        });

        clearDpnInterfaceList();
    }

    public static class DpnInterfaceInfo {
        BigInteger dpId;
        int rsPuntFlowConfigured;
        List<Long> ofPortList;
        List<Ipv6Address> ndTargetFlowsPunted;

        DpnInterfaceInfo(BigInteger dpnId) {
            dpId = dpnId;
            ofPortList = new ArrayList<>();
            ndTargetFlowsPunted = new ArrayList<>();
            rsPuntFlowConfigured = Ipv6Constants.FLOWS_NOT_CONFIGURED;
        }

        public void setDpId(BigInteger dpId) {
            this.dpId = dpId;
        }

        public BigInteger getDpId() {
            return dpId;
        }

        public void setRsFlowConfiguredStatus(int status) {
            this.rsPuntFlowConfigured = status;
        }

        public int getRsFlowConfiguredStatus() {
            return rsPuntFlowConfigured;
        }

        public List<Ipv6Address> getNDTargetFlows() {
            return ndTargetFlowsPunted;
        }

        public void updateNDTargetAddress(Ipv6Address ipv6Address, int addOrRemove) {
            if (addOrRemove == Ipv6Constants.ADD_ENTRY) {
                this.ndTargetFlowsPunted.add(ipv6Address);
            } else {
                this.ndTargetFlowsPunted.remove(ipv6Address);
            }
        }

        public void clearNdTargetFlowInfo() {
            this.ndTargetFlowsPunted.clear();
        }

        public void updateofPortList(Long ofPort) {
            this.ofPortList.add(ofPort);
        }

        public void removeOfPortFromList(Long ofPort) {
            this.ofPortList.remove(ofPort);
        }

        public void clearOfPortList() {
            this.ofPortList.clear();
        }

        @Override
        public String toString() {
            return "DpnInterfaceInfo [dpId=" + dpId + " rsPuntFlowConfigured=" + rsPuntFlowConfigured + " ofPortList="
                    + ofPortList + "]";
        }
    }
}
