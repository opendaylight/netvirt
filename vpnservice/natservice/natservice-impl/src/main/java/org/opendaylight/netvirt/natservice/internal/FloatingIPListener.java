/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.FloatingIpInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.PortsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMappingBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.GroupTypes;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yangtools.yang.common.RpcResult;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by emhamla on 1/18/2016.
 */
public class FloatingIPListener extends AbstractDataChangeListener<IpMapping> implements AutoCloseable{
    private static final Logger LOG = LoggerFactory.getLogger(FloatingIPListener.class);
    private static final long FIXED_DELAY_IN_MILLISECONDS = 4000;
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private OdlInterfaceRpcService interfaceManager;
    private IMdsalApiManager mdsalManager;
    private FloatingIPHandler handler;
    private IdManagerService idManager;


    public FloatingIPListener (final DataBroker db) {
        super(IpMapping.class);
        broker = db;
        registerListener(db);
    }

    void setFloatingIpHandler(FloatingIPHandler handler) {
        this.handler = handler;
    }


    public void setIdManager(IdManagerService idManager) {
        this.idManager = idManager;
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("FloatingIP Listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), FloatingIPListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("FloatingIP DataChange listener registration fail!", e);
            throw new IllegalStateException("FloatingIP Listener registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<IpMapping> getWildCardPath() {
        return InstanceIdentifier.create(FloatingIpInfo.class).child(RouterPorts.class).child(Ports.class).child(IpMapping.class);
    }

    public void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    @Override
    protected void add(final InstanceIdentifier<IpMapping> identifier,
                       final IpMapping mapping) {
        LOG.trace("FloatingIPListener add ip mapping method - key: " + identifier + ", value=" + mapping );
        processFloatingIPAdd(identifier, mapping);
    }

    @Override
    protected void remove(InstanceIdentifier<IpMapping> identifier, IpMapping mapping) {
        LOG.trace("FloatingIPListener remove ip mapping method - key: " + identifier + ", value=" + mapping );
        processFloatingIPDel(identifier, mapping);
    }

    @Override
    protected void update(InstanceIdentifier<IpMapping> identifier, IpMapping original, IpMapping update) {
        LOG.trace("FloatingIPListener update ip mapping method - key: " + identifier + ", original=" + original + ", update=" + update );
    }

    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput
                    dpIdInput =
                    new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                    dpIdOutput =
                    interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

    private FlowEntity buildPreDNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId, long vpnId) {
        return buildPreDNATFlowEntity(dpId, internalIp, externalIp, routerId, vpnId, NatConstants.INVALID_ID);
    }

    private FlowEntity buildPreDNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId, long vpnId, long associatedVpn) {
        LOG.info("Bulding DNAT Flow entity for ip {} ", externalIp);

        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;
        LOG.debug("Segment id {} in build preDNAT Flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
                externalIp, "32" }));

//        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
//                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.set_destination_ip, new String[]{ internalIp, "32" }));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.write_metadata,
                new BigInteger[] { MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID }));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.DNAT_TABLE }));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }



    private FlowEntity buildDNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId) {
        return buildDNATFlowEntity(dpId, internalIp, externalIp, routerId, NatConstants.INVALID_ID);
    }

    private FlowEntity buildDNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId, long associatedVpn) {

        LOG.info("Bulding DNAT Flow entity for ip {} ", externalIp);

        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;
        LOG.debug("Segment id {} in build DNAT", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID }));

        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        matches.add(new MatchInfo(MatchFieldType.ipv4_destination, new String[] {
        //        externalIp, "32" }));
                  internalIp, "32" }));

        List<ActionInfo> actionsInfos = new ArrayList<>();
//        actionsInfos.add(new ActionInfo(ActionType.set_destination_ip, new String[]{ internalIp, "32" }));

        List<InstructionInfo> instructions = new ArrayList<>();
