/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.api.rev160608.InterfaceAcl;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class AclServiceUtils {

    public static final Integer PROTO_MATCH_PRIORITY = 61010;
    public static final Integer PREFIX_MATCH_PRIORITY = 61009;
    public static final Integer PROTO_PREFIX_MATCH_PRIORITY = 61008;
    public static final Integer PROTO_PORT_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_PORT_PREFIX_MATCH_PRIORITY = 61007;
    public static final Integer PROTO_DHCP_SERVER_MATCH_PRIORITY = 61006;
    public static final Integer PROTO_VM_IP_MAC_MATCH_PRIORITY = 36001;
    public static final Integer CT_STATE_UNTRACKED_PRIORITY = 62030;
    public static final Integer CT_STATE_TRACKED_EXIST_PRIORITY = 62020;
    public static final Integer CT_STATE_TRACKED_NEW_PRIORITY = 62010;
    public static final Integer CT_STATE_NEW_PRIORITY_DROP = 36007;
    public static final short dhcpClientPort_IpV4 = 68;
    public static final short dhcpServerPort_IpV4 = 67;
    public static final short dhcpClientPort_IpV6 = 568;
    public static final short dhcpServerPort_Ipv6 = 567;
    public static final BigInteger COOKIE_ACL_BASE = new BigInteger("6900000", 16);
    public static final int UNTRACKED_CT_STATE = 0x00;
    public static final int UNTRACKED_CT_STATE_MASK = 0x20;
    public static final int TRACKED_EST_CT_STATE = 0x22;
    public static final int TRACKED_REL_CT_STATE = 0x24;
    public static final int TRACKED_NEW_CT_STATE = 0x21;
    public static final int TRACKED_INV_CT_STATE = 0x30;
    public static final int TRACKED_INV_CT_STATE_MASK = 0x30;
    public static final int TRACKED_CT_STATE_MASK = 0x37;
    public static final int TRACKED_NEW_CT_STATE_MASK = 0x21;
    private static final Logger LOG = LoggerFactory.getLogger(AclServiceUtils.class);

    /**
     * Retrieves the Interface from the datastore.
     * @param broker the data broker
     * @param interfaceName the interface name
     * @return the interface.
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .Interface getInterface(DataBroker broker, String interfaceName) {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface> optInterface =
                read(broker, LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
        if (optInterface.isPresent()) {
            return optInterface.get();
        }
        return null;
    }

    /**
     * Builds the interface identifier.
     * @param interfaceName the interface name.
     * @return the interface identifier.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
        .interfaces.Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
                .child(
                    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                    .Interface.class, new InterfaceKey(interfaceName)).build();
    }

    /**
     * Retrieves the object from the datastore.
     * @param broker the data broker.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @return the required object.
     */
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tx.close();
        }

        return result;
    }

    /**
     * Get the data path number for the interface.
     * @param interfaceManagerRpcService interfaceManagerRpcService instance.
     * @param ifName the interface name.
     * @return the dpn.
     */
    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput dpIdInput =
                    new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>> dpIdOutput =
                    interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

    /**
     * Retrieves the interface state.
     * @param dataBroker the data broker.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
        .Interface getInterfaceStateFromOperDS(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> ifStateId = buildStateInterfaceId(interfaceName);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> ifStateOptional = MDSALUtil.read(LogicalDatastoreType
                .OPERATIONAL, ifStateId, dataBroker);
        if (!ifStateOptional.isPresent()) {
            return null;
        }

        return ifStateOptional.get();
    }

    /**
     * Build the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
        .interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> idBuilder = InstanceIdentifier.builder(InterfacesState.class)
            .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface.class, new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
            .rev140508.interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    /**
     * Checks whether port security is enabled for the port.
     * @param port the port.
     * @param broker the data broker.
     * @return the port security is enabled/not.
     */
    public static boolean isPortSecurityEnabled(Interface port, DataBroker broker) {
        if (port == null) {
            LOG.error("Port is Null");
            return false;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated}",
                port.getName());
            return false;
        }
        return aclInPort.isPortSecurityEnabled();
    }

    /**
     * Returns the DHCP match.
     * @param srcPort the source port.
     * @param dscPort the destination port.
     * @return list of matches.
     */
    public static List<MatchInfoBase> programDhcpMatches(int srcPort, int dscPort) {
        List<MatchInfoBase> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.ip_proto,
                new long[] { IPProtocols.UDP.intValue() }));
        matches.add(new MatchInfo(MatchFieldType.udp_dst,
                new long[] { srcPort }));
        matches.add(new MatchInfo(MatchFieldType.udp_src,
                new long[] { dscPort}));
        return matches;
    }
}
