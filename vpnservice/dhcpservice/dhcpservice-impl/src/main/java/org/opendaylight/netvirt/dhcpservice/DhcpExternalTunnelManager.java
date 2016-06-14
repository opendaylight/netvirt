/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DHCPMConstants;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.genius.utils.clustering.ClusteringUtils;
//TODO: FIXME: Missing elements in HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

public class DhcpExternalTunnelManager {

    private static final Logger logger = LoggerFactory.getLogger(DhcpExternalTunnelManager.class);
    public static final String UNKNOWN_DMAC = "00:00:00:00:00:00";
    private static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    logger.debug("Success in Datastore write operation");
                }
                @Override
                public void onFailure(Throwable error) {
                    logger.error("Error in Datastore write operation", error);
                }
            };
    private final DataBroker broker;
    private IMdsalApiManager mdsalUtil;

    private ConcurrentMap<BigInteger, List<Pair<IpAddress, String>>> designatedDpnsToTunnelIpElanNameCache = new ConcurrentHashMap<BigInteger, List<Pair<IpAddress, String>>>();
    private ConcurrentMap<Pair<IpAddress, String>, Set<String>> tunnelIpElanNameToVmMacCache = new ConcurrentHashMap<Pair<IpAddress, String>, Set<String>>();
    private ConcurrentMap<Pair<BigInteger, String>, Port> vniMacAddressToPortCache = new ConcurrentHashMap<Pair<BigInteger, String>, Port>();
    private ItmRpcService itmRpcService;
    private EntityOwnershipService entityOwnershipService;

    public DhcpExternalTunnelManager(DataBroker broker, IMdsalApiManager mdsalUtil, ItmRpcService itmRpcService, EntityOwnershipService entityOwnershipService) {
        this.broker = broker;
        this.mdsalUtil = mdsalUtil;
        this.itmRpcService = itmRpcService;
        this.entityOwnershipService = entityOwnershipService;
        initilizeCaches();
    }

    private void initilizeCaches() {
        logger.trace("Loading designatedDpnsToTunnelIpElanNameCache");
        InstanceIdentifier<DesignatedSwitchesForExternalTunnels> instanceIdentifier = InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).build();
        Optional<DesignatedSwitchesForExternalTunnels> designatedSwitchForTunnelOptional = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        if (designatedSwitchForTunnelOptional.isPresent()) {
            List<DesignatedSwitchForTunnel> list = designatedSwitchForTunnelOptional.get().getDesignatedSwitchForTunnel();
            for (DesignatedSwitchForTunnel designatedSwitchForTunnel : list) {
                List<Pair<IpAddress, String>> listOfTunnelIpElanNamePair = designatedDpnsToTunnelIpElanNameCache.get(designatedSwitchForTunnel.getDpId());
                if (listOfTunnelIpElanNamePair == null) {
                    listOfTunnelIpElanNamePair = new LinkedList<Pair<IpAddress, String>>();
                }
                Pair<IpAddress, String> tunnelIpElanNamePair = new ImmutablePair<IpAddress, String>(designatedSwitchForTunnel.getTunnelRemoteIpAddress(), designatedSwitchForTunnel.getElanInstanceName());
                listOfTunnelIpElanNamePair.add(tunnelIpElanNamePair);
                designatedDpnsToTunnelIpElanNameCache.put(BigInteger.valueOf(designatedSwitchForTunnel.getDpId()), listOfTunnelIpElanNamePair);
            }
        }
        logger.trace("Loading vniMacAddressToPortCache");
        InstanceIdentifier<Ports> inst = InstanceIdentifier.builder(Neutron.class).child(Ports.class).build();
        Optional<Ports> optionalPorts = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (optionalPorts.isPresent()) {
            List<Port> list = optionalPorts.get().getPort();
            for (Port port : list) {
                if(NeutronUtils.isPortVnicTypeNormal(port)) {
                    continue;
                }
                String macAddress = port.getMacAddress();
                Uuid networkId = port.getNetworkId();
                String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
                if (segmentationId == null) {
                    return;
                }
                updateVniMacToPortCache(new BigInteger(segmentationId), macAddress, port);
            }
        }

    }


    public BigInteger designateDpnId(IpAddress tunnelIp,
            String elanInstanceName, List<BigInteger> dpns) {
        BigInteger designatedDpnId = readDesignatedSwitchesForExternalTunnel(tunnelIp, elanInstanceName);
        if (designatedDpnId != null && !designatedDpnId.equals(DHCPMConstants.INVALID_DPID)) {
            logger.trace("Dpn {} already designated for tunnelIp - elan : {} - {}", designatedDpnId, tunnelIp, elanInstanceName);
            return designatedDpnId;
        }
        return chooseDpn(tunnelIp, elanInstanceName, dpns);
    }

    public void installDhcpFlowsForVms(IpAddress tunnelIp, String elanInstanceName, List<BigInteger> dpns,
            BigInteger designatedDpnId, String vmMacAddress) {
        installDhcpEntries(designatedDpnId, vmMacAddress, entityOwnershipService);
        dpns.remove(designatedDpnId);
        for (BigInteger dpn : dpns) {
            installDhcpDropAction(dpn, vmMacAddress, entityOwnershipService);
        }
        updateLocalCache(tunnelIp, elanInstanceName, vmMacAddress);
    }

    public void installDhcpFlowsForVms(IpAddress tunnelIp,
            String elanInstanceName, BigInteger designatedDpnId, Set<String> listVmMacAddress) {
        for (String vmMacAddress : listVmMacAddress) {
            installDhcpEntries(designatedDpnId, vmMacAddress);
        }
    }

    public void unInstallDhcpFlowsForVms(String elanInstanceName, List<BigInteger> dpns, String vmMacAddress) {
        for (BigInteger dpn : dpns) {
            unInstallDhcpEntries(dpn, vmMacAddress, entityOwnershipService);
        }
        removeFromLocalCache(elanInstanceName, vmMacAddress);
    }

    public BigInteger readDesignatedSwitchesForExternalTunnel(IpAddress tunnelIp, String elanInstanceName) {
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier = InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).child(DesignatedSwitchForTunnel.class, new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp)).build();
        Optional<DesignatedSwitchForTunnel> designatedSwitchForTunnelOptional = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        if (designatedSwitchForTunnelOptional.isPresent()) {
            return BigInteger.valueOf(designatedSwitchForTunnelOptional.get().getDpId());
        }
        return null;
    }

    public void writeDesignatedSwitchForExternalTunnel(BigInteger dpnId, IpAddress tunnelIp, String elanInstanceName) {
        DesignatedSwitchForTunnelKey designatedSwitchForTunnelKey = new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp);
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier = InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).child(DesignatedSwitchForTunnel.class, designatedSwitchForTunnelKey).build();
        DesignatedSwitchForTunnel designatedSwitchForTunnel = new DesignatedSwitchForTunnelBuilder().setDpId(dpnId.longValue()).setElanInstanceName(elanInstanceName).setTunnelRemoteIpAddress(tunnelIp).setKey(designatedSwitchForTunnelKey).build();
        logger.trace("Writing into CONFIG DS tunnelIp {}, elanInstanceName {}, dpnId {}", tunnelIp, elanInstanceName, dpnId);
        MDSALDataStoreUtils.asyncUpdate(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier, designatedSwitchForTunnel, DEFAULT_CALLBACK);
    }

    public void removeDesignatedSwitchForExternalTunnel(BigInteger dpnId, IpAddress tunnelIp, String elanInstanceName) {
        DesignatedSwitchForTunnelKey designatedSwitchForTunnelKey = new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp);
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier = InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).child(DesignatedSwitchForTunnel.class, designatedSwitchForTunnelKey).build();
        logger.trace("Writing into CONFIG DS tunnelIp {}, elanInstanceName {}, dpnId {}", tunnelIp, elanInstanceName, dpnId);
        MDSALDataStoreUtils.asyncRemove(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier, DEFAULT_CALLBACK);
    }

    public void installDhcpDropActionOnDpn(BigInteger dpId) {
        List<String> vmMacs = getAllVmMacs();
        logger.trace("Installing drop actions to this new DPN {} VMs {}", dpId, vmMacs);
        for (String vmMacAddress : vmMacs) {
            installDhcpDropAction(dpId, vmMacAddress);
        }
    }

    private List<String> getAllVmMacs() {
        List<String> vmMacs = new LinkedList<String>();
        Collection<Set<String>> listOfVmMacs = tunnelIpElanNameToVmMacCache.values();
        for (Set<String> list : listOfVmMacs) {
            vmMacs.addAll(list);
        }
        return vmMacs;
    }

    public void updateLocalCache(BigInteger designatedDpnId, IpAddress tunnelIp, String elanInstanceName) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<IpAddress, String>(tunnelIp, elanInstanceName);
        List<Pair<IpAddress, String>> tunnelIpElanNameList;
        tunnelIpElanNameList = designatedDpnsToTunnelIpElanNameCache.get(designatedDpnId);
        if (tunnelIpElanNameList == null) {
            tunnelIpElanNameList = new LinkedList<Pair<IpAddress, String>>();
        }
        tunnelIpElanNameList.add(tunnelIpElanName);
        designatedDpnsToTunnelIpElanNameCache.put(designatedDpnId, tunnelIpElanNameList);
    }

    private void updateLocalCache(IpAddress tunnelIp, String elanInstanceName, String vmMacAddress) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<IpAddress, String>(tunnelIp, elanInstanceName);
        Set<String> listExistingVmMacAddress;
        listExistingVmMacAddress = tunnelIpElanNameToVmMacCache.get(tunnelIpElanName);
        if (listExistingVmMacAddress == null) {
            listExistingVmMacAddress = new HashSet<String>();
        }
        listExistingVmMacAddress.add(vmMacAddress);
        tunnelIpElanNameToVmMacCache.put(tunnelIpElanName, listExistingVmMacAddress);
    }

    public void handleDesignatedDpnDown(BigInteger dpnId, List<BigInteger> listOfDpns) {
        logger.trace("In handleDesignatedDpnDown dpnId {}, listOfDpns {}", dpnId, listOfDpns);
        try {
            List<Pair<IpAddress, String>> listOfTunnelIpElanNamePairs = designatedDpnsToTunnelIpElanNameCache.get(dpnId);
            if (!dpnId.equals(DHCPMConstants.INVALID_DPID)) {
                List<String> listOfVms = getAllVmMacs();
                for (String vmMacAddress : listOfVms) {
                    unInstallDhcpEntries(dpnId, vmMacAddress);
                }
            }
            if (listOfTunnelIpElanNamePairs == null || listOfTunnelIpElanNamePairs.isEmpty()) {
                logger.trace("No tunnelIpElanName to handle for dpn {}. Returning", dpnId);
                return;
            }
            for (Pair<IpAddress, String> pair : listOfTunnelIpElanNamePairs) {
                updateCacheAndInstallNewFlows(dpnId, listOfDpns, pair);
            }
        } catch (Exception e) {
            logger.error("Error in handleDesignatedDpnDown {}", e);
        }
    }

    public void updateCacheAndInstallNewFlows(BigInteger dpnId,
            List<BigInteger> listOfDpns, Pair<IpAddress, String> pair)
            throws ExecutionException {
        BigInteger newDesignatedDpn = chooseDpn(pair.getLeft(), pair.getRight(), listOfDpns);
        if (newDesignatedDpn.equals(DHCPMConstants.INVALID_DPID)) {
            return;
        }
        Set<String> listVmMacs = tunnelIpElanNameToVmMacCache.get(pair);
        if (listVmMacs != null && !listVmMacs.isEmpty()) {
            installDhcpFlowsForVms(pair.getLeft(), pair.getRight(), newDesignatedDpn, listVmMacs);
        }
    }

    private void changeExistingFlowToDrop(Pair<IpAddress, String> tunnelIpElanNamePair, BigInteger dpnId) {
        try {
            Set<String> listVmMacAddress = tunnelIpElanNameToVmMacCache.get(tunnelIpElanNamePair);
            if (listVmMacAddress == null || listVmMacAddress.isEmpty()) {
                return;
            }
            for (String vmMacAddress : listVmMacAddress) {
                installDhcpDropAction(dpnId, vmMacAddress);
            }
        } catch (Exception e) {
            logger.error("Error in uninstallExistingFlows {}", e);
        }
    }

    /**
     * Choose a dpn among the list of elanDpns such that it has lowest count of being the designated dpn.
     * @param tunnelIp
     * @param elanInstanceName
     * @param dpns
     * @return
     */
    private BigInteger chooseDpn(IpAddress tunnelIp, String elanInstanceName,
            List<BigInteger> dpns) {
        BigInteger designatedDpnId = DHCPMConstants.INVALID_DPID;
        if (dpns != null && dpns.size() != 0) {
            List<BigInteger> candidateDpns = DhcpServiceUtils.getDpnsForElan(elanInstanceName, broker);
            candidateDpns.retainAll(dpns);
            logger.trace("Choosing new dpn for tunnelIp {}, elanInstanceName {}, among elanDpns {}", tunnelIp, elanInstanceName, candidateDpns);
            boolean elanDpnAvailableFlag = true;
            if (candidateDpns == null || candidateDpns.isEmpty()) {
                candidateDpns = dpns;
                elanDpnAvailableFlag = false;
            }
            int size = 0;
            L2GatewayDevice device = getDeviceFromTunnelIp(elanInstanceName, tunnelIp);
            if (device == null) {
                logger.trace("Could not find any device for elanInstanceName {} and tunnelIp {}", elanInstanceName, tunnelIp);
                handleUnableToDesignateDpn(tunnelIp, elanInstanceName);
                return designatedDpnId;
            }
            for (BigInteger dpn : candidateDpns) {
                String hwvtepNodeId = device.getHwvtepNodeId();
                if (!elanDpnAvailableFlag) {
                    if (!isTunnelConfigured(dpn, hwvtepNodeId)) {
                        logger.trace("Tunnel is not configured on dpn {} to TOR {}", dpn, hwvtepNodeId);
                        continue;
                    }
                } else if (!isTunnelUp(hwvtepNodeId, dpn)) {
                    logger.trace("Tunnel is not up between dpn {} and TOR {}", dpn, hwvtepNodeId);
                    continue;
                }
                List<Pair<IpAddress, String>> tunnelIpElanNameList = designatedDpnsToTunnelIpElanNameCache.get(dpn);
                if (tunnelIpElanNameList == null) {
                    designatedDpnId = dpn;
                    break;
                }
                if (size == 0 || tunnelIpElanNameList.size() < size) {
                    size = tunnelIpElanNameList.size();
                    designatedDpnId = dpn;
                }
            }
            writeDesignatedSwitchForExternalTunnel(designatedDpnId, tunnelIp, elanInstanceName);
            return designatedDpnId;
        }
        handleUnableToDesignateDpn(tunnelIp, elanInstanceName);
        return designatedDpnId;
    }

    private void handleUnableToDesignateDpn(IpAddress tunnelIp, String elanInstanceName) {
        writeDesignatedSwitchForExternalTunnel(DHCPMConstants.INVALID_DPID, tunnelIp, elanInstanceName);
    }

    public void installDhcpEntries(BigInteger dpnId, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL, vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil);
    }

    public void installDhcpEntries(final BigInteger dpnId, final String vmMacAddress, EntityOwnershipService eos) {
        final String nodeId = DhcpServiceUtils.getNodeIdFromDpnId(dpnId);
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                eos, /*HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME*/"elan", "elan");
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    installDhcpEntries(dpnId, vmMacAddress);
                } else {
                    logger.trace("Exiting installDhcpEntries since this cluster node is not the owner for dpn {}", nodeId);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                logger.error("Error while fetching checkNodeEntityOwner", error);
            }
        });
    }

    public void unInstallDhcpEntries(BigInteger dpnId, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL, vmMacAddress, NwConstants.DEL_FLOW, mdsalUtil);
    }

    public void unInstallDhcpEntries(final BigInteger dpnId, final String vmMacAddress, EntityOwnershipService eos) {
        final String nodeId = DhcpServiceUtils.getNodeIdFromDpnId(dpnId);
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                eos, /*HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME*/ "elan", "elan");
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    unInstallDhcpEntries(dpnId, vmMacAddress);
                } else {
                    logger.trace("Exiting unInstallDhcpEntries since this cluster node is not the owner for dpn {}", nodeId);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                logger.error("Error while fetching checkNodeEntityOwner", error);
            }
        });
    }

    public void installDhcpDropAction(BigInteger dpn, String vmMacAddress) {
        DhcpServiceUtils.setupDhcpDropAction(dpn, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL, vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil);
    }

    public void installDhcpDropAction(final BigInteger dpnId, final String vmMacAddress, EntityOwnershipService eos) {
        final String nodeId = DhcpServiceUtils.getNodeIdFromDpnId(dpnId);
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(
                eos, /*HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                HwvtepSouthboundConstants.ELAN_ENTITY_NAME*/ "elan", "elan");
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    installDhcpDropAction(dpnId, vmMacAddress);
                } else {
                    logger.trace("Exiting installDhcpDropAction since this cluster node is not the owner for dpn {}", nodeId);
                }
            }

            @Override
            public void onFailure(Throwable error) {
                logger.error("Error while fetching checkNodeEntityOwner", error);
            }
        });
    }

    public void handleTunnelStateDown(IpAddress tunnelIp, BigInteger interfaceDpn) {
        logger.trace("In handleTunnelStateDown tunnelIp {}, interfaceDpn {}", tunnelIp, interfaceDpn);
        if (interfaceDpn == null) {
            return;
        }
        try {
            List<Pair<IpAddress, String>> tunnelElanPairList = designatedDpnsToTunnelIpElanNameCache.get(interfaceDpn);
            if (tunnelElanPairList == null || tunnelElanPairList.isEmpty()) {
                return;
            }
            for (Pair<IpAddress, String> tunnelElanPair : tunnelElanPairList) {
                IpAddress tunnelIpInDpn = tunnelElanPair.getLeft();
                if (tunnelIpInDpn.equals(tunnelIp)) {
                    List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(broker);
                    dpns.remove(interfaceDpn);
                    changeExistingFlowToDrop(tunnelElanPair, interfaceDpn);
                    updateCacheAndInstallNewFlows(interfaceDpn, dpns, tunnelElanPair);
                }
            }
        } catch (Exception e) {
            logger.error("Error in handleTunnelStateDown {}", e.getMessage());
            logger.trace("Exception details {}", e);
        }
    }

    private void removeFromLocalCache(String elanInstanceName, String vmMacAddress) {
        Set<Pair<IpAddress, String>> tunnelIpElanNameKeySet = tunnelIpElanNameToVmMacCache.keySet();
        for (Pair<IpAddress, String> pair : tunnelIpElanNameKeySet) {
            if (pair.getRight().trim().equalsIgnoreCase(elanInstanceName.trim())) {
                Set<String> listExistingVmMacAddress;
                listExistingVmMacAddress = tunnelIpElanNameToVmMacCache.get(pair);
                if (listExistingVmMacAddress == null || listExistingVmMacAddress.isEmpty()) {
                    continue;
                }
                listExistingVmMacAddress.remove(vmMacAddress);
                if (listExistingVmMacAddress.size() > 0) {
                    tunnelIpElanNameToVmMacCache.put(pair, listExistingVmMacAddress);
                    return;
                }
                tunnelIpElanNameToVmMacCache.remove(pair);
            }
        }
    }

    public void removeFromLocalCache(BigInteger designatedDpnId, IpAddress tunnelIp, String elanInstanceName) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<IpAddress, String>(tunnelIp, elanInstanceName);
        List<Pair<IpAddress, String>> tunnelIpElanNameList;
        tunnelIpElanNameList = designatedDpnsToTunnelIpElanNameCache.get(designatedDpnId);
        if (tunnelIpElanNameList != null) {
            tunnelIpElanNameList.remove(tunnelIpElanName);
            if (tunnelIpElanNameList.size() != 0) {
                designatedDpnsToTunnelIpElanNameCache.put(designatedDpnId, tunnelIpElanNameList);
            } else {
                designatedDpnsToTunnelIpElanNameCache.remove(designatedDpnId);
            }
        }
    }

    public void updateVniMacToPortCache(BigInteger vni, String macAddress, Port port) {
        if (macAddress == null) {
            return;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<BigInteger, String>(vni, macAddress.toUpperCase());
        logger.trace("Updating vniMacAddressToPortCache with vni {} , mac {} , pair {} and port {}", vni, macAddress.toUpperCase(), vniMacAddressPair, port);
        vniMacAddressToPortCache.put(vniMacAddressPair, port);
    }

    public void removeVniMacToPortCache(BigInteger vni, String macAddress) {
        if (macAddress == null) {
            return;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<BigInteger, String>(vni, macAddress.toUpperCase());
        vniMacAddressToPortCache.remove(vniMacAddressPair);
    }

    public Port readVniMacToPortCache(BigInteger vni, String macAddress) {
        if (macAddress == null) {
            return null;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<BigInteger, String>(vni, macAddress.toUpperCase());
        logger.trace("Reading vniMacAddressToPortCache with vni {} , mac {} , pair {} and port {}", vni, macAddress.toUpperCase(), vniMacAddressPair, vniMacAddressToPortCache.get(vniMacAddressPair));
        return vniMacAddressToPortCache.get(vniMacAddressPair);
    }

    public String getExternalTunnelInterfaceName(String sourceNode, String dstNode) {
        String tunnelInterfaceName = null;
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        try {
            Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = itmRpcService
                    .getExternalTunnelInterfaceName(new GetExternalTunnelInterfaceNameInputBuilder()
                            .setSourceNode(sourceNode).setDestinationNode(dstNode).setTunnelType(tunType).build());

            RpcResult<GetExternalTunnelInterfaceNameOutput> rpcResult = output.get();
            if (rpcResult.isSuccessful()) {
                tunnelInterfaceName = rpcResult.getResult().getInterfaceName();
                logger.debug("Tunnel interface name: {}", tunnelInterfaceName);
            } else {
                logger.warn("RPC call to ITM.GetExternalTunnelInterfaceName failed with error: {}",
                        rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            logger.error("Failed to get external tunnel interface name for sourceNode: {} and dstNode: {}: {} ",
                    sourceNode, dstNode, e);
        }
        return tunnelInterfaceName;
    }

    public static Optional<Node> getNode(DataBroker dataBroker, String physicalSwitchNodeId) {
        InstanceIdentifier<Node> psNodeId = HwvtepSouthboundUtils
                .createInstanceIdentifier(new NodeId(physicalSwitchNodeId));
        Optional<Node> physicalSwitchOptional = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, psNodeId, dataBroker);
        return physicalSwitchOptional;
    }

    public RemoteMcastMacs createRemoteMcastMac(Node dstDevice, String logicalSwitchName, IpAddress internalTunnelIp) {
        List<LocatorSet> locators = new ArrayList<>();
        for (TerminationPoint tp : dstDevice.getTerminationPoint()) {
            HwvtepPhysicalLocatorAugmentation aug = tp.getAugmentation(HwvtepPhysicalLocatorAugmentation.class);
            if (internalTunnelIp.getIpv4Address().equals(aug.getDstIp().getIpv4Address())) {
                HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                        HwvtepSouthboundUtils.createPhysicalLocatorInstanceIdentifier(dstDevice.getNodeId(), aug));
                locators.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());
            }
        }
        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(dstDevice.getNodeId(), new HwvtepNodeName(logicalSwitchName)));

        RemoteMcastMacs remoteUcastMacs = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(UNKNOWN_DMAC))
                .setLogicalSwitchRef(lsRef).setLocatorSet(locators).build();
        return remoteUcastMacs;
    }

    private WriteTransaction putRemoteMcastMac(WriteTransaction transaction, String elanName, L2GatewayDevice device, IpAddress internalTunnelIp) {
        Optional<Node> optionalNode = getNode(broker, device.getHwvtepNodeId());
        Node dstNode = optionalNode.get();
        if (dstNode == null) {
            logger.debug("could not get device node {} ", device.getHwvtepNodeId());
            return null;
        }
        RemoteMcastMacs macs = createRemoteMcastMac(dstNode, elanName, internalTunnelIp);
        HwvtepUtils.putRemoteMcastMac(transaction, dstNode.getNodeId(), macs);
        return transaction;
    }

    public void installRemoteMcastMac(final BigInteger designatedDpnId, final IpAddress tunnelIp, final String elanInstanceName) {
        if (designatedDpnId.equals(DHCPMConstants.INVALID_DPID)) {
            return;
        }
        ListenableFuture<Boolean> checkEntityOwnerFuture = ClusteringUtils.checkNodeEntityOwner(entityOwnershipService,
                        /*HwvtepSouthboundConstants.ELAN_ENTITY_TYPE, HwvtepSouthboundConstants.ELAN_ENTITY_NAME*/ "elan", "elan");
        Futures.addCallback(checkEntityOwnerFuture, new FutureCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean isOwner) {
                if (isOwner) {
                    logger.info("Installing remote McastMac");
                    L2GatewayDevice device = getDeviceFromTunnelIp(elanInstanceName, tunnelIp);
                    String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(designatedDpnId), device.getHwvtepNodeId());
                    IpAddress internalTunnelIp = null;
                    if (tunnelInterfaceName != null) {
                        Interface tunnelInterface = DhcpServiceUtils.getInterfaceFromConfigDS(tunnelInterfaceName, broker);
                        if (tunnelInterface == null) {
                            logger.trace("Tunnel Interface is not present {}", tunnelInterfaceName);
                            return;
                        }
                        internalTunnelIp = tunnelInterface.getAugmentation(IfTunnel.class).getTunnelSource();
                        WriteTransaction transaction = broker.newWriteOnlyTransaction();
                        putRemoteMcastMac(transaction, elanInstanceName, device, internalTunnelIp);
                        if (transaction != null) {
                            transaction.submit();
                        }
                    }
                } else {
                      logger.info("Installing remote McastMac is not executed for this node.");
                }
            }

            @Override
            public void onFailure(Throwable error) {
                logger.error("Failed to install remote McastMac", error);
            }
        });
    }

    private L2GatewayDevice getDeviceFromTunnelIp(String elanInstanceName, IpAddress tunnelIp) {
        ConcurrentMap<String, L2GatewayDevice> devices = L2GatewayCacheUtils.getCache();
        for (L2GatewayDevice device : devices.values()) {
            if (device.getTunnelIp().equals(tunnelIp)) {
                return device;
            }
        }
        return null;
    }

    private boolean isTunnelUp(String nodeName, BigInteger dpn) {
        boolean isTunnelUp = false;
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(dpn), nodeName);
        if (tunnelInterfaceName == null) {
            logger.debug("Tunnel Interface is not present {}", tunnelInterfaceName);
            return isTunnelUp;
        }
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface tunnelInterface = DhcpServiceUtils.getInterfaceFromOperationalDS(tunnelInterfaceName, broker);
        if (tunnelInterface == null) {
            logger.debug("Interface {} is not present in interface state", tunnelInterfaceName);
            return isTunnelUp;
        }
        isTunnelUp = (tunnelInterface.getOperStatus() == OperStatus.Up) ? true :false;
        return isTunnelUp;
    }

    public void handleTunnelStateUp(IpAddress tunnelIp, BigInteger interfaceDpn) {
        logger.trace("In handleTunnelStateUp tunnelIp {}, interfaceDpn {}", tunnelIp, interfaceDpn);
        try {
            List<Pair<IpAddress, String>> tunnelIpElanPair = designatedDpnsToTunnelIpElanNameCache.get(DHCPMConstants.INVALID_DPID);
            List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(broker);
            if (tunnelIpElanPair == null || tunnelIpElanPair.isEmpty()) {
                return;
            }
            for (Pair<IpAddress, String> pair : tunnelIpElanPair) {
                if (tunnelIp.equals(pair.getLeft())) {
                    designateDpnId(tunnelIp, pair.getRight(), dpns);
                }
            }
        } catch (Exception e) {
            logger.error("Error in handleTunnelStateUp {}", e.getMessage());
            logger.trace("Exception details {}", e);
        }
    }

    private boolean isTunnelConfigured(BigInteger dpn, String hwVtepNodeId) {
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(dpn), hwVtepNodeId);
        if (tunnelInterfaceName == null) {
            return false;
        }
        Interface tunnelInterface = DhcpServiceUtils.getInterfaceFromConfigDS(tunnelInterfaceName, broker);
        if (tunnelInterface == null) {
            logger.debug("Tunnel Interface is not present {}", tunnelInterfaceName);
            return false;
        }
        return true;
    }

    public void unInstallDhcpFlowsForVms(String elanInstanceName, IpAddress tunnelIp, List<BigInteger> dpns) {
        Pair<IpAddress, String> tunnelIpElanNamePair = new ImmutablePair<IpAddress, String>(tunnelIp, elanInstanceName);
        Set<String> vmMacs = tunnelIpElanNameToVmMacCache.get(tunnelIpElanNamePair);
        logger.trace("In unInstallFlowsForVms elanInstanceName {}, tunnelIp {}, dpns {}, vmMacs {}", elanInstanceName, tunnelIp, dpns, vmMacs);
        if (vmMacs == null) {
            return;
        }
        for (String vmMacAddress : vmMacs) {
            for (BigInteger dpn : dpns) {
                unInstallDhcpEntries(dpn, vmMacAddress, entityOwnershipService);
            }
        }
        tunnelIpElanNameToVmMacCache.remove(tunnelIpElanNamePair);
    }
}
