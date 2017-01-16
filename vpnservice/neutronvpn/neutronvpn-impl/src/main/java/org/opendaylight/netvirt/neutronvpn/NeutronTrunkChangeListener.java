/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunk.attributes.SubPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.Trunks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.trunks.rev170118.trunks.attributes.trunks.Trunk;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NeutronTrunkChangeListener extends AsyncDataTreeChangeListenerBase<Trunk, NeutronTrunkChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronTrunkChangeListener.class);
    private final DataBroker dataBroker;

    public NeutronTrunkChangeListener(final DataBroker dataBroker) {
        super(Trunk.class, NeutronTrunkChangeListener.class);
        this.dataBroker = dataBroker;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Trunk> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Trunks.class).child(Trunk.class);
    }

    @Override
    protected NeutronTrunkChangeListener getDataTreeChangeListener() {
        return NeutronTrunkChangeListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<Trunk> identifier, Trunk input) {
        Preconditions.checkNotNull(input.getPortId());
        NeutronvpnUtils.addToTrunkPortsCache(input);
        Port port = NeutronvpnUtils.getNeutronPort(dataBroker, input.getPortId());
        LOG.trace("Adding Trunk : key: {}, value={} to port {}",
                identifier, input, port.getName());
    }

    @Override
    protected void remove(InstanceIdentifier<Trunk> identifier, Trunk input) {
        Preconditions.checkNotNull(input.getPortId());
        NeutronvpnUtils.removeFromTrunkPortsCache(input);
        Port port = NeutronvpnUtils.getNeutronPort(dataBroker, input.getPortId());
        LOG.trace("Removing Trunk : key: {}, value={} from port {}",
                identifier, input, port.getName());
    }

    @Override
    protected void update(InstanceIdentifier<Trunk> identifier, Trunk original, Trunk update) {
        NeutronvpnUtils.addToTrunkPortsCache(update);
        List<SubPorts> added = new ArrayList<>(update.getSubPorts());
        List<SubPorts> deleted = new ArrayList<>(original.getSubPorts());
        added.retainAll(original.getSubPorts());
        deleted.retainAll(update.getSubPorts());
        for (SubPorts subPort:deleted) {
            NeutronvpnUtils.removeFromSubPortsCache(subPort);
        }
        for (SubPorts subPort:added) {
            NeutronvpnUtils.addToSubPortsCache(update.getPortId(), subPort);
        }
        /*
         * TODO:
         *     Add Vlan information to corresponding interface
         *     It will get added when we spawn VM on this port
         *     but adding it now should help with sync issues.
         */
        LOG.trace("Updated Trunk : key: {}. subPortsAdded={}, subPortsDeleted={}", added, deleted);
    }

}
