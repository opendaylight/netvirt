/*
 * Copyright (c) 2016 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.openstack.netvirt.providers;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.openstack.netvirt.providers.openflow13.Service;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.providers.config.rev160109.NetvirtProvidersConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.providers.config.rev160109.NetvirtProvidersConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.table.types.rev131026.TableId;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NetvirtProvidersConfigImpl implements AutoCloseable, DataChangeListener {

    private static final Logger LOG = LoggerFactory.getLogger(NetvirtProvidersConfigImpl.class);

    private final ListenerRegistration<DataChangeListener> registration;
    private final ExecutorService executorService = Executors.newFixedThreadPool(1);
    private final MdsalUtils mdsalUtils;

    private static short tableOffset;

    public NetvirtProvidersConfigImpl(final DataBroker dataBroker, short tableOffset) {
        mdsalUtils = new MdsalUtils(dataBroker);
        NetvirtProvidersConfigImpl.tableOffset = tableOffset;
        InstanceIdentifier<NetvirtProvidersConfig> path =
                InstanceIdentifier.builder(NetvirtProvidersConfig.class).build();
        registration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, path, this,
                AsyncDataBroker.DataChangeScope.SUBTREE);

        NetvirtProvidersConfigBuilder netvirtProvidersConfigBuilder = new NetvirtProvidersConfigBuilder();
        NetvirtProvidersConfig netvirtProvidersConfig =
                mdsalUtils.read(LogicalDatastoreType.CONFIGURATION, path);
        if (netvirtProvidersConfig != null) {
            netvirtProvidersConfigBuilder = new NetvirtProvidersConfigBuilder(netvirtProvidersConfig);
        }
        if (netvirtProvidersConfigBuilder.getTableOffset() == null) {
            netvirtProvidersConfigBuilder.setTableOffset(tableOffset);
        }
        boolean result = mdsalUtils.merge(LogicalDatastoreType.CONFIGURATION, path,
                netvirtProvidersConfigBuilder.build());

        LOG.info("NetvirtProvidersConfigImpl: dataBroker= {}, registration= {}, tableOffset= {}, result= {}",
                dataBroker, registration, tableOffset, result);
    }

    @Override
    public void close() {
        if (registration != null) {
            registration.close();
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public static void setTableOffset(short tableOffset) {
        try {
            new TableId((short) (tableOffset + Service.L2_FORWARDING.getTable()));
        } catch (IllegalArgumentException e) {
            LOG.warn("Invalid table offset: {}", tableOffset, e);
            return;
        }

        LOG.info("setTableOffset: changing from {} to {}",
                NetvirtProvidersConfigImpl.tableOffset, tableOffset);
        NetvirtProvidersConfigImpl.tableOffset = tableOffset;
    }

    @Override
    public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> asyncDataChangeEvent) {
        executorService.submit(new Runnable() {

            @Override
            public void run() {
                LOG.info("onDataChanged: {}", asyncDataChangeEvent);
                processConfigCreate(asyncDataChangeEvent);
                processConfigUpdate(asyncDataChangeEvent);
            }
        });
    }

    private void processConfigCreate(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes) {
        for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : changes.getCreatedData().entrySet()) {
            if (entry.getValue() instanceof NetvirtProvidersConfig) {
                NetvirtProvidersConfig netvirtProvidersConfig = (NetvirtProvidersConfig) entry.getValue();
                applyConfig(netvirtProvidersConfig);
            }
        }
    }

    private void processConfigUpdate(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changes) {
        for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : changes.getUpdatedData().entrySet()) {
            if (entry.getValue() instanceof NetvirtProvidersConfig) {
                LOG.info("processConfigUpdate: {}", entry);
                NetvirtProvidersConfig netvirtProvidersConfig = (NetvirtProvidersConfig) entry.getValue();
                applyConfig(netvirtProvidersConfig);
            }
        }
    }

    private void applyConfig(NetvirtProvidersConfig netvirtProvidersConfig) {
        LOG.info("processConfigUpdate: {}", netvirtProvidersConfig);
        if (netvirtProvidersConfig.getTableOffset() != null) {
            NetvirtProvidersConfigImpl.setTableOffset(netvirtProvidersConfig.getTableOffset());
        }
    }

    public static short getTableOffset() {
        return tableOffset;
    }
}
