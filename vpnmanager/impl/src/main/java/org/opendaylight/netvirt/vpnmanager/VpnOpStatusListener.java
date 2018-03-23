/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class VpnOpStatusListener extends AsyncDataTreeChangeListenerBase<VpnInstanceOpDataEntry, VpnOpStatusListener> {
    private static final Logger LOG = LoggerFactory.getLogger(VpnOpStatusListener.class);
    private final DataBroker dataBroker;
    private final IBgpManager bgpManager;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;
    private final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver;
    private final JobCoordinator jobCoordinator;

    @Inject
    public VpnOpStatusListener(final DataBroker dataBroker, final IBgpManager bgpManager,
                               final IdManagerService idManager, final IFibManager fibManager,
                               final IMdsalApiManager mdsalManager,
                               final VpnFootprintService vpnFootprintService,
                               final IVpnClusterOwnershipDriver vpnClusterOwnershipDriver,
                               final JobCoordinator jobCoordinator) {
        super(VpnInstanceOpDataEntry.class, VpnOpStatusListener.class);
        this.dataBroker = dataBroker;
        this.bgpManager = bgpManager;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
        this.vpnClusterOwnershipDriver = vpnClusterOwnershipDriver;
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnInstanceOpDataEntry> getWildCardPath() {
        return InstanceIdentifier.create(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class);
    }

    @Override
    protected VpnOpStatusListener getDataTreeChangeListener() {
        return VpnOpStatusListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInstanceOpDataEntry> identifier, VpnInstanceOpDataEntry value) {
        LOG.info("remove: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                          VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        LOG.info("update: Processing update for vpn {} with rd {}", update.getVpnInstanceName(), update.getVrfId());
        if (!vpnClusterOwnershipDriver.amIOwner()) {
            // Am not the current owner for L3VPN service, don't bother
            LOG.trace("I am not the owner");
            return;
        }
        if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete
                && vpnFootprintService.isVpnFootPrintCleared(update)) {
            //Cleanup VPN data
            final String vpnName = update.getVpnInstanceName();
            final List<String> rds = update.getRd();
            String primaryRd = update.getVrfId();
            final long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
            jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                // Clean up VpnInstanceToVpnId from Config DS
                VpnUtil.removeVpnIdToVpnInstance(dataBroker, vpnId, writeTxn);
                VpnUtil.removeVpnInstanceToVpnId(dataBroker, vpnName, writeTxn);
                LOG.trace("Removed vpnIdentifier for  rd{} vpnname {}", primaryRd, vpnName);
                // Clean up FIB Entries Config DS
                fibManager.removeVrfTable(primaryRd, null);
                // Clean up VPNExtraRoutes Operational DS
                if (VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                    if (update.getType() == VpnInstanceOpDataEntry.Type.L2) {
                        rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.L2VPN));
                    }
                    if (update.isIpv4Configured()) {
                        rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.IPV4));
                    }
                    if (update.isIpv6Configured()) {
                        rds.parallelStream().forEach(rd -> bgpManager.deleteVrf(rd, false, AddressFamily.IPV6));
                    }
                }
                InstanceIdentifier<Vpn> vpnToExtraroute = VpnExtraRouteHelper.getVpnToExtrarouteVpnIdentifier(vpnName);
                Optional<Vpn> optVpnToExtraroute = VpnUtil.read(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, vpnToExtraroute);
                if (optVpnToExtraroute.isPresent()) {
                    VpnUtil.removeVpnExtraRouteForVpn(dataBroker, vpnName, writeTxn);
                }

                if (VpnUtil.isL3VpnOverVxLan(update.getL3vni())) {
                    VpnUtil.removeExternalTunnelDemuxFlows(vpnName, dataBroker, mdsalManager);
                }

                // Clean up VPNInstanceOpDataEntry
                VpnUtil.removeVpnOpInstance(dataBroker, primaryRd, writeTxn);
                // Clean up PrefixToInterface Operational DS
                VpnUtil.removePrefixToInterfaceForVpnId(dataBroker, vpnId, writeTxn);

                // Clean up L3NextHop Operational DS
                VpnUtil.removeL3nexthopForVpnId(dataBroker, vpnId, writeTxn);

                // Release the ID used for this VPN back to IdManager
                VpnUtil.releaseId(idManager, VpnConstants.VPN_IDPOOL_NAME, vpnName);

                List<ListenableFuture<Void>> futures = new ArrayList<>();
                futures.add(writeTxn.submit());
                return futures;
            }, SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        } else if (update.getVpnState() == VpnInstanceOpDataEntry.VpnState.Created) {
            final String vpnName = update.getVpnInstanceName();
            final List<String> rds = update.getRd();
            String primaryRd = update.getVrfId();
            if (!VpnUtil.isBgpVpn(vpnName, primaryRd)) {
                return;
            }
            if (original == null) {
                LOG.error("VpnOpStatusListener.update: vpn {} with RD {}. add() handler already called",
                       vpnName, primaryRd);
                return;
            }
            if (update.getVpnTargets() == null) {
                LOG.error("VpnOpStatusListener.update: vpn {} with RD {} vpnTargets not ready",
                       vpnName, primaryRd);
                return;
            }
            List<VpnTarget> vpnTargetList = update.getVpnTargets().getVpnTarget();
            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();
            if (vpnTargetList != null) {
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
            } else {
                LOG.error("VpnOpStatusListener.update: vpn target list is empty, cannot add BGP"
                      + " VPN {} RD {}", vpnName, primaryRd);
                return;
            }
            jobCoordinator.enqueueJob("VPN-" + update.getVpnInstanceName(), () -> {
                WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
                long primaryRdAddFailed = rds.parallelStream().filter(rd -> {
                    try {
                        LOG.info("VpnOpStatusListener.update: updating BGPVPN for vpn {} with RD {}"
                                + " Type is {}, IPv4 is {}, IPv6 is {}", vpnName, primaryRd, update.getType(),
                                update.isIpv4Configured(), update.isIpv6Configured());
                        if (update.getType() == VpnInstanceOpDataEntry.Type.L2) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.L2VPN);
                        } else {
                            bgpManager.deleteVrf(rd, false, AddressFamily.L2VPN);
                        }
                        if (!original.isIpv4Configured() && update.isIpv4Configured()) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.IPV4);
                        } else if (original.isIpv4Configured() && !update.isIpv4Configured()) {
                            bgpManager.deleteVrf(rd, false, AddressFamily.IPV4);
                        }
                        if (!original.isIpv6Configured() && update.isIpv6Configured()) {
                            bgpManager.addVrf(rd, irtList, ertList, AddressFamily.IPV6);
                        } else if (original.isIpv6Configured() && !update.isIpv6Configured()) {
                            bgpManager.deleteVrf(rd, false, AddressFamily.IPV6);
                        }
                    } catch (Exception e) {
                        LOG.error("VpnOpStatusListener.update: Exception when updating VRF to BGP"
                               + " for vpn {} rd {}", vpnName, rd);
                        return false;
                    }
                    return false;
                }).count();
                return Collections.emptyList();
            });
        }
    }

    @Override
    protected void add(final InstanceIdentifier<VpnInstanceOpDataEntry> identifier,
                       final VpnInstanceOpDataEntry value) {
        LOG.debug("add: Ignoring vpn Op {} with rd {}", value.getVpnInstanceName(), value.getVrfId());
    }
}