//        instructions.add(new InstructionInfo(InstructionType.write_metadata, new BigInteger[] { BigInteger.valueOf
//                (routerId), MetaDataUtil.METADATA_MASK_VRFID }));
        actionsInfos.add(new ActionInfo(ActionType.nx_resubmit, new String[] { Integer.toString(NwConstants.L3_FIB_TABLE) }));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        //instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NatConstants.L3_FIB_TABLE }));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;

    }

    private FlowEntity buildPreSNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long vpnId, long routerId) {
        return buildPreSNATFlowEntity(dpId, internalIp, externalIp, vpnId, routerId, NatConstants.INVALID_ID);
    }

    private FlowEntity buildPreSNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long vpnId, long routerId, long associatedVpn) {

        LOG.info("Building PSNAT Flow entity for ip {} ", internalIp);

        long segmentId = (associatedVpn == NatConstants.INVALID_ID) ? routerId : associatedVpn;

        LOG.debug("Segment id {} in build preSNAT flow", segmentId);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        matches.add(new MatchInfo(MatchFieldType.ipv4_source, new String[] {
                internalIp, "32" }));

        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getVpnIdMetadata(segmentId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        actionsInfos.add(new ActionInfo(ActionType.set_source_ip, new String[]{ externalIp, "32" }));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.write_metadata,
                new BigInteger[] { MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.SNAT_TABLE }));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private FlowEntity buildSNATFlowEntity(BigInteger dpId, String internalIp, String externalIp, long vpnId, String macAddress) {
        LOG.info("Building SNAT Flow entity for ip {} ", internalIp);
        long groupId = installExtNetGroupEntry(dpId, vpnId, macAddress);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        matches.add(new MatchInfo(MatchFieldType.ipv4_source, new String[] {
                  externalIp, "32" }));

        List<ActionInfo> actionsInfo = new ArrayList<>();
        List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();

        IpAddress externalIpv4Address = new IpAddress(new Ipv4Address(externalIp));
        MacAddress dstMac = NatUtil.getMacAddressForFloatingIp(broker, externalIpv4Address);
        if (dstMac != null) {
            actionsInfo.add(new ActionInfo(ActionType.set_field_eth_src, new String[] { dstMac.getValue() }));
        } else {
            LOG.warn("No MAC address found for floating IP {}", externalIp);
        }

        actionsInfo.add(new ActionInfo(ActionType.group, new String[] {String.valueOf(groupId)}));
        instructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfo));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, vpnId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;
    }

    private long installExtNetGroupEntry(BigInteger dpId, long vpnId, String macAddress) {
        String routerId = NatUtil.getRouterIdfromVpnId(broker, vpnId);
        if (routerId == null) {
            LOG.warn("No router associated with vpn id {}", vpnId);
            return 0;
        }

        String extNetwork = NatUtil.getAssociatedExternalNetwork(broker, routerId);
        if (extNetwork == null) {
            LOG.warn("No external network associated with router id {} vpn id {}", routerId, vpnId);
            return 0;
        }

        String extInterface = NatServiceAccessor.getElanProvider().getExternalElanInterface(extNetwork, dpId);
        if (extInterface == null) {
            LOG.warn("No external ELAN interface attached to vpn id {} on DPN {}", vpnId, dpId);
            return 0;
        }

        List<ActionInfo> actionList = NatUtil.getEgressActionsForInterface(interfaceManager, extInterface, null);
        if (actionList == null || actionList.isEmpty()) {
            LOG.warn("No actions found for interface {} vpn id {}", extInterface, vpnId);
            return 0;
        }

        if(!Strings.isNullOrEmpty(macAddress)) {
            actionList.add(new ActionInfo(ActionType.set_field_eth_dest, new String[]{ macAddress }));
        }

        List<BucketInfo> listBucketInfo = new ArrayList<BucketInfo>();
        listBucketInfo.add(new BucketInfo(actionList));
        long groupId = NatUtil.createGroupId(NatUtil.getGroupIdKey(routerId), idManager);
        GroupEntity groupEntity = MDSALUtil.buildGroupEntity(dpId, groupId, routerId, GroupTypes.GroupIndirect,
                listBucketInfo);
        LOG.trace("Install ext-net Group: id {} gw mac address {} vpn id {}", groupId, macAddress, vpnId);
        mdsalManager.syncInstallGroup(groupEntity, FIXED_DELAY_IN_MILLISECONDS);
        return groupId;
    }

    private void createDNATTblEntry(BigInteger dpnId, String internalIp, String externalIp, long routerId, long vpnId, long associatedVpnId) {
        FlowEntity pFlowEntity = buildPreDNATFlowEntity(dpnId, internalIp, externalIp, routerId, vpnId, associatedVpnId );
        mdsalManager.installFlow(pFlowEntity);

        FlowEntity flowEntity = buildDNATFlowEntity(dpnId, internalIp, externalIp, routerId, associatedVpnId);
        mdsalManager.installFlow(flowEntity);
    }

    private void removeDNATTblEntry(BigInteger dpnId, String internalIp, String externalIp, long routerId) {
        FlowEntity pFlowEntity = buildPreDNATDeleteFlowEntity(dpnId, internalIp, externalIp, routerId );
        mdsalManager.removeFlow(pFlowEntity);

        FlowEntity flowEntity = buildDNATDeleteFlowEntity(dpnId, internalIp, externalIp, routerId);
        mdsalManager.removeFlow(flowEntity);
    }

    private void createSNATTblEntry(BigInteger dpnId, String internalIp, String externalIp, long vpnId, long routerId, String macAddress, long associatedVpnId) {
        FlowEntity pFlowEntity = buildPreSNATFlowEntity(dpnId, internalIp, externalIp, vpnId , routerId, associatedVpnId);
        mdsalManager.installFlow(pFlowEntity);

        FlowEntity flowEntity = buildSNATFlowEntity(dpnId, internalIp, externalIp, vpnId, macAddress);
        mdsalManager.installFlow(flowEntity);

    }

    private void removeSNATTblEntry(BigInteger dpnId, String internalIp, long routerId, String externalIp, long vpnId) {
        FlowEntity pFlowEntity = buildPreSNATDeleteFlowEntity(dpnId, internalIp, routerId, externalIp);
        mdsalManager.removeFlow(pFlowEntity);

        FlowEntity flowEntity = buildSNATDeleteFlowEntity(dpnId, internalIp, vpnId, externalIp);
        mdsalManager.removeFlow(flowEntity);

    }

    private Uuid getExtNetworkId(final InstanceIdentifier<RouterPorts> pIdentifier, LogicalDatastoreType dataStoreType) {
        Optional<RouterPorts> rtrPort = NatUtil.read(broker, dataStoreType, pIdentifier);
        if(!rtrPort.isPresent()) {
            LOG.error("Unable to read router port entry for {}", pIdentifier);
            return null;
        }

        Uuid extNwId = rtrPort.get().getExternalNetworkId();
        return extNwId;
    }

    private long getVpnId(Uuid extNwId) {
        InstanceIdentifier<Networks> nwId = InstanceIdentifier.builder(ExternalNetworks.class).child(Networks.class, new NetworksKey(extNwId)).build();
        Optional<Networks> nw = NatUtil.read(broker, LogicalDatastoreType.CONFIGURATION, nwId);
        if(!nw.isPresent()) {
            LOG.error("Unable to read external network for {}", extNwId);
            return NatConstants.INVALID_ID;
        }

        Uuid vpnUuid = nw.get().getVpnid();
        if(vpnUuid == null) {
            return NatConstants.INVALID_ID;
        }

        //Get the id using the VPN UUID (also vpn instance name)
        return NatUtil.readVpnId(broker, vpnUuid.getValue());
    }

    private long getVpnId(Uuid extNwId, long routerId) {
        long vpnId = getVpnId(extNwId);
        return vpnId < 0 ? routerId : vpnId;
    }

    private void processFloatingIPAdd(final InstanceIdentifier<IpMapping> identifier,
                                      final IpMapping mapping) {
        LOG.trace("Add event - key: {}, value: {}", identifier, mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> pIdentifier = identifier.firstIdentifierOf(RouterPorts.class);
        createNATFlowEntries(interfaceName, mapping, pIdentifier, routerId);
    }

    private void processFloatingIPDel(final InstanceIdentifier<IpMapping> identifier,
                                      final IpMapping mapping) {
        LOG.trace("Del event - key: {}, value: {}", identifier, mapping);

        final String routerId = identifier.firstKeyOf(RouterPorts.class).getRouterId();
        final PortsKey pKey = identifier.firstKeyOf(Ports.class);
        String interfaceName = pKey.getPortName();

        InstanceIdentifier<RouterPorts> pIdentifier = identifier.firstIdentifierOf(RouterPorts.class);
        removeNATFlowEntries(interfaceName, mapping, pIdentifier, routerId);
    }

    private InetAddress getInetAddress(String ipAddr) {
        InetAddress ipAddress = null;
        try {
            ipAddress = InetAddress.getByName(ipAddr);
        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException for ip {}", ipAddr);
        }
        return ipAddress;
    }

    private boolean validateIpMapping(IpMapping mapping) {
        return getInetAddress(mapping.getInternalIp()) != null &&
                    getInetAddress(mapping.getExternalIp()) != null;
    }

    void createNATFlowEntries(String interfaceName, final IpMapping mapping,
                              final InstanceIdentifier<RouterPorts> pIdentifier, final String routerName) {
        if(!validateIpMapping(mapping)) {
            LOG.warn("Not a valid ip addresses in the mapping {}", mapping);
            return;
        }

        //Get the DPN on which this interface resides
        BigInteger dpnId = getDpnForInterface(interfaceManager, interfaceName);

        if(dpnId.equals(BigInteger.ZERO)) {
             LOG.error("No DPN for interface {}. NAT flow entries for ip mapping {} will not be installed",
                     interfaceName, mapping);
             return;
        }

        long routerId = NatUtil.getVpnId(broker, routerName);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve router id for {} to create NAT Flow entries", routerName);
            return;
        }
        //Check if the router to vpn association is present
        //long associatedVpnId = NatUtil.getAssociatedVpn(broker, routerName);
        Uuid associatedVpn = NatUtil.getVpnForRouter(broker, routerName);
        long associatedVpnId = NatConstants.INVALID_ID;
        if(associatedVpn == null) {
            LOG.debug("Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("Router {} is associated with VPN Instance with Id {}", routerName, associatedVpn);
            associatedVpnId = NatUtil.getVpnId(broker, associatedVpn.getValue());
            LOG.debug("vpninstance Id is {} for VPN {}", associatedVpnId, associatedVpn);
            //routerId = associatedVpnId;
        }

        Uuid extNwId = getExtNetworkId(pIdentifier, LogicalDatastoreType.CONFIGURATION);
        if(extNwId == null) {
            LOG.error("External network associated with interface {} could not be retrieved", interfaceName);
            LOG.error("NAT flow entries will not be installed {}", mapping);
            return;
        }
        long vpnId = getVpnId(extNwId, routerId);
        if(vpnId < 0) {
            LOG.error("No VPN associated with Ext nw {}. Unable to create SNAT table entry for fixed ip {}",
                    extNwId, mapping.getInternalIp());
            return;
        }

        //Create the DNAT and SNAT table entries
        createDNATTblEntry(dpnId, mapping.getInternalIp(), mapping.getExternalIp(), routerId, vpnId, associatedVpnId);


        String macAddr = getExternalGatewayMacAddress(routerName);
        createSNATTblEntry(dpnId, mapping.getInternalIp(), mapping.getExternalIp(), vpnId, routerId, macAddr, associatedVpnId);

        handler.onAddFloatingIp(dpnId, routerName, extNwId, interfaceName, mapping.getExternalIp(), mapping
                .getInternalIp());
    }

    void createNATFlowEntries(BigInteger dpnId,  String interfaceName, String routerName, Uuid externalNetworkId, String internalIp, String externalIp) {
        long routerId = NatUtil.getVpnId(broker, routerName);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve router id for {} to create NAT Flow entries", routerName);
            return;
        }
        //Check if the router to vpn association is present
        long associatedVpnId = NatUtil.getAssociatedVpn(broker, routerName);
        if(associatedVpnId == NatConstants.INVALID_ID) {
            LOG.debug("Router {} is not assicated with any BGP VPN instance", routerName);
        } else {
            LOG.debug("Router {} is associated with VPN Instance with Id {}", routerName, associatedVpnId);
            //routerId = associatedVpnId;
        }

        long vpnId = getVpnId(externalNetworkId, routerId);
        if(vpnId < 0) {
            LOG.error("Unable to create SNAT table entry for fixed ip {}", internalIp);
            return;
        }
        //Create the DNAT and SNAT table entries
        createDNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId, associatedVpnId);

        String macAddr = getExternalGatewayMacAddress(routerName);
        createSNATTblEntry(dpnId, internalIp, externalIp, vpnId, routerId, macAddr, associatedVpnId);

        handler.onAddFloatingIp(dpnId, routerName, externalNetworkId, interfaceName, externalIp, internalIp);
    }

    void createNATOnlyFlowEntries(BigInteger dpnId,  String interfaceName, String routerName, String associatedVPN, Uuid externalNetworkId, String internalIp, String externalIp) {
        //String segmentId = associatedVPN == null ? routerName : associatedVPN;
        LOG.debug("Retrieving vpn id for VPN {} to proceed with create NAT Flows", routerName);
        long routerId = NatUtil.getVpnId(broker, routerName);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve vpn id for {} to create NAT Flow entries", routerName);
            return;
        }
        long associatedVpnId = NatUtil.getVpnId(broker, associatedVPN);
        LOG.debug("Associated VPN Id {} for router {}", associatedVpnId, routerName);
        long vpnId = getVpnId(externalNetworkId, routerId);
        if(vpnId < 0) {
            LOG.error("Unable to create SNAT table entry for fixed ip {}", internalIp);
            return;
        }
        //Create the DNAT and SNAT table entries
        //createDNATTblEntry(dpnId, internalIp, externalIp, routerId, vpnId);
        FlowEntity pFlowEntity = buildPreDNATFlowEntity(dpnId, internalIp, externalIp, routerId, vpnId, associatedVpnId );
        mdsalManager.installFlow(pFlowEntity);

        FlowEntity flowEntity = buildDNATFlowEntity(dpnId, internalIp, externalIp, routerId, associatedVpnId);
        mdsalManager.installFlow(flowEntity);

        String macAddr = getExternalGatewayMacAddress(routerName);
        //createSNATTblEntry(dpnId, internalIp, externalIp, vpnId, routerId, macAddr);
        pFlowEntity = buildPreSNATFlowEntity(dpnId, internalIp, externalIp, vpnId , routerId, associatedVpnId);
        mdsalManager.installFlow(pFlowEntity);

        flowEntity = buildSNATFlowEntity(dpnId, internalIp, externalIp, vpnId, macAddr);
        mdsalManager.installFlow(flowEntity);

    }

    private String getExternalGatewayMacAddress(String routerName) {
        InstanceIdentifier<Routers> routersIdentifier = NatUtil.buildRouterIdentifier(routerName);
        Optional<Routers> optRouters = NatUtil.read(broker, LogicalDatastoreType.CONFIGURATION, routersIdentifier);
        if(optRouters.isPresent()) {
            Routers routers = optRouters.get();
            return routers.getExtGwMacAddress();
        }
        return "";
    }

    void removeNATFlowEntries(String interfaceName, final IpMapping mapping,
                              final InstanceIdentifier<RouterPorts> pIdentifier, final String routerName) {

        //Get the DPN on which this interface resides
        BigInteger dpnId = getDpnForInterface(interfaceManager, interfaceName);
        if(dpnId.equals(BigInteger.ZERO)) {
            LOG.info("Abort processing Floating ip configuration. No DPN for port : {}", interfaceName);
            return;
        }

        long routerId = NatUtil.getVpnId(broker, routerName);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve router id for {} to remove NAT Flow entries", routerName);
            return;
        }
        //if(routerId == NatConstants.INVALID_ID) {
        //The router could be associated with BGP VPN
        Uuid associatedVPN = NatUtil.getVpnForRouter(broker, routerName);
        long associatedVpnId = NatConstants.INVALID_ID;
        if(associatedVPN == null) {
            LOG.warn("Could not retrieve router id for {} to remove NAT Flow entries", routerName);
        } else {
            LOG.debug("Retrieving vpn id for VPN {} to proceed with remove NAT Flows", associatedVPN.getValue());
            associatedVpnId = NatUtil.getVpnId(broker, associatedVPN.getValue());
        }

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, mapping.getInternalIp(), mapping.getExternalIp(), routerId);

        Uuid extNwId = getExtNetworkId(pIdentifier, LogicalDatastoreType.OPERATIONAL);
        if(extNwId == null) {
            LOG.error("External network associated with interface {} could not be retrieved", interfaceName);
            return;
        }
        long vpnId = getVpnId(extNwId);
        if(vpnId < 0) {
            LOG.error("No VPN associated with ext nw {}. Unable to delete SNAT table entry for fixed ip {}",
                    extNwId, mapping.getInternalIp());
            return;
        }
        removeSNATTblEntry(dpnId, mapping.getInternalIp(), routerId, mapping.getExternalIp(), vpnId);

        long label = getOperationalIpMapping(routerName, interfaceName, mapping.getInternalIp());
        if(label < 0) {
            LOG.error("Could not retrieve label for prefix {} in router {}", mapping.getInternalIp(), routerId);
            return;
        }
        //Uuid extNwId = getExtNetworkId(pIdentifier);
