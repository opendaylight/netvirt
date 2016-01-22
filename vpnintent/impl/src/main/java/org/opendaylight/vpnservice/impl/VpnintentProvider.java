/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.impl;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabelsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.Vpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.VpnsBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class VpnintentProvider implements BindingAwareProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnintentProvider.class);
    public static final InstanceIdentifier<Vpns> VPN_INTENT_IID = InstanceIdentifier.builder(Vpns.class).build();
    public static final InstanceIdentifier<MplsLabels> LABELS_IID = InstanceIdentifier.builder(MplsLabels.class).build();

    private DataBroker dataBroker;

    @Override
    public void onSessionInitiated(ProviderContext session) {
        LOG.info("VpnintentProvider Session Initiated");
        dataBroker = session.getSALService(DataBroker.class);

        Vpns vpns = new VpnsBuilder().build();
        MplsLabels labels = new MplsLabelsBuilder().build();

        // Initialize MD-SAL data store for vpn-intents and mpls-labels
        initDatastore(LogicalDatastoreType.CONFIGURATION, VPN_INTENT_IID, vpns);
        initDatastore(LogicalDatastoreType.OPERATIONAL, LABELS_IID, labels);
    }

    @Override
    public void close() throws Exception {
        LOG.info("VpnintentProvider Closed");
    }

    private <T extends DataObject> void initDatastore(LogicalDatastoreType store, InstanceIdentifier<T> iid, T object) {
        // Put data to MD-SAL data store
        WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(store, iid, object);

        // Perform the tx.submit asynchronously
        Futures.addCallback(transaction.submit(), new FutureCallback<Void>() {
            @Override
            public void onSuccess(final Void result) {
                LOG.info("initDatastore for VPN-Intents: transaction succeeded");
            }

            @Override
            public void onFailure(final Throwable throwable) {
                LOG.error("initDatastore for VPN-Intents: transaction failed");
            }
        });
        LOG.info("initDatastore: data populated: {}, {}, {}", store, iid, object);
    }
}
