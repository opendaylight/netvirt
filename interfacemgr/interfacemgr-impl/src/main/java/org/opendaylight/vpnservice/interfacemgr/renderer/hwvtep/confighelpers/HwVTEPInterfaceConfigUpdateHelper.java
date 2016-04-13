/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.confighelpers;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.overlay.rev150105.Tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.EncapsulationTypeVxlanOverIpv4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HwVTEPInterfaceConfigUpdateHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HwVTEPInterfaceConfigUpdateHelper.class);

    public static List<ListenableFuture<Void>> updateConfiguration(DataBroker dataBroker, InstanceIdentifier<Node> physicalSwitchNodeId,
                                                                InstanceIdentifier<Node> globalNodeId,
                                                                Interface interfaceNew, IfTunnel ifTunnel) {
        List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
        LOG.info("adding hwvtep configuration for {}", interfaceNew.getName());

        // create hwvtep through ovsdb plugin
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        if(globalNodeId != null) {
            updateBfdMonitoring(dataBroker, globalNodeId, physicalSwitchNodeId, ifTunnel);

        }else{
            LOG.debug("specified physical switch is not connected {}", physicalSwitchNodeId);
        }
        futures.add(transaction.submit());
        return futures;
    }

    /*
     * bfd monitoring interval and enable/disbale attributes can be modified
     */
    public static List<ListenableFuture<Void>> updateBfdMonitoring(DataBroker dataBroker, InstanceIdentifier<Node> globalNodeId, InstanceIdentifier<Node> physicalSwitchId,
                                                                   IfTunnel ifTunnel) {
        List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        TunnelsBuilder tBuilder = new TunnelsBuilder();
        InstanceIdentifier<TerminationPoint> localTEPInstanceIdentifier =
                SouthboundUtils.createTEPInstanceIdentifier(globalNodeId, ifTunnel.getTunnelSource());
        InstanceIdentifier<TerminationPoint> remoteTEPInstanceIdentifier =
                SouthboundUtils.createTEPInstanceIdentifier(globalNodeId, ifTunnel.getTunnelDestination());
        InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier = SouthboundUtils.
                createTunnelsInstanceIdentifier(physicalSwitchId, localTEPInstanceIdentifier, remoteTEPInstanceIdentifier);

        LOG.debug("updating bfd monitoring parameters for the hwvtep {}", tunnelsInstanceIdentifier);
        tBuilder.setKey(new TunnelsKey(new HwvtepPhysicalLocatorRef(localTEPInstanceIdentifier),
                new HwvtepPhysicalLocatorRef(remoteTEPInstanceIdentifier)));
        List <BfdParams> bfdParams = new ArrayList<>();
        SouthboundUtils.fillBfdParameters(bfdParams, ifTunnel);
        tBuilder.setBfdParams(bfdParams);
        transaction.merge(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier,tBuilder.build(), true);
        futures.add(transaction.submit());
        return futures;
    }
}
