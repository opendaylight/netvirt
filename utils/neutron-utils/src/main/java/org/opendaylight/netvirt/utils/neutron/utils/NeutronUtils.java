/*
 * Copyright © 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.utils.neutron.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronNetwork;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronPort;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityGroup;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSecurityRule;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSubnet;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronNetworkCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronPortCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityGroupCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSecurityRuleCRUD;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronSubnetCRUD;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;

public class NeutronUtils {
    public NeutronPort createNeutronPort(String networkId, String subnetId,
                                         String id, String owner, String ipaddr, String mac) {
        INeutronPortCRUD iNeutronPortCRUD =
                (INeutronPortCRUD) ServiceHelper.getGlobalInstance(INeutronPortCRUD.class, this);
        NeutronPort np = new NeutronPort();
        np.initDefaults();
        np.setID(id);
        np.setDeviceOwner(owner);
        np.setMacAddress(mac);
        np.setNetworkUUID(networkId);
        List<org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs> srcAddressList =
                new ArrayList<>();
        org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs nip = new org.opendaylight.netvirt.openstack.netvirt.translator.Neutron_IPs();
        nip.setIpAddress(ipaddr);
        nip.setSubnetUUID(subnetId);
        srcAddressList.add(nip);
        np.setFixedIPs(srcAddressList);
        List<NeutronSecurityGroup> nsgs = new ArrayList<>();
        np.setSecurityGroups(nsgs);
        iNeutronPortCRUD.addPort(np);
        return np;
    }

    public NeutronSubnet createNeutronSubnet(String subnetId, String tenantId,
                                              String networkId, String cidr) {
        INeutronSubnetCRUD iNeutronSubnetCRUD =
                (INeutronSubnetCRUD) ServiceHelper.getGlobalInstance(INeutronSubnetCRUD.class, this);
        NeutronSubnet ns = new NeutronSubnet();
        ns.setID(subnetId);
        ns.setCidr(cidr);
        ns.initDefaults();
        ns.setNetworkUUID(networkId);
        ns.setTenantID(tenantId);
        iNeutronSubnetCRUD.addSubnet(ns);
        return ns;
    }

    public NeutronNetwork createNeutronNetwork(String uuid, String tenantID, String networkTypeVxlan, String segId) {
        INeutronNetworkCRUD iNeutronNetworkCRUD =
                (INeutronNetworkCRUD) ServiceHelper.getGlobalInstance(INeutronNetworkCRUD.class, this);
        NeutronNetwork nn = new NeutronNetwork();
        nn.setID(uuid);
        nn.initDefaults();
        nn.setTenantID(tenantID);
        nn.setProviderNetworkType(networkTypeVxlan);
        nn.setProviderSegmentationID(segId);
        iNeutronNetworkCRUD.addNetwork(nn);
        return nn;
    }
}
