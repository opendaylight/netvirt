/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.PortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpManager {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpManager.class);
    private final IMdsalApiManager mdsalUtil;
    private final DhcpserviceConfig config;
    private final DataBroker broker;
    private final DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private final IInterfaceManager interfaceManager;
    private final IElanService elanService;
    private final JobCoordinator jobCoordinator;
    private DhcpPortCache dhcpPortCache;
    private final ItmRpcService itmRpcService;
    private final DhcpServiceCounters dhcpServiceCounters;

    private volatile int dhcpOptLeaseTime = 0;
    private volatile String dhcpOptDefDomainName;
    private DhcpInterfaceEventListener dhcpInterfaceEventListener;
    private DhcpInterfaceConfigListener dhcpInterfaceConfigListener;

    @Inject
    public DhcpManager(final IMdsalApiManager mdsalApiManager,
            final DhcpserviceConfig config, final DataBroker dataBroker,
            final DhcpExternalTunnelManager dhcpExternalTunnelManager, final IInterfaceManager interfaceManager,
            @Named("elanService") IElanService ielanService, final DhcpPortCache dhcpPortCache,
            final JobCoordinator jobCoordinator, final ItmRpcService itmRpcService,
            DhcpServiceCounters dhcpServiceCounters) {
        this.mdsalUtil = mdsalApiManager;
        this.config = config;
        this.broker = dataBroker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
        this.interfaceManager = interfaceManager;
        this.elanService = ielanService;
        this.dhcpPortCache = dhcpPortCache;
        this.jobCoordinator = jobCoordinator;
        this.itmRpcService = itmRpcService;
        this.dhcpServiceCounters = dhcpServiceCounters;
        configureLeaseDuration(DhcpMConstants.DEFAULT_LEASE_TIME);
    }

    @PostConstruct
    public void init() {
        LOG.trace("Netvirt DHCP Manager Init .... {}",config.isControllerDhcpEnabled());
        if (config.isControllerDhcpEnabled()) {
            dhcpInterfaceEventListener = new DhcpInterfaceEventListener(this, broker, dhcpExternalTunnelManager,
                    interfaceManager, elanService, dhcpPortCache, jobCoordinator, itmRpcService);
            dhcpInterfaceConfigListener = new DhcpInterfaceConfigListener(broker, dhcpExternalTunnelManager, this,
                    jobCoordinator);
            LOG.info("DHCP Service initialized");
        }
    }

    @PreDestroy
    public void close() {
        if (dhcpInterfaceEventListener != null) {
            dhcpInterfaceEventListener.close();
        }
        if (dhcpInterfaceConfigListener != null) {
            dhcpInterfaceConfigListener.close();
        }
        LOG.info("DHCP Service closed");
    }

    public int setLeaseDuration(int leaseDuration) {
        configureLeaseDuration(leaseDuration);
        return getDhcpLeaseTime();
    }

    public String setDefaultDomain(String defaultDomain) {
        this.dhcpOptDefDomainName = defaultDomain;
        return getDhcpDefDomain();
    }

    public int getDhcpLeaseTime() {
        return this.dhcpOptLeaseTime;
    }

    public int getDhcpRenewalTime() {
        return this.dhcpOptLeaseTime;
    }

    public int getDhcpRebindingTime() {
        return this.dhcpOptLeaseTime;
    }

    public String getDhcpDefDomain() {
        return this.dhcpOptDefDomainName;
    }

    private void configureLeaseDuration(int leaseTime) {
        this.dhcpOptLeaseTime = leaseTime;
    }

    public Subnet getNeutronSubnet(Port port) {
        if (port != null) {
            // DHCP Service is only interested in IPv4 IPs/Subnets
            return getNeutronSubnet(port.getFixedIps());
        }
        return null;
    }

    public Subnet getNeutronSubnet(List<FixedIps> fixedIps) {
        for (FixedIps fixedIp: fixedIps) {
            if (fixedIp.getIpAddress().getIpv4Address() != null) {
                return getNeutronSubnet(fixedIp.getSubnetId());
            }
        }
        return null;
    }

    private Subnet getNeutronSubnet(Uuid subnetId) {
        Subnet subnet = null;
        InstanceIdentifier<Subnet> inst = InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet
                .class, new SubnetKey(subnetId));
        Optional<Subnet> sn = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (sn.isPresent()) {
            subnet = sn.get();
        }
        LOG.trace("Subnet {} = {}", subnetId, subnet);
        return subnet;
    }

    public Port getNeutronPort(String name) {
        Port prt = null;
        InstanceIdentifier<Port> inst = InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class,
                new PortKey(new Uuid(name)));
        Optional<Port> port = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (port.isPresent()) {
            prt = port.get();
        }
        LOG.trace("Port {} = {}", name, prt);
        return prt;
    }

    public void installDhcpEntries(@Nullable BigInteger dpnId, @Nullable String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.ADD_FLOW,
                mdsalUtil, dhcpServiceCounters, tx);
    }

    public void unInstallDhcpEntries(@Nullable BigInteger dpId, @Nullable String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.DEL_FLOW,
                mdsalUtil, dhcpServiceCounters, tx);
    }

    public void setupDefaultDhcpFlows(BigInteger dpId) {
        setupTableMissForDhcpTable(dpId);
        if (config.isDhcpDynamicAllocationPoolEnabled()) {
            setupDhcpAllocationPoolFlow(dpId);
        }
    }

    private void setupTableMissForDhcpTable(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionNxResubmit(NwConstants.LPORT_DISPATCHER_TABLE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE, "DHCPTableMissFlow",
                0, "DHCP Table Miss Flow", 0, 0,
                DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        dhcpServiceCounters.installDhcpTableMissFlow();
        mdsalUtil.installFlow(flowEntity);
        setupTableMissForHandlingExternalTunnel(dpId);
    }

    private void setupDhcpAllocationPoolFlow(BigInteger dpId) {
        List<MatchInfo> matches = DhcpServiceUtils.getDhcpMatch();
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE,
                "DhcpAllocationPoolFlow", DhcpMConstants.DEFAULT_DHCP_ALLOCATION_POOL_FLOW_PRIORITY,
                "Dhcp Allocation Pool Flow", 0, 0, DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        LOG.trace("Installing DHCP Allocation Pool Flow DpId {}", dpId);
        dhcpServiceCounters.installDhcpFlow();
        mdsalUtil.installFlow(flowEntity);
    }

    private void setupTableMissForHandlingExternalTunnel(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionGotoTable(NwConstants.EXTERNAL_TUNNEL_TABLE));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL,
                "DHCPTableMissFlowForExternalTunnel",
                0, "DHCP Table Miss Flow For External Tunnel", 0, 0,
                DhcpMConstants.COOKIE_DHCP_BASE, matches, instructions);
        dhcpServiceCounters.installDhcpTableMissFlowForExternalTable();
        mdsalUtil.installFlow(flowEntity);
    }
}
