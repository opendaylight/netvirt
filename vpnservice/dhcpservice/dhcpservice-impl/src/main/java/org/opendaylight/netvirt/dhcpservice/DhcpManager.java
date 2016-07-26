/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpManager.class);
    private final DataBroker broker;
    IMdsalApiManager mdsalUtil;

    private int dhcpOptLeaseTime = 0;
    private int dhcpOptRenewalTime = 0;
    private int dhcpOptRebindingTime = 0;
    private String dhcpOptDefDomainName;
    private INeutronVpnManager neutronVpnService;
    // cache used to maintain DpnId and physical address for each interface.
    private static HashMap<String, ImmutablePair<BigInteger, String>> interfaceToDpnIdMacAddress = new HashMap<>();

    /**
    * @param db - dataBroker reference
    */
    public DhcpManager(final DataBroker db) {
        broker = db;
        configureLeaseDuration(DHCPMConstants.DEFAULT_LEASE_TIME);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalUtil = mdsalManager;
    }

    public void setNeutronVpnService(INeutronVpnManager neutronVpnService) {
        logger.debug("Setting NeutronVpn dependency");
        this.neutronVpnService = neutronVpnService;
    }

    @Override
    public void close() throws Exception {
        logger.info("DHCP Manager Closed");
    }

    public int setLeaseDuration(int leaseDuration) {
        configureLeaseDuration(leaseDuration);
        return getDhcpLeaseTime();
    }

    public String setDefaultDomain(String defaultDomain) {
        this.dhcpOptDefDomainName = defaultDomain;
        return getDhcpDefDomain();
    }

    protected int getDhcpLeaseTime() {
        return this.dhcpOptLeaseTime;
    }

    protected int getDhcpRenewalTime() {
        return this.dhcpOptLeaseTime;
    }

    protected int getDhcpRebindingTime() {
        return this.dhcpOptLeaseTime;
    }

    protected String getDhcpDefDomain() {
        return this.dhcpOptDefDomainName;
    }

    private void configureLeaseDuration(int leaseTime) {
        this.dhcpOptLeaseTime = leaseTime;
        if(leaseTime > 0) {
            this.dhcpOptRenewalTime = this.dhcpOptLeaseTime/2;
            this.dhcpOptRebindingTime = (this.dhcpOptLeaseTime*7)/8;
        } else {
            this.dhcpOptRenewalTime = -1;
            this.dhcpOptRebindingTime = -1;
        }
    }

    public Subnet getNeutronSubnet(Port nPort) {
        if (nPort != null) {
            try {
                return neutronVpnService.getNeutronSubnet(nPort.getFixedIps().get(0).getSubnetId());
            } catch (Exception e) {
                logger.warn("Failed to get Neutron Subnet from Port: {}", e);
            }
        }
        return null;
    }

    public Port getNeutronPort(String name) {
        try {
            return neutronVpnService.getNeutronPort(name);
        } catch (IllegalArgumentException e) {
            return null;
        } catch (Exception ex) {
            logger.trace("In getNeutronPort interface name passed {} exception message {}.", name, ex.getMessage());
            return null;
        }
    }

    public void installDhcpEntries(BigInteger dpnId, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, DHCPMConstants.DHCP_TABLE, vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil);
    }

    public void unInstallDhcpEntries(BigInteger dpId, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpId, DHCPMConstants.DHCP_TABLE, vmMacAddress, NwConstants.DEL_FLOW, mdsalUtil);
    }

    public void setupTableMissForDhcpTable(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        List <ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[]{
                Short.toString(NwConstants.LPORT_DISPATCHER_TABLE)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, DHCPMConstants.DHCP_TABLE, "DHCPTableMissFlow",
                0, "DHCP Table Miss Flow", 0, 0,
                DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
        DhcpServiceCounters.install_dhcp_table_miss_flow.inc();
        mdsalUtil.installFlow(flowEntity);
        setupTableMissForHandlingExternalTunnel(dpId);
    }

    private void setupTableMissForHandlingExternalTunnel(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.EXTERNAL_TUNNEL_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL, "DHCPTableMissFlowForExternalTunnel",
                0, "DHCP Table Miss Flow For External Tunnel", 0, 0,
                DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
        DhcpServiceCounters.install_dhcp_table_miss_flow_for_external_table.inc();
        mdsalUtil.installFlow(flowEntity);
    }

    public void updateInterfaceCache(String interfaceName, ImmutablePair<BigInteger, String> pair) {
        interfaceToDpnIdMacAddress.put(interfaceName, pair);
    }

    public ImmutablePair<BigInteger, String> getInterfaceCache(String interfaceName) {
        return interfaceToDpnIdMacAddress.get(interfaceName);
    }

    public void removeInterfaceCache(String interfaceName) {
        interfaceToDpnIdMacAddress.remove(interfaceName);
    }

    public void bindDhcpService(String interfaceName, short tableId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "dhcp", interfaceName),
                        DHCPMConstants.DHCP_SERVICE_PRIORITY, DHCPMConstants.DEFAULT_FLOW_PRIORITY,
                        DHCPMConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, DHCPMConstants.DHCP_SERVICE_PRIORITY), serviceInfo);
    }

    private InstanceIdentifier buildServiceId(String interfaceName,
                                              short dhcpServicePriority) {
        return InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(dhcpServicePriority)).build();
    }

    public BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class).addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public void unbindDhcpService(String interfaceName) {
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, DHCPMConstants.DHCP_SERVICE_PRIORITY));
    }
}
