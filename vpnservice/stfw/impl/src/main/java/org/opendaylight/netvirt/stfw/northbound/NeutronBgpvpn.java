/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.northbound;

import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.utils.RandomUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.BgpvpnTypeL3;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.Bgpvpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.Bgpvpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.BgpvpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.bgpvpns.rev150903.bgpvpns.attributes.bgpvpns.BgpvpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronBgpvpn {

    private List<Uuid> networkIds;
    private Uuid tenantId;
    private Uuid bgpvpnId;
    private List<String> rd;
    private List<String> rt;
    //    private String ert;
//    private String irt;
    private String name;

    public List<Uuid> getNetworkId() {
        return networkIds;
    }

    public void setNetworkId(Uuid networkId) {
        this.networkIds.add(networkId);
    }

    public NeutronBgpvpn(NeutronNetwork neutronNetwork, int idx) {
        networkIds = new ArrayList<Uuid>();
        tenantId = neutronNetwork.getTenantId();
        bgpvpnId = RandomUtils.createUuid();
        rd = new ArrayList<String>();
        rd.add(RandomUtils.createRD(idx));
        rt = new ArrayList<String>();
        rt.add(RandomUtils.createRT(idx));
        name = "BGPVPN-" + idx;
    }

    public InstanceIdentifier<Bgpvpn> getBgpvpnIdentifier() {
        return InstanceIdentifier.create(Neutron.class).child(Bgpvpns.class)
            .child(Bgpvpn.class, new BgpvpnKey(bgpvpnId));
    }

    public Uuid getUuid() {
        return this.bgpvpnId;
    }

    public Bgpvpn build() {
        BgpvpnBuilder bgpvpn = new BgpvpnBuilder();
        bgpvpn.setTenantId(this.tenantId);
        bgpvpn.setUuid(this.bgpvpnId);
        bgpvpn.setAdminStateUp(true);
        bgpvpn.setAutoAggregate(false);
        bgpvpn.setRouteDistinguishers(this.rd);
        bgpvpn.setRouteTargets(this.rt);
        bgpvpn.setType(BgpvpnTypeL3.class);
        if (this.getNetworkId() != null) {
            bgpvpn.setNetworks(this.getNetworkId());
        }
        return bgpvpn.build();
    }

    public Uuid getTenantId() {
        return tenantId;
    }

    public NeutronBgpvpn createNeutronBgpvpn(WriteTransaction tx, NeutronNetwork neutronNetwork, int idx) {
        NeutronBgpvpn bgpvpn = new NeutronBgpvpn(neutronNetwork, idx);
        tx.put(LogicalDatastoreType.CONFIGURATION, bgpvpn.getBgpvpnIdentifier(), bgpvpn.build(), true);
        return bgpvpn;
    }

    public NeutronBgpvpn associate(WriteTransaction tx, NeutronNetwork neutronNetwork) {
        this.setNetworkId(neutronNetwork.getId());
        tx.merge(LogicalDatastoreType.CONFIGURATION, this.getBgpvpnIdentifier(), this.build(), true);
        return this;
    }

    public String getName() {
        return name;
    }

}
