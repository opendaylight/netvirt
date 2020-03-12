/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpPortInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.port.info.FloatingIpIdToPortMapping;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class FloatingIPPortInfoListener extends AsyncDataTreeChangeListenerBase<FloatingIpIdToPortMapping,
        FloatingIPPortInfoListener> {

    private static final Logger LOG = LoggerFactory.getLogger(FloatingIPPortInfoListener.class);

    private final DataBroker dataBroker;
    private final FloatingIPListener floatingIPListener;


    @Inject
    public FloatingIPPortInfoListener(DataBroker dataBroker,
                                      FloatingIPListener floatingIPListener) {
        super(FloatingIpIdToPortMapping.class, FloatingIPPortInfoListener.class);
        this.dataBroker = dataBroker;
        this.floatingIPListener = floatingIPListener;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, this.dataBroker);
    }

    @Override
    protected FloatingIPPortInfoListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected InstanceIdentifier<FloatingIpIdToPortMapping> getWildCardPath() {
        return InstanceIdentifier.builder(FloatingIpPortInfo.class).child(FloatingIpIdToPortMapping.class).build();
    }

    @Override
    protected void add(InstanceIdentifier<FloatingIpIdToPortMapping> identifier,
                       final FloatingIpIdToPortMapping floatingIpIdToPortMapping) {
        floatingIPListener.processPendingFloatingIPs(floatingIpIdToPortMapping.getFloatingIpId().getValue());
    }

    @Override
    protected void remove(InstanceIdentifier<FloatingIpIdToPortMapping> identifier,
                          final FloatingIpIdToPortMapping floatingIpIdToPortMapping) {
        floatingIPListener.removePendingFloatingIPs(floatingIpIdToPortMapping.getFloatingIpId().getValue());
    }

    @Override
    protected void update(InstanceIdentifier<FloatingIpIdToPortMapping> identifier, FloatingIpIdToPortMapping original,
                          FloatingIpIdToPortMapping update) {
    }
}
