/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.listeners;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcClassifierDataProcessorBase;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for AccessList.
 */
public class NetvirtSfcAclListener extends DelegatingDataTreeListener<Acl> {
    /**
     * {@link NetvirtSfcAclListener} constructor.
     * @param provider OpenFlow 1.3 Provider
     * @param db MdSal {@link DataBroker}
     */
    public NetvirtSfcAclListener(final NetvirtSfcClassifierDataProcessorBase<Acl> dataProcessor, final DataBroker db) {
        super(dataProcessor, db, new DataTreeIdentifier<>(LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(AccessLists.class).child(Acl.class)));
    }
}
