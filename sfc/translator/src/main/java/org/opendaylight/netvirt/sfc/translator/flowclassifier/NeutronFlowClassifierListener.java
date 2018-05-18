/*
 * Copyright (c) 2016, 2017 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.translator.flowclassifier;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.sfc.translator.DelegatingDataTreeListener;
import org.opendaylight.netvirt.sfc.translator.SfcMdsalHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.SfcFlowClassifiers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.sfc.flow.classifier.rev160511.sfc.flow.classifiers.attributes.sfc.flow.classifiers.SfcFlowClassifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * OpenDaylight Neutron Flow Classifier yang models data change listener.
 */
public class NeutronFlowClassifierListener extends DelegatingDataTreeListener<SfcFlowClassifier> {

    private static final InstanceIdentifier<SfcFlowClassifier> FLOW_CLASSIFIERS_IID =
            InstanceIdentifier.create(Neutron.class).child(SfcFlowClassifiers.class).child(SfcFlowClassifier.class);

    private final SfcMdsalHelper sfcMdsalHelper;

    public NeutronFlowClassifierListener(DataBroker db) {
        super(db, new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION, FLOW_CLASSIFIERS_IID));
        sfcMdsalHelper = new SfcMdsalHelper(db);

    }

    /**
     * Method removes Acl respective to SfcFlowClassifier which is identified by InstanceIdentifier.
     *
     * @param deletedSfcFlowClassifier        - SfcFlowClassifier for removing
     */
    @Override
    public void remove(SfcFlowClassifier deletedSfcFlowClassifier) {
        Acl aclFlowClassifier = FlowClassifierTranslator.buildAcl(deletedSfcFlowClassifier);
        sfcMdsalHelper.removeAclFlowClassifier(aclFlowClassifier);
    }

    /**
     * Method updates the original SfcFlowClassifier to the update SfcFlowClassifier.
     * Both are identified by same InstanceIdentifier.
     *
     * @param origSfcFlowClassifier        - original SfcFlowClassifier
     * @param updatedSfcFlowClassifier     - changed SfcFlowClassifier (contain updates)
     */
    @Override
    public void update(SfcFlowClassifier origSfcFlowClassifier, SfcFlowClassifier updatedSfcFlowClassifier) {

        Acl aclFlowClassifier = FlowClassifierTranslator.buildAcl(updatedSfcFlowClassifier);
        sfcMdsalHelper.updateAclFlowClassifier(aclFlowClassifier);
    }

    /**
     * Method adds the SfcFlowClassifier which is identified by InstanceIdentifier
     * to device.
     *
     * @param sfcFlowClassifier        - new SfcFlowClassifier
     */
    @Override
    public void add(SfcFlowClassifier sfcFlowClassifier) {
        // Respective ACL classifier will be written in data store, once the chain is created.
    }

}
