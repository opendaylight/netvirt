/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.Arrays;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.utils.hwvtep.HwvtepHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ManagerListener extends AsyncClusteredDataTreeChangeListenerBase<Managers, ManagerListener> {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerListener.class);

    private final DataBroker dataBroker;

    @Inject
    public ManagerListener(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Managers> getWildCardPath() {
        return HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier()
                .child(Node.class)
                .augmentation(HwvtepGlobalAugmentation.class)
                .child(Managers.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Managers> key, Managers managers) {
    }

    @Override
    protected void update(InstanceIdentifier<Managers> key, Managers before, Managers after) {
    }

    @Override
    protected void add(InstanceIdentifier<Managers> key, Managers managers) {
        InstanceIdentifier<Node> parent = key.firstIdentifierOf(Node.class);
        if (managers.getKey().getTarget().getValue().contains(HwvtepHAUtil.MANAGER_KEY)
                && managers.getManagerOtherConfigs() != null) {
            managers.getManagerOtherConfigs().stream()
                .filter(otherConfig -> otherConfig.getKey().getOtherConfigKey().contains(HwvtepHAUtil.HA_CHILDREN))
                .flatMap(otherConfig -> Arrays.stream(otherConfig.getOtherConfigValue().split(",")))
                .map(HwvtepHAUtil::convertToInstanceIdentifier)
                .forEach(childIid -> HwvtepHACache.getInstance().addChild(parent, childIid));
        }
    }

    @Override
    protected ManagerListener getDataTreeChangeListener() {
        return this;
    }
}
