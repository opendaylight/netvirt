/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclServiceManagerImpl implements AclServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceManagerImpl.class);

    private List<AclServiceListener> aclServiceListenerList;

    /**
     * Initialize the ACL service listener list.
     */
    public AclServiceManagerImpl() {
        aclServiceListenerList = new ArrayList<>();
    }

    @Override
    public void addAclServiceListner(AclServiceListener aclServiceListner) {
        aclServiceListenerList.add(aclServiceListner);
    }

    @Override
    public void removeAclServiceListner(AclServiceListener aclServiceListner) {
        aclServiceListenerList.remove(aclServiceListner);
    }

    @Override
    public void notify(AclInterface port, Action action) {
        for (AclServiceListener aclServiceListener : aclServiceListenerList) {
            if (action == Action.ADD) {
                aclServiceListener.applyAcl(port);
            } else if (action == Action.UPDATE) {
                aclServiceListener.updateAcl(port);
            } else if (action == Action.REMOVE) {
                aclServiceListener.removeAcl(port);
            }
        }
    }

    @Override
    public void notifyAce(AclInterface port, Action action, Ace ace) {
        for (AclServiceListener aclServiceListener : aclServiceListenerList) {
            if (action == Action.ADD) {
                aclServiceListener.applyAce(port, ace);
            } else if (action == Action.REMOVE) {
                aclServiceListener.removeAce(port, ace);
            }
        }
    }

}
