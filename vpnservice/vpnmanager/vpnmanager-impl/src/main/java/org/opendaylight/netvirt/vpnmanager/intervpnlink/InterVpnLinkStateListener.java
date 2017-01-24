/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class responsible for listening to changes in the State of an InterVpnLink,
 * specially for when the InterVpnLink becomes active
 *
 */
public class InterVpnLinkStateListener
    extends AsyncDataTreeChangeListenerBase<InterVpnLinkState, InterVpnLinkStateListener> {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkStateListener.class);

    private final DataBroker dataBroker;
    private final IVpnLinkService ivpnLinkService;

    public InterVpnLinkStateListener(final DataBroker dataBroker, final IVpnLinkService interVpnLinkService) {
        super(InterVpnLinkState.class, InterVpnLinkStateListener.class);
        this.dataBroker = dataBroker;
        this.ivpnLinkService = interVpnLinkService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<InterVpnLinkState> getWildCardPath() {
        return InstanceIdentifier.create(InterVpnLinkStates.class).child(InterVpnLinkState.class);
    }

    @Override
    protected InterVpnLinkStateListener getDataTreeChangeListener() {
        return InterVpnLinkStateListener.this;
    }


    @Override
    protected void remove(InstanceIdentifier<InterVpnLinkState> key, InterVpnLinkState dataObjectModification) {
    }

    @Override
    protected void update(InstanceIdentifier<InterVpnLinkState> key, InterVpnLinkState before,
                          InterVpnLinkState after) {
        if (before.getState() == InterVpnLinkState.State.Error && after.getState() == InterVpnLinkState.State.Active) {
            Optional<InterVpnLinkDataComposite> optIVpnLink =
                InterVpnLinkCache.getInterVpnLinkByName(after.getInterVpnLinkName());

            if (!optIVpnLink.isPresent()) {
                LOG.warn("InterVpnLink {} became ACTIVE, but could not found its info in Cache");
                InterVpnLinkCache.addInterVpnLinkStateToCaches(after);
                optIVpnLink = InterVpnLinkCache.getInterVpnLinkByName(after.getInterVpnLinkName());
            }
            ivpnLinkService.handleStaticRoutes(optIVpnLink.get());
        }
    }


    @Override
    protected void add(InstanceIdentifier<InterVpnLinkState> key, InterVpnLinkState dataObjectModification) {
    }
}
