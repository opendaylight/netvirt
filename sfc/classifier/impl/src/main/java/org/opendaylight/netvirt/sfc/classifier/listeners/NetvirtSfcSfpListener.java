/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.listeners;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.sfc.classifier.service.ClassifierService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.ServiceFunctionPathsState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sfp.rev140701.service.function.paths.state.ServiceFunctionPathState;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Data tree listener for AccessList.
 */
@Singleton
public class NetvirtSfcSfpListener extends
        AsyncDataTreeChangeListenerBase<ServiceFunctionPathState, NetvirtSfcSfpListener> {

    private final DataBroker dataBroker;
    private final ClassifierService classifierService;

    @Inject
    public NetvirtSfcSfpListener(final DataBroker dataBroker, final ClassifierService classifierService) {
        super(ServiceFunctionPathState.class, NetvirtSfcSfpListener.class);

        this.dataBroker = dataBroker;
        this.classifierService = classifierService;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<ServiceFunctionPathState> getWildCardPath() {
        return InstanceIdentifier
            .create(ServiceFunctionPathsState.class)
            .child(ServiceFunctionPathState.class);
    }

    @Override
    protected NetvirtSfcSfpListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void add(InstanceIdentifier<ServiceFunctionPathState> key, ServiceFunctionPathState sfp) {
        classifierService.updateAll();
    }

    @Override
    protected void remove(InstanceIdentifier<ServiceFunctionPathState> key, ServiceFunctionPathState sfp) {
        classifierService.updateAll();
    }

    @Override
    protected void update(InstanceIdentifier<ServiceFunctionPathState> key, ServiceFunctionPathState sfpBefore,
                          ServiceFunctionPathState sfpAfter) {
        classifierService.updateAll();
    }
}
