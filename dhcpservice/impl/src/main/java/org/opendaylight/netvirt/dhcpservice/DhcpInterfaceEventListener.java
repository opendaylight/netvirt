/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.util.List;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceAddJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceRemoveJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceUpdateJob;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev170119.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceEventListener extends AbstractAsyncDataTreeChangeListener<Interface> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceEventListener.class);

    private final DataBroker dataBroker;
    private final DhcpManager dhcpManager;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final JobCoordinator jobCoordinator;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final DhcpPortCache dhcpPortCache;
    private final ItmRpcService itmRpcService;

    public DhcpInterfaceEventListener(DhcpManager dhcpManager, DataBroker dataBroker,
                                      DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                      IInterfaceManager interfaceManager, IElanService elanService,
                                      DhcpPortCache dhcpPortCache, JobCoordinator jobCoordinator,
                                      ItmRpcService itmRpcService) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(InterfacesState.class)
                .child(Interface.class),
                Executors.newListeningSingleThreadExecutor("DhcpInterfaceEventListener", LOG));
        this.dhcpManager = dhcpManager;
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.dhcpPortCache = dhcpPortCache;
        this.jobCoordinator = jobCoordinator;
        this.itmRpcService = itmRpcService;
    }

    @Override
    public void close() {
        super.close();
        LOG.info("DhcpInterfaceEventListener Closed");
    }

    @Override
    public void remove(InstanceIdentifier<Interface> identifier, Interface del) {
        if (!L2vlan.class.equals(del.getType()) && !Tunnel.class.equals(del.getType())) {
            return;
        }
        List<String> ofportIds = del.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        String interfaceName = del.getName();
        Port port = dhcpPortCache.get(interfaceName);
        if (NeutronConstants.IS_DHCP_PORT.test(port)) {
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        Uint64 dpnId = DhcpServiceUtils.getDpnIdFromNodeConnectorId(nodeConnectorId);

        DhcpInterfaceRemoveJob job = new DhcpInterfaceRemoveJob(dhcpManager, dhcpExternalTunnelManager,
                dataBroker, del, dpnId, interfaceManager, elanService, port);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
        dhcpPortCache.remove(interfaceName);
    }

    @Override
    public void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // We're only interested in Vlan and Tunnel ports
        if (!L2vlan.class.equals(update.getType()) && !Tunnel.class.equals(update.getType())) {
            return;
        }
        if ((original.getOperStatus().getIntValue() ^ update.getOperStatus().getIntValue()) == 0) {
            LOG.trace("Interface operstatus is same orig {} updated {}", original, update);
            return;
        }
        List<String> ofportIds = update.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        Uint64 dpnId = DhcpServiceUtils.getDpnIdFromNodeConnectorId(nodeConnectorId);
        String interfaceName = update.getName();
        OperStatus updatedOperStatus = update.getOperStatus();
        if (original.getOperStatus().equals(OperStatus.Up) && updatedOperStatus.equals(OperStatus.Unknown)) {
            updatedOperStatus = OperStatus.Down;
        }
        DhcpInterfaceUpdateJob job = new DhcpInterfaceUpdateJob(dhcpExternalTunnelManager, dataBroker,
                interfaceName, dpnId, updatedOperStatus, interfaceManager);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
    }

    @Override
    public void add(InstanceIdentifier<Interface> identifier, Interface add) {
        // We're only interested in Vlan and Tunnel ports
        if (!L2vlan.class.equals(add.getType()) && !Tunnel.class.equals(add.getType())) {
            return;
        }
        String interfaceName = add.getName();
        LOG.trace("DhcpInterfaceAddJob to be created for interface {}", interfaceName);
        List<String> ofportIds = add.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        if (!Tunnel.class.equals(add.getType())) {
            Port port = dhcpManager.getNeutronPort(interfaceName);
            if (NeutronConstants.IS_DHCP_PORT.test(port)) {
                return;
            }
            dhcpPortCache.put(interfaceName, port);
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        Uint64 dpnId = DhcpServiceUtils.getDpnIdFromNodeConnectorId(nodeConnectorId);
        DhcpInterfaceAddJob job = new DhcpInterfaceAddJob(dhcpManager, dhcpExternalTunnelManager, dataBroker,
                add, dpnId, interfaceManager, elanService, itmRpcService);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
    }
}
