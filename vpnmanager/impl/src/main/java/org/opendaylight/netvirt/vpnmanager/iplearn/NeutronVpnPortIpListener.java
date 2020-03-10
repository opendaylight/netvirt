/*
 * Copyright (c) 2020 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
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
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.config.rev161130.VpnConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NeutronVpnPortIpListener
        extends AsyncDataTreeChangeListenerBase<VpnPortipToPort, NeutronVpnPortIpListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronVpnPortIpListener.class);
    private final DataBroker dataBroker;
    private final AlivenessMonitorService alivenessManager;
    private final AlivenessMonitorUtils alivenessMonitorUtils;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator jobCoordinator;
    private final VpnConfig vpnConfig;
    private final VpnUtil vpnUtil;

    private Optional<Uint32> arpMonitorProfileId = Optional.absent();
    private Optional<Uint32> ipv6NdMonitorProfileId = Optional.absent();

    @Inject
    public NeutronVpnPortIpListener(final DataBroker dataBroker, AlivenessMonitorService alivenessManager,
                                    AlivenessMonitorUtils alivenessMonitorUtils,
                                    IInterfaceManager interfaceManager, JobCoordinator jobCoordinator,
                                    VpnConfig vpnConfig, VpnUtil vpnUtil) {
        super(VpnPortipToPort.class, NeutronVpnPortIpListener.class);
        this.dataBroker = dataBroker;
        this.alivenessManager = alivenessManager;
        this.alivenessMonitorUtils = alivenessMonitorUtils;
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.vpnConfig = vpnConfig;
        this.vpnUtil = vpnUtil;
    }

    @PostConstruct
    public void start() {
        this.arpMonitorProfileId = alivenessMonitorUtils.allocateArpMonitorProfile();
        this.ipv6NdMonitorProfileId = alivenessMonitorUtils.allocateIpv6NaMonitorProfile();
        if (this.arpMonitorProfileId == null || this.ipv6NdMonitorProfileId == null) {
            LOG.error("Error while allocating ARP and IPv6 ND Profile Ids: ARP={}, IPv6ND={}", arpMonitorProfileId,
                    ipv6NdMonitorProfileId);
        }
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    @Override
    protected NeutronVpnPortIpListener getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> id, VpnPortipToPort value,
                          VpnPortipToPort dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> identifier, VpnPortipToPort value) {
        LOG.trace("add : VpnPortipToPort {}", value);
        if (!value.isLearntIp()) {
            return;
        }

        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, value.getPortName());
        if (interfaceState == null) {
            LOG.info("Interface {} on vpn {} skipped as interfaceState is not available",
                    value.getPortName(), value.getVpnName());
            return;
        }

        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            if (value.getMacAddress() == null) {
                LOG.error("NeutronVpnPortIpListener: The mac address received is null for VpnPortipToPort {}, "
                        + "ignoring the DTCN", value);
                return;
            }
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName = value.getVpnName();
            MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, value.getPortName(),
                    value.getCreationTime());
            Optional<Uint32> monitorProfileId = getMonitorProfileId(value.getPortFixedip());
            vpnUtil.addOrUpdateDpnToMacEntry(dataBroker, value.getDpnId(), macEntry);
            jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                    new IpMonitorStartTask(macEntry, monitorProfileId.get(), alivenessMonitorUtils));
        } catch (UnknownHostException e) {
            LOG.error("NeutronVpnPortIpListener: Exception while handling add of VpnPortipToPort {}", value, e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        LOG.info("remove : VpnPortipToPort {}", value);
        if (!value.isLearntIp()) {
            return;
        }
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            if (value.getMacAddress() == null) {
                LOG.error("NeutronVpnPortIpListener: The mac address received is null for VpnVipToPort {}, "
                        + "ignoring the DTCN", value);
                return;
            }

            String vpnName = value.getVpnName();
            String learntIp = srcInetAddr.getHostAddress();
            VpnPortipToPort vpnVipToPort = vpnUtil.getVpnPortipToPort(vpnName, learntIp);
            if (vpnVipToPort != null && !vpnVipToPort.getCreationTime().equals(value.getCreationTime())) {
                LOG.warn("The MIP {} over vpn {} has been learnt again and processed. "
                        + "Ignoring this remove event.", learntIp, vpnName);
                return;
            }
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String interfaceName = value.getPortName();
            MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName,
                    value.getCreationTime());
            vpnUtil.removeOrUpdateDpnToMacEntry(dataBroker, value.getDpnId(), macEntry);
            jobCoordinator.enqueueJob(VpnUtil.buildIpMonitorJobKey(srcInetAddr.toString(), vpnName),
                    new IpMonitorStopTask(macEntry, dataBroker, alivenessMonitorUtils, Boolean.FALSE, vpnUtil));
        } catch (UnknownHostException e) {
            LOG.error("NeutronVpnPortIpListener: Exception while handling remove of VpnPortipToPort {}", value, e);
        }
    }

    public Optional<Uint32> getMonitorProfileId(String ipAddress) {
        if (NWUtil.isIpv4Address(ipAddress)) {
            return this.arpMonitorProfileId;
        } else {
            return this.ipv6NdMonitorProfileId;
        }
    }
}
