/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class OvsVlanMemberConfigAddHelper {
    private static final Logger LOG = LoggerFactory.getLogger(OvsVlanMemberConfigAddHelper.class);
    public static List<ListenableFuture<Void>> addConfiguration(DataBroker dataBroker, ParentRefs parentRefs,
                                                                Interface interfaceNew, IfL2vlan ifL2vlan,
                                                                IdManagerService idManager) {
        LOG.debug("add vlan member configuration {}",interfaceNew.getName());
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        WriteTransaction t = dataBroker.newWriteOnlyTransaction();

        InterfaceManagerCommonUtils.createInterfaceChildEntry(t, parentRefs.getParentInterface(), interfaceNew.getName());

        InterfaceKey interfaceKey = new InterfaceKey(parentRefs.getParentInterface());
        Interface ifaceParent = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(interfaceKey, dataBroker);
        if (ifaceParent == null) {
            LOG.info("Parent Interface: {} not found when adding child interface: {}",
                    parentRefs.getParentInterface(), interfaceNew.getName());
            futures.add(t.submit());
            return futures;
        }

        IfL2vlan parentIfL2Vlan = ifaceParent.getAugmentation(IfL2vlan.class);
        if (parentIfL2Vlan == null || parentIfL2Vlan.getL2vlanMode() != IfL2vlan.L2vlanMode.Trunk) {
            LOG.error("Parent Interface: {} not of trunk Type when adding trunk-member: {}", ifaceParent, interfaceNew);
            futures.add(t.submit());
            return futures;
        }

        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface ifState =
                InterfaceManagerCommonUtils.getInterfaceStateFromOperDS(parentRefs.getParentInterface(), dataBroker);
        if (ifState != null) {
            LOG.debug("add interface state info for vlan member {}",interfaceNew.getName());
            InterfaceManagerCommonUtils.addStateEntry(interfaceNew.getName(), t, dataBroker, idManager, ifState);
        }

        futures.add(t.submit());
        return futures;
    }
}
