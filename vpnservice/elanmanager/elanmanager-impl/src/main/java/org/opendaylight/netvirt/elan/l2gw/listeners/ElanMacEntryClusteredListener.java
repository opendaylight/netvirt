/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.listeners;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ListenableFuture;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.netvirt.elan.utils.ElanClusterUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanMacEntryClusteredListener
        extends AsyncClusteredDataTreeChangeListenerBase<MacEntry, ElanMacEntryClusteredListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ElanMacEntryClusteredListener.class);

    private final DataBroker broker;
    private final ElanL2GatewayUtils elanL2GatewayUtils;
    private final ElanClusterUtils elanClusterUtils;
    private final ElanUtils elanUtils;

    @Inject
    public ElanMacEntryClusteredListener(DataBroker broker,
                                         ElanL2GatewayUtils elanL2GatewayUtils,
                                         ElanClusterUtils elanClusterUtils,
                                         ElanUtils elanUtils) {
        this.broker = broker;
        this.elanL2GatewayUtils = elanL2GatewayUtils;
        this.elanClusterUtils = elanClusterUtils;
        this.elanUtils = elanUtils;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, this.broker);
    }

    @Override
    protected InstanceIdentifier<MacEntry> getWildCardPath() {
        return InstanceIdentifier.builder(
                ElanForwardingTables.class).child(MacTable.class).child(MacEntry.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<MacEntry> identifier, MacEntry deleted) {
        elanClusterUtils.runOnlyInOwnerNode("Removing elan mac from l2gw devices",
                HwvtepHAUtil.L2GW_JOB_KEY + deleted.getMacAddress().getValue(), () -> {
                List<ListenableFuture<Void>> result = null;
                String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
                elanL2GatewayUtils.removeMacsFromElanExternalDevices(elanName,
                        Collections.singletonList(deleted.getMacAddress()));
                return Collections.emptyList();
            });
    }

    @Override
    protected void update(InstanceIdentifier<MacEntry> identifier, MacEntry before, MacEntry after) {
        add(identifier, after);
    }

    @Override
    protected void add(InstanceIdentifier<MacEntry> identifier, MacEntry added) {
        elanClusterUtils.runOnlyInOwnerNode("Adding elan mac to l2gw devices",
                HwvtepHAUtil.L2GW_JOB_KEY + added.getMacAddress().getValue(), () -> {
                List<ListenableFuture<Void>> result = null;
                String elanName = identifier.firstKeyOf(MacTable.class).getElanInstanceName();
                ElanInstance elanInstance = elanUtils.getElanInstanceByName(broker, elanName);
                if (elanUtils.isVxlanNetworkOrVxlanSegment(elanInstance)) {
                    BigInteger dpId = elanL2GatewayUtils.getDpidFromInterface(added.getInterface());
                    return elanL2GatewayUtils.scheduleAddDpnMacInExtDevices(elanName, dpId,
                            Lists.newArrayList(added.getMacAddress()));
                }
                return Collections.emptyList();
            });
    }

    @Override
    protected ElanMacEntryClusteredListener getDataTreeChangeListener() {
        return this;
    }
}
