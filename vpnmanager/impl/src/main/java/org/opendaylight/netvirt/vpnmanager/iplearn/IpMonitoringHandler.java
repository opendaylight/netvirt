/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.iplearn;

import com.google.common.base.Optional;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.arputil.api.ArpConstants;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class IpMonitoringHandler
        extends AsyncClusteredDataTreeChangeListenerBase<LearntVpnVipToPort, IpMonitoringHandler> {
    private static final Logger LOG = LoggerFactory.getLogger(IpMonitoringHandler.class);
    private final DataBroker dataBroker;
    private final AlivenessMonitorService alivenessManager;
    private final INeutronVpnManager neutronVpnService;
    private final IInterfaceManager interfaceManager;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final JobCoordinator jobCoordinator;

    private Long arpMonitorProfileId = 0L;
    private Long ndMonitorProfileId = 0L;
    private EntityOwnershipCandidateRegistration candidateRegistration;

    @Inject
    public IpMonitoringHandler(final DataBroker dataBroker, AlivenessMonitorService alivenessManager,
            INeutronVpnManager neutronVpnService, IInterfaceManager interfaceManager,
            EntityOwnershipService entityOwnershipService, JobCoordinator jobCoordinator) {
        super(LearntVpnVipToPort.class, IpMonitoringHandler.class);
        this.dataBroker = dataBroker;
        this.alivenessManager = alivenessManager;
        this.neutronVpnService = neutronVpnService;
        this.interfaceManager = interfaceManager;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
        this.jobCoordinator = jobCoordinator;
    }

    @PostConstruct
    public void start() {
        Optional<Long> arpProfileIdOptional = AlivenessMonitorUtils.allocateProfile(alivenessManager,
            ArpConstants.FAILURE_THRESHOLD, ArpConstants.ARP_CACHE_TIMEOUT_MILLIS, ArpConstants.MONITORING_WINDOW,
            EtherTypes.Arp);
        if (arpProfileIdOptional.isPresent()) {
            arpMonitorProfileId = arpProfileIdOptional.get();
        }

        Optional<Long> ndProfileIdOptional = AlivenessMonitorUtils.allocateProfile(alivenessManager,
                VpnConstants.FAILURE_THRESHOLD, VpnConstants.ND_FLOW_VALID_TIMEOUT_MILLIS,
                VpnConstants.MONITORING_WINDOW, EtherTypes.Nd);
        if(ndProfileIdOptional.isPresent()) {
            ndMonitorProfileId = ndProfileIdOptional.get();
        }

        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
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

    @Override
    protected InstanceIdentifier<LearntVpnVipToPort> getWildCardPath() {
        return InstanceIdentifier.create(LearntVpnVipToPortData.class).child(LearntVpnVipToPort.class);
    }

    @Override
    protected IpMonitoringHandler getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<LearntVpnVipToPort> id, LearntVpnVipToPort value,
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
    protected void add(InstanceIdentifier<LearntVpnVipToPort> identifier, LearntVpnVipToPort value) {
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

                Long monitorProfileId = getMonitorProfileId(value.getPortFixedip());
                jobCoordinator.enqueueJob(buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                        new IpMonitorStartTask(macEntry, monitorProfileId, dataBroker, alivenessManager,
                                neutronVpnService, interfaceManager));
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    @Override
    protected void remove(InstanceIdentifier<LearntVpnVipToPort> key, LearntVpnVipToPort value) {
        runOnlyInOwnerNode("IpMonitoringHandler: remove event", () -> {
            try {
                InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
                if (value.getMacAddress() == null) {
                    LOG.warn("The mac address received is null for LearntVpnVipToPort {}, ignoring the DTCN", value);
                    return;
                }
                String vpnName =  value.getVpnName();
                MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
                String interfaceName =  value.getPortName();
                MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName,
                        value.getCreationTime());

                jobCoordinator.enqueueJob(buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                        new IpMonitorStopTask(macEntry, dataBroker, alivenessManager, Boolean.FALSE));
            } catch (UnknownHostException e) {
                LOG.error("Error in deserializing packet {} with exception", value, e);
            }
        });
    }

    private void runOnlyInOwnerNode(String jobDesc, final Runnable job) {
        entityOwnershipUtils.runOnlyInOwnerNode(VpnConstants.IP_MONITORING_ENTITY, VpnConstants.IP_MONITORING_ENTITY,
                jobCoordinator, jobDesc, job);
    }

    private Long getMonitorProfileId(String strIpAddress) {
        IpAddress ipAddress = new IpAddress(strIpAddress.toCharArray());
        if (ipAddress.getIpv4Address() != null) {
            return this.arpMonitorProfileId;
        } else if(ipAddress.getIpv6Address() != null){
            return this.ndMonitorProfileId;
        }
        return null;
    }

    static String buildIpMonitorJobKey(String ip, String vpnName) {
        return VpnConstants.IP_MONITOR_JOB_PREFIX_KEY + "-" + vpnName + "-" + ip;
    }

}

