/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundConstants;
import org.opendaylight.genius.utils.hwvtep.HwvtepSouthboundUtils;
import org.opendaylight.genius.utils.hwvtep.HwvtepUtils;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.netvirt.dhcpservice.api.IDhcpExternalTunnelManager;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.elanmanager.utils.ElanL2GwCacheUtils;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.DesignatedSwitchesForExternalTunnels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.dhcp.rev160428.designated.switches._for.external.tunnels.DesignatedSwitchForTunnelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.dhcpservice.api.rev150710.subnet.dhcp.port.data.SubnetToDhcpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepLogicalSwitchRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepNodeName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepPhysicalLocatorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.RemoteMcastMacsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical.locator.set.attributes.LocatorSetBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpExternalTunnelManager implements IDhcpExternalTunnelManager {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpExternalTunnelManager.class);
    public static final String UNKNOWN_DMAC = "00:00:00:00:00:00";

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final IMdsalApiManager mdsalUtil;
    private final ItmRpcService itmRpcService;
    private final EntityOwnershipUtils entityOwnershipUtils;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator jobCoordinator;
    private final L2GatewayCache l2GatewayCache;
    private IElanService elanService;
    private final DhcpServiceCounters dhcpServiceCounters;

    private final ConcurrentMap<BigInteger, Set<Pair<IpAddress, String>>> designatedDpnsToTunnelIpElanNameCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<IpAddress, String>, Set<String>> tunnelIpElanNameToVmMacCache =
            new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<IpAddress, String>, Set<String>> availableVMCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<Pair<BigInteger, String>, Port> vniMacAddressToPortCache = new ConcurrentHashMap<>();

    @Override
    public ConcurrentMap<BigInteger, Set<Pair<IpAddress, String>>> getDesignatedDpnsToTunnelIpElanNameCache() {
        return designatedDpnsToTunnelIpElanNameCache;
    }

    @Override
    public ConcurrentMap<Pair<IpAddress, String>, Set<String>> getTunnelIpElanNameToVmMacCache() {
        return tunnelIpElanNameToVmMacCache;
    }

    @Override
    public ConcurrentMap<Pair<IpAddress, String>, Set<String>> getAvailableVMCache() {
        return availableVMCache;
    }

    @Override
    public ConcurrentMap<Pair<BigInteger, String>, Port> getVniMacAddressToPortCache() {
        return vniMacAddressToPortCache;
    }

    @Inject
    public DhcpExternalTunnelManager(final DataBroker broker,
            final IMdsalApiManager mdsalUtil, final ItmRpcService itmRpcService,
            final EntityOwnershipService entityOwnershipService, final IInterfaceManager interfaceManager,
            final JobCoordinator jobCoordinator, final L2GatewayCache l2GatewayCache,
            @Named("elanService") IElanService ielanService, DhcpServiceCounters dhcpServiceCounters) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.mdsalUtil = mdsalUtil;
        this.itmRpcService = itmRpcService;
        this.entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.l2GatewayCache = l2GatewayCache;
        this.elanService = ielanService;
        this.dhcpServiceCounters = dhcpServiceCounters;
    }

    @PostConstruct
    public void init() {
        initilizeCaches();
    }

    private void initilizeCaches() {
        LOG.trace("Loading designatedDpnsToTunnelIpElanNameCache");
        InstanceIdentifier<DesignatedSwitchesForExternalTunnels> instanceIdentifier =
                InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).build();
        Optional<DesignatedSwitchesForExternalTunnels> designatedSwitchForTunnelOptional =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        if (designatedSwitchForTunnelOptional.isPresent()) {
            List<DesignatedSwitchForTunnel> list =
                    designatedSwitchForTunnelOptional.get().getDesignatedSwitchForTunnel();
            for (DesignatedSwitchForTunnel designatedSwitchForTunnel : list) {
                Set<Pair<IpAddress, String>> setOfTunnelIpElanNamePair =
                        designatedDpnsToTunnelIpElanNameCache
                                .get(BigInteger.valueOf(designatedSwitchForTunnel.getDpId()));
                if (setOfTunnelIpElanNamePair == null) {
                    setOfTunnelIpElanNamePair = new CopyOnWriteArraySet<>();
                }
                Pair<IpAddress, String> tunnelIpElanNamePair =
                        new ImmutablePair<>(designatedSwitchForTunnel.getTunnelRemoteIpAddress(),
                                designatedSwitchForTunnel.getElanInstanceName());
                setOfTunnelIpElanNamePair.add(tunnelIpElanNamePair);
                designatedDpnsToTunnelIpElanNameCache.put(BigInteger.valueOf(designatedSwitchForTunnel.getDpId()),
                        setOfTunnelIpElanNamePair);
            }
        }
        LOG.trace("Loading vniMacAddressToPortCache");
        InstanceIdentifier<Ports> inst = InstanceIdentifier.builder(Neutron.class).child(Ports.class).build();
        Optional<Ports> optionalPorts = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (optionalPorts.isPresent()) {
            List<Port> list = optionalPorts.get().getPort();
            for (Port port : list) {
                if (NeutronUtils.isPortVnicTypeNormal(port)) {
                    continue;
                }
                String macAddress = port.getMacAddress().getValue();
                Uuid networkId = port.getNetworkId();
                String segmentationId = DhcpServiceUtils.getSegmentationId(networkId, broker);
                if (segmentationId == null) {
                    return;
                }
                updateVniMacToPortCache(new BigInteger(segmentationId), macAddress, port);
            }
        }
    }

    public BigInteger designateDpnId(IpAddress tunnelIp, String elanInstanceName, List<BigInteger> dpns) {
        BigInteger designatedDpnId = readDesignatedSwitchesForExternalTunnel(tunnelIp, elanInstanceName);
        if (designatedDpnId != null && !designatedDpnId.equals(DhcpMConstants.INVALID_DPID)) {
            LOG.trace("Dpn {} already designated for tunnelIp - elan : {} - {}", designatedDpnId, tunnelIp,
                    elanInstanceName);
            return designatedDpnId;
        }
        return chooseDpn(tunnelIp, elanInstanceName, dpns);
    }

    public void installDhcpFlowsForVms(final IpAddress tunnelIp, String elanInstanceName, final List<BigInteger> dpns,
            final BigInteger designatedDpnId, final String vmMacAddress) {
        LOG.trace("In installDhcpFlowsForVms ipAddress {}, elanInstanceName {}, dpn {}, vmMacAddress {}", tunnelIp,
                elanInstanceName, designatedDpnId, vmMacAddress);

        String tunnelIpDpnKey = getTunnelIpDpnKey(tunnelIp, designatedDpnId);
        jobCoordinator.enqueueJob(getJobKey(tunnelIpDpnKey), () -> {
            if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    dpns.remove(designatedDpnId);
                    for (BigInteger dpn : dpns) {
                        installDhcpDropAction(dpn, vmMacAddress, tx);
                    }
                    installDhcpEntries(designatedDpnId, vmMacAddress, tx);
                }));
            } else {
                LOG.trace("Exiting installDhcpEntries since this cluster node is not the owner for dpn");
            }

            return Collections.emptyList();
        });

        updateLocalCache(tunnelIp, elanInstanceName, vmMacAddress);
    }

    public void installDhcpFlowsForVms(BigInteger designatedDpnId, Set<String> listVmMacAddress, WriteTransaction tx) {
        for (String vmMacAddress : listVmMacAddress) {
            installDhcpEntries(designatedDpnId, vmMacAddress, tx);
        }
    }

    public void unInstallDhcpFlowsForVms(String elanInstanceName, List<BigInteger> dpns, String vmMacAddress) {
        unInstallDhcpEntriesOnDpns(dpns, vmMacAddress);
        removeFromLocalCache(elanInstanceName, vmMacAddress);
    }

    public void unInstallDhcpFlowsForVms(String elanInstanceName, IpAddress tunnelIp, List<BigInteger> dpns) {
        Pair<IpAddress, String> tunnelIpElanNamePair = new ImmutablePair<>(tunnelIp, elanInstanceName);
        Set<String> vmMacs = tunnelIpElanNameToVmMacCache.get(tunnelIpElanNamePair);
        LOG.trace("In unInstallFlowsForVms elanInstanceName {}, tunnelIp {}, dpns {}, vmMacs {}",
                elanInstanceName, tunnelIp, dpns, vmMacs);
        if (vmMacs == null) {
            return;
        }
        for (String vmMacAddress : vmMacs) {
            unInstallDhcpEntriesOnDpns(dpns, vmMacAddress);
        }
        tunnelIpElanNameToVmMacCache.remove(tunnelIpElanNamePair);
    }

    public BigInteger readDesignatedSwitchesForExternalTunnel(IpAddress tunnelIp, String elanInstanceName) {
        if (tunnelIp == null || elanInstanceName == null || elanInstanceName.isEmpty()) {
            return null;
        }
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier =
                InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class)
                        .child(DesignatedSwitchForTunnel.class,
                                new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp)).build();
        Optional<DesignatedSwitchForTunnel> designatedSwitchForTunnelOptional =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        if (designatedSwitchForTunnelOptional.isPresent()) {
            return BigInteger.valueOf(designatedSwitchForTunnelOptional.get().getDpId());
        }
        return null;
    }

    public void writeDesignatedSwitchForExternalTunnel(BigInteger dpnId, IpAddress tunnelIp,
                                                       String elanInstanceName) {
        DesignatedSwitchForTunnelKey designatedSwitchForTunnelKey =
                new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp);
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier =
                InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class)
                        .child(DesignatedSwitchForTunnel.class, designatedSwitchForTunnelKey).build();
        DesignatedSwitchForTunnel designatedSwitchForTunnel =
                new DesignatedSwitchForTunnelBuilder().setDpId(dpnId.longValue())
                        .setElanInstanceName(elanInstanceName).setTunnelRemoteIpAddress(tunnelIp)
                        .withKey(designatedSwitchForTunnelKey).build();
        LOG.trace("Writing into CONFIG DS tunnelIp {}, elanInstanceName {}, dpnId {}", tunnelIp, elanInstanceName,
                dpnId);
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier, designatedSwitchForTunnel);
        updateLocalCache(dpnId, tunnelIp, elanInstanceName);
    }

    public void removeDesignatedSwitchForExternalTunnel(BigInteger dpnId, IpAddress tunnelIp,
                                                        String elanInstanceName) {
        DesignatedSwitchForTunnelKey designatedSwitchForTunnelKey =
                new DesignatedSwitchForTunnelKey(elanInstanceName, tunnelIp);
        InstanceIdentifier<DesignatedSwitchForTunnel> instanceIdentifier =
                InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class)
                        .child(DesignatedSwitchForTunnel.class, designatedSwitchForTunnelKey).build();
        LOG.trace("Removing from CONFIG DS tunnelIp {}, elanInstanceName {}, dpnId {}", tunnelIp,
                elanInstanceName, dpnId);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        removeFromLocalCache(dpnId, tunnelIp, elanInstanceName);
    }

    // This method is called whenever new OVS Switch is added.
    public void installDhcpDropActionOnDpn(BigInteger dpId) {
        // During controller restart we'll get add for designatedDpns as well and we
        // need not install drop flows for those dpns
        if (designatedDpnsToTunnelIpElanNameCache.get(dpId) != null) {
            LOG.trace("The dpn {} is designated DPN need not install drop flows");
            return;
        }
        // Read from DS since the cache may not get loaded completely in restart scenario
        if (isDpnDesignatedDpn(dpId)) {
            LOG.trace("The dpn {} is designated DPN need not install drop flows");
            return;
        }
        List<String> vmMacs = getAllVmMacs();
        LOG.trace("Installing drop actions to this new DPN {} VMs {}", dpId, vmMacs);
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
            for (String vmMacAddress : vmMacs) {
                installDhcpDropAction(dpId, vmMacAddress, tx);
            }
        }), LOG, "Error writing to the datastore");
    }

    private boolean isDpnDesignatedDpn(BigInteger dpId) {
        InstanceIdentifier<DesignatedSwitchesForExternalTunnels> instanceIdentifier =
                InstanceIdentifier.builder(DesignatedSwitchesForExternalTunnels.class).build();
        Optional<DesignatedSwitchesForExternalTunnels> designatedSwitchForTunnelOptional =
                MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
        if (designatedSwitchForTunnelOptional.isPresent()) {
            List<DesignatedSwitchForTunnel> list =
                    designatedSwitchForTunnelOptional.get().getDesignatedSwitchForTunnel();
            for (DesignatedSwitchForTunnel designatedSwitchForTunnel : list) {
                if (dpId.equals(BigInteger.valueOf(designatedSwitchForTunnel.getDpId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> getAllVmMacs() {
        List<String> vmMacs = new LinkedList<>();
        Collection<Set<String>> listOfVmMacs = tunnelIpElanNameToVmMacCache.values();
        for (Set<String> list : listOfVmMacs) {
            vmMacs.addAll(list);
        }
        return vmMacs;
    }

    public void updateLocalCache(BigInteger designatedDpnId, IpAddress tunnelIp, String elanInstanceName) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<>(tunnelIp, elanInstanceName);
        Set<Pair<IpAddress, String>> tunnelIpElanNameSet;
        tunnelIpElanNameSet = designatedDpnsToTunnelIpElanNameCache.get(designatedDpnId);
        if (tunnelIpElanNameSet == null) {
            tunnelIpElanNameSet = new CopyOnWriteArraySet<>();
        }
        tunnelIpElanNameSet.add(tunnelIpElanName);
        LOG.trace("Updating designatedDpnsToTunnelIpElanNameCache for designatedDpn {} value {}", designatedDpnId,
                tunnelIpElanNameSet);
        designatedDpnsToTunnelIpElanNameCache.put(designatedDpnId, tunnelIpElanNameSet);
    }

    public void updateLocalCache(IpAddress tunnelIp, String elanInstanceName, String vmMacAddress) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<>(tunnelIp, elanInstanceName);
        Set<String> setOfExistingVmMacAddress;
        setOfExistingVmMacAddress = tunnelIpElanNameToVmMacCache.get(tunnelIpElanName);
        if (setOfExistingVmMacAddress == null) {
            setOfExistingVmMacAddress = new CopyOnWriteArraySet<>();
        }
        setOfExistingVmMacAddress.add(vmMacAddress);
        LOG.trace("Updating tunnelIpElanNameToVmMacCache for tunnelIpElanName {} value {}", tunnelIpElanName,
                setOfExistingVmMacAddress);
        tunnelIpElanNameToVmMacCache.put(tunnelIpElanName, setOfExistingVmMacAddress);
        updateExistingVMTunnelIPCache(tunnelIp, elanInstanceName, vmMacAddress);
    }

    public void updateExistingVMTunnelIPCache(IpAddress tunnelIp, String elanInstanceName, String vmMacAddress) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<>(tunnelIp, elanInstanceName);
        Set<String> listExistingVmMacAddress;
        listExistingVmMacAddress = availableVMCache.get(tunnelIpElanName);
        if (listExistingVmMacAddress == null) {
            listExistingVmMacAddress = new CopyOnWriteArraySet<>();
        }
        listExistingVmMacAddress.add(vmMacAddress);
        LOG.trace("Updating availableVMCache for tunnelIpElanName {} value {}", tunnelIpElanName,
                listExistingVmMacAddress);
        availableVMCache.put(tunnelIpElanName, listExistingVmMacAddress);
    }

    public void handleDesignatedDpnDown(BigInteger dpnId, List<BigInteger> listOfDpns) {
        LOG.trace("In handleDesignatedDpnDown dpnId {}, listOfDpns {}", dpnId, listOfDpns);
        Set<Pair<IpAddress, String>> setOfTunnelIpElanNamePairs = designatedDpnsToTunnelIpElanNameCache.get(dpnId);
        if (setOfTunnelIpElanNamePairs == null || setOfTunnelIpElanNamePairs.isEmpty()) {
            LOG.trace("No tunnelIpElanName to handle for dpn {}. Returning", dpnId);
        } else {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                if (!dpnId.equals(DhcpMConstants.INVALID_DPID)) {
                    List<String> listOfVms = getAllVmMacs();
                    for (String vmMacAddress : listOfVms) {
                        unInstallDhcpEntries(dpnId, vmMacAddress, tx);
                    }
                }
                for (Pair<IpAddress, String> pair : setOfTunnelIpElanNamePairs) {
                    updateCacheAndInstallNewFlows(listOfDpns, pair, tx);
                }
            }), LOG, "Error writing to datastore");
        }
    }

    public void updateCacheAndInstallNewFlows(List<BigInteger> listOfDpns, Pair<IpAddress, String> pair,
            WriteTransaction tx) {
        BigInteger newDesignatedDpn = chooseDpn(pair.getLeft(), pair.getRight(), listOfDpns);
        if (newDesignatedDpn.equals(DhcpMConstants.INVALID_DPID)) {
            return;
        }
        Set<String> setOfVmMacs = tunnelIpElanNameToVmMacCache.get(pair);
        if (setOfVmMacs != null && !setOfVmMacs.isEmpty()) {
            LOG.trace("Updating DHCP flows for VMs {} with new designated DPN {}", setOfVmMacs, newDesignatedDpn);
            installDhcpFlowsForVms(newDesignatedDpn, setOfVmMacs, tx);
        }
        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = getSubnetDhcpPortData(pair.getRight());
        if (subnetDhcpData.isPresent()) {
            configureDhcpArpRequestResponseFlow(newDesignatedDpn, pair.getRight(), true,
                    pair.getLeft(), subnetDhcpData.get().getPortFixedip(), subnetDhcpData.get().getPortMacaddress());
        }
    }

    private void changeExistingFlowToDrop(Pair<IpAddress, String> tunnelIpElanNamePair, BigInteger dpnId,
                                          WriteTransaction tx) {
        Set<String> setOfVmMacAddress = tunnelIpElanNameToVmMacCache.get(tunnelIpElanNamePair);
        if (setOfVmMacAddress == null || setOfVmMacAddress.isEmpty()) {
            return;
        }
        for (String vmMacAddress : setOfVmMacAddress) {
            installDhcpDropAction(dpnId, vmMacAddress, tx);
        }
    }

    /**
     * Choose a dpn among the list of elanDpns such that it has lowest count of being the designated dpn.
     * @param tunnelIp The tunnel Ip address
     * @param elanInstanceName The elan instance name
     * @param dpns The data path nodes
     * @return The designated dpn
     */
    private BigInteger chooseDpn(IpAddress tunnelIp, String elanInstanceName,
            List<BigInteger> dpns) {
        BigInteger designatedDpnId = DhcpMConstants.INVALID_DPID;
        if (dpns != null && dpns.size() != 0) {
            List<BigInteger> candidateDpns = DhcpServiceUtils.getDpnsForElan(elanInstanceName, broker);
            candidateDpns.retainAll(dpns);
            LOG.trace("Choosing new dpn for tunnelIp {}, elanInstanceName {}, among elanDpns {}",
                    tunnelIp, elanInstanceName, candidateDpns);
            boolean elanDpnAvailableFlag = true;
            if (candidateDpns.isEmpty()) {
                candidateDpns = dpns;
                elanDpnAvailableFlag = false;
            }
            int size = 0;
            L2GatewayDevice device = getDeviceFromTunnelIp(tunnelIp);
            if (device == null) {
                LOG.trace("Could not find any device for elanInstanceName {} and tunnelIp {}",
                        elanInstanceName, tunnelIp);
                handleUnableToDesignateDpn(tunnelIp, elanInstanceName);
                return designatedDpnId;
            }
            for (BigInteger dpn : candidateDpns) {
                String hwvtepNodeId = device.getHwvtepNodeId();
                if (!elanDpnAvailableFlag) {
                    if (!isTunnelConfigured(dpn, hwvtepNodeId)) {
                        LOG.trace("Tunnel is not configured on dpn {} to TOR {}", dpn, hwvtepNodeId);
                        continue;
                    }
                } else if (!isTunnelUp(hwvtepNodeId, dpn)) {
                    LOG.trace("Tunnel is not up between dpn {} and TOR {}", dpn, hwvtepNodeId);
                    continue;
                }
                Set<Pair<IpAddress, String>> tunnelIpElanNameSet = designatedDpnsToTunnelIpElanNameCache.get(dpn);
                if (tunnelIpElanNameSet == null) {
                    designatedDpnId = dpn;
                    break;
                }
                if (size == 0 || tunnelIpElanNameSet.size() < size) {
                    size = tunnelIpElanNameSet.size();
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
        writeDesignatedSwitchForExternalTunnel(DhcpMConstants.INVALID_DPID, tunnelIp, elanInstanceName);
    }

    private void installDhcpEntries(BigInteger dpnId, String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL,
                vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil, dhcpServiceCounters, tx);
    }

    public void addOrRemoveDhcpArpFlowforElan(String elanInstanceName, boolean addFlow, String dhcpIpAddress,
                                              String dhcpMacAddress) {
        LOG.trace("Configure DHCP SR-IOV Arp flows for Elan {} dpns .", elanInstanceName);
        for (Entry<BigInteger, Set<Pair<IpAddress,String>>> entry : designatedDpnsToTunnelIpElanNameCache.entrySet()) {
            BigInteger dpn = entry.getKey();
            Set<Pair<IpAddress,String>> tunnelIpElanNameSet = entry.getValue();
            for (Pair<IpAddress, String> pair : tunnelIpElanNameSet) {
                if (pair.getRight().equalsIgnoreCase(elanInstanceName)) {
                    if (addFlow) {
                        LOG.trace("Adding SR-IOV DHCP Arp Flows for Elan {} and tunnelIp {}",
                                elanInstanceName, pair.getLeft());
                        configureDhcpArpRequestResponseFlow(dpn, elanInstanceName, true,
                                pair.getLeft(), dhcpIpAddress, dhcpMacAddress);
                    } else {
                        LOG.trace("Deleting SR-IOV DHCP Arp Flows for Elan {} and tunnelIp {}",
                                elanInstanceName, pair.getLeft());
                        configureDhcpArpRequestResponseFlow(dpn, elanInstanceName, false,
                                pair.getLeft(), dhcpIpAddress, dhcpMacAddress);
                    }
                }
            }
        }
    }


    public void configureDhcpArpRequestResponseFlow(BigInteger dpnId, String elanInstanceName, boolean addFlow,
                                            IpAddress tunnelIp, String dhcpIpAddress, String dhcpMacAddress) {
        L2GatewayDevice device = getDeviceFromTunnelIp(tunnelIp);
        if (device == null) {
            LOG.error("Unable to get L2Device for tunnelIp {} and elanInstanceName {}", tunnelIp,
                    elanInstanceName);
        }
        jobCoordinator.enqueueJob(getJobKey(elanInstanceName), () -> {
            if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(dpnId),
                        device.getHwvtepNodeId());
                int lportTag = interfaceManager.getInterfaceInfo(tunnelInterfaceName).getInterfaceTag();
                InstanceIdentifier<ElanInstance> elanIdentifier = InstanceIdentifier.builder(ElanInstances.class)
                        .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
                Optional<ElanInstance> optElan = MDSALUtil.read(broker,
                        LogicalDatastoreType.CONFIGURATION, elanIdentifier);
                if (optElan.isPresent()) {
                    LOG.trace("Configuring the SR-IOV Arp request/response flows for LPort {} ElanTag {}.",
                            lportTag, optElan.get().getElanTag());
                    Uuid nwUuid = new Uuid(elanInstanceName);
                    String strVni = DhcpServiceUtils.getSegmentationId(nwUuid, broker);
                    BigInteger vni = strVni != null ? new BigInteger(strVni) : BigInteger.ZERO;
                    if (!vni.equals(BigInteger.ZERO)) {
                        if (addFlow) {
                            LOG.trace("Installing the SR-IOV DHCP Arp flow for DPN {} Port Ip {}, Lport {}.",
                                    dpnId, dhcpIpAddress, lportTag);
                            installDhcpArpRequestFlows(dpnId, vni, dhcpIpAddress, lportTag,
                                    optElan.get().getElanTag());
                            installDhcpArpResponderFlows(dpnId, tunnelInterfaceName, lportTag, elanInstanceName,
                                    dhcpIpAddress, dhcpMacAddress);
                        } else {
                            LOG.trace("Uninstalling the SR-IOV DHCP Arp flows for DPN {} Port Ip {}, Lport {}.",
                                    dpnId, dhcpIpAddress, lportTag);
                            uninstallDhcpArpRequestFlows(dpnId, vni, dhcpIpAddress, lportTag);
                            uninstallDhcpArpResponderFlows(dpnId, tunnelInterfaceName, lportTag, dhcpIpAddress);
                        }
                    }
                }
            }
            return null;
        });
    }

    public  java.util.Optional<SubnetToDhcpPort> getSubnetDhcpPortData(String elanInstanceName) {
        java.util.Optional<SubnetToDhcpPort> optSubnetDhcp = java.util.Optional.empty();
        Uuid nwUuid = new Uuid(elanInstanceName);
        List<Uuid> subnets = DhcpServiceUtils.getSubnetIdsFromNetworkId(broker, nwUuid);
        for (Uuid subnet : subnets) {
            if (DhcpServiceUtils.isIpv4Subnet(broker, subnet)) {
                optSubnetDhcp = DhcpServiceUtils.getSubnetDhcpPortData(broker, subnet.getValue());
                return optSubnetDhcp;
            }
        }
        return optSubnetDhcp;
    }

    private void installDhcpArpRequestFlows(BigInteger dpnId, BigInteger vni, String dhcpIpAddress,
                                            int lportTag, Long elanTag) {
        DhcpServiceUtils.setupDhcpArpRequest(dpnId, NwConstants.EXTERNAL_TUNNEL_TABLE, vni, dhcpIpAddress,
                lportTag, elanTag, true, mdsalUtil);
    }

    private void installDhcpArpResponderFlows(BigInteger dpnId, String interfaceName, int lportTag,
                                              String elanInstanceName, String dhcpIpAddress, String dhcpMacAddress) {
        LOG.trace("Adding SR-IOV DHCP ArpResponder for elan {} Lport {} Port Ip {}.",
                elanInstanceName, lportTag, dhcpIpAddress);
        ArpResponderInput.ArpReponderInputBuilder builder = new ArpResponderInput.ArpReponderInputBuilder();
        builder.setDpId(dpnId).setInterfaceName(interfaceName).setSpa(dhcpIpAddress).setSha(dhcpMacAddress)
                .setLportTag(lportTag);
        builder.setInstructions(ArpResponderUtil.getInterfaceInstructions(interfaceManager, interfaceName,
                dhcpIpAddress, dhcpMacAddress, itmRpcService));
        elanService.addExternalTunnelArpResponderFlow(builder.buildForInstallFlow(), elanInstanceName);
    }

    private void uninstallDhcpArpResponderFlows(BigInteger dpnId, String interfaceName, int lportTag,
                                                String dhcpIpAddress) {
        LOG.trace("Removing SR-IOV DHCP ArpResponder flow for interface {} on DPN {}", interfaceName, dpnId);
        ArpResponderInput arpInput = new ArpResponderInput.ArpReponderInputBuilder().setDpId(dpnId)
                .setInterfaceName(interfaceName).setSpa(dhcpIpAddress)
                .setLportTag(lportTag).buildForRemoveFlow();
        elanService.removeArpResponderFlow(arpInput);
    }

    private void uninstallDhcpArpRequestFlows(BigInteger dpnId, BigInteger vni, String dhcpIpAddress,
                                              int lportTag) {
        DhcpServiceUtils.setupDhcpArpRequest(dpnId, NwConstants.EXTERNAL_TUNNEL_TABLE, vni, dhcpIpAddress,
                lportTag, null, false, mdsalUtil);
    }


    public void unInstallDhcpEntries(BigInteger dpnId, String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpFlowEntry(dpnId, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL,
                vmMacAddress, NwConstants.DEL_FLOW, mdsalUtil, dhcpServiceCounters, tx);
    }

    private void installDhcpDropAction(BigInteger dpn, String vmMacAddress, WriteTransaction tx) {
        DhcpServiceUtils.setupDhcpDropAction(dpn, NwConstants.DHCP_TABLE_EXTERNAL_TUNNEL,
                vmMacAddress, NwConstants.ADD_FLOW, mdsalUtil, dhcpServiceCounters, tx);
    }

    public List<ListenableFuture<Void>> handleTunnelStateDown(IpAddress tunnelIp, BigInteger interfaceDpn) {
        LOG.trace("In handleTunnelStateDown tunnelIp {}, interfaceDpn {}", tunnelIp, interfaceDpn);
        if (interfaceDpn == null) {
            return Collections.emptyList();
        }
        synchronized (getTunnelIpDpnKey(tunnelIp, interfaceDpn)) {
            Set<Pair<IpAddress, String>> tunnelElanPairSet =
                    designatedDpnsToTunnelIpElanNameCache.get(interfaceDpn);
            if (tunnelElanPairSet == null || tunnelElanPairSet.isEmpty()) {
                return Collections.emptyList();
            }
            for (Pair<IpAddress, String> tunnelElanPair : tunnelElanPairSet) {
                IpAddress tunnelIpInDpn = tunnelElanPair.getLeft();
                if (tunnelIpInDpn.equals(tunnelIp)) {
                    if (!checkL2GatewayConnection(tunnelElanPair)) {
                        LOG.trace("Couldn't find device for given tunnelIpElanPair {} in L2GwConnCache",
                                tunnelElanPair);
                        return Collections.emptyList();
                    }
                }
            }
            return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                for (Pair<IpAddress, String> tunnelElanPair : tunnelElanPairSet) {
                    IpAddress tunnelIpInDpn = tunnelElanPair.getLeft();
                    String elanInstanceName = tunnelElanPair.getRight();
                    if (tunnelIpInDpn.equals(tunnelIp)) {
                        if (!checkL2GatewayConnection(tunnelElanPair)) {
                            LOG.trace("Couldn't find device for given tunnelIpElanPair {} in L2GwConnCache",
                                    tunnelElanPair);
                        }
                        List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(broker);
                        dpns.remove(interfaceDpn);
                        changeExistingFlowToDrop(tunnelElanPair, interfaceDpn, tx);
                        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = getSubnetDhcpPortData(elanInstanceName);
                        if (subnetDhcpData.isPresent()) {
                            configureDhcpArpRequestResponseFlow(interfaceDpn, elanInstanceName, false,
                                    tunnelIpInDpn, subnetDhcpData.get().getPortFixedip(),
                                    subnetDhcpData.get().getPortMacaddress());
                        }
                        updateCacheAndInstallNewFlows(dpns, tunnelElanPair, tx);
                    }
                }
            }));
        }
    }

    private boolean checkL2GatewayConnection(Pair<IpAddress, String> tunnelElanPair) {
        ConcurrentMap<String, L2GatewayDevice> l2GwDevices =
                ElanL2GwCacheUtils.getInvolvedL2GwDevices(tunnelElanPair.getRight());
        for (L2GatewayDevice device : l2GwDevices.values()) {
            if (device.getTunnelIp().equals(tunnelElanPair.getLeft())) {
                return true;
            }
        }
        return false;
    }

    private String getTunnelIpDpnKey(IpAddress tunnelIp, BigInteger interfaceDpn) {
        return tunnelIp.toString() + interfaceDpn;
    }

    private void removeFromLocalCache(String elanInstanceName, String vmMacAddress) {
        for (Entry<Pair<IpAddress, String>, Set<String>> entry : tunnelIpElanNameToVmMacCache.entrySet()) {
            Pair<IpAddress, String> pair = entry.getKey();
            if (pair.getRight().trim().equalsIgnoreCase(elanInstanceName.trim())) {
                Set<String> setOfExistingVmMacAddress = entry.getValue();
                if (setOfExistingVmMacAddress == null || setOfExistingVmMacAddress.isEmpty()) {
                    continue;
                }
                LOG.trace("Removing vmMacAddress {} from listOfMacs {} for elanInstanceName {}", vmMacAddress,
                        setOfExistingVmMacAddress, elanInstanceName);
                setOfExistingVmMacAddress.remove(vmMacAddress);
                if (setOfExistingVmMacAddress.size() > 0) {
                    tunnelIpElanNameToVmMacCache.put(pair, setOfExistingVmMacAddress);
                    return;
                }
                tunnelIpElanNameToVmMacCache.remove(pair);
            }
        }
    }

    public void removeFromLocalCache(BigInteger designatedDpnId, IpAddress tunnelIp, String elanInstanceName) {
        Pair<IpAddress, String> tunnelIpElanName = new ImmutablePair<>(tunnelIp, elanInstanceName);
        Set<Pair<IpAddress, String>> tunnelIpElanNameSet;
        tunnelIpElanNameSet = designatedDpnsToTunnelIpElanNameCache.get(designatedDpnId);
        if (tunnelIpElanNameSet != null) {
            LOG.trace("Removing tunnelIpElan {} from designatedDpnsToTunnelIpElanNameCache. Existing list {} for "
                            + "designatedDpnId {}",
                    tunnelIpElanName, tunnelIpElanNameSet, designatedDpnId);
            tunnelIpElanNameSet.remove(tunnelIpElanName);
            if (tunnelIpElanNameSet.size() != 0) {
                designatedDpnsToTunnelIpElanNameCache.put(designatedDpnId, tunnelIpElanNameSet);
            } else {
                designatedDpnsToTunnelIpElanNameCache.remove(designatedDpnId);
            }
        }
    }

    public void updateVniMacToPortCache(BigInteger vni, String macAddress, Port port) {
        if (macAddress == null) {
            return;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<>(
                vni, macAddress.toUpperCase(Locale.getDefault()));
        LOG.trace("Updating vniMacAddressToPortCache with vni {} , mac {} , pair {} and port {}", vni,
                macAddress.toUpperCase(Locale.getDefault()), vniMacAddressPair, port);
        vniMacAddressToPortCache.put(vniMacAddressPair, port);
    }

    public void removeVniMacToPortCache(BigInteger vni, String macAddress) {
        if (macAddress == null) {
            return;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<>(
                vni, macAddress.toUpperCase(Locale.getDefault()));
        vniMacAddressToPortCache.remove(vniMacAddressPair);
    }

    public Port readVniMacToPortCache(BigInteger vni, String macAddress) {
        if (macAddress == null) {
            return null;
        }
        Pair<BigInteger, String> vniMacAddressPair = new ImmutablePair<>(
                vni, macAddress.toUpperCase(Locale.getDefault()));
        LOG.trace("Reading vniMacAddressToPortCache with vni {} , mac {} , pair {} and port {}",
                vni, macAddress.toUpperCase(Locale.getDefault()), vniMacAddressPair,
                vniMacAddressToPortCache.get(vniMacAddressPair));
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
                LOG.trace("Tunnel interface name: {}", tunnelInterfaceName);
            } else {
                LOG.warn("RPC call to ITM.GetExternalTunnelInterfaceName failed with error: {}", rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Failed to get external tunnel interface name for sourceNode: {} and dstNode: {}",
                    sourceNode, dstNode, e);
        }
        return tunnelInterfaceName;
    }

    public static Optional<Node> getNode(DataBroker dataBroker, String physicalSwitchNodeId) {
        InstanceIdentifier<Node> psNodeId = HwvtepSouthboundUtils
                .createInstanceIdentifier(new NodeId(physicalSwitchNodeId));
        return MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, psNodeId, dataBroker);
    }

    public RemoteMcastMacs createRemoteMcastMac(Node dstDevice, String logicalSwitchName, IpAddress internalTunnelIp) {
        Set<LocatorSet> locators = new HashSet<>();
        TerminationPointKey terminationPointKey = HwvtepSouthboundUtils.getTerminationPointKey(
                internalTunnelIp.getIpv4Address().getValue());
        HwvtepPhysicalLocatorRef phyLocRef = new HwvtepPhysicalLocatorRef(
                HwvtepSouthboundUtils.createInstanceIdentifier(dstDevice.getNodeId()).child(TerminationPoint.class,
                        terminationPointKey));
        locators.add(new LocatorSetBuilder().setLocatorRef(phyLocRef).build());

        HwvtepLogicalSwitchRef lsRef = new HwvtepLogicalSwitchRef(HwvtepSouthboundUtils
                .createLogicalSwitchesInstanceIdentifier(dstDevice.getNodeId(), new HwvtepNodeName(logicalSwitchName)));

        RemoteMcastMacs remoteMcastMacs = new RemoteMcastMacsBuilder()
                .setMacEntryKey(new MacAddress(UNKNOWN_DMAC))
                .setLogicalSwitchRef(lsRef).build();
        InstanceIdentifier<RemoteMcastMacs> iid = HwvtepSouthboundUtils.createRemoteMcastMacsInstanceIdentifier(
                dstDevice.getNodeId(), remoteMcastMacs.key());
        ReadOnlyTransaction transaction = broker.newReadOnlyTransaction();
        try {
            //TODO do async mdsal read
            remoteMcastMacs = transaction.read(LogicalDatastoreType.CONFIGURATION, iid).checkedGet().get();
            locators.addAll(remoteMcastMacs.getLocatorSet());
            return new RemoteMcastMacsBuilder(remoteMcastMacs).setLocatorSet(new ArrayList<>(locators)).build();
        } catch (ReadFailedException e) {
            LOG.error("Failed to read the macs {}", iid);
        }
        return null;
    }

    private WriteTransaction putRemoteMcastMac(WriteTransaction transaction, String elanName,
                                               L2GatewayDevice device, IpAddress internalTunnelIp) {
        Optional<Node> optionalNode = getNode(broker, device.getHwvtepNodeId());
        Node dstNode = optionalNode.get();
        if (dstNode == null) {
            LOG.trace("could not get device node {} ", device.getHwvtepNodeId());
            return null;
        }
        RemoteMcastMacs macs = createRemoteMcastMac(dstNode, elanName, internalTunnelIp);
        HwvtepUtils.putRemoteMcastMac(transaction, dstNode.getNodeId(), macs);
        return transaction;
    }

    public void installRemoteMcastMac(final BigInteger designatedDpnId, final IpAddress tunnelIp,
                                      final String elanInstanceName) {
        if (designatedDpnId.equals(DhcpMConstants.INVALID_DPID)) {
            return;
        }

        jobCoordinator.enqueueJob(getJobKey(elanInstanceName), () -> {
            if (!entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                LOG.info("Installing remote McastMac is not executed for this node.");
                return Collections.emptyList();
            }

            LOG.info("Installing remote McastMac");
            L2GatewayDevice device = getDeviceFromTunnelIp(tunnelIp);
            if (device == null) {
                LOG.error("Unable to get L2Device for tunnelIp {} and elanInstanceName {}", tunnelIp,
                    elanInstanceName);
                return Collections.emptyList();
            }
            String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(designatedDpnId),
                    device.getHwvtepNodeId());
            if (tunnelInterfaceName != null) {
                Interface tunnelInterface =
                        interfaceManager.getInterfaceInfoFromConfigDataStore(tunnelInterfaceName);
                if (tunnelInterface == null) {
                    LOG.trace("Tunnel Interface is not present {}", tunnelInterfaceName);
                    return Collections.emptyList();
                }
                return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(
                    tx -> putRemoteMcastMac(tx, elanInstanceName, device,
                            tunnelInterface.augmentation(IfTunnel.class).getTunnelSource())));
            }
            return Collections.emptyList();
        });
    }

    private L2GatewayDevice getDeviceFromTunnelIp(IpAddress tunnelIp) {
        Collection<L2GatewayDevice> devices = l2GatewayCache.getAll();
        LOG.trace("In getDeviceFromTunnelIp devices {}", devices);
        for (L2GatewayDevice device : devices) {
            if (tunnelIp.equals(device.getTunnelIp())) {
                return device;
            }
        }
        return null;
    }

    private boolean isTunnelUp(String nodeName, BigInteger dpn) {
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(dpn), nodeName);
        if (tunnelInterfaceName == null) {
            LOG.trace("Tunnel Interface is not present on node {} with dpn {}", nodeName, dpn);
            return false;
        }
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
                .Interface tunnelInterface =
                DhcpServiceUtils.getInterfaceFromOperationalDS(tunnelInterfaceName, broker);
        if (tunnelInterface == null) {
            LOG.trace("Interface {} is not present in interface state", tunnelInterfaceName);
            return false;
        }
        return tunnelInterface.getOperStatus() == OperStatus.Up;
    }

    public List<ListenableFuture<Void>> handleTunnelStateUp(IpAddress tunnelIp, BigInteger interfaceDpn) {
        LOG.trace("In handleTunnelStateUp tunnelIp {}, interfaceDpn {}", tunnelIp, interfaceDpn);
        synchronized (getTunnelIpDpnKey(tunnelIp, interfaceDpn)) {
            Set<Pair<IpAddress, String>> tunnelIpElanPair =
                    designatedDpnsToTunnelIpElanNameCache.get(DhcpMConstants.INVALID_DPID);
            List<BigInteger> dpns = DhcpServiceUtils.getListOfDpns(broker);
            if (tunnelIpElanPair == null || tunnelIpElanPair.isEmpty()) {
                LOG.trace("There are no undesignated DPNs");
                return Collections.emptyList();
            }
            return Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(tx -> {
                for (Pair<IpAddress, String> pair : tunnelIpElanPair) {
                    if (tunnelIp.equals(pair.getLeft())) {
                        String elanInstanceName = pair.getRight();
                        BigInteger newDesignatedDpn = designateDpnId(tunnelIp, elanInstanceName, dpns);
                        if (newDesignatedDpn != null && !newDesignatedDpn.equals(DhcpMConstants.INVALID_DPID)) {
                            Set<String> vmMacAddress = tunnelIpElanNameToVmMacCache.get(pair);
                            if (vmMacAddress != null && !vmMacAddress.isEmpty()) {
                                LOG.trace("Updating DHCP flow for macAddress {} with newDpn {}",
                                        vmMacAddress, newDesignatedDpn);
                                installDhcpFlowsForVms(newDesignatedDpn, vmMacAddress, tx);
                            }
                        }
                        java.util.Optional<SubnetToDhcpPort> subnetDhcpData = getSubnetDhcpPortData(elanInstanceName);
                        if (subnetDhcpData.isPresent()) {
                            configureDhcpArpRequestResponseFlow(newDesignatedDpn, elanInstanceName,
                                    true, tunnelIp, subnetDhcpData.get().getPortFixedip(),
                                    subnetDhcpData.get().getPortMacaddress());
                        }
                    }
                }
            }));
        }
    }

    private boolean isTunnelConfigured(BigInteger dpn, String hwVtepNodeId) {
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(dpn), hwVtepNodeId);
        if (tunnelInterfaceName == null) {
            return false;
        }
        Interface tunnelInterface = interfaceManager.getInterfaceInfoFromConfigDataStore(tunnelInterfaceName);
        if (tunnelInterface == null) {
            LOG.trace("Tunnel Interface is not present {}", tunnelInterfaceName);
            return false;
        }
        return true;
    }

    public void removeFromAvailableCache(Pair<IpAddress, String> tunnelIpElanName) {
        availableVMCache.remove(tunnelIpElanName);
    }

    private void unInstallDhcpEntriesOnDpns(final List<BigInteger> dpns, final String vmMacAddress) {
        jobCoordinator.enqueueJob(getJobKey(vmMacAddress), () -> {
            if (entityOwnershipUtils.isEntityOwner(HwvtepSouthboundConstants.ELAN_ENTITY_TYPE,
                    HwvtepSouthboundConstants.ELAN_ENTITY_NAME)) {
                return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    for (final BigInteger dpn : dpns) {
                        unInstallDhcpEntries(dpn, vmMacAddress, tx);
                    }
                }));
            } else {
                LOG.trace("Exiting unInstallDhcpEntries since this cluster node is not the owner for dpn");
            }

            return Collections.emptyList();
        });
    }

    public IpAddress getTunnelIpBasedOnElan(String elanInstanceName, String vmMacAddress) {
        LOG.trace("DhcpExternalTunnelManager getTunnelIpBasedOnElan elanInstanceName {}", elanInstanceName);
        IpAddress tunnelIp = null;
        for (Entry<Pair<IpAddress, String>, Set<String>> entry : availableVMCache.entrySet()) {
            Pair<IpAddress, String> pair = entry.getKey();
            LOG.trace("DhcpExternalTunnelManager getTunnelIpBasedOnElan left {} right {}", pair.getLeft(),
                    pair.getRight());
            if (pair.getRight().trim().equalsIgnoreCase(elanInstanceName.trim())) {
                Set<String> listExistingVmMacAddress = entry.getValue();
                if (listExistingVmMacAddress != null && !listExistingVmMacAddress.isEmpty()
                        && listExistingVmMacAddress.contains(vmMacAddress)) {
                    tunnelIp = pair.getLeft();
                    break;
                }
            }
        }
        LOG.trace("DhcpExternalTunnelManager getTunnelIpBasedOnElan returned tunnelIP {}", tunnelIp);
        return tunnelIp;
    }

    private String getJobKey(final String jobKeySuffix) {
        return DhcpMConstants.DHCP_JOB_KEY_PREFIX + jobKeySuffix;
    }
}
