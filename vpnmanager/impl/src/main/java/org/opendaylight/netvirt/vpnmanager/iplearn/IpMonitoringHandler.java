/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.serviceutils.tools.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IpMonitoringHandler extends AbstractClusteredAsyncDataTreeChangeListener<LearntVpnVipToPort> {
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitoringHandler.class);
    private final DataBroker dataBroker;
    private final AlivenessMonitorService alivenessManager;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private final INeutronVpnManager neutronVpnService;
    private final IInterfaceManager interfaceManager;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final JobCoordinator jobCoordinator;
    private final VpnUtil vpnUtil;

    private Optional<Uint32> arpMonitorProfileId = Optional.empty();
    private Optional<Uint32> ipv6NdMonitorProfileId = Optional.empty();
    private EntityOwnershipCandidateRegistration candidateRegistration;

    @Inject
    public IpMonitoringHandler(final DataBroker dataBroker, AlivenessMonitorService alivenessManager,
            INeutronVpnManager neutronVpnService, IInterfaceManager interfaceManager,
            EntityOwnershipService entityOwnershipService, JobCoordinator jobCoordinator,
            AlivenessMonitorUtils alivenessMonitorUtils, VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.OPERATIONAL, InstanceIdentifier.create(LearntVpnVipToPortData.class)
                .child(LearntVpnVipToPort.class),
                Executors.newListeningSingleThreadExecutor("IpMonitoringHandler", LOG));
        this.dataBroker = dataBroker;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.interfaceManager = interfaceManager;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
        this.jobCoordinator = jobCoordinator;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.vpnUtil = vpnUtil;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        this.arpMonitorProfileId = alivenessMonitorUtils.allocateArpMonitorProfile();
        this.ipv6NdMonitorProfileId = alivenessMonitorUtils.allocateIpv6NaMonitorProfile();
        if (this.arpMonitorProfileId == null || this.ipv6NdMonitorProfileId == null) {
            LOG.error("Error while allocating ARP and IPv6 ND Profile Ids: ARP={}, IPv6ND={}", arpMonitorProfileId,
                    ipv6NdMonitorProfileId);
        }

        try {
            candidateRegistration = entityOwnershipUtils.getEntityOwnershipService().registerCandidate(
                    new Entity(VpnConstants.IP_MONITORING_ENTITY, VpnConstants.IP_MONITORING_ENTITY));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register the entity {}", VpnConstants.IP_MONITORING_ENTITY);
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

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    public void update(InstanceIdentifier<LearntVpnVipToPort> id, LearntVpnVipToPort value,
            LearntVpnVipToPort dataObjectModificationAfter) {
        runOnlyInOwnerNode("IpMonitoringHandler: update event", () -> {
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
    public void add(InstanceIdentifier<LearntVpnVipToPort> identifier, LearntVpnVipToPort value) {
        runOnlyInOwnerNode("IpMonitoringHandler: add event", () -> {
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

                Optional<Uint32> monitorProfileId = getMonitorProfileId(value.getPortFixedip());
                if (monitorProfileId.isPresent()) {
                    jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                            new IpMonitorStartTask(macEntry, monitorProfileId.get().toJava(), alivenessMonitorUtils));
                }
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    @Override
    public void remove(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort value) {
        runOnlyInOwnerNode("IpMonitoringHandler: remove event", () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if (value.getMacAddress() == null) {
                    LOG.warn("The mac address received is null for LearntVpnVipToPort {}, ignoring the DTCN", value);
                    return;
                }
                String vpnName =  value.getVpnName();
                String learntIp = srcInetAddr.getHostAddress();
                LearntVpnVipToPort vpnVipToPort = vpnUtil.getLearntVpnVipToPort(vpnName, learntIp);
                if (vpnVipToPort != null && !vpnVipToPort.getCreationTime().equals(value.getCreationTime())) {
                    LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                            + "Ignoring this remove event.", learntIp, vpnName);
                    return;
                }
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String interfaceName =  value.getPortName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName,
                        value.getCreationTime());

                jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                        new IpMonitorStopTask(macEntry, dataBroker, Boolean.FALSE, vpnUtil, alivenessMonitorUtils));
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    private void runOnlyInOwnerNode(String jobDesc, final Runnable job) {
        entityOwnershipUtils.runOnlyInOwnerNode(VpnConstants.IP_MONITORING_ENTITY, VpnConstants.IP_MONITORING_ENTITY,
                jobCoordinator, jobDesc, job);
    }

    private Optional<Uint32> getMonitorProfileId(String ipAddress) {
        if (NWUtil.isIpv4Address(ipAddress)) {
            return this.arpMonitorProfileId;
        } else {
            return this.ipv6NdMonitorProfileId;
        }
    }
}

