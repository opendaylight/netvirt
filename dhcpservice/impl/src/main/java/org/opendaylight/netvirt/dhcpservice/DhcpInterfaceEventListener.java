/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceAddJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceRemoveJob;
import org.opendaylight.netvirt.dhcpservice.jobs.DhcpInterfaceUpdateJob;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpInterfaceEventListener
        extends AsyncDataTreeChangeListenerBase<Interface, DhcpInterfaceEventListener> {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpInterfaceEventListener.class);

    private final DataBroker dataBroker;
    private final DhcpManager dhcpManager;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final JobCoordinator jobCoordinator;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final DhcpPortCache dhcpPortCache;

    public DhcpInterfaceEventListener(DhcpManager dhcpManager, DataBroker dataBroker,
                                      DhcpExternalTunnelManager dhcpExternalTunnelManager,
                                      IInterfaceManager interfaceManager, IElanService elanService,
                                      DhcpPortCache dhcpPortCache, JobCoordinator jobCoordinator) {
        super(Interface.class, DhcpInterfaceEventListener.class);
        this.dhcpManager = dhcpManager;
        this.dataBroker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.interfaceManager = interfaceManager;
        this.elanService = elanService;
        this.dhcpPortCache = dhcpPortCache;
        this.jobCoordinator = jobCoordinator;
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    public void close() {
        super.close();
        LOG.info("DhcpInterfaceEventListener Closed");
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> identifier, Interface del) {
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
        BigInteger dpnId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        DhcpInterfaceRemoveJob job = new DhcpInterfaceRemoveJob(dhcpManager, dhcpExternalTunnelManager,
                dataBroker, del, dpnId, interfaceManager, elanService, port);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
        dhcpPortCache.remove(interfaceName);
    }

    @Override
    protected void update(InstanceIdentifier<Interface> identifier,
            Interface original, Interface update) {
        // We're only interested in Vlan and Tunnel ports
        if (!L2vlan.class.equals(update.getType()) && !Tunnel.class.equals(update.getType())) {
            return;
        }
        if ((original.getOperStatus().getIntValue() ^ update.getOperStatus().getIntValue()) == 0) {
            LOG.trace("Interface operstatus {} is same", update.getOperStatus());
            return;
        }

        if (original.getOperStatus().equals(OperStatus.Unknown) || update.getOperStatus().equals(OperStatus.Unknown)) {
            LOG.trace("New/old interface state is unknown not handling");
            return;
        }

        List<String> ofportIds = update.getLowerLayerIf();
        if (ofportIds == null || ofportIds.isEmpty()) {
            return;
        }
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpnId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        String interfaceName = update.getName();
        DhcpInterfaceUpdateJob job = new DhcpInterfaceUpdateJob(dhcpExternalTunnelManager, dataBroker,
                interfaceName, dpnId, update.getOperStatus(), interfaceManager);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
    }

    @Override
    protected void add(InstanceIdentifier<Interface> identifier, Interface add) {
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
        Port port = dhcpManager.getNeutronPort(interfaceName);
        if (NeutronConstants.IS_DHCP_PORT.test(port)) {
            return;
        }
        dhcpPortCache.put(interfaceName, port);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        BigInteger dpnId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        DhcpInterfaceAddJob job = new DhcpInterfaceAddJob(dhcpManager, dhcpExternalTunnelManager, dataBroker,
                add, dpnId, interfaceManager, elanService);
        jobCoordinator.enqueueJob(DhcpServiceUtils.getJobKey(interfaceName), job, DhcpMConstants.RETRY_COUNT);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected DhcpInterfaceEventListener getDataTreeChangeListener() {
        return DhcpInterfaceEventListener.this;
    }
}
