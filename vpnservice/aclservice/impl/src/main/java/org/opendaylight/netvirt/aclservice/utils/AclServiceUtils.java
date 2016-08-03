/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchFieldType;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.packet.IPProtocols;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.Ipv4Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.AclKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AclServiceUtils {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceUtils.class);

    private AclServiceUtils() { }

    /**
     * Retrieves the Interface from the datastore.
     * @param broker the data broker
     * @param interfaceName the interface name
     * @return the interface.
     */
    public static Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
        .Interface> getInterface(DataBroker broker, String interfaceName) {
        return read(broker, LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
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
    public static <T extends DataObject> Optional<T> read(
            DataBroker broker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {

        Optional<T> result = Optional.absent();
        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();
        try {
            result = tx.read(datastoreType, path).checkedGet();
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read InstanceIdentifier {} from {}", path, datastoreType, e);
        } finally {
            tx.close();
        }
        return result;
    }

    /**
     * Retrieves the acl matching the key from the data store.
     *
     * @param broker the data broker
     * @param aclKey the acl key
     * @return the acl
     */
    public static Acl getAcl(DataBroker broker, String aclKey) {
        Optional<Acl> optAcl = read(broker,
            LogicalDatastoreType.CONFIGURATION, getAclInstanceIdentifier(aclKey));
        if (optAcl.isPresent()) {
            return optAcl.get();
        }
        return null;
    }

    /** Creates the Acl instance identifier.
     *
     * @param aclKey the acl key
     * @return the instance identifier
     */
    public static InstanceIdentifier<Acl> getAclInstanceIdentifier(String aclKey) {
        return InstanceIdentifier
                .builder(AccessLists.class)
                .child(Acl.class,
                        new AclKey(aclKey,Ipv4Acl.class))
                .build();
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
     * @return the port security is enabled/not.
     */
    public static boolean isPortSecurityEnabled(Interface port) {
        if (port == null) {
            LOG.error("Port is Null");
            return false;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated to Interface port: {}", port.getName());
            return false;
        }
        return aclInPort.isPortSecurityEnabled();
    }

    /**
     * Checks whether port security is enabled for the port.
     * @param port the port.
     * @return the list of security groups.
     */
    public static List<Uuid> getInterfaceAcls(Interface port) {
        if (port == null) {
            LOG.error("Port is Null");
            return null;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated}",
                port.getName());
            return null;
        }
        return aclInPort.getSecurityGroups();
    }

    /**
     * Retrieves the security rule attribute augmentation from the access list.
     * @param ace the access list entry
     * @return the security rule attributes
     */
    public static SecurityRuleAttr  getAccesssListAttributes(Ace ace) {
        if (ace == null) {
            LOG.error("Ace is Null");
            return null;
        }
        SecurityRuleAttr aceAttributes = ace.getAugmentation(SecurityRuleAttr.class);
        if (aceAttributes == null) {
            LOG.error("Ace is null");
            return null;
        }
        return aceAttributes;
    }

    /**
     * Returns the DHCP match.
     *
     * @param srcPort the source port.
     * @param dstPort the destination port.
     * @param lportTag the lport tag
     * @return list of matches.
     */
    public static List<MatchInfoBase> buildDhcpMatches(int srcPort, int dstPort, int lportTag) {
        List<MatchInfoBase> matches = new ArrayList<>(6);
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { NwConstants.ETHTYPE_IPV4 }));
        matches.add(new MatchInfo(MatchFieldType.ip_proto,
                new long[] { IPProtocols.UDP.intValue() }));
        matches.add(new MatchInfo(MatchFieldType.udp_dst,
                new long[] { dstPort }));
        matches.add(new MatchInfo(MatchFieldType.udp_src,
                new long[] { srcPort}));
        matches.add(AclServiceUtils.buildLPortTagMatch(lportTag));
        return matches;
    }

    /**
     * Builds the service id.
     *
     * @param interfaceName the interface name
     * @param serviceIndex the service index
     * @param serviceMode the service mode
     * @return the instance identifier
     */
    public static InstanceIdentifier<BoundServices> buildServiceId(String interfaceName, short serviceIndex,
            Class<? extends ServiceModeBase> serviceMode) {
        return InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, serviceMode))
                .child(BoundServices.class, new BoundServicesKey(serviceIndex)).build();
    }

    /**
     * Gets the bound services.
     *
     * @param serviceName the service name
     * @param servicePriority the service priority
     * @param flowPriority the flow priority
     * @param cookie the cookie
     * @param instructions the instructions
     * @return the bound services
     */
    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
            BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority)
                .setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority)).setServiceName(serviceName)
                .setServicePriority(servicePriority).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static List<Uuid> getUpdatedAclList(Interface updatedPort, Interface currentPort) {
        if (updatedPort == null) {
            return null;
        }
        List<Uuid> updatedAclList = new ArrayList<>(AclServiceUtils.getInterfaceAcls(updatedPort));
        if (currentPort == null) {
            return updatedAclList;
        }
        List<Uuid> currentAclList = new ArrayList<>(AclServiceUtils.getInterfaceAcls(currentPort));
        for (Iterator<Uuid> iterator = updatedAclList.iterator(); iterator.hasNext();) {
            Uuid updatedAclUuid = iterator.next();
            for (Uuid currentAclUuid :currentAclList) {
                if (updatedAclUuid.getValue().equals(currentAclUuid.getValue())) {
                    iterator.remove();
                }
            }
        }
        return updatedAclList;
    }

    public static List<AllowedAddressPairs> getUpdatedAllowedAddressPairs(Interface updatedPort,
            Interface currentPort) {
        if (updatedPort == null) {
            return null;
        }
        List<AllowedAddressPairs> updatedAllowedAddressPairs =
                new ArrayList<>(AclServiceUtils.getPortAllowedAddresses(updatedPort));
        if (currentPort == null) {
            return updatedAllowedAddressPairs;
        }
        List<AllowedAddressPairs> currentAllowedAddressPairs =
                new ArrayList<>(AclServiceUtils.getPortAllowedAddresses(currentPort));
        for (Iterator<AllowedAddressPairs> iterator = updatedAllowedAddressPairs.iterator(); iterator.hasNext();) {
            AllowedAddressPairs updatedAllowedAddressPair = iterator.next();
            for (AllowedAddressPairs currentAllowedAddressPair : currentAllowedAddressPairs) {
                if (updatedAllowedAddressPair.getKey().equals(currentAllowedAddressPair.getKey())) {
                    iterator.remove();
                    break;
                }
            }
        }
        return updatedAllowedAddressPairs;
    }

    public static List<AllowedAddressPairs> getPortAllowedAddresses(Interface port) {
        if (port == null) {
            LOG.error("Port is Null");
            return null;
        }
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort == null) {
            LOG.error("getSecurityGroupInPortList: no security group associated to Interface port: {}", port.getName());
            return null;
        }
        return aclInPort.getAllowedAddressPairs();
    }

    public static BigInteger getDpIdFromIterfaceState(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
            .interfaces.rev140508.interfaces.state.Interface interfaceState) {
        BigInteger dpId = null;
        String interfaceName = interfaceState.getName();
        List<String> ofportIds = interfaceState.getLowerLayerIf();
        if (ofportIds != null && !ofportIds.isEmpty()) {
            NodeConnectorId nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
            dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
        }
        return dpId;
    }

    /**
     * Builds the ip matches.
     *
     * @param ipPrefixOrAddress the ip prefix or address
     * @param ipv4MatchType the ipv4 match type
     * @return the list
     */
    public static List<MatchInfoBase> buildIpMatches(IpPrefixOrAddress ipPrefixOrAddress,
            MatchFieldType ipv4MatchType) {
        List<MatchInfoBase> flowMatches = new ArrayList<>();
        flowMatches.add(new MatchInfo(MatchFieldType.eth_type, new long[] {NwConstants.ETHTYPE_IPV4}));
        IpPrefix ipPrefix = ipPrefixOrAddress.getIpPrefix();
        if (ipPrefix != null) {
            if (ipPrefix.getIpv4Prefix().getValue() != null) {
                String[] ipaddressValues = ipPrefix.getIpv4Prefix().getValue().split("/");
                flowMatches.add(new MatchInfo(ipv4MatchType, new String[] {ipaddressValues[0], ipaddressValues[1]}));
            } else {
                // Handle IPv6
            }
        } else {
            IpAddress ipAddress = ipPrefixOrAddress.getIpAddress();
            if (ipAddress.getIpv4Address() != null) {
                flowMatches
                        .add(new MatchInfo(ipv4MatchType, new String[] {ipAddress.getIpv4Address().getValue(), "32"}));
            } else {
                // Handle IPv6
            }
        }
        return flowMatches;
    }

    /**
     * Gets the lport tag match.
     *
     * @param lportTag the lport tag
     * @return the lport tag match
     */
    public static MatchInfo buildLPortTagMatch(int lportTag) {
        return new MatchInfo(MatchFieldType.metadata,
                new BigInteger[] {MetaDataUtil.getLportTagMetaData(lportTag), MetaDataUtil.METADATA_MASK_LPORT_TAG});
    }
}
