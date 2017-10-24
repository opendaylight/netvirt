/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.annotation.PreDestroy;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpMonitoringHandler
        extends AsyncClusteredDataTreeChangeListenerBase<LearntVpnVipToPort, ArpMonitoringHandler> {
    private static final Logger LOG = LoggerFactory.getLogger(ArpMonitoringHandler.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceRpc;
    private final IMdsalApiManager mdsalManager;
    private final AlivenessMonitorService alivenessManager;
    private final INeutronVpnManager neutronVpnService;
    private final IInterfaceManager interfaceManager;
    private final EntityOwnershipUtils entityOwnershipUtils;

    private Long arpMonitorProfileId = 0L;
    private EntityOwnershipCandidateRegistration candidateRegistration;

    public ArpMonitoringHandler(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceRpc,
            IMdsalApiManager mdsalManager, AlivenessMonitorService alivenessManager,
            INeutronVpnManager neutronVpnService, IInterfaceManager interfaceManager,
            EntityOwnershipService entityOwnershipService) {
        super(LearntVpnVipToPort.class, ArpMonitoringHandler.class);
        this.dataBroker = dataBroker;
        this.interfaceRpc = interfaceRpc;
        this.mdsalManager = mdsalManager;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.interfaceManager = interfaceManager;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
    }

    public void start() {
        Optional<Long> profileIdOptional = AlivenessMonitorUtils.allocateProfile(alivenessManager,
            ArpConstants.FAILURE_THRESHOLD, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, ArpConstants.MONITORING_WINDOW,
            EtherTypes.Arp);
        if (profileIdOptional.isPresent()) {
            arpMonitorProfileId = profileIdOptional.get();
        } else {
            LOG.error("Error while allocating Profile Id", profileIdOptional);
        }
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);

        try {
            candidateRegistration = entityOwnershipUtils.getEntityOwnershipService().registerCandidate(
                    new Entity(VpnConstants.ARP_MONITORING_ENTITY, VpnConstants.ARP_MONITORING_ENTITY));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register the entity {}", VpnConstants.ARP_MONITORING_ENTITY);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();

        if (candidateRegistration != null) {
            candidateRegistration.close();
        }
    }

    @Override
    protected InstanceIdentifier<LearntVpnVipToPort> getWildCardPath() {
        return InstanceIdentifier.create(LearntVpnVipToPortData.class).child(LearntVpnVipToPort.class);
    }

    @Override
    protected ArpMonitoringHandler getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<LearntVpnVipToPort> id, LearntVpnVipToPort value,
            LearntVpnVipToPort dataObjectModificationAfter) {
        runOnlyInOwnerNode("ArpMonitoringHandler: update event", () -> {
            try {
                if (value.getMacAddress() == null || dataObjectModificationAfter.getMacAddress() == null) {
                    LOG.warn("The mac address received is null for LearntVpnVipIpToPort {}, ignoring the DTCN",
                            dataObjectModificationAfter);
                    return;
                }
                remove(id, value);
                add(id, dataObjectModificationAfter);
            } catch (Exception e) {
                LOG.error("Error in handling update to LearntVpnVipIpToPort for vpnName {} and IP Address {}",
                        value.getVpnName(), value.getPortFixedip(), e);
            }
        });
    }

    @Override
    protected void add(InstanceIdentifier<LearntVpnVipToPort> identifier, LearntVpnVipToPort value) {
        runOnlyInOwnerNode("ArpMonitoringHandler: add event", () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if (value.getMacAddress() == null) {
                    LOG.warn("The mac address received is null for VpnPortipToPort {}, ignoring the DTCN", value);
                    return;
                }
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String vpnName =  value.getVpnName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, value.getPortName(),
                        value.getCreationTime());
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpMonitorStartTask(macEntry, arpMonitorProfileId, dataBroker, alivenessManager,
                                interfaceRpc, neutronVpnService, interfaceManager));
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    @Override
    protected void remove(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort value) {
        runOnlyInOwnerNode("ArpMonitoringHandler: remove event", () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if (value.getMacAddress() == null) {
                    LOG.warn("The mac address received is null for LearntVpnVipToPort {}, ignoring the DTCN", value);
                    return;
                }
                String vpnName =  value.getVpnName();
                String learntIp = srcInetAddr.getHostAddress();
                LearntVpnVipToPort vpnVipToPort = VpnUtil.getLearntVpnVipToPort(dataBroker, vpnName, learntIp);
                if (vpnVipToPort != null && !vpnVipToPort.getCreationTime().equals(value.getCreationTime())) {
                    LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                            + "Ignoring this remove event.", learntIp, vpnName);
                    return;
                }
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String interfaceName =  value.getPortName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName,
                        value.getCreationTime());
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(), vpnName),
                        new ArpMonitorStopTask(macEntry, dataBroker, alivenessManager));
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    private void runOnlyInOwnerNode(String jobDesc, final Runnable job) {
        entityOwnershipUtils.runOnlyInOwnerNode(VpnConstants.ARP_MONITORING_ENTITY, VpnConstants.ARP_MONITORING_ENTITY,
                DataStoreJobCoordinator.getInstance(), jobDesc, job);
    }

    static String buildJobKey(String ip, String vpnName) {
        return new StringBuilder(ArpConstants.ARPJOB).append(ip).append(vpnName).toString();
    }
}

