/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.listeners;

import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.utils.hwvtep.HwvtepNodeHACache;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.elan.l2gw.ha.HwvtepHAUtil;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Managers;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ManagerListener extends AbstractClusteredAsyncDataTreeChangeListener<Managers> {

    private static final Logger LOG = LoggerFactory.getLogger(ManagerListener.class);

    private final DataBroker dataBroker;
    private final HwvtepNodeHACache hwvtepNodeHACache;

    @Inject
    public ManagerListener(DataBroker dataBroker, HwvtepNodeHACache hwvtepNodeHACache) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                HwvtepSouthboundUtils.createHwvtepTopologyInstanceIdentifier().child(Node.class)
                        .augmentation(HwvtepGlobalAugmentation.class).child(Managers.class),
                Executors.newListeningSingleThreadExecutor("ManagerListener", LOG));
        this.dataBroker = dataBroker;
        this.hwvtepNodeHACache = hwvtepNodeHACache;
    }

    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
    }

    @Override
    public void remove(InstanceIdentifier<Managers> key, Managers managers) {
    }

    @Override
    public void update(InstanceIdentifier<Managers> key, Managers before, Managers after) {
    }

    @Override
    public void add(InstanceIdentifier<Managers> key, Managers managers) {
        InstanceIdentifier<Node> parent = key.firstIdentifierOf(Node.class);
        if (managers.key().getTarget().getValue().contains(HwvtepHAUtil.MANAGER_KEY)
                && managers.getManagerOtherConfigs() != null) {
            managers.getManagerOtherConfigs().stream()
                .filter(otherConfig -> otherConfig.key().getOtherConfigKey().contains(HwvtepHAUtil.HA_CHILDREN))
                .flatMap(otherConfig -> Arrays.stream(otherConfig.getOtherConfigValue().split(",")))
                .map(HwvtepHAUtil::convertToInstanceIdentifier)
                .forEach(childIid -> hwvtepNodeHACache.addChild(parent, childIid));
        }
    }
}
