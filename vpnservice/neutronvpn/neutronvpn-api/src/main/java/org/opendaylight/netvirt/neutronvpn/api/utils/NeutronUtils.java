/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.api.utils;

import java.util.List;
import java.util.Objects;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.binding.rev150712.PortBindingExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.NetworkTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.neutron.networks.network.Segments;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronUtils.class);

    public static final String VNIC_TYPE_NORMAL = "normal";
    public static final String PORT_STATUS_ACTIVE = "ACTIVE";
    public static final String PORT_STATUS_BUILD = "BUILD";
    public static final String PORT_STATUS_DOWN = "DOWN";
    public static final String PORT_STATUS_ERROR = "ERROR";
    public static final String PORT_STATUS_NOTAPPLICABLE = "N/A";

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
            LOG.error("writePortStatus: failed neutron port status write. isCreate ? " + create, e);
            return false;
        }

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
        PortBindingExtension portBinding = port.getAugmentation(PortBindingExtension.class);
        if (portBinding == null || portBinding.getVnicType() == null) {
            // By default, VNIC_TYPE is NORMAL
            return true;
        }
        String vnicType = portBinding.getVnicType().trim().toLowerCase();
        return vnicType.equals(VNIC_TYPE_NORMAL);
    }

    public static <T extends NetworkTypeBase> String getSegmentationIdFromNeutronNetwork(Network network,
            Class<T> networkType) {
        String segmentationId = null;
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            segmentationId = providerExtension.getSegmentationId();
            if (segmentationId == null) {
                List<Segments> providerSegments = providerExtension.getSegments();
                if (providerSegments != null && providerSegments.size() > 0) {
                    for (Segments providerSegment: providerSegments) {
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
        return (networkType != null && networkType.isAssignableFrom(expectedNetworkType));
    }

    public static <T extends NetworkTypeBase> boolean isNetworkSegmentType(Network network, Long index,
                                                                           Class<T> expectedNetworkType) {
        Class<? extends NetworkTypeBase> segmentType = null;
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            List<Segments> providerSegments = providerExtension.getSegments();
            if (providerSegments != null && providerSegments.size() > 0) {
                for (Segments providerSegment : providerSegments) {
                    if (Objects.equals(providerSegment.getSegmentationIndex(), index)) {
                        segmentType = providerSegment.getNetworkType();
                        break;
                    }
                }
            }
        }
        return (segmentType != null && segmentType.isAssignableFrom(expectedNetworkType));
    }

    public static Long getNumberSegmentsFromNeutronNetwork(Network network) {
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        Integer numSegs = 0;
        if (providerExtension != null) {
            List<Segments> providerSegments = providerExtension.getSegments();
            if (providerSegments != null) {
                numSegs = providerSegments.size();
            }
        }
        return Long.valueOf(numSegs);
    }

    public static String getSegmentationIdFromNeutronNetworkSegment(Network network, Long index) {
        String segmentationId = null;
        NetworkProviderExtension providerExtension = network.getAugmentation(NetworkProviderExtension.class);
        if (providerExtension != null) {
            List<Segments> providerSegments = providerExtension.getSegments();
            if (providerSegments != null && providerSegments.size() > 0) {
                for (Segments providerSegment : providerSegments) {
                    if (Objects.equals(providerSegment.getSegmentationIndex(), index)) {
                        segmentationId = providerSegment.getSegmentationId();
                        break;
                    }
                }
            }
        }
        return segmentationId;
    }


}
