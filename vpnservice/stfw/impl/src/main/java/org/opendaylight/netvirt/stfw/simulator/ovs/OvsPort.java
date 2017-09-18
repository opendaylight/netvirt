/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.simulator.ovs;

import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public class OvsPort {
    private Uuid uuid;
    private String name;
    private int ofPortId;

    public OvsPort(String name) {
        uuid = RandomUtils.createUuid();
        this.name = name;
    }

    public Uuid getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setOfPortId(int ofPortId) {
        this.ofPortId = ofPortId;
    }

    public int getOfPortId() {
        return ofPortId;
    }

}
