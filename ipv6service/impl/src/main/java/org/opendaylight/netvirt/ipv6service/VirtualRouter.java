/*
 * Copyright © 2015, 2017 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.netvirt.ipv6service.api.IVirtualRouter;
import org.opendaylight.netvirt.ipv6service.api.IVirtualSubnet;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

public final class VirtualRouter implements IVirtualRouter  {
    private final Uuid routerUUID;
    private final Uuid tenantID;
    private final String name;
    private final ConcurrentMap<Uuid, VirtualSubnet> subnets = new ConcurrentHashMap<>();
    private final ConcurrentMap<Uuid, VirtualPort> interfaces = new ConcurrentHashMap<>();

    private VirtualRouter(Builder builder) {
        this.routerUUID = builder.routerUUID;
        this.tenantID = builder.tenantID;
        this.name = builder.name;
    }

    @Override
    public Uuid getRouterUUID() {
        return routerUUID;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Uuid getTenantID() {
        return tenantID;
    }

    public void addSubnet(VirtualSubnet snet) {
        Uuid subnetUUID = snet.getSubnetUUID();
        if (subnetUUID != null) {
            subnets.put(subnetUUID, snet);
        }
    }

    public void removeSubnet(IVirtualSubnet snet) {
        Uuid subnetUUID = snet.getSubnetUUID();
        if (subnetUUID != null) {
            subnets.remove(subnetUUID);
        }
    }

    public void addInterface(VirtualPort intf) {
        Uuid intfUUID = intf.getIntfUUID();
        if (intfUUID != null) {
            interfaces.put(intfUUID, intf);
        }
    }

    public void removeInterface(IVirtualPort intf) {
        Uuid intfUUID = intf.getIntfUUID();
        if (intfUUID != null) {
            interfaces.remove(intfUUID);
        }
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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Uuid routerUUID;
        private Uuid tenantID;
        private String name;

        public Builder routerUUID(Uuid newRouterUUID) {
            this.routerUUID = newRouterUUID;
            return this;
        }

        public Builder tenantID(Uuid newTenantID) {
            this.tenantID = newTenantID;
            return this;
        }

        public Builder name(String newName) {
            this.name = newName;
            return this;
        }

        public VirtualRouter build() {
            return new VirtualRouter(this);
        }
    }
}
