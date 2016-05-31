/*
 * Copyright (c) 2016 Intel Corporation.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.impl;

import java.util.List;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.netvirt.ClusterAwareMdsalUtils;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryListener;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryService;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbTables;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.Hostconfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.Hostconfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.HostconfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HostConfigService implements OvsdbInventoryListener {
    private static final Logger LOG = LoggerFactory.getLogger(HostConfigService.class);

    private static final String OS_HOST_CONFIG_HOST_ID_KEY = "odl_os_hostconfig_hostid";
    private static final String OS_HOST_CONFIG_HOST_TYPE_KEY = "odl_os_hostconfig_hosttype";
    private static final String OS_HOST_CONFIG_CONFIG_KEY = "odl_os_hostconfig_config";

    private final ClusterAwareMdsalUtils mdsalUtils;
    private final Southbound southbound;

    public HostConfigService(final ClusterAwareMdsalUtils mdsalUtils,
            final OvsdbInventoryService ovsdbInventoryService,
            final Southbound southbound) {
        this.mdsalUtils = mdsalUtils;
        this.southbound = southbound;

        ovsdbInventoryService.listenerAdded(this);
    }

    @Override
    public void ovsdbUpdate(Node node, DataObject resourceAugmentationData, OvsdbType ovsdbType, Action action) {
        boolean result;
        Hostconfig hostConfig;
        InstanceIdentifier<Hostconfig> hostConfigId;

        if (ovsdbType != OvsdbType.NODE) {
            return;
        }
        hostConfig = buildHostConfigInfo(node);
        if (hostConfig == null) {
              return;
        }
        LOG.trace("ovsdbUpdate: {} - {} - <<{}>> <<{}>>", ovsdbType, action, node, resourceAugmentationData);
        switch (action) {
            case ADD:
            case UPDATE:
                    hostConfigId = createInstanceIdentifier(hostConfig);
                    result = mdsalUtils.put(LogicalDatastoreType.OPERATIONAL, hostConfigId, hostConfig);
                    LOG.trace("Add Node: result: {}", result);
                break;
            case DELETE:
                    hostConfigId = createInstanceIdentifier(hostConfig);
                    result = mdsalUtils.delete(LogicalDatastoreType.OPERATIONAL, hostConfigId);
                    LOG.trace("Delete Node: result: {}", result);
                break;
        }
    }

    @Override
    public void triggerUpdates() {
        List<Node> ovsdbNodes = southbound.readOvsdbTopologyNodes();
        for (Node node : ovsdbNodes) {
            ovsdbUpdate(node, node.getAugmentation(OvsdbNodeAugmentation.class),
                    OvsdbInventoryListener.OvsdbType.NODE, Action.ADD);
        }
    }

    private Hostconfig buildHostConfigInfo(Node node) {
        HostconfigBuilder hostconfigBuilder = new HostconfigBuilder();
        String value;

        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_HOST_ID_KEY);
        if (value == null){
            return null;
        }
        hostconfigBuilder.setHostId(value);
        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_HOST_TYPE_KEY);
        if (value == null) {
            return null;
        }
        hostconfigBuilder.setHostType(value);
        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_CONFIG_KEY);
        if (value == null) {
            return null;
        }
        hostconfigBuilder.setConfig(value);
        return hostconfigBuilder.build();
    }

    private InstanceIdentifier<Hostconfig> createInstanceIdentifier() {
        return InstanceIdentifier.create(Neutron.class)
                .child(Hostconfigs.class)
                .child(Hostconfig.class);
    }

    private InstanceIdentifier<Hostconfig> createInstanceIdentifier(Hostconfig hostconfig) {
        return InstanceIdentifier.create(Neutron.class)
                .child(Hostconfigs.class)
                .child(Hostconfig.class, hostconfig.getKey());
    }
}
