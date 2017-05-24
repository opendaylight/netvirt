/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceOpListener extends AsyncDataTreeChangeListenerBase<VpnInterface, VpnInterfaceOpListener>
    implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnFootprintService vpnFootprintService;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /*public VpnInterfaceOpListener(final DataBroker dataBroker) {
        super(VpnInterface.class);
        this.dataBroker = dataBroker;
    }*/

    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager,
        final VpnFootprintService vpnFootprintService) {
        super(VpnInterface.class, VpnInterfaceOpListener.class);
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInterface> getWildCardPath() {
        return InstanceIdentifier.create(VpnInterfaces.class).child(VpnInterface.class);
    }

    @Override
    protected VpnInterfaceOpListener getDataTreeChangeListener() {
        return VpnInterfaceOpListener.this;
    }


    @Override
    protected void remove(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface del) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();
        final String vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(del);
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> {
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                postProcessVpnInterfaceRemoval(identifier, del, writeOperTxn);
                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeOperTxn.submit());
                LOG.info("remove: Removed vpn operational data for interface {} on dpn {} vpn {}", del.getName(),
                        del.getDpnId(), VpnHelper.getFirstVpnNameFromVpnInterface(del));
                return futures;
            });
    }

    private void postProcessVpnInterfaceRemoval(InstanceIdentifier<VpnInterface> identifier, VpnInterface del,
        WriteTransaction writeOperTxn) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();
        String vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(del);

        LOG.info("postProcessVpnInterfaceRemoval: interface name {} vpnName {} dpn {}", interfaceName, vpnName,
                del.getDpnId());
        //decrement the vpn interface count in Vpn Instance Op Data
        Optional<VpnInstance> vpnInstance = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                                         VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));

        if (vpnInstance.isPresent()) {
            String rd = null;
            rd = vpnInstance.get().getVrfId();
            //String rd = getRouteDistinguisher(del.getVpnInstanceName());

            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);

            Adjacencies adjs = del.getAugmentation(Adjacencies.class);
            List<Adjacency> adjList = (adjs != null) ? adjs.getAdjacency() : null;

            if (vpnInstOp != null && adjList != null && adjList.size() > 0) {
                /*
                 * When a VPN Interface is removed by FibManager (aka VrfEntryListener and its cohorts),
                 * one adjacency for that VPN Interface will be hanging around along with that
                 * VPN Interface.   That adjacency could be primary (or) non-primary.
                 * If its a primary adjacency, then a prefix-to-interface entry will be available for the
                 * same.  If its a non-primary adjacency, then a prefix-to-interface entry will not be
                 * available for the same, instead we will have vpn-to-extraroutes filled in for them.
                 *
                 * Here we try to remove prefix-to-interface entry for pending adjacency in the deleted
                 * vpnInterface.   More importantly, we also update the vpnInstanceOpData by removing this
                 * vpnInterface from it.
                 */
                // TODO(vivek) # It is not yet clear, where we are cleaning up the prefix-to-interface
                // TODO(vivek) # for primary adjacencies and that has to be fixed.
                Adjacency adjacency = adjs.getAdjacency().get(0);
                List<Prefixes> prefixToInterface = new ArrayList<>();
                Optional<Prefixes> prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                        VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                if (prefix.isPresent()) {
                    prefixToInterface.add(prefix.get());
                }
                if (prefixToInterface.isEmpty()) {
                    for (String nh : adjacency.getNextHopIpList()) {
                        prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(nh)));
                        if (prefix.isPresent()) {
                            prefixToInterface.add(prefix.get());
                        }
                    }
                }
                /*
                 * In VPN Migration scenarios, there is a race condition where we use the new DPNID
                 * for the migrated VM instead of old DPNID because when we read prefix-to-interface to cleanup
                 * old DPNID, we actually get the new DPNID.
                 *
                 * More dangerously, we tend to alter the new prefix-to-interface which should be retained intac
                 * for the migration to succeed in L3VPN.  As a workaround, here we are going to use the dpnId in
                 * the deleted vpnInterface itself instead of tinkering with the prefix-to-interface.  Further we
                 * will tinker prefix-to-interface only when are damn sure if its value matches our
                 * deleted vpnInterface.
                 *
                 */
                for (Prefixes pref : prefixToInterface) {
                    if (isMatchedPrefixToInterface(pref, del)) {
                        if (writeOperTxn != null) {
                            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()));
                        } else {
                            VpnUtil.delete(dataBroker, LogicalDatastoreType.OPERATIONAL,
                                    VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(), pref.getIpAddress()),
                                    VpnUtil.DEFAULT_CALLBACK);
                        }
                    }
                }
                if (del.getDpnId() != null) {
                    vpnFootprintService.updateVpnToDpnMapping(del.getDpnId(), VpnHelper
                            .getFirstVpnNameFromVpnInterface(del), rd,
                            interfaceName, null /*ipAddressSourceValuePair*/,
                            false /* do delete */);
                }
            }
            LOG.info("postProcessVpnInterfaceRemoval: Removed vpn operational data and updated vpn footprint"
                    + " for interface {} on dpn {} vpn {}", interfaceName, del.getDpnId(), vpnName);
        } else {
            LOG.error("postProcessVpnInterfaceRemoval: rd not retrievable as vpninstancetovpnid for vpn {} is absent,"
                    + " trying rd as {}. interface {} dpn {}", vpnName, vpnName, interfaceName,
                    del.getDpnId());
        }
        notifyTaskIfRequired(interfaceName);
    }

    private boolean isMatchedPrefixToInterface(Prefixes prefix, VpnInterface vpnInterface) {
        if ((prefix != null) && (vpnInterface != null)) {
            if (prefix.getDpnId() != null && vpnInterface.getDpnId() != null) {
                if (prefix.getVpnInterfaceName() != null && vpnInterface.getName() != null) {
                    return ((prefix.getDpnId().equals(vpnInterface.getDpnId()))
                            && (prefix.getVpnInterfaceName().equalsIgnoreCase(vpnInterface.getName())));
                }
            }
        }
        return false;
    }

    private void notifyTaskIfRequired(String intfName) {
        Runnable notifyTask = vpnInterfaceManager.isNotifyTaskQueued(intfName);
        if (notifyTask == null) {
            LOG.debug("notifyTaskIfRequired: No tasks queued to wait for deletion of vpnInterface {}", intfName);
            return;
        }
        executorService.execute(notifyTask);
    }

    @Override
    protected void update(final InstanceIdentifier<VpnInterface> identifier, final VpnInterface original,
        final VpnInterface update) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        final String interfaceName = key.getName();

        LOG.trace("update: VpnInterfaceOpListener updated: original {} updated {}", original, update);
        if (VpnHelper.getFirstVpnNameFromVpnInterface(original)
            .equals(VpnHelper.getFirstVpnNameFromVpnInterface(update))) {
            return;
        }

        final String vpnName = VpnHelper.getFirstVpnNameFromVpnInterface(update);
        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        dataStoreCoordinator.enqueueJob("VPNINTERFACE-" + interfaceName,
            () -> {
                postProcessVpnInterfaceUpdate(identifier, original, update);
                LOG.info("update: vpn interface {} on dpn {} vpn {} processed successfully", update.getName(),
                     update.getDpnId(), VpnHelper.getFirstVpnNameFromVpnInterface(update));
                return null;
            });
    }

    private void postProcessVpnInterfaceUpdate(InstanceIdentifier<VpnInterface> identifier, VpnInterface original,
        VpnInterface update) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class, VpnInterfaceKey.class);
        String interfaceName = key.getName();

        LOG.info("postProcessVpnInterfaceUpdate: interface name {} vpnName {} dpn {}", interfaceName,
                VpnHelper.getFirstVpnNameFromVpnInterface(update), update.getDpnId());
        //increment the vpn interface count in Vpn Instance Op Data
        VpnInstanceOpDataEntry vpnInstOp = null;
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance> origVpnInstance =
            VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                         VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(
                                  VpnHelper.getFirstVpnNameFromVpnInterface(original)));

        if (origVpnInstance.isPresent()) {
            String rd = origVpnInstance.get().getVrfId();

            vpnInstOp = VpnUtil.getVpnInstanceOpData(dataBroker, rd);

            Adjacencies adjs = original.getAugmentation(Adjacencies.class);
            List<Adjacency> adjList = (adjs != null) ? adjs.getAdjacency() : null;

            if (vpnInstOp != null && adjList != null && adjList.size() > 0) {
                Adjacency adjacency = adjs.getAdjacency().get(0);
                List<Prefixes> prefixToInterfaceList = new ArrayList<>();
                Optional<Prefixes> prefixToInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                        VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                if (prefixToInterface.isPresent()) {
                    prefixToInterfaceList.add(prefixToInterface.get());
                } else {
                    for (String adj : adjacency.getNextHopIpList()) {
                        prefixToInterface = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(adj)));
                        if (prefixToInterface.isPresent()) {
                            prefixToInterfaceList.add(prefixToInterface.get());
                        }
                    }
                }
                for (Prefixes prefix : prefixToInterfaceList) {
                    vpnFootprintService.updateVpnToDpnMapping(prefix.getDpnId(),
                        VpnHelper.getFirstVpnNameFromVpnInterface(original), rd,
                        interfaceName, null /*ipAddressSourceValuePair*/, false /* delete */);
                }
            }
            LOG.info("postProcessVpnInterfaceUpdate: Updated vpn operational data and vpn footprint"
                    + " for interface {} on dpn {} vpn {}", interfaceName, update.getDpnId(),
                    VpnHelper.getFirstVpnNameFromVpnInterface(update));
        } else {
            LOG.error("postProcessVpnInterfaceUpdate: rd not retrievable as vpninstancetovpnid for vpn {} is absent,"
                    + " trying rd as {}. interface {} dpn {}", VpnHelper.getFirstVpnNameFromVpnInterface(update),
                    VpnHelper.getFirstVpnNameFromVpnInterface(update), interfaceName, update.getDpnId());
        }
        notifyTaskIfRequired(interfaceName);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> identifier, VpnInterface add) {
    }
}
