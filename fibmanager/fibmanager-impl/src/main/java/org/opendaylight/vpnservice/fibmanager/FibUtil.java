/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.fibmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

public class FibUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FibUtil.class);
    static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    static <T extends DataObject> void asyncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                  InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    static <T extends DataObject> void syncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                 InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        tx.submit();
    }

    static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    static InstanceIdentifier<Adjacency> getAdjacencyIdentifier(String vpnInterfaceName, String ipAddress) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces.class)
                .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface.class, new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey(vpnInterfaceName)).augmentation(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies.class).child(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.Adjacency.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.adjacency.list.AdjacencyKey(ipAddress)).build();
    }

    static InstanceIdentifier<Adjacencies> getAdjListPath(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces.class)
                .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface.class, new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey(vpnInterfaceName)).augmentation(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.Adjacencies.class).build();
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(long vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.PrefixToInterface.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.VpnIds.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.VpnIdsKey(vpnId)).child(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces.class)
                .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface.class, new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.VpnInstanceOpData.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey(rd))
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey(dpnId)).build();
    }

    static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                public void onSuccess(Void result) {
                    LOG.debug("Success in Datastore operation");
                }

                public void onFailure(Throwable error) {
                    LOG.error("Error in Datastore operation", error);
                };
            };

}
