/*
 * Copyright © 2015, 2017 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.util.HashMap;
import java.util.Map;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualRouter  {

    private Uuid routerUUID;
    private Uuid tenantID;
    private String name;
    private Map<Uuid, VirtualSubnet> subnets = new HashMap<>();
    private Map<Uuid, VirtualPort>   interfaces = new HashMap<>();

    /**
     * Logger instance.
     */
    static final Logger LOG = LoggerFactory.getLogger(VirtualRouter.class);

    public VirtualRouter() {
    }

    public Uuid getRouterUUID() {
        return routerUUID;
    }

    public VirtualRouter setRouterUUID(Uuid routerUUID) {
        this.routerUUID = routerUUID;
        return this;
    }

    public String getName() {
        return name;
    }

    public VirtualRouter setName(String name) {
        this.name = name;
        return this;
    }

    public Uuid getTenantID() {
        return tenantID;
    }

    public VirtualRouter setTenantID(Uuid tenantID) {
        this.tenantID = tenantID;
        return this;
    }

    public void addSubnet(VirtualSubnet snet) {
        subnets.put(snet.getSubnetUUID(), snet);
    }

    public void removeSubnet(VirtualSubnet snet) {
        subnets.remove(snet.getSubnetUUID());
    }

    public void addInterface(VirtualPort intf) {
        interfaces.put(intf.getIntfUUID(), intf);
    }

    public void removeInterface(VirtualPort intf) {
        interfaces.remove(intf.getIntfUUID());
    }

    public void removeSelf() {
        for (VirtualPort intf : interfaces.values()) {
            if (intf != null) {
                intf.setRouter(null);
            }
        }

        for (VirtualSubnet snet : subnets.values()) {
            if (snet != null) {
                snet.setRouter(null);
            }
        }
    }
}
