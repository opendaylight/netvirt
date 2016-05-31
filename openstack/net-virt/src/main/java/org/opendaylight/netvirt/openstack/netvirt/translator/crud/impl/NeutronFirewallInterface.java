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
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronFirewall;
import org.opendaylight.netvirt.openstack.netvirt.translator.crud.INeutronFirewallCRUD;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.fwaas.rev150712.firewalls.attributes.firewalls.Firewall;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class NeutronFirewallInterface extends AbstractNeutronInterface<Firewall, NeutronFirewall> implements INeutronFirewallCRUD {

    NeutronFirewallInterface(final DataBroker dataBroker) {
        super(dataBroker);
    }

    @Override
    public boolean neutronFirewallExists(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public NeutronFirewall getNeutronFirewall(String uuid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<NeutronFirewall> getAllNeutronFirewalls() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean addNeutronFirewall(NeutronFirewall input) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removeNeutronFirewall(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean updateNeutronFirewall(String uuid, NeutronFirewall delta) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean neutronFirewallInUse(String uuid) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    protected Firewall toMd(String uuid) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected InstanceIdentifier<Firewall> createInstanceIdentifier(
            Firewall item) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected Firewall toMd(NeutronFirewall neutronObject) {
        // TODO Auto-generated method stub
        return null;
    }
}
