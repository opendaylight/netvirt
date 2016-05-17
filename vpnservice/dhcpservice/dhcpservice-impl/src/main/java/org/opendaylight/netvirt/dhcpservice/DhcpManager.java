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
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.config.rev150710.DhcpserviceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpManager {

    private static final Logger logger = LoggerFactory.getLogger(DhcpManager.class);
    private final IMdsalApiManager mdsalUtil;
    private final INeutronVpnManager neutronVpnService;

    private int dhcpOptLeaseTime = 0;
    private String dhcpOptDefDomainName;
    private DhcpInterfaceEventListener dhcpInterfaceEventListener;
    private DhcpInterfaceConfigListener dhcpInterfaceConfigListener;

    // cache used to maintain DpnId and physical address for each interface.
    private static HashMap<String, ImmutablePair<BigInteger, String>> interfaceToDpnIdMacAddress = new HashMap<>();

    public DhcpManager(final IMdsalApiManager mdsalApiManager,
            final INeutronVpnManager neutronVpnManager,
            DhcpserviceConfig config, final DataBroker dataBroker,
            final DhcpExternalTunnelManager dhcpExternalTunnelManager) {
        this.mdsalUtil = mdsalApiManager;
        this.neutronVpnService = neutronVpnManager;
        configureLeaseDuration(DHCPMConstants.DEFAULT_LEASE_TIME);

        if (config.isControllerDhcpEnabled()) {
            dhcpInterfaceEventListener = new DhcpInterfaceEventListener(this, dataBroker, dhcpExternalTunnelManager);
            dhcpInterfaceConfigListener = new DhcpInterfaceConfigListener(dataBroker, dhcpExternalTunnelManager);
        }
    }

    public void close() throws Exception {
        if (dhcpInterfaceEventListener != null) {
            dhcpInterfaceEventListener.close();
        }
        if (dhcpInterfaceConfigListener != null) {
            dhcpInterfaceConfigListener.close();
        }
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
        } else {
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
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil);
    }

    public void unInstallDhcpEntries(BigInteger dpId, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpId, NwConstants.DHCP_TABLE, vmMacAddress, NwConstants.DEL_FLOW, mdsalUtil);
    }

    public void setupTableMissForDhcpTable(BigInteger dpId) {
        List<MatchInfo> matches = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.LPORT_DISPATCHER_TABLE }));

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DHCP_TABLE, "DHCPTableMissFlow",
                0, "DHCP Table Miss Flow", 0, 0,
                DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
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
}
