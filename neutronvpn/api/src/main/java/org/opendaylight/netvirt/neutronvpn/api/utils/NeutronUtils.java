/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.utils;

import static java.util.Objects.requireNonNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.neutron.networks.network.Segments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.neutron.networks.network.SegmentsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeutronUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronUtils.class);

    public static final String VNIC_TYPE_NORMAL = "normal";
    public static final String PORT_STATUS_ACTIVE = "ACTIVE";
    public static final String PORT_STATUS_BUILD = "BUILD";
    public static final String PORT_STATUS_DOWN = "DOWN";
    public static final String PORT_STATUS_ERROR = "ERROR";
    public static final String PORT_STATUS_NOTAPPLICABLE = "N/A";
    private static volatile Pattern uuidPattern;

    private NeutronUtils() {

    }

    /**
     * Create a Neutron Port status entry in the operational data store.
     * @param uuid The uuid of the Neutron port
     * @param portStatus value to set the status (see constants above)
     * @param dataBroker DataBroker instance
     * @return true if transaction submitted successfully
     */
    public static boolean createPortStatus(String uuid, String portStatus, DataBroker dataBroker) {
        return writePortStatus(uuid, portStatus, dataBroker, true);
    }

    /**
     * Update a Neutron Port status entry in the operational data store.
     * @param uuid The uuid of the Neutron port
     * @param portStatus value to set the status (see constants above)
     * @param dataBroker DataBroker instance
     * @return true if transaction submitted successfully
     */
    public static boolean updatePortStatus(String uuid, String portStatus, DataBroker dataBroker) {
        return writePortStatus(uuid, portStatus, dataBroker, false);
    }

    private static boolean writePortStatus(String uuid, String portStatus, DataBroker dataBroker, boolean create) {
        Uuid uuidObj = new Uuid(uuid);
        PortBuilder portBuilder = new PortBuilder();
        portBuilder.setUuid(uuidObj);
        portBuilder.setStatus(portStatus);

        InstanceIdentifier iid = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(
                                                                            Port.class, new PortKey(uuidObj));
        SingleTransactionDataBroker tx = new SingleTransactionDataBroker(dataBroker);
        try {
            if (create) {
                tx.syncWrite(LogicalDatastoreType.OPERATIONAL, iid, portBuilder.build());
            } else {
                tx.syncUpdate(LogicalDatastoreType.OPERATIONAL, iid, portBuilder.build());
            }
        } catch (TransactionCommitFailedException e) {
            LOG.error("writePortStatus: failed neutron port status write. isCreate: {}", create, e);
            return false;
        }

        LOG.debug("writePortStatus: operational port status for {} set to {}", uuid, portStatus);

        return true;
    }

    /**
    * Delete a Neutron Port status entry from the operational data store.
    * @param uuid The uuid of the Neutron port
    * @param dataBroker DataBroker instance
    * @return true if transaction submitted successfully
    */
    public static boolean deletePortStatus(String uuid, DataBroker dataBroker) {
        Uuid uuidObj = new Uuid(uuid);

        InstanceIdentifier iid = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(
                Port.class, new PortKey(uuidObj));
        SingleTransactionDataBroker tx = new SingleTransactionDataBroker(dataBroker);
        try {
            tx.syncDelete(LogicalDatastoreType.OPERATIONAL, iid);
        } catch (TransactionCommitFailedException e) {
            LOG.error("deletePortStatus: failed neutron port status delete", e);
            return false;
        }

        return true;
    }

    public static boolean isPortVnicTypeNormal(Port port) {
        PortBindingExtension portBinding = port.augmentation(PortBindingExtension.class);
        if (portBinding == null || portBinding.getVnicType() == null) {
            // By default, VNIC_TYPE is NORMAL
            return true;
        }
        String vnicType = portBinding.getVnicType().trim().toLowerCase(Locale.getDefault());
        return VNIC_TYPE_NORMAL.equals(vnicType);
    }

    public static <T extends NetworkTypeBase> String getSegmentationIdFromNeutronNetwork(Network network,
            Class<T> networkType) {
        String segmentationId = null;
        NetworkProviderExtension providerExtension = network.augmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            segmentationId = providerExtension.getSegmentationId();
            if (segmentationId == null) {
                Map<SegmentsKey, Segments> providerSegmentsMap = providerExtension.getSegments();
                if (providerSegmentsMap != null && providerSegmentsMap.size() > 0) {
                    for (Segments providerSegment: providerSegmentsMap.values()) {
                        if (isNetworkSegmentType(providerSegment, networkType)) {
                            segmentationId = providerSegment.getSegmentationId();
                            break;
                        }
                    }
                }
            }
        }
        return segmentationId;
    }

    static <T extends NetworkTypeBase> boolean isNetworkSegmentType(Segments providerSegment,
            Class<T> expectedNetworkType) {
        Class<? extends NetworkTypeBase> networkType = providerSegment.getNetworkType();
        return networkType != null && networkType.isAssignableFrom(expectedNetworkType);
    }

    public static boolean isUuid(String possibleUuid) {
        requireNonNull(possibleUuid, "possibleUuid == null");

        if (uuidPattern == null) {
            // Thread safe because it really doesn't matter even if we were to do this initialization more than once
            if (Uuid.PATTERN_CONSTANTS.size() != 1) {
                throw new IllegalStateException("Uuid.PATTERN_CONSTANTS.size() != 1");
            }
            uuidPattern = Pattern.compile(Uuid.PATTERN_CONSTANTS.get(0));
        }

        if (uuidPattern.matcher(possibleUuid).matches()) {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    @NonNull
    public static List<Uuid> getVpnMapRouterIdsListUuid(@Nullable List<RouterIds> routerIds) {
        if (routerIds == null) {
            return Collections.emptyList();
        }
        return routerIds.stream().map(
            routerId -> routerId.getRouterId()).collect(Collectors.toList());
    }
}
