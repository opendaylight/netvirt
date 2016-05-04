/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.statehelpers;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceManagerCommonUtils;
import org.opendaylight.vpnservice.interfacemgr.commons.InterfaceMetaUtils;
import org.opendaylight.vpnservice.interfacemgr.renderer.hwvtep.utilities.SouthboundUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.Tunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdParams;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.tunnel.attributes.BfdStatus;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class HwVTEPInterfaceStateUpdateHelper {
    private static final Logger LOG = LoggerFactory.getLogger(HwVTEPInterfaceStateUpdateHelper.class);

    public static List<ListenableFuture<Void>> updatePhysicalSwitch(DataBroker dataBroker, InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier,
                                                                    Tunnels tunnelsNew, Tunnels tunnelsOld) {
        List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
        LOG.debug("updating physical switch for tunnels");
        String interfaceName =
                InterfaceMetaUtils.getInterfaceForTunnelInstanceIdentifier(tunnelsInstanceIdentifier.toString(), dataBroker);
        if (interfaceName == null) {
            return futures;
        }

        // update opstate of interface if TEP has gone down/up as a result of BFD monitoring
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        InterfaceManagerCommonUtils.updateOpState(transaction, interfaceName, getTunnelOpState(tunnelsNew.getBfdStatus()));
        futures.add(transaction.submit());
        return futures;
    }

    private static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus
                getTunnelOpState(List<BfdStatus> tunnelBfdStatus) {
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus
                livenessState = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Down;
        if (tunnelBfdStatus != null && !tunnelBfdStatus.isEmpty()) {
            for (BfdStatus bfdState : tunnelBfdStatus) {
                if (bfdState.getBfdStatusKey().equalsIgnoreCase(SouthboundUtils.BFD_OP_STATE)) {
                    String bfdOpState = bfdState.getBfdStatusValue();
                    if (bfdOpState.equalsIgnoreCase(SouthboundUtils.BFD_STATE_UP)) {
                        livenessState = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Up;
                    } else {
                        livenessState = org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus.Down;
                    }
                    break;
                }
            }
        }
        return livenessState;
    }


    public static List<ListenableFuture<Void>> startBfdMonitoring(DataBroker dataBroker,
                                                                  InstanceIdentifier<Tunnels> tunnelsInstanceIdentifier,
                                                                  Tunnels tunnelsNew) {
        List<ListenableFuture<Void>> futures = new ArrayList<ListenableFuture<Void>>();
        /*String interfaceName =
                InterfaceMetaUtils.getInterfaceForTunnelInstanceIdentifier(tunnelsInstanceIdentifier.toString(), dataBroker);
        if (interfaceName == null) {
            LOG.debug("no interface configured for the tunnel {}", tunnelsInstanceIdentifier);
            return futures;
        }*/

        LOG.debug("starting bfd monitoring for the hwvtep {}", tunnelsInstanceIdentifier);
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        TunnelsBuilder tBuilder = new TunnelsBuilder();
        tBuilder.setKey(new TunnelsKey(tunnelsNew.getLocalLocatorRef(), tunnelsNew.getRemoteLocatorRef()));
        tBuilder.setLocalLocatorRef(tunnelsNew.getLocalLocatorRef());
        tBuilder.setRemoteLocatorRef(tunnelsNew.getLocalLocatorRef());
        List <BfdParams> bfdParams = new ArrayList<>();
        SouthboundUtils.fillBfdParameters(bfdParams, null);
        tBuilder.setBfdParams(bfdParams);
        transaction.put(LogicalDatastoreType.CONFIGURATION, tunnelsInstanceIdentifier,tBuilder.build(), true);
        futures.add(transaction.submit());
        return futures;
    }
}
