/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.attributes.Devices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.L2gatewayConnections;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateway.connections.attributes.l2gatewayconnections.L2gatewayConnection;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.L2gateways;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gateway;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l2gateways.rev150712.l2gateways.attributes.l2gateways.L2gatewayKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

public class DhcpL2GatewayConnectionListener extends AsyncClusteredDataChangeListenerBase<L2gatewayConnection,DhcpL2GatewayConnectionListener> implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpL2GatewayConnectionListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final DataBroker dataBroker;

    public DhcpL2GatewayConnectionListener(DataBroker dataBroker, DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        super(L2gatewayConnection.class, DhcpL2GatewayConnectionListener.class);
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.dataBroker = dataBroker;
    }

    @Override
    protected void remove(InstanceIdentifier<L2gatewayConnection> identifier,
            L2gatewayConnection del) {
        Uuid gatewayId = del.getL2gatewayId();
        InstanceIdentifier<L2gateway> inst = InstanceIdentifier.create(Neutron.class).child(L2gateways.class)
                .child(L2gateway.class, new L2gatewayKey(gatewayId));
        //TODO: Eliminate DS read
        Optional<L2gateway> l2Gateway = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, inst);
        if (!l2Gateway.isPresent()) {
            logger.trace("L2Gw not present id {}", gatewayId);
            return;
        }
        List<Devices> l2Devices = l2Gateway.get().getDevices();
        for (Devices l2Device : l2Devices) {
            String l2DeviceName = l2Device.getDeviceName();
            L2GatewayDevice l2GatewayDevice = L2GatewayCacheUtils.getL2DeviceFromCache(l2DeviceName);
            IpAddress tunnelIp = l2GatewayDevice.getTunnelIp();
            Uuid networkUuid = del.getNetworkId();
            boolean isLastConnection = isLastGatewayConnection(networkUuid, l2DeviceName);
            if (!isLastConnection) {
                logger.trace("Not the last L2GatewayConnection. Not removing flows.");
                continue;
            }
            BigInteger designatedDpnId = dhcpExternalTunnelManager.readDesignatedSwitchesForExternalTunnel(tunnelIp, del.getNetworkId().getValue());
            if (designatedDpnId == null) {
                logger.error("Could not find designated DPN ID for tunnelIp {} and elanInstance {}", tunnelIp, del.getNetworkId().getValue());
                continue;
            }
            dhcpExternalTunnelManager.removeDesignatedSwitchForExternalTunnel(designatedDpnId, tunnelIp, del.getNetworkId().getValue());
        }
    }

    private boolean isLastGatewayConnection(Uuid networkUuid, String deviceName) {
        boolean isLastConnection = true;
        InstanceIdentifier<L2gatewayConnections> l2gatewayConnectionIdentifier = InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class);
        //TODO: Avoid DS read.
        Optional<L2gatewayConnections> l2GwConnection = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, l2gatewayConnectionIdentifier);
        List<L2gatewayConnection> l2GatewayConnectionList = l2GwConnection.get().getL2gatewayConnection();
        for (L2gatewayConnection l2gatewayConnection : l2GatewayConnectionList) {
            if (networkUuid.equals(l2gatewayConnection.getNetworkId())) {
                logger.trace("Found a connection with same network uuid {}", l2gatewayConnection);
                if (!isLastDeviceReferred(deviceName, l2gatewayConnection.getL2gatewayId())) {
                    isLastConnection = false;
                    break;
                }
            }
        }
        return isLastConnection;
    }

    private boolean isLastDeviceReferred(String deviceName, Uuid gatewayId) {
        boolean isLastDeviceReferred = true;
        InstanceIdentifier<L2gateway> l2gatewayIdentifier = InstanceIdentifier.create(Neutron.class).child(L2gateways.class).child(L2gateway.class, new L2gatewayKey(gatewayId));
        //TODO: Avoid DS read.
        Optional<L2gateway> l2Gateway = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, l2gatewayIdentifier);
        if (!l2Gateway.isPresent()) {
            return isLastDeviceReferred;
        }
        List<Devices> devices = l2Gateway.get().getDevices();
        for (Devices device : devices) {
            if (device.getDeviceName().equals(deviceName)) {
                logger.trace("Found a device {} in L2Gateway {}", device, l2Gateway);
                return false;
            }
        }
        return isLastDeviceReferred;
    }

    @Override
    protected void update(InstanceIdentifier<L2gatewayConnection> identifier,
            L2gatewayConnection original, L2gatewayConnection update) {

    }

    @Override
    protected void add(InstanceIdentifier<L2gatewayConnection> identifier,
            L2gatewayConnection add) {

    }

    @Override
    protected InstanceIdentifier<L2gatewayConnection> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(L2gatewayConnections.class)
                .child(L2gatewayConnection.class);
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return DhcpL2GatewayConnectionListener.this;
    }

    @Override
    protected DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.SUBTREE;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                logger.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        logger.info("DhcpL2GatewayConnection listener Closed");
    }
}
