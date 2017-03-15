/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.processors.NetvirtSfcDataProcessorBase;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for AccessList.
 */
public class NetvirtSfcRspListener extends AsyncDataTreeChangeListenerBase<RenderedServicePath, NetvirtSfcRspListener>
implements AutoCloseable {

    private final DataBroker dataBroker;
    private final NetvirtSfcDataProcessorBase<RenderedServicePath> dataProcessor;

    @Inject
    public NetvirtSfcRspListener(DataBroker dataBroker,
            NetvirtSfcDataProcessorBase<RenderedServicePath> dataProcessor) {
        super(RenderedServicePath.class, NetvirtSfcRspListener.class);

        this.dataBroker = dataBroker;
        this.dataProcessor = dataProcessor;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected NetvirtSfcRspListener getDataTreeChangeListener() {
        return null;
    }

    @Override
    protected InstanceIdentifier<RenderedServicePath> getWildCardPath() {
        return null;
    }

    @Override
    protected void add(InstanceIdentifier<RenderedServicePath> key, RenderedServicePath rsp) {
        dataProcessor.add(key, rsp);
    }

    @Override
    protected void remove(InstanceIdentifier<RenderedServicePath> key, RenderedServicePath rsp) {
        dataProcessor.remove(key, rsp);
    }

    @Override
    protected void update(InstanceIdentifier<RenderedServicePath> key, RenderedServicePath rspBefore,
            RenderedServicePath rspAfter) {
        dataProcessor.update(key, rspBefore, rspAfter);
    }
}