//        Uuid extNwId = getExternalNetworkForRouter(routerName);
//        if(extNwId == null) {
//            LOG.error("External network associated with router {} could not be retrieved", routerName);
//            return;
//        }
        handler.onRemoveFloatingIp(dpnId, routerName, extNwId, mapping.getExternalIp(), mapping.getInternalIp(), (int) label);
        removeOperationalDS(routerName, interfaceName, mapping.getInternalIp(), mapping.getExternalIp());

    }

    void removeNATFlowEntries(BigInteger dpnId, String interfaceName, String vpnName, String routerName, Uuid externalNetworkId, String internalIp, String externalIp) {
        long routerId = NatUtil.getVpnId(broker, routerName);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve router id for {} to remove NAT Flow entries", routerName);
            return;
        }

        long vpnId = NatUtil.getVpnId(broker, vpnName);
        if(vpnId == NatConstants.INVALID_ID) {
            LOG.warn("VPN Id not found for {} to remove NAT flow entries {}", vpnName, internalIp);
        }

        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId);

        removeSNATTblEntry(dpnId, internalIp, routerId, externalIp, vpnId);

        long label = getOperationalIpMapping(routerName, interfaceName, internalIp);
        if(label < 0) {
            LOG.error("Could not retrieve label for prefix {} in router {}", internalIp, routerId);
            return;
        }
        //handler.onRemoveFloatingIp(dpnId, routerName, externalNetworkId, externalIp, internalIp, (int)label);
        ((VpnFloatingIpHandler)handler).cleanupFibEntries(dpnId, vpnName, externalIp, label);
        removeOperationalDS(routerName, interfaceName, internalIp, externalIp);
    }

    void removeNATOnlyFlowEntries(BigInteger dpnId, String interfaceName, String routerName, String associatedVPN,
                                                                          String internalIp, String externalIp) {
        String segmentId = associatedVPN == null ? routerName : associatedVPN;
        LOG.debug("Retrieving vpn id for VPN {} to proceed with remove NAT Flows", segmentId);
        long routerId = NatUtil.getVpnId(broker, segmentId);
        if(routerId == NatConstants.INVALID_ID) {
            LOG.warn("Could not retrieve vpn id for {} to remove NAT Flow entries", segmentId);
            return;
        }
        //Delete the DNAT and SNAT table entries
        removeDNATTblEntry(dpnId, internalIp, externalIp, routerId);

        //removeSNATTblEntry(dpnId, internalIp, routerId, externalIp);
    }

    private long getOperationalIpMapping(String routerId, String interfaceName, String internalIp) {
        InstanceIdentifier<IpMapping> ipMappingIdentifier = NatUtil.getIpMappingIdentifier(routerId, interfaceName, internalIp);
        Optional<IpMapping> ipMapping = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, ipMappingIdentifier);
        if(ipMapping.isPresent()) {
            return ipMapping.get().getLabel();
        }
        return NatConstants.INVALID_ID;
    }

    private Uuid getExternalNetworkForRouter(String routerId) {
        InstanceIdentifier<RouterPorts> identifier = NatUtil.getRouterPortsId(routerId);
        Optional<RouterPorts> optRouterPorts = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, identifier);
        if(optRouterPorts.isPresent()) {
            RouterPorts routerPorts = optRouterPorts.get();
            return routerPorts.getExternalNetworkId();
        }
        return null;
    }

    void updateOperationalDS(String routerId, String interfaceName, long label, String internalIp, String externalIp) {

        LOG.info("Updating operational DS for floating ip config : {} with label {}", internalIp, label);
        InstanceIdentifier<Ports> portsId = NatUtil.getPortsIdentifier(routerId, interfaceName);
        Optional<Ports> optPorts = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portsId);
        IpMapping ipMapping = new IpMappingBuilder().setKey(new IpMappingKey(internalIp)).setInternalIp(internalIp)
                .setExternalIp(externalIp).setLabel(label).build();
        if(optPorts.isPresent()) {
            LOG.debug("Ports {} entry already present. Updating ipmapping for internal ip {}", interfaceName, internalIp);
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, portsId.child(IpMapping.class, new IpMappingKey(internalIp)), ipMapping);
        } else {
            LOG.debug("Adding Ports entry {} along with ipmapping {}", interfaceName, internalIp);
            List<IpMapping> ipMappings = new ArrayList<>();
            ipMappings.add(ipMapping);
            Ports ports = new PortsBuilder().setKey(new PortsKey(interfaceName)).setPortName(interfaceName).setIpMapping(ipMappings).build();
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, portsId, ports);
        }
    }

    void removeOperationalDS(String routerId, String interfaceName, String internalIp, String externalIp) {
        LOG.info("Remove operational DS for floating ip config: {}", internalIp);
        InstanceIdentifier<IpMapping> ipMappingId = NatUtil.getIpMappingIdentifier(routerId, interfaceName, internalIp);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, ipMappingId);
    }

    private FlowEntity buildPreDNATDeleteFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId) {

        LOG.info("Bulding Delete DNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PDNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PDNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }



    private FlowEntity buildDNATDeleteFlowEntity(BigInteger dpId, String internalIp, String externalIp, long routerId) {

        LOG.info("Bulding Delete DNAT Flow entity for ip {} ", externalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.DNAT_TABLE, routerId, externalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.DNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;

    }

    private FlowEntity buildPreSNATDeleteFlowEntity(BigInteger dpId, String internalIp, long routerId, String externalIp) {

        LOG.info("Building Delete PSNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.PSNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.PSNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;
    }

    private FlowEntity buildSNATDeleteFlowEntity(BigInteger dpId, String internalIp, long routerId, String externalIp) {

        LOG.info("Building Delete SNAT Flow entity for ip {} ", internalIp);

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.SNAT_TABLE, routerId, internalIp);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.SNAT_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, null, null);

        return flowEntity;


    }

}

