/*
 * Copyright (c) 2013, 2015 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt;

import com.google.common.base.Preconditions;
import java.net.HttpURLConnection;
import org.opendaylight.netvirt.openstack.netvirt.api.Action;
import org.opendaylight.netvirt.openstack.netvirt.api.EventDispatcher;
import org.opendaylight.netvirt.openstack.netvirt.impl.NeutronL3Adapter;
import org.opendaylight.netvirt.openstack.netvirt.translator.NeutronSubnet;
import org.opendaylight.netvirt.openstack.netvirt.translator.iaware.INeutronSubnetAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SubnetHandler extends AbstractHandler implements INeutronSubnetAware {

    private static final Logger LOG = LoggerFactory.getLogger(SubnetHandler.class);

    // The implementation for each of these services is resolved by the OSGi Service Manager
    private final NeutronL3Adapter neutronL3Adapter;

    public SubnetHandler(final NeutronL3Adapter neutronL3Adapter,
            final EventDispatcher eventDispatcher) {
        this.neutronL3Adapter = neutronL3Adapter;
        this.eventDispatcher = eventDispatcher;
        eventDispatcher.eventHandlerAdded(
                AbstractEvent.HandlerType.NEUTRON_SUBNET, this);
    }

    @Override
    public int canCreateSubnet(NeutronSubnet subnet) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSubnetCreated(NeutronSubnet subnet) {
        enqueueEvent(new NorthboundEvent(subnet, Action.ADD));
    }

    @Override
    public int canUpdateSubnet(NeutronSubnet delta, NeutronSubnet original) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSubnetUpdated(NeutronSubnet subnet) {
        enqueueEvent(new NorthboundEvent(subnet, Action.UPDATE));
    }

    @Override
    public int canDeleteSubnet(NeutronSubnet subnet) {
        return HttpURLConnection.HTTP_OK;
    }

    @Override
    public void neutronSubnetDeleted(NeutronSubnet subnet) {
        enqueueEvent(new NorthboundEvent(subnet, Action.DELETE));
    }

    /**
     * Process the event.
     *
     * @param abstractEvent the {@link AbstractEvent} event to be handled.
     * @see EventDispatcher
     */
    @Override
    public void processEvent(AbstractEvent abstractEvent) {
        if (!(abstractEvent instanceof NorthboundEvent)) {
            LOG.error("Unable to process abstract event {}", abstractEvent);
            return;
        }
        NorthboundEvent ev = (NorthboundEvent) abstractEvent;
        switch (ev.getAction()) {
            case ADD:
                // fall through
            case DELETE:
                // fall through
            case UPDATE:
                Preconditions.checkNotNull(neutronL3Adapter);
                neutronL3Adapter.handleNeutronSubnetEvent(ev.getSubnet(), ev.getAction());
                break;
            default:
                LOG.warn("Unable to process event action {}", ev.getAction());
                break;
        }
    }
}