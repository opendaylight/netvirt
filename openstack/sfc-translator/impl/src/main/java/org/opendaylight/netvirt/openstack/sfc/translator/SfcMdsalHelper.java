/*
 * Copyright (c) 2016 Brocade Communications Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.sfc.translator;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility methods to read OpenDaylight SFC models.
 */
public class SfcMdsalHelper {
    private static final Logger LOG = LoggerFactory.getLogger(SfcMdsalHelper.class);
    private static InstanceIdentifier<AccessLists> accessListIid = InstanceIdentifier.create(AccessLists.class);

    private final DataBroker dataBroker;
    private final MdsalUtils mdsalUtils;

    public SfcMdsalHelper(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        mdsalUtils = new MdsalUtils(this.dataBroker);
    }

    public void addAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclKey(aclFlowClassifier);
        LOG.info("Write ACL FlowClassifier {} to config data store at {}",aclFlowClassifier, aclIid);
        mdsalUtils.put(LogicalDatastoreType.CONFIGURATION, aclIid, aclFlowClassifier);
    }

    public void updateAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclKey(aclFlowClassifier);
        LOG.info("Update ACL FlowClassifier {} in config data store at {}",aclFlowClassifier, aclIid);
        mdsalUtils.merge(LogicalDatastoreType.CONFIGURATION, aclIid, aclFlowClassifier);
    }

    public void removeAclFlowClassifier(Acl aclFlowClassifier) {
        InstanceIdentifier<Acl> aclIid = getAclKey(aclFlowClassifier);
        LOG.info("Remove ACL FlowClassifier {} from config data store at {}",aclFlowClassifier, aclIid);
        mdsalUtils.delete(LogicalDatastoreType.CONFIGURATION, aclIid);
    }

    private InstanceIdentifier<Acl> getAclKey(Acl aclFlowClassifier) {
        return accessListIid.builder().child(Acl.class, aclFlowClassifier.getKey()).build();
    }
}
