/*
 * Copyright (c) 2015 Dell Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.routemgr.net;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

//import org.apache.commons.collections4.trie;

public class VirtualSubnet  {

    private Uuid subnetUUID;
    private Uuid tenantID;
    private String name;
    private IpAddress gatewayIp;
    private Boolean   allocPoolFlag;
    private List<APool> poolList;
    private IpPrefix subnetCidr;
    private String ipVersion;
    private String ipv6AddressMode;
    private String ipv6RAMode;

    // associated router
    private VirtualRouter router;
    // interface list
    private HashMap<Uuid, VirtualPort> interfaces;


    /**
     * Logger instance.
     */
    static final Logger logger = LoggerFactory.getLogger(VirtualSubnet.class);

    void init() {
        this.interfaces  = new HashMap<>();
        this.router      = null;
        poolList         = new ArrayList();
    }

    public VirtualSubnet() {
        init();
    }

    public VirtualSubnet setSubnetUUID(Uuid subnetUUID) {
        this.subnetUUID = subnetUUID;
        return this;
    }

    public Uuid getSubnetUUID() {
        return subnetUUID;
    }

    public String getName() {
        return name;
    }

    public VirtualSubnet setName(String name) {
        this.name = name;
        return this;
    }

    public Uuid getTenantID() {
        return tenantID;
    }

    public VirtualSubnet setTenantID(Uuid tenantID) {
        this.tenantID = tenantID;
        return this;
    }

    public VirtualSubnet setGatewayIp(IpAddress gwIp) {
        this.gatewayIp = gwIp;
        return this;
    }

    public IpAddress getGatewayIp() {
        return gatewayIp;
    }

    public VirtualSubnet setIPVersion(String ipVersion) {
        this.ipVersion = ipVersion;
        return this;
    }

    public String getIpVersion() { return ipVersion;}

    public VirtualSubnet setSubnetCidr(IpPrefix subnetCidr) {
        this.subnetCidr = subnetCidr;
        return this;
    }

    public IpPrefix getSubnetCidr() { return subnetCidr;}

    public VirtualSubnet setIpv6AddressMode(String ipv6AddressMode) {
        this.ipv6AddressMode = ipv6AddressMode;
        return this;
    }

    public String getIpv6AddressMode() {
        return ipv6AddressMode;
    }

    public VirtualSubnet setIpv6RAMode(String ipv6RAMode) {
        this.ipv6RAMode = ipv6RAMode;
        return this;
    }

    public String getIpv6RAMode() {
        return ipv6RAMode;
    }

    public VirtualSubnet setAllocPoolFlag(Boolean flag) {
        allocPoolFlag = flag;
        return this;
    }

    public Boolean getAllocPoolFlag() {
        return allocPoolFlag;
    }

    public void addPool(IpAddress start, IpAddress end) {
        APool pool = new APool();
        pool.setPoolStart(start);
        pool.setPoolEnd(end);
        poolList.add(pool);
    }

    public void setRouter(VirtualRouter rtr) {
        this.router = rtr;
    }

    public VirtualRouter getRouter() {
        return router;
    }

    public void addInterface(VirtualPort intf) {
        interfaces.put(intf.getIntfUUID(), intf);
    }

    public void removeInterface(VirtualPort intf) {
        interfaces.remove(intf.getIntfUUID());
    }

    public void removeSelf() {
        Collection<VirtualPort> intfs = interfaces.values();

        Iterator itr = intfs.iterator();
        while (itr.hasNext()) {
            VirtualPort intf = (VirtualPort) itr.next();
            if (intf != null) {
                intf.removeSubnetInfo(subnetUUID);
            }
        }

        if (router != null) {
            router.removeSubnet(this);
        }
        return;
    }

    private class APool {
        private IpAddress poolStart;
        private IpAddress poolEnd;

        public void setPoolStart(IpAddress start) {
            this.poolStart = start;
        }

        public IpAddress getPoolStart() {
            return poolStart;
        }

        public void setPoolEnd(IpAddress end) {
            this.poolEnd = end;
        }

        public IpAddress getPoolEnd() {
            return poolEnd;
        }
    }
}
