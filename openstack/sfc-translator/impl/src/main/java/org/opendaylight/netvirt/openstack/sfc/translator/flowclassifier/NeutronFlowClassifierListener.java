/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator.flowclassifier;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.SfcFlowClassifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.sfc.flow.classifiers.SfcFlowClassifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * OpenDaylight Neutron Flow Classifier yang models data change listener
 */
public class NeutronFlowClassifierListener extends DelegatingDataTreeListener<SfcFlowClassifier> {

    private static final InstanceIdentifier<SfcFlowClassifier> flowClassifiersIid =
            InstanceIdentifier.create(Neutron.class).child(SfcFlowClassifiers.class).child(SfcFlowClassifier.class);

    public NeutronFlowClassifierListener(DataBroker db, FlowClassifierTranslator flowClassifierTranslator) {
        super(flowClassifierTranslator, db,
                new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,flowClassifiersIid));
    }
}
