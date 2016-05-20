/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInstanceListener extends AbstractDataChangeListener<VpnInstance> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInstanceListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;

    private static final ThreadFactory threadFactory = new ThreadFactoryBuilder()
            .setNameFormat("NV-VpnMgr-%d").build();
    private ExecutorService executorService = Executors.newSingleThreadExecutor(threadFactory);

    private ConcurrentMap<String, Runnable> vpnOpMap = new ConcurrentHashMap<String, Runnable>();

    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final VpnInterfaceManager vpnInterfaceManager;

    private static final InstanceIdentifier<VpnInstance> VPN_INSTANCE_IDD = InstanceIdentifier
            .create(VpnInstances.class).child(VpnInstance.class);

    /**
     * Listens for data change related to VPN Instance
     * Informs the BGP about VRF information
     *
     * @param db - dataBroker reference
     * @param bgpManager - bgpManager reference
     * @param idManager - idManager reference
     * @param vpnInterfaceManager - vpnInterfaceManager reference
     */
    public VpnInstanceListener(final DataBroker db, final IBgpManager bgpManager,
            final IdManagerService idManager,
            final VpnInterfaceManager vpnInterfaceManager) {
        super(VpnInstance.class);
        this.broker = db;
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    /**
     * Blueprint start method.
     */
    public void start() {
        listenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                VPN_INSTANCE_IDD, VpnInstanceListener.this, DataChangeScope.SUBTREE);
    }

    /**
     * Blueprint close method.
     */
    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            listenerRegistration.close();
        }
    }

    public void notifyTaskIfRequired(String vpnName) {
        Runnable notifyTask = vpnOpMap.remove(vpnName);
        if (notifyTask == null) {
            LOG.trace("VpnInstanceOpListener update: No Notify Task queued for vpnName {}", vpnName);
            return;
        }
        executorService.execute(notifyTask);
    }

    private void waitForOpRemoval(String id, long timeout) {
        //wait till DCN for update on VPN Instance Op Data signals that vpn interfaces linked to this vpn instance is zero
        Runnable notifyTask = new VpnNotifyTask();
        synchronized (id.intern()) {
            try {
                vpnOpMap.put(id, notifyTask);
                synchronized (notifyTask) {
                    try {
                        notifyTask.wait(timeout);
                    } catch (InterruptedException e) {
                    }
                }
            } finally {
                vpnOpMap.remove(id);
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstance> identifier, VpnInstance del) {
        LOG.trace("Remove VPN event key: {}, value: {}", identifier, del);
        String vpnName = del.getVpnInstanceName();
        String rd = del.getIpv4Family().getRouteDistinguisher();
        long vpnId = VpnUtil.getVpnId(broker, vpnName);

        //TODO(vpnteam): Entire code would need refactoring to listen only on the parent object - VPNInstance
        Optional<VpnInstanceOpDataEntry> vpnOpValue = null;
        if ((rd != null) && (!rd.isEmpty())) {
            vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(rd));
        } else {
            vpnOpValue = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(vpnName));
        }

        if ((vpnOpValue != null) && (vpnOpValue.isPresent())) {
            VpnInstanceOpDataEntry vpnOpEntry = null;
            long timeout = VpnConstants.MIN_WAIT_TIME_IN_MILLISECONDS;
            Long intfCount = 0L;

            vpnOpEntry = vpnOpValue.get();
            intfCount = vpnOpEntry.getVpnInterfaceCount();
            if (intfCount != null && intfCount > 0) {
                // Minimum wait time of 10 seconds for one VPN Interface clearance (inclusive of full trace on)
                timeout = intfCount * 10000;
                // Maximum wait time of 90 seconds for all VPN Interfaces clearance (inclusive of full trace on)
                if (timeout > VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS) {
                    timeout = VpnConstants.MAX_WAIT_TIME_IN_MILLISECONDS;
                }
                LOG.trace("VPNInstance removal count of interface at {} for for rd {}, vpnname {}",
                        intfCount, rd, vpnName);
            }
            LOG.trace("VPNInstance removal thread waiting for {} seconds for rd {}, vpnname {}",
                    (timeout/1000), rd, vpnName);

            if ((rd != null)  && (!rd.isEmpty())) {
                waitForOpRemoval(rd, timeout);
            } else {
                waitForOpRemoval(vpnName, timeout);
            }

            LOG.trace("Returned out of waiting for  Op Data removal for rd {}, vpnname {}", rd, vpnName);
        }
        // Clean up VpnInstanceToVpnId from Config DS
        VpnUtil.removeVpnInstanceToVpnId(broker, vpnName);
        LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", rd, vpnName);
        if (rd != null) {
            try {
                bgpManager.deleteVrf(rd);
            } catch (Exception e) {
                LOG.error("Exception when removing VRF from BGP for RD {} in VPN {} exception " + e, rd, vpnName);
            }

            // Clean up VPNExtraRoutes Operational DS
            VpnUtil.removeVpnExtraRouteForVpn(broker, rd);

            // Clean up VPNInstanceOpDataEntry
            VpnUtil.removeVpnOpInstance(broker, rd);
        } else {
            // Clean up FIB Entries Config DS
            VpnUtil.removeVrfTableForVpn(broker, vpnName);

            // Clean up VPNExtraRoutes Operational DS
            VpnUtil.removeVpnExtraRouteForVpn(broker, vpnName);

            // Clean up VPNInstanceOpDataEntry
            VpnUtil.removeVpnOpInstance(broker, vpnName);
        }

        // Clean up PrefixToInterface Operational DS
        VpnUtil.removePrefixToInterfaceForVpnId(broker, vpnId);

        // Clean up L3NextHop Operational DS
        VpnUtil.removeL3nexthopForVpnId(broker, vpnId);

        // Release the ID used for this VPN back to IdManager

        VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);
    }

    @Override
    protected void update(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance original, VpnInstance update) {
        LOG.trace("Update VPN event key: {}, value: {}", identifier, update);
    }

    @Override
    protected void add(InstanceIdentifier<VpnInstance> identifier,
            VpnInstance value) {
        LOG.trace("Add VPN event key: {}, value: {}", identifier, value);
        VpnAfConfig config = value.getIpv4Family();
        String rd = config.getRouteDistinguisher();

        long vpnId = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, value.getVpnInstanceName());
        LOG.trace("VPN instance to ID generated.");
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
            vpnInstanceToVpnId = VpnUtil.getVpnInstanceToVpnId(value.getVpnInstanceName(), vpnId,
                                                                    (rd != null) ? rd : value.getVpnInstanceName());

        TransactionUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                   VpnUtil.getVpnInstanceToVpnIdIdentifier(value.getVpnInstanceName()),
                   vpnInstanceToVpnId, TransactionUtil.DEFAULT_CALLBACK);

        IFibManager fibManager = vpnInterfaceManager.getFibManager();
        try {
            String cachedTransType = fibManager.getReqTransType();
            LOG.trace("Value for confTransportType is " + cachedTransType);
            if (cachedTransType.equals("Invalid")) {
                try {
                    fibManager.setConfTransType("L3VPN", "VXLAN");
                    LOG.trace("setting it to vxlan now");
                } catch (Exception e) {
                    LOG.trace("Exception caught setting the cached value for transportType");
                    LOG.error(e.getMessage());
                }
            } else {
                LOG.trace(":cached val is neither unset/invalid. NO-op.");
            }
        } catch (Exception e) {
            System.out.println("Exception caught accessing the cached value for transportType");
            LOG.error(e.getMessage());
        }

        if(rd == null) {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder();
            builder.setVrfId(value.getVpnInstanceName()).setVpnId(vpnId);
            builder.setVpnInterfaceCount(0L);
            TransactionUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL,
                    VpnUtil.getVpnInstanceOpDataIdentifier(value.getVpnInstanceName()),
                    builder.build(), TransactionUtil.DEFAULT_CALLBACK);

        } else {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder();
            builder.setVrfId(rd).setVpnId(vpnId);
            builder.setVpnInterfaceCount(0L);
            TransactionUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL,
                       VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                       builder.build(), TransactionUtil.DEFAULT_CALLBACK);

            List<VpnTarget> vpnTargetList = config.getVpnTargets().getVpnTarget();

            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();

            for (VpnTarget vpnTarget : vpnTargetList) {
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                    ertList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                    irtList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                    ertList.add(vpnTarget.getVrfRTValue());
                    irtList.add(vpnTarget.getVrfRTValue());
                }
            }

            try {
                bgpManager.addVrf(rd, irtList, ertList);
            } catch(Exception e) {
                LOG.error("Exception when adding VRF to BGP", e);
            }
        }
        //Try to add up vpn Interfaces if already in Operational Datastore
        InstanceIdentifier<VpnInterfaces> vpnInterfacesId = InstanceIdentifier.builder(VpnInterfaces.class).build();
        Optional<VpnInterfaces> optionalVpnInterfaces = TransactionUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vpnInterfacesId);

        if(optionalVpnInterfaces.isPresent()) {
            List<VpnInterface> vpnInterfaces = optionalVpnInterfaces.get().getVpnInterface();
            for(VpnInterface vpnInterface : vpnInterfaces) {
                if(vpnInterface.getVpnInstanceName().equals(value.getVpnInstanceName())) {
                    LOG.debug("VpnInterface {} will be added from VPN {}", vpnInterface.getName(), value.getVpnInstanceName());
                    vpnInterfaceManager.add(
                                VpnUtil.getVpnInterfaceIdentifier(vpnInterface.getName()), vpnInterface);

                }
            }
        }
    }

    public boolean isVPNConfigured() {

        InstanceIdentifier<VpnInstances> vpnsIdentifier =
                InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> optionalVpns = TransactionUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                vpnsIdentifier);
        if (!optionalVpns.isPresent() ||
                optionalVpns.get().getVpnInstance() == null ||
                optionalVpns.get().getVpnInstance().isEmpty()) {
            LOG.trace("No VPNs configured.");
            return false;
        }
        LOG.trace("VPNs are configured on the system.");
        return true;
    }

    protected VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = TransactionUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if(vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }
}
