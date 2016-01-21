/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.listeners;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.vpnservice.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.commons.AlivenessMonitorUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.globals.InterfaceInfo;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigAddHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigRemoveHelper;
import org.opendaylight.vpnservice.interfacemgr.renderer.ovs.confighelpers.OvsInterfaceConfigUpdateHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.LivenessState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeVxlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * This class listens for interface creation/removal/update in Configuration DS.
 * This is used to handle interfaces for base of-ports.
 */
public class AlivenessMonitorListener implements org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.AlivenessMonitorListener {
    private static final Logger LOG = LoggerFactory.getLogger(AlivenessMonitorListener.class);
    private DataBroker dataBroker;

    public AlivenessMonitorListener(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @Override
    public void onMonitorEvent(MonitorEvent notification) {
        Long monitorId = notification.getEventData().getMonitorId();
        String trunkInterfaceName = AlivenessMonitorUtils.getInterfaceFromMonitorId(dataBroker, monitorId);
        if (trunkInterfaceName == null) {
            LOG.debug("Either monitoring for interface - {} not started by Interfacemgr or it is not LLDP monitoring", trunkInterfaceName);
            return;
        }
        LivenessState livenessState = notification.getEventData().getMonitorState();
        Interface interfaceInfo = InterfaceManagerCommonUtils.getInterfaceFromConfigDS(new InterfaceKey(trunkInterfaceName),
                dataBroker);
        IfTunnel tunnelInfo = interfaceInfo.getAugmentation(IfTunnel.class);
        // Not handling monitoring event if it is GRE Trunk Interface.
        if (tunnelInfo.getTunnelInterfaceType().isAssignableFrom(TunnelTypeGre.class)) {
            return;
        }
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus opState =
                livenessState == LivenessState.Up ? org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up :
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Down;
        InterfaceManagerCommonUtils.setOpStateForInterface(dataBroker, trunkInterfaceName, opState);
    }

}