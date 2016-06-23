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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;

public class AclServiceManagerImpl implements AclServiceManager {


    private List<AclServiceListener> aclServiceListenerList;

    /**
     * Intialize the acl service listener list.
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
    public void notify(Interface port, Action action) {
        for (AclServiceListener aclServiceListener:aclServiceListenerList) {
            if (action == Action.ADD) {
                aclServiceListener.applyAcl(port);
            } else if (action == Action.UPDATE) {
                aclServiceListener.updateAcl(port);
            } else if (action == Action.REMOVE) {
                aclServiceListener.removeAcl(port);
            }
        }

    }

}
