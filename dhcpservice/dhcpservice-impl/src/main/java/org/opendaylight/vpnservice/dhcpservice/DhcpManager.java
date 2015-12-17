/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.dhcpservice;

import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;

import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import com.google.common.util.concurrent.FutureCallback;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.dhcpservice.api.DHCPMConstants;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.ActionType;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.InstructionInfo;
import org.opendaylight.vpnservice.mdsalutil.InstructionType;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.NwConstants;
import org.opendaylight.vpnservice.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.vpnservice.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DhcpManager implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(DhcpManager.class);
    private final DataBroker broker;
    IMdsalApiManager mdsalUtil;

    private int dhcpOptLeaseTime = 0;
    private int dhcpOptRenewalTime = 0;
    private int dhcpOptRebindingTime = 0;
    private String dhcpOptDefDomainName = "openstacklocal";

    private static final FutureCallback<Void> DEFAULT_CALLBACK =
        new FutureCallback<Void>() {
            public void onSuccess(Void result) {
                logger.debug("Success in Datastore write operation");
            }
            public void onFailure(Throwable error) {
                logger.error("Error in Datastore write operation", error);
            };
        };

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

    @Override
    public void close() throws Exception {
        logger.info("DHCP Manager Closed");
    }

    public void installDhcpEntries(BigInteger dpnId) {
        logger.debug("Installing Default DHCP Flow tp DPN: {}", dpnId);
        setupDefaultDhcpFlow(dpnId, DHCPMConstants.DHCP_TABLE, NwConstants.ADD_FLOW);
    }

    private void setupDefaultDhcpFlow(BigInteger dpId,  short tableId, int addOrRemove) {

        List<MatchInfo> matches = new ArrayList<MatchInfo>();

        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.ip_proto,
                new long[] { IPProtocols.UDP.intValue() }));
        matches.add(new MatchInfo(MatchFieldType.udp_src,
                new long[] { DHCPMConstants.dhcpClientPort }));
        matches.add(new MatchInfo(MatchFieldType.udp_dst,
                new long[] { DHCPMConstants.dhcpServerPort }));

        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();

        // Punt to controller
        actionsInfos.add(new ActionInfo(ActionType.punt_to_controller,
                new String[] {}));
        instructions.add(new InstructionInfo(InstructionType.write_actions,
                actionsInfos));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                getDefaultDhcpFlowRef(dpId, tableId),DHCPMConstants.DEFAULT_DHCP_FLOW_PRIORITY, "DHCP", 0, 0,
                DHCPMConstants.COOKIE_DHCP_BASE, matches, instructions);
        mdsalUtil.installFlow(flowEntity);
    }

    private String getDefaultDhcpFlowRef(BigInteger dpId, long tableId) {
        return new StringBuffer().append(DHCPMConstants.FLOWID_PREFIX).append(dpId)
                        .append(NwConstants.FLOWID_SEPARATOR).append(tableId).toString();
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
        /* TODO: Once NeutronVpn is merged, use it to get Subnet
        if (nPort != null) {
            try {
                return neutronVpnService.getNeutronSubnet(nPort.getFixedIps().get(0).getSubnetId());
            } catch (Exception e) {
                logger.warn("Failed to get Neutron Subnet from Port: {}", e);
            }
        }
        */
        if (nPort.getFixedIps() != null && !nPort.getFixedIps().isEmpty()) {
            InstanceIdentifier<Subnet> sIid =
                            InstanceIdentifier.create(Neutron.class).child(Subnets.class)
                                .child(Subnet.class, new SubnetKey(nPort.getFixedIps().get(0).getSubnetId()));
            Optional<Subnet> optSubnet = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, sIid, broker);
            if (optSubnet.isPresent()) {
                return optSubnet.get();
            }
        }
        return null;
    }

    public Port getNeutronPort(String name) {
        // TODO Once NeutronVpn is merged, use it to get port
        //return neutronVpnService.getNeutronPort(name);
        InstanceIdentifier<Ports> pIid = InstanceIdentifier.create(Neutron.class).child(Ports.class);
        Optional<Ports> optPorts = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, pIid, broker);
        if(optPorts.isPresent()) {
            for(Port port: optPorts.get().getPort()) {
                if(port.getUuid().getValue().startsWith(name.substring(3))) {
                    return port;
                }
            }
        }
        return null;
    }

}
