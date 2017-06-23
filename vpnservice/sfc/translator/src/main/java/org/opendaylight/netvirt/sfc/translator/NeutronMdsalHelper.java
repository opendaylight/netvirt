/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.SfcFlowClassifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.sfc.flow.classifiers.SfcFlowClassifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.sfc.flow.classifiers.SfcFlowClassifierKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairGroups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.PortPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pair.groups.PortPairGroupKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPair;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.rev160511.sfc.attributes.port.pairs.PortPairKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility functions to read Neutron models (e.g network, subnet, port, sfc flow classifier
 * port pair, port group, port chain) from md-sal data store.
 */
public class NeutronMdsalHelper {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronMdsalHelper.class);
    private static final InstanceIdentifier<SfcFlowClassifiers> FC_IID =
            InstanceIdentifier.create(Neutron.class).child(SfcFlowClassifiers.class);
    private static final InstanceIdentifier<Ports> PORTS_IID =
            InstanceIdentifier.create(Neutron.class).child(Ports.class);
    private static final InstanceIdentifier<PortPairs> PORT_PAIRS_IID =
            InstanceIdentifier.create(Neutron.class).child(PortPairs.class);
    private static final InstanceIdentifier<PortPairGroups> PORT_PAIR_GROUPS_IID =
            InstanceIdentifier.create(Neutron.class).child(PortPairGroups.class);

    private final DataBroker dataBroker;

    public NeutronMdsalHelper(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public Port getNeutronPort(Uuid portId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getNeutronPortPath(portId)).orNull();
    }

    public PortPair getNeutronPortPair(Uuid portPairId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getNeutronPortPairPath(portPairId)).orNull();
    }

    public PortPairGroup getNeutronPortPairGroup(Uuid portPairGroupId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getNeutronPortPairGroupPath(portPairGroupId)).orNull();
    }

    public SfcFlowClassifier getNeutronFlowClassifier(Uuid flowClassifierId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getNeutronSfcFlowClassifierPath(flowClassifierId)).orNull();
    }

    private InstanceIdentifier<Port> getNeutronPortPath(Uuid portId) {
        return PORTS_IID.builder().child(Port.class, new PortKey(portId)).build();
    }

    private InstanceIdentifier<PortPair> getNeutronPortPairPath(Uuid portPairId) {
        return PORT_PAIRS_IID.builder().child(PortPair.class, new PortPairKey(portPairId)).build();
    }

    private InstanceIdentifier<PortPairGroup> getNeutronPortPairGroupPath(Uuid portPairGroupId) {
        return PORT_PAIR_GROUPS_IID.builder().child(PortPairGroup.class, new PortPairGroupKey(portPairGroupId)).build();
    }

    private InstanceIdentifier<SfcFlowClassifier> getNeutronSfcFlowClassifierPath(Uuid portId) {
        return FC_IID.builder().child(SfcFlowClassifier.class, new SfcFlowClassifierKey(portId)).build();
    }
}
