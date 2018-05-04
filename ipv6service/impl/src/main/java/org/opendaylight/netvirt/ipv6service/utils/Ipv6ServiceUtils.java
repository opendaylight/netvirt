/*
 * Copyright © 2016, 2017 Red Hat, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.ipv6service.utils;

import com.google.common.base.Optional;
import com.google.common.net.InetAddresses;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationEth;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationIpv6;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionOutput;
import org.opendaylight.genius.mdsalutil.actions.ActionPuntToController;
import org.opendaylight.genius.mdsalutil.actions.ActionPushVlan;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldVlanVid;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIcmpv6Type;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIpv6NdTarget;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIpv6NdTll;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIpv6;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetSource;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6NdSll;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6NdTarget;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Source;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.ipv6service.api.IVirtualPort;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.EthernetHeader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ipv6service.nd.packet.rev160620.Ipv6Header;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class Ipv6ServiceUtils {
    private static final Logger LOG = LoggerFactory.getLogger(Ipv6ServiceUtils.class);
    public static final Ipv6Address ALL_NODES_MCAST_ADDR = newIpv6Address("FF02::1");
    public static final Ipv6Address UNSPECIFIED_ADDR = newIpv6Address("0:0:0:0:0:0:0:0");

    private static Ipv6Address newIpv6Address(String ip) {
        try {
            return Ipv6Address.getDefaultInstance(InetAddress.getByName(ip).getHostAddress());
        } catch (UnknownHostException e) {
            LOG.error("Ipv6ServiceUtils: Error instantiating ipv6 address", e);
            return null;
        }
    }

    private final DataBroker broker;
    private final IMdsalApiManager mdsalUtil;

    @Inject
    public Ipv6ServiceUtils(DataBroker broker, IMdsalApiManager mdsalUtil) {
        this.broker = broker;
        this.mdsalUtil = mdsalUtil;
    }

    /**
     * Retrieves the object from the datastore.
     * @param datastoreType the data store type.
     * @param path the wild card path.
     * @return the required object.
     */
    public <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the Interface from the datastore.
     * @param interfaceName the interface name
     * @return the interface.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .Interface getInterface(String interfaceName) {
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .Interface> optInterface =
                read(LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
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
     * Build the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
            .interfaces.state.Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                .child(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                        .state.Interface.class,
                        new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                        .rev140508.interfaces.state.InterfaceKey(interfaceName));
        return idBuilder.build();
    }

    /**
     * Retrieves the interface state.
     * @param interfaceName the interface name.
     * @return the interface state.
     */
    public org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .Interface getInterfaceStateFromOperDS(String interfaceName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                .interfaces.state.Interface> ifStateId = buildStateInterfaceId(interfaceName);
        return MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, ifStateId, broker).orNull();
    }

    public static String bytesToHexString(byte[] bytes) {
        if (bytes == null) {
            return "null";
        }
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                buf.append(":");
            }
            short u8byte = (short) (bytes[i] & 0xff);
            String tmp = Integer.toHexString(u8byte);
            if (tmp.length() == 1) {
                buf.append("0");
            }
            buf.append(tmp);
        }
        return buf.toString();
    }

    public static byte[] bytesFromHexString(String values) {
        String target = "";
        if (values != null) {
            target = values;
        }
        String[] octets = target.split(":");

        byte[] ret = new byte[octets.length];
        for (int i = 0; i < octets.length; i++) {
            ret[i] = Integer.valueOf(octets[i], 16).byteValue();
        }
        return ret;
    }

    public static int calcIcmpv6Checksum(byte[] packet, Ipv6Header ip6Hdr) {
        long checksum = getSummation(ip6Hdr.getSourceIpv6());
        checksum += getSummation(ip6Hdr.getDestinationIpv6());
        checksum = normalizeChecksum(checksum);

        checksum += ip6Hdr.getIpv6Length();
        checksum += ip6Hdr.getNextHeader();

        int icmp6Offset = Ipv6Constants.ICMPV6_OFFSET;
        long value = (packet[icmp6Offset] & 0xff) << 8 | packet[icmp6Offset + 1] & 0xff;
        checksum += value;
        checksum = normalizeChecksum(checksum);
        icmp6Offset += 2;

        //move to icmp6 payload skipping the checksum field
        icmp6Offset += 2;
        int length = packet.length - icmp6Offset;
        while (length > 1) {
            value = (packet[icmp6Offset] & 0xff) << 8 | packet[icmp6Offset + 1] & 0xff;
            checksum += value;
            checksum = normalizeChecksum(checksum);
            icmp6Offset += 2;
            length -= 2;
        }

        if (length > 0) {
            checksum += packet[icmp6Offset];
            checksum = normalizeChecksum(checksum);
        }

        int finalChecksum = (int)(~checksum & 0xffff);
        return finalChecksum;
    }

    public static boolean validateChecksum(byte[] packet, Ipv6Header ip6Hdr, int recvChecksum) {
        return calcIcmpv6Checksum(packet, ip6Hdr) == recvChecksum;
    }

    private static long getSummation(Ipv6Address addr) {
        byte[] baddr = null;
        try {
            baddr = InetAddress.getByName(addr.getValue()).getAddress();
        } catch (UnknownHostException e) {
            LOG.error("getSummation: Failed to deserialize address {}", addr.getValue(), e);
            return 0;
        }

        long sum = 0;
        int len = 0;
        long value = 0;
        while (len < baddr.length) {
            value = (baddr[len] & 0xff) << 8 | baddr[len + 1] & 0xff;
            sum += value;
            sum = normalizeChecksum(sum);
            len += 2;
        }
        return sum;
    }

    private static  long normalizeChecksum(long value) {
        if ((value & 0xffff0000) != 0) {
            value = value & 0xffff;
            value += 1;
        }
        return value;
    }

    public static byte[] convertEthernetHeaderToByte(EthernetHeader ethPdu) {
        byte[] data = new byte[16];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put(bytesFromHexString(ethPdu.getDestinationMac().getValue()));
        buf.put(bytesFromHexString(ethPdu.getSourceMac().getValue()));
        buf.putShort((short)ethPdu.getEthertype().intValue());
        return data;
    }

    public static byte[] convertIpv6HeaderToByte(Ipv6Header ip6Pdu) {
        byte[] data = new byte[128];
        Arrays.fill(data, (byte)0);

        ByteBuffer buf = ByteBuffer.wrap(data);
        long flowLabel = (long)(ip6Pdu.getVersion() & 0x0f) << 28
                | ip6Pdu.getFlowLabel() & 0x0fffffff;
        buf.putInt((int)flowLabel);
        buf.putShort((short)ip6Pdu.getIpv6Length().intValue());
        buf.put((byte)ip6Pdu.getNextHeader().shortValue());
        buf.put((byte)ip6Pdu.getHopLimit().shortValue());
        try {
            byte[] baddr = InetAddress.getByName(ip6Pdu.getSourceIpv6().getValue()).getAddress();
            buf.put(baddr);
            baddr = InetAddress.getByName(ip6Pdu.getDestinationIpv6().getValue()).getAddress();
            buf.put(baddr);
        } catch (UnknownHostException e) {
            LOG.error("convertIpv6HeaderToByte: Failed to serialize src, dest address", e);
        }
        return data;
    }

    public static Ipv6Address getIpv6LinkLocalAddressFromMac(MacAddress mac) {
        byte[] octets = bytesFromHexString(mac.getValue());

        /* As per the RFC2373, steps involved to generate a LLA include
           1. Convert the 48 bit MAC address to 64 bit value by inserting 0xFFFE
              between OUI and NIC Specific part.
           2. Invert the Universal/Local flag in the OUI portion of the address.
           3. Use the prefix "FE80::/10" along with the above 64 bit Interface
              identifier to generate the IPv6 LLA. */

        StringBuilder interfaceID = new StringBuilder();
        short u8byte = (short) (octets[0] & 0xff);
        u8byte ^= 1 << 1;
        interfaceID.append(Integer.toHexString(0xFF & u8byte));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[1]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[2]));
        interfaceID.append("ff:fe");
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[3]), 2, "0"));
        interfaceID.append(":");
        interfaceID.append(Integer.toHexString(0xFF & octets[4]));
        interfaceID.append(StringUtils.leftPad(Integer.toHexString(0xFF & octets[5]), 2, "0"));

        // Return the address in its fully expanded format.
        Ipv6Address ipv6LLA = new Ipv6Address(InetAddresses.forString(
                "fe80:0:0:0:" + interfaceID.toString()).getHostAddress());
        return ipv6LLA;
    }

    public static Ipv6Address getIpv6SolicitedNodeMcastAddress(Ipv6Address ipv6Address) {

        /* According to RFC 4291, a Solicited Node Multicast Address is derived by adding the 24
           lower order bits with the Solicited Node multicast prefix (i.e., FF02::1:FF00:0/104).
           Example: For IPv6Address of FE80::2AA:FF:FE28:9C5A, the corresponding solicited node
           multicast address would be FF02::1:FF28:9C5A
         */

        byte[] octets;
        try {
            octets = InetAddress.getByName(ipv6Address.getValue()).getAddress();
        } catch (UnknownHostException e) {
            LOG.error("getIpv6SolicitedNodeMcastAddress: Failed to serialize ipv6Address ", e);
            return null;
        }

        // Return the address in its fully expanded format.
        Ipv6Address solictedV6Address = new Ipv6Address(InetAddresses.forString("ff02::1:ff"
                 + StringUtils.leftPad(Integer.toHexString(0xFF & octets[13]), 2, "0") + ":"
                 + StringUtils.leftPad(Integer.toHexString(0xFF & octets[14]), 2, "0")
                 + StringUtils.leftPad(Integer.toHexString(0xFF & octets[15]), 2, "0")).getHostAddress());

        return solictedV6Address;
    }

    public static MacAddress getIpv6MulticastMacAddress(Ipv6Address ipv6Address) {

        /* According to RFC 2464, a Multicast MAC address is derived by concatenating 32 lower
           order bits of IPv6 Multicast Address with the multicast prefix (i.e., 33:33).
           Example: For Multicast IPv6Address of FF02::1:FF28:9C5A, the corresponding L2 multicast
           address would be 33:33:28:9C:5A
         */
        byte[] octets;
        try {
            octets = InetAddress.getByName(ipv6Address.getValue()).getAddress();
        } catch (UnknownHostException e) {
            LOG.error("getIpv6MulticastMacAddress: Failed to serialize ipv6Address ", e);
            return null;
        }

        String macAddress = "33:33:"
                + StringUtils.leftPad(Integer.toHexString(0xFF & octets[12]), 2, "0") + ":"
                + StringUtils.leftPad(Integer.toHexString(0xFF & octets[13]), 2, "0") + ":"
                + StringUtils.leftPad(Integer.toHexString(0xFF & octets[14]), 2, "0") + ":"
                + StringUtils.leftPad(Integer.toHexString(0xFF & octets[15]), 2, "0");

        return new MacAddress(macAddress);
    }

    private static List<MatchInfo> getIcmpv6RSMatch(Long elanTag) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Ipv6Constants.ICMP_V6_RS_CODE, (short) 0));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NSMatch(Long elanTag, String ndTarget) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Ipv6Constants.ICMP_V6_NS_CODE, (short) 0));
        matches.add(new MatchIpv6NdTarget(new Ipv6Address(ndTarget)));
        matches.add(new MatchMetadata(MetaDataUtil.getElanTagMetadata(elanTag), MetaDataUtil.METADATA_MASK_SERVICE));
        return matches;
    }

    private static String getIPv6FlowRef(BigInteger dpId, Long elanTag, String flowType) {
        return new StringBuffer().append(Ipv6Constants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(elanTag).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(flowType).toString();
    }

    private static String getIPv6OvsFlowRef(short tableId, BigInteger dpId, int lportTag, String ndTargetAddr) {
        return new StringBuffer().append(Ipv6Constants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(tableId).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(lportTag).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(ndTargetAddr).toString();
    }

    private static String getIPv6OvsFlowRef(short tableId, BigInteger dpId, int lportTag, String ndTargetAddr,
                                            String vmMacAddress) {
        return new StringBuffer().append(Ipv6Constants.FLOWID_PREFIX)
                .append(dpId).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(tableId).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(lportTag).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(vmMacAddress).append(Ipv6Constants.FLOWID_SEPARATOR)
                .append(ndTargetAddr).toString();
    }

    public void installIcmpv6NsPuntFlow(short tableId, BigInteger dpId,  Long elanTag, String ipv6Address,
            int addOrRemove) {
        List<MatchInfo> neighborSolicitationMatch = getIcmpv6NSMatch(elanTag, ipv6Address);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                getIPv6FlowRef(dpId, elanTag, ipv6Address),Ipv6Constants.DEFAULT_FLOW_PRIORITY, "IPv6NS",
                0, 0, NwConstants.COOKIE_IPV6_TABLE, neighborSolicitationMatch, instructions);
        if (addOrRemove == Ipv6Constants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Neighbor Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.removeFlow(rsFlowEntity);
        } else {
            LOG.trace("Installing IPv6 Neighbor Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.installFlow(rsFlowEntity);
        }
    }

    public void installIcmpv6RsPuntFlow(short tableId, BigInteger dpId, Long elanTag, int addOrRemove) {
        if (dpId == null || dpId.equals(Ipv6Constants.INVALID_DPID)) {
            return;
        }
        List<MatchInfo> routerSolicitationMatch = getIcmpv6RSMatch(elanTag);
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        // Punt to controller
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                getIPv6FlowRef(dpId, elanTag, "IPv6RS"),Ipv6Constants.DEFAULT_FLOW_PRIORITY, "IPv6RS", 0, 0,
                NwConstants.COOKIE_IPV6_TABLE, routerSolicitationMatch, instructions);
        if (addOrRemove == Ipv6Constants.DEL_FLOW) {
            LOG.trace("Removing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.removeFlow(rsFlowEntity);
        } else {
            LOG.trace("Installing IPv6 Router Solicitation Flow DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.installFlow(rsFlowEntity);
        }
    }

    public BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                          BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie)
                .setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    private InstanceIdentifier<BoundServices> buildServiceId(String interfaceName,
                                              short priority) {
        return InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class,
                new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(priority)).build();
    }

    public void bindIpv6Service(String interfaceName, Long elanTag, short tableId) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getElanTagMetadata(elanTag),
                MetaDataUtil.METADATA_MASK_SERVICE, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(tableId, ++instructionKey));
        short serviceIndex = ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME, NwConstants.IPV6_SERVICE_INDEX);
        BoundServices
                serviceInfo =
                getBoundServices(String.format("%s.%s", "ipv6", interfaceName),
                        serviceIndex, Ipv6Constants.DEFAULT_FLOW_PRIORITY,
                        NwConstants.COOKIE_IPV6_TABLE, instructions);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, serviceIndex), serviceInfo);
    }

    public void unbindIpv6Service(String interfaceName) {
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION,
                buildServiceId(interfaceName, ServiceIndex.getIndex(NwConstants.IPV6_SERVICE_NAME,
                        NwConstants.IPV6_SERVICE_INDEX)));
    }

    public void instIcmpv6NsMatchFlow(short tableId, BigInteger dpId, Long elanTag, int lportTag, String vmMacAddress,
                                      Ipv6Address ndTargetAddr, int addOrRemove,  WriteTransaction installFlowInvTx,
                                      WriteTransaction removeFlowInvTx, Boolean isSllOptionSet) {
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionSetIcmpv6Type(Ipv6Constants.ICMP_V6_NA_CODE));
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(NwConstants.ARP_RESPONDER_TABLE));

        List<MatchInfo> neighborSolicitationMatch = getIcmpv6NsMatchFlow(elanTag, lportTag, vmMacAddress,
                ndTargetAddr, isSllOptionSet);

        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                Boolean.TRUE.equals(isSllOptionSet)
                        ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(), vmMacAddress) :
                        getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),
                Ipv6Constants.DEFAULT_FLOW_PRIORITY,
                "IPv6NS", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                neighborSolicitationMatch, instructions);

        if (addOrRemove == Ipv6Constants.DEL_FLOW) {
            LOG.debug("installIcmpv6NsResponderFlow: Removing IPv6 Neighbor Solicitation Flow on "
                    + "DpId {} for NDTraget {}, elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);
            mdsalUtil.removeFlowToTx(rsFlowEntity, removeFlowInvTx);
        } else {
            LOG.debug("installIcmpv6NsResponderFlow: Installing IPv6 Neighbor Solicitation Flow on "
                    + "DpId {} for NDTraget {} elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);
            mdsalUtil.addFlowToTx(rsFlowEntity, installFlowInvTx);
        }
    }


    public void installIcmpv6NaResponderFlow(OdlInterfaceRpcService interfaceManagerRpc, short tableId, BigInteger dpId,
                                             Long elanTag, int lportTag, IVirtualPort intf, Ipv6Address ndTargetAddr,
                                             String rtrIntMacAddress, int addOrRemove,
                                             WriteTransaction installFlowInvTx, WriteTransaction removeFlowInvTx,
                                             Boolean isTllOptionSet) {

        List<MatchInfo> neighborAdvertisementMatch = getIcmpv6NaResponderMatch(elanTag, lportTag, intf.getMacAddress(),
                ndTargetAddr, isTllOptionSet);

        if (addOrRemove == Ipv6Constants.DEL_FLOW) {
            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    Boolean.TRUE.equals(isTllOptionSet)
                            ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(),
                            intf.getMacAddress()) : getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),
                    Ipv6Constants.DEFAULT_FLOW_PRIORITY,
                    "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                    neighborAdvertisementMatch, null);
            LOG.debug("installIcmpv6NaResponderFlow: Removing IPv6 Neighbor Advertisement Flow on "
                    + "DpId {} for the NDTraget {}, elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);

            mdsalUtil.removeFlowToTx(rsFlowEntity, removeFlowInvTx);
        } else {
            List<ActionInfo> actionsInfos = new ArrayList<>();
            // Move Eth Src to Eth Dst
            actionsInfos.add(new ActionMoveSourceDestinationEth());
            actionsInfos.add(new ActionSetFieldEthernetSource(new MacAddress(rtrIntMacAddress)));

            // Move Ipv6 Src to Ipv6 Dst
            actionsInfos.add(new ActionMoveSourceDestinationIpv6());
            actionsInfos.add(new ActionSetSourceIpv6(ndTargetAddr.getValue()));

            actionsInfos.add(new ActionSetIpv6NdTarget(ndTargetAddr));
            if (Boolean.TRUE.equals(isTllOptionSet)) {
                actionsInfos.add(new ActionSetIpv6NdTll(new MacAddress(rtrIntMacAddress)));
            }
            actionsInfos.add(new ActionNxLoadInPort(BigInteger.ZERO));
            List<ActionInfo> egressActions = new ArrayList<>();
            egressActions = getEgressActionsForInterface(interfaceManagerRpc, intf.getIntfUUID().getValue(),
                    egressActions.size());
            if (egressActions.isEmpty()) {
                LOG.error("installIcmpv6NaResponderFlow: Failed to retrieve egress action for interface {}, "
                                + "NdTarget {}. Aborting NA responder flow installation on the DPN {}",
                        intf.getIntfUUID(), ndTargetAddr.getValue(), dpId);
                return;
            }
            actionsInfos.addAll(egressActions);
            List<InstructionInfo> instructions = new ArrayList<>();
            instructions.add(new InstructionApplyActions(actionsInfos));

            FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                    Boolean.TRUE.equals(isTllOptionSet)
                            ? getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue(),
                            intf.getMacAddress()) : getIPv6OvsFlowRef(tableId, dpId, lportTag, ndTargetAddr.getValue()),
                    Ipv6Constants.DEFAULT_FLOW_PRIORITY,
                    "IPv6NA", 0, 0, NwConstants.COOKIE_IPV6_TABLE,
                    neighborAdvertisementMatch, instructions);
            LOG.debug("installIcmpv6NaResponderFlow: Installing IPv6 Neighbor Advertisement Flow on "
                    + "DpId {} for the NDTraget {},  elanTag {}, lportTag {}", dpId, ndTargetAddr.getValue(),
                    elanTag, lportTag);

            mdsalUtil.addFlowToTx(rsFlowEntity, installFlowInvTx);
        }
    }

    public void installIcmpv6NsDefaultPuntFlow(short tableId, BigInteger dpId,  Long elanTag, String ipv6Address,
                                               int addOrRemove, WriteTransaction installFlowInvTx,
                                               WriteTransaction removeFlowInvTx) {
        List<MatchInfo> neighborSolicitationMatch = getIcmpv6NSMatch(elanTag, ipv6Address);
        neighborSolicitationMatch.add(new MatchIpv6Source(UNSPECIFIED_ADDR.getValue() + "/128"));
        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionPuntToController());
        instructions.add(new InstructionApplyActions(actionsInfos));
        FlowEntity rsFlowEntity = MDSALUtil.buildFlowEntity(dpId, tableId,
                getIPv6FlowRef(dpId, elanTag, ipv6Address),Ipv6Constants.DEFAULT_OVS_FLOW_PRIORITY, "IPv6NS",
                0, 0, NwConstants.COOKIE_IPV6_TABLE, neighborSolicitationMatch, instructions);
        if (addOrRemove == Ipv6Constants.DEL_FLOW) {
            LOG.debug("installIcmpv6NsDefaultPuntFlow: Removing OVS based NA responder default subnet punt flow on "
                    + "DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.removeFlowToTx(rsFlowEntity, removeFlowInvTx);
        } else {
            LOG.debug("installIcmpv6NsDefaultPuntFlow: Installing OVS based NA responder default subnet punt flow on "
                    + "DpId {}, elanTag {}", dpId, elanTag);
            mdsalUtil.addFlowToTx(rsFlowEntity, installFlowInvTx);
        }
    }

    private List<MatchInfo> getIcmpv6NsMatchFlow(Long elanTag, int lportTag, String vmMacAddress,
                                                 Ipv6Address ndTargetAddr,
                                                 Boolean isSllOptionSet) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Ipv6Constants.ICMP_V6_NS_CODE, (short) 0));
        matches.add(new MatchIpv6NdTarget(new Ipv6Address(ndTargetAddr)));
        if (Boolean.TRUE.equals(isSllOptionSet)) {
            matches.add(new MatchIpv6NdSll(new MacAddress(vmMacAddress)));
        }
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag, lportTag),
                ElanHelper.getElanMetadataMask()));
        return matches;
    }

    private List<MatchInfo> getIcmpv6NaResponderMatch(Long elanTag, int lportTag, String vmMacAddress,
                                                      Ipv6Address ndTarget, Boolean isTllOptionSet) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV6);
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchIcmpv6(Ipv6Constants.ICMP_V6_NA_CODE, (short) 0));
        matches.add(new MatchIpv6NdTarget(new Ipv6Address(ndTarget)));
        if (Boolean.TRUE.equals(isTllOptionSet)) {
            matches.add(new MatchEthernetSource(new MacAddress(vmMacAddress)));
        }
        matches.add(new MatchMetadata(ElanHelper.getElanMetadataLabel(elanTag, lportTag),
                ElanHelper.getElanMetadataMask()));
        return matches;
    }

    private List<ActionInfo> getEgressActionsForInterface(OdlInterfaceRpcService interfaceManagerRpc,
                                                            final String ifName, int actionKey) {
        RpcResult rpcResult;
        List<Action> actions;
        try {
            rpcResult = interfaceManagerRpc.getEgressActionsForInterface(
                    new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName).build()).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("RPC Call to Get egress vm actions for interface {} returned with Errors {}",
                        ifName, rpcResult.getErrors());
                return Collections.emptyList();
            } else {
                GetEgressActionsForInterfaceOutput output =
                        (GetEgressActionsForInterfaceOutput) rpcResult.getResult();
                actions = output.getAction();
            }
            List<ActionInfo> listActionInfo = new ArrayList<>();
            for (Action action : actions) {
                actionKey = action.getKey().getOrder() + actionKey;
                org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.Action
                        actionClass = action.getAction();
                if (actionClass instanceof OutputActionCase) {
                    listActionInfo.add(new ActionOutput(actionKey,
                            ((OutputActionCase) actionClass).getOutputAction().getOutputNodeConnector()));
                } else if (actionClass instanceof PushVlanActionCase) {
                    listActionInfo.add(new ActionPushVlan(actionKey));
                } else if (actionClass instanceof SetFieldCase) {
                    if (((SetFieldCase) actionClass).getSetField().getVlanMatch() != null) {
                        int vlanVid = ((SetFieldCase) actionClass).getSetField().getVlanMatch()
                                .getVlanId().getVlanId().getValue();
                        listActionInfo.add(new ActionSetFieldVlanVid(actionKey, vlanVid));
                    }
                } else if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                    Short tableId = ((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable();
                    listActionInfo.add(new ActionNxResubmit(actionKey, tableId));
                } else if (actionClass instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                    NxRegLoad nxRegLoad =
                            ((NxActionRegLoadNodesNodeTableFlowApplyActionsCase) actionClass).getNxRegLoad();
                    listActionInfo.add(new ActionRegLoad(actionKey, NxmNxReg6.class,
                            nxRegLoad.getDst().getStart(), nxRegLoad.getDst().getEnd(),
                            nxRegLoad.getValue().longValue()));
                }
            }
            return listActionInfo;
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when egress actions for interface {}", ifName, e);
        }
        LOG.warn("Exception when egress actions for interface {}", ifName);
        return Collections.emptyList();
    }
}
