/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HwVTEPConfigRemoveHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HwVTEPConfigRemoveHelper.class);

    public static List<ListenableFuture<Void>> removeConfiguration(DataBroker dataBroker, Interface interfaceOld,
                                                                   InstanceIdentifier<Node> globalNodeId,
                                                                   InstanceIdentifier<Node> physicalSwitchNodeId) {
        List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        LOG.info("removing hwvtep configuration for {}", interfaceOld.getName());
        if(globalNodeId != null) {
            IfTunnel ifTunnel = interfaceOld.getAugmentation(IfTunnel.class);
            //removeTunnelTableEntry(transaction, ifTunnel, physicalSwitchNodeId);
            removeTerminationEndPoint(transaction, ifTunnel, globalNodeId);
            InterfaceManagerCommonUtils.deleteStateEntry(interfaceOld.getName(), transaction);
            InterfaceMetaUtils.removeTunnelToInterfaceMap(physicalSwitchNodeId, transaction, ifTunnel);
        }
        futures.add(transaction.submit());
        return futures;
    }

    private static void removeTerminationEndPoint(WriteTransaction transaction, IfTunnel ifTunnel,
                                               InstanceIdentifier<Node> globalNodeId) {
        LOG.info("removing remote termination end point {}", ifTunnel.getTunnelDestination());
        TerminationPointKey tpKey = SouthboundUtils.getTerminationPointKey(ifTunnel.getTunnelDestination().
                getIpv4Address().getValue());
        InstanceIdentifier<TerminationPoint> tpPath = SouthboundUtils.createInstanceIdentifier
                (globalNodeId, tpKey);
        transaction.delete(LogicalDatastoreType.CONFIGURATION,  tpPath);
    }

    private static void removeTunnelTableEntry(WriteTransaction transaction, IfTunnel ifTunnel,
                                               InstanceIdentifier<Node> physicalSwitchNodeId) {
        LOG.info("removing tunnel table entry for {}", ifTunnel.getTunnelDestination());
        InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier = SouthboundUtils.createTunnelsInstanceIdentifier(physicalSwitchNodeId,
                ifTunnel.getTunnelSource(), ifTunnel.getTunnelDestination());
        transaction.delete(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier);
    }
}