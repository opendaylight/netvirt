/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.providers;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * @author ebrjohn
 *
 */
public class GeniusProvider {

    private final DataBroker dataBroker;

    public GeniusProvider(final DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    // TODO add necessary methods to interact with Genius

    public static Optional<NodeId> getNodeIdFromLogicalInterface(String logicalInterface) {
        // TODO finish this return the bridge the logicalPort is on
        return Optional.empty();
    }

    public static void bindPortOnIngressClassifier(String interfaceName) {
        // TODO these NwConstants need to change when 53468 is merged in Genius
        bindService(
                NwConstants.SFC_SERVICE_INDEX,               // NwConstants.SFC_CLASSIFIER_INDEX,
                NwConstants.SFC_SERVICE_NAME,                // NwConstants.SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.SFC_SERVICE_INDEX,               // Not sure what to put for this one
                NwConstants.SFC_TRANSPORT_CLASSIFIER_TABLE,  // NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE,
                interfaceName);
    }

    public static void bindPortOnEgressClassifier(String interfaceName) {
        // TODO these NwConstants need to change when 53468 is merged in Genius
        bindService(
                NwConstants.SFC_SERVICE_INDEX,               // NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                NwConstants.SFC_SERVICE_NAME,                // NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.SFC_SERVICE_INDEX,               // Not sure what to put for this one
                NwConstants.SFC_TRANSPORT_CLASSIFIER_TABLE,  // NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_COOKIE,
                interfaceName);
    }

    public static void unbindPortOnIngressClassifier(String interfaceName) {
        // TODO need to change to NwConstants.SFC_CLASSIFIER_INDEX when 53468 is merged in Genius
        unbindService(interfaceName, NwConstants.SFC_SERVICE_INDEX);
    }

    public static void unbindPortOnEgressClassifier(String interfaceName) {
        // TODO need to change to NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX when 53468 is merged in Genius
        unbindService(interfaceName, NwConstants.SFC_SERVICE_INDEX);
    }

    // TODO This class should be refactored to allow for multiple transactions to be combined
    //      Probably by passing in the transaction to the above bindPortOnIngress/EgressClassifier()
    //      and unbindPortOnIngress/EgressClassifier()

    private static CompletableFuture<Void> bindService(short serviceId, String serviceName, int servicePriority,
            short serviceDestTable, BigInteger serviceTableCookie, String interfaceName) {

        InstanceIdentifier<BoundServices> id = InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceId)).build();

        StypeOpenflow stypeOpenflow = new StypeOpenflowBuilder().setFlowCookie(serviceTableCookie)
                .setFlowPriority(servicePriority)
                .setInstruction(Collections.singletonList(
                        MDSALUtil.buildAndGetGotoTableInstruction(serviceDestTable, 0)))
                .build();
        BoundServices boundServices = new BoundServicesBuilder().setServiceName(serviceName)
                .setServicePriority(serviceId).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, stypeOpenflow).build();

        // TODO need the dataBroker to make the transaction
        //WriteTransaction writeTx;
        //writeTx.put(LogicalDatastoreType.CONFIGURATION, id, boundServices);

        return CompletableFuture.completedFuture(null);
    }

    private static CompletableFuture<Void> unbindService(String interfaceName, short serviceId) {
        InstanceIdentifier<BoundServices> id = InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceId)).build();

        // TODO need the dataBroker to make the transaction
        //WriteTransaction writeTx;
        //writeTx.delete(LogicalDatastoreType.CONFIGURATION, id);

        return CompletableFuture.completedFuture(null);
    }

}
