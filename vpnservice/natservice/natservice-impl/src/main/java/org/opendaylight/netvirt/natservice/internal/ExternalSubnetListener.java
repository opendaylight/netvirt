/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalSubnetListener extends AsyncDataTreeChangeListenerBase<Subnets, ExternalSubnetListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalSubnetListener.class);
    private final DataBroker dataBroker;
    private final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer;

    public ExternalSubnetListener(final DataBroker dataBroker,
            final SNATDefaultRouteProgrammer snatDefaultRouteProgrammer) {
        this.dataBroker = dataBroker;
        this.snatDefaultRouteProgrammer = snatDefaultRouteProgrammer;
    }

    @Override
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnets> getWildCardPath() {
        return InstanceIdentifier.create(ExternalSubnets.class).child(Subnets.class);
    }

    @Override
    protected ExternalSubnetListener getDataTreeChangeListener() {
        return ExternalSubnetListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Subnets> identifier, Subnets subnets) {
        LOG.trace("NAT Service : External Subnet remove mapping method - key:{}. value={}",identifier, subnets);
        Uuid externalNetworkId = subnets.getExternalNetworkId();
        addOrDelDefaultFibRouteToSNAT(subnets, externalNetworkId.getValue(), NwConstants.DEL_FLOW);
    }

    @Override
    protected void update(InstanceIdentifier<Subnets> identifier, Subnets subnetsOrig,
            Subnets subnetsNew) {
        LOG.trace("NAT Service : External Subnet update - key:{}. original={}, new={}",
                identifier, subnetsOrig, subnetsNew);
    }

    @Override
    protected void add(InstanceIdentifier<Subnets> identifier, Subnets subnets) {
        LOG.trace("NAT Service : External Subnet add mapping method - key:{}. value={}",identifier, subnets);
        Uuid externalNetworkId = subnets.getExternalNetworkId();
        addOrDelDefaultFibRouteToSNAT(subnets, externalNetworkId.getValue(), NwConstants.ADD_FLOW);
    }

    private void addOrDelDefaultFibRouteToSNAT(Subnets subnet, String networkId, int flowAction) {
        long vpnId = NatUtil.getVpnId(dataBroker, subnet.getVpnId().getValue());
        if (vpnId == NatConstants.INVALID_ID) {
            LOG.debug("NAT Service : Invalid VPN ID for subnet {}, delaying default FIB route to SNAT flow "
                    + "installation/removal.", subnet);
            return;
        }

        snatDefaultRouteProgrammer.addOrDelDefaultFibRouteToSNATForSubnet(subnet, networkId, flowAction, vpnId);
    }
}
