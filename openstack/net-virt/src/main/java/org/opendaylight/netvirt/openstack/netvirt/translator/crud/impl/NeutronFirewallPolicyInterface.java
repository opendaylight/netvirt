/*
 * Copyright (c) 2014, 2015 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.translator.crud.impl;

import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronFirewallPolicy;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronFirewallPolicyCRUD;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.fwaas.rev150712.policies.attributes.FirewallPolicies;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 */

public class NeutronFirewallPolicyInterface extends AbstractNeutronInterface<FirewallPolicies, NeutronFirewallPolicy> implements INeutronFirewallPolicyCRUD {

    NeutronFirewallPolicyInterface(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public boolean neutronFirewallPolicyExists(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public NeutronFirewallPolicy getNeutronFirewallPolicy(String uuid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<NeutronFirewallPolicy> getAllNeutronFirewallPolicies() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean addNeutronFirewallPolicy(NeutronFirewallPolicy input) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removeNeutronFirewallPolicy(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean updateNeutronFirewallPolicy(String uuid,
            NeutronFirewallPolicy delta) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean neutronFirewallPolicyInUse(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected FirewallPolicies toMd(String uuid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected InstanceIdentifier<FirewallPolicies> createInstanceIdentifier(
            FirewallPolicies item) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected FirewallPolicies toMd(NeutronFirewallPolicy neutronObject) {
        // TODO Auto-generated method stub
        return null;
    }
}
