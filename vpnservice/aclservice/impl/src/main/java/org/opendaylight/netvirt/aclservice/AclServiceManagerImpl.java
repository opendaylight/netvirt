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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclServiceManagerImpl implements AclServiceManager {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceManagerImpl.class);

    private final List<AclServiceListener> aclServiceListeners = new ArrayList<>();

    @Inject
    public AclServiceManagerImpl(final AclServiceImplFactory factory) {
        addAclServiceListner(factory.createIngressAclServiceImpl());
        addAclServiceListner(factory.createEgressAclServiceImpl());

        LOG.info("ACL Service Initiated");
    }

    @Override
    public void addAclServiceListner(AclServiceListener aclServiceListner) {
        aclServiceListeners.add(aclServiceListner);
    }

    @Override
    public void removeAclServiceListner(AclServiceListener aclServiceListner) {
        aclServiceListeners.remove(aclServiceListner);
    }

    @Override
    public void notify(AclInterface port, AclInterface oldPort, Action action) {
        for (AclServiceListener aclServiceListener : aclServiceListeners) {
            boolean result = false;
            if (action == Action.ADD) {
                result = aclServiceListener.applyAcl(port);
            } else if (action == Action.UPDATE) {
                result = aclServiceListener.updateAcl(oldPort, port);
            } else if (action == Action.REMOVE) {
                result = aclServiceListener.removeAcl(port);
            }
            if (result) {
                LOG.debug("Acl action {} invoking listener {} succeeded", action,
                    aclServiceListener.getClass().getName());
            } else {
                LOG.warn("Acl action {} invoking listener {} failed", action, aclServiceListener.getClass().getName());
            }
        }
    }

    @Override
    public void notifyAce(AclInterface port, Action action, Ace ace) {
        for (AclServiceListener aclServiceListener : aclServiceListeners) {
            LOG.debug("Ace action {} invoking class {}", action, aclServiceListener.getClass().getName());
            if (action == Action.ADD) {
                aclServiceListener.applyAce(port, ace);
            } else if (action == Action.REMOVE) {
                aclServiceListener.removeAce(port, ace);
            }
        }
    }

}
