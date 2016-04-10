/*
 * Copyright (c) 2016 Intel Corporation.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.netvirt.ClusterAwareMdsalUtils;
import org.opendaylight.netvirt.openstack.netvirt.ConfigInterface;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryListener;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbInventoryService;
import org.opendaylight.netvirt.openstack.netvirt.api.Southbound;
import org.opendaylight.netvirt.openstack.netvirt.api.OvsdbTables;
import org.opendaylight.netvirt.utils.servicehelper.ServiceHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.Hostconfigs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.Hostconfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.hostconfig.rev150712.hostconfig.attributes.hostconfigs.HostconfigBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class HostConfigService implements OvsdbInventoryListener, ConfigInterface {
    private static final Logger LOG = LoggerFactory.getLogger(HostConfigService.class);

    private static final String OS_HOST_CONFIG_HOST_ID_KEY = "odl_os_hostconfig_hostid";
    private static final String OS_HOST_CONFIG_HOST_TYPE_KEY = "odl_os_hostconfig_hosttype";
    private static final String OS_HOST_CONFIG_CONFIG_KEY = "odl_os_hostconfig_config";

    private final DataBroker databroker;
    private final ClusterAwareMdsalUtils mdsalUtils;
    private volatile OvsdbInventoryService ovsdbInventoryService;
    private volatile Southbound southbound;

    public HostConfigService(DataBroker dataBroker) {
        this.databroker = dataBroker;
        mdsalUtils = new ClusterAwareMdsalUtils(dataBroker);
    }

    @Override
    public void ovsdbUpdate(Node node, DataObject resourceAugmentationData, OvsdbType ovsdbType, Action action) {
        LOG.info("ovsdbUpdate: {} - {} - <<{}>> <<{}>>", ovsdbType, action, node, resourceAugmentationData);
        boolean result;
        Hostconfig hostConfig;
        InstanceIdentifier<Hostconfig> hostConfigId;

        if (ovsdbType != OvsdbType.NODE) {
            return;
        }
        hostConfig = extractHostConfigInfo(node);
        if (hostConfig == null) {
              return;
        }
        switch (action) {
            case ADD:
            case UPDATE:
                    hostConfigId = createInstanceIdentifier(hostConfig);
                    result = mdsalUtils.put(LogicalDatastoreType.OPERATIONAL, hostConfigId, hostConfig);
                    LOG.info("Add Node: result: {}", result);
                break;
            case DELETE:
                    hostConfigId = createInstanceIdentifier(hostConfig);
                    result = mdsalUtils.delete(LogicalDatastoreType.OPERATIONAL, hostConfigId);
                    LOG.info("Delete Node: result: {}", result);
                break;
        }
    }

    @Override
    public void triggerUpdates() {
        LOG.info("triggerUpdates");
    }

    private Hostconfig extractHostConfigInfo(Node node) {
        HostconfigBuilder hostconfigBuilder = new HostconfigBuilder();
        String value;

        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_HOST_ID_KEY);
        if (value == null){
            LOG.info("Host Config not defined for the node");
            return null;
        }
        hostconfigBuilder.setHostId(value);
        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_HOST_TYPE_KEY);
        if (value == null) {
            LOG.warn("Host Config Missing Host type");
            return null;
        }
        hostconfigBuilder.setHostType(value);
        value = southbound.getExternalId(node, OvsdbTables.OPENVSWITCH, OS_HOST_CONFIG_CONFIG_KEY);
        if (value == null) {
            LOG.warn("Host Config Missing Host config");
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

    @Override
    public void setDependencies(ServiceReference serviceReference) {
        southbound =
                (Southbound) ServiceHelper.getGlobalInstance(Southbound.class, this);
        ovsdbInventoryService =
                (OvsdbInventoryService) ServiceHelper.getGlobalInstance(OvsdbInventoryService.class, this);
        ovsdbInventoryService.listenerAdded(this);
    }

    @Override
    public void setDependencies(Object impl) {
    }
}
