/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;

public interface AclServiceManager {

    enum Action {
        ADD,
        UPDATE,
        REMOVE
    }

    void addAclServiceListner(AclServiceListener aclServiceListner);

    void removeAclServiceListner(AclServiceListener aclServiceListner);

    void notify(Interface port, Action action);
}
