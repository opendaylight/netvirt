/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProtocolTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.port.map.ip.port.mapping.intext.ip.protocol.type.ip.port.map.IpPortExternal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.IpPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.snatint.ip.port.map.intip.port.map.ip.port.IntIpProtoType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatInterfaceStateChangeListener
    extends AsyncDataTreeChangeListenerBase<Interface, NatInterfaceStateChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(NatInterfaceStateChangeListener.class);
    private static final String NAT_DS = "NATDS";
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService odlInterfaceRpcService;
    private final JobCoordinator coordinator;
    private final FloatingIPListener floatingIPListener;
    private final NeutronvpnService neutronVpnService;
    private final IMdsalApiManager mdsalManager;
    private final NaptManager naptManager;
    Table<Interface.OperStatus, Interface.OperStatus, IntfTransitionState> stateTable = HashBasedTable.create();

    enum IntfTransitionState {
        STATE_UP,
        STATE_DOWN,
        STATE_IGNORE
    }

    private void initialize() {
        //  Interface State Transition Table
        //               Up                Down            Unknown
        // ---------------------------------------------------------------
        /* Up       { STATE_IGNORE,   STATE_DOWN,     STATE_DOWN }, */
        /* Down     { STATE_UP,       STATE_IGNORE,   STATE_IGNORE }, */
        /* Unknown  { STATE_UP,       STATE_DOWN,     STATE_IGNORE }, */
        stateTable.put(Interface.OperStatus.Up, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
        stateTable.put(Interface.OperStatus.Down, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Up, IntfTransitionState.STATE_UP);
        stateTable.put(Interface.OperStatus.Unknown, Interface.OperStatus.Down, IntfTransitionState.STATE_DOWN);
        stateTable.put(Interface.OperStatus.Up, Interface.OperStatus.Unknown, IntfTransitionState.STATE_DOWN);
    }

    @Inject
    public NatInterfaceStateChangeListener(final DataBroker dataBroker,
            final OdlInterfaceRpcService odlInterfaceRpcService, final JobCoordinator coordinator,
            final FloatingIPListener floatingIPListener,final NeutronvpnService neutronvpnService,
            final IMdsalApiManager mdsalManager, final NaptManager naptManager) {
        super(Interface.class, NatInterfaceStateChangeListener.class);
        this.dataBroker = dataBroker;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.coordinator = coordinator;
        this.floatingIPListener = floatingIPListener;
        this.neutronVpnService = neutronvpnService;
        this.mdsalManager = mdsalManager;
        this.naptManager = naptManager;
        initialize();
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(InterfacesState.class).child(Interface.class);
    }

    @Override
    protected NatInterfaceStateChangeListener getDataTreeChangeListener() {
        return NatInterfaceStateChangeListener.this;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void add(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("add : Interface {} up event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("add : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        String interfaceName = intrf.getName();
        BigInteger intfDpnId;
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(intrf);
        } catch (Exception e) {
            LOG.error("add : Exception occured while retriving dpnid for interface {}", intrf.getName(), e);
            return;
        }
        if (BigInteger.ZERO.equals(intfDpnId)) {
            LOG.warn("add : Could not retrieve dp id for interface {} ", interfaceName);
            return;
        }
        // We service only VM interfaces. We do not service Tunnel Interfaces here.
        // Tunnel events are directly serviced by TunnelInterfacesStateListener present as part of
        // VpnInterfaceManager
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            String routerName = routerInterface.getRouterName();
            NatInterfaceStateAddWorker natIfaceStateAddWorker = new NatInterfaceStateAddWorker(interfaceName,
                    intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + intrf.getName(), natIfaceStateAddWorker);

            NatFlowAddWorker natFlowAddWorker = new NatFlowAddWorker(interfaceName, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natFlowAddWorker, NatConstants.NAT_DJC_MAX_RETRIES);
        } else {
            LOG.info("add : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void remove(InstanceIdentifier<Interface> identifier, Interface intrf) {
        LOG.trace("remove : Interface {} removed event received", intrf);
        if (!L2vlan.class.equals(intrf.getType())) {
            LOG.debug("remove : Interface {} is not Vlan Interface.Ignoring", intrf.getName());
            return;
        }
        String interfaceName = intrf.getName();
        BigInteger intfDpnId = BigInteger.ZERO;
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(intrf);
        } catch (Exception e) {
            LOG.error("remove : Exception occured while retriving dpnid for interface {}",  intrf.getName(), e);
            InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
            Optional<VpnInterface> cfgVpnInterface =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!cfgVpnInterface.isPresent()) {
                LOG.warn("remove : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                return;
            }
            for (VpnInstanceNames vpnInterfaceVpnInstance : cfgVpnInterface.get().getVpnInstanceNames()) {
                String vpnName  = vpnInterfaceVpnInstance.getVpnName();
                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                      .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                      SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                if (optVpnInterface.isPresent()) {
                    intfDpnId = optVpnInterface.get().getDpnId();
                    break;
                }
            }
        }
        if (intfDpnId.equals(BigInteger.ZERO)) {
            LOG.warn("remove : Could not retrieve dpnid for interface {} ", interfaceName);
            return;
        }
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            String routerName = routerInterface.getRouterName();
            NatInterfaceStateRemoveWorker natIfaceStateRemoveWorker = new NatInterfaceStateRemoveWorker(interfaceName,
                    intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natIfaceStateRemoveWorker);

            NatFlowRemoveWorker natFlowRemoveWorker = new NatFlowRemoveWorker(intrf, intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + interfaceName, natFlowRemoveWorker,
                    NatConstants.NAT_DJC_MAX_RETRIES);
        } else {
            LOG.info("remove : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void update(InstanceIdentifier<Interface> identifier, Interface original, Interface update) {
        LOG.trace("update : Operation Interface update event - Old: {}, New: {}", original, update);
        if (!L2vlan.class.equals(update.getType())) {
            LOG.debug("update : Interface {} is not Vlan Interface.Ignoring", update.getName());
            return;
        }
        BigInteger intfDpnId = BigInteger.ZERO;
        String interfaceName = update.getName();
        try {
            intfDpnId = NatUtil.getDpIdFromInterface(update);
        } catch (Exception e) {
            LOG.error("update : Exception occured while retriving dpnid for interface {}",  update.getName(), e);
            InstanceIdentifier<VpnInterface> id = NatUtil.getVpnInterfaceIdentifier(interfaceName);
            Optional<VpnInterface> cfgVpnInterface =
                    SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!cfgVpnInterface.isPresent()) {
                LOG.warn("update : Interface {} is not a VPN Interface, ignoring.", interfaceName);
                return;
            }
            for (VpnInstanceNames vpnInterfaceVpnInstance : cfgVpnInterface.get().getVpnInstanceNames()) {
                String vpnName  = vpnInterfaceVpnInstance.getVpnName();
                InstanceIdentifier<VpnInterfaceOpDataEntry> idOper = NatUtil
                      .getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnName);
                Optional<VpnInterfaceOpDataEntry> optVpnInterface =
                      SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                            dataBroker, LogicalDatastoreType.OPERATIONAL, idOper);
                if (optVpnInterface.isPresent()) {
                    intfDpnId = optVpnInterface.get().getDpnId();
                    break;
                }
            }
        }
        if (intfDpnId.equals(BigInteger.ZERO)) {
            LOG.warn("remove : Could not retrieve dpnid for interface {} ", interfaceName);
            return;
        }
        RouterInterface routerInterface = NatUtil.getConfiguredRouterInterface(dataBroker, interfaceName);
        if (routerInterface != null) {
            String routerName = routerInterface.getRouterName();
            NatInterfaceStateUpdateWorker natIfaceStateupdateWorker = new NatInterfaceStateUpdateWorker(original,
                    update, intfDpnId, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + update.getName(), natIfaceStateupdateWorker);
            NatFlowUpdateWorker natFlowUpdateWorker = new NatFlowUpdateWorker(original, update, routerName);
            coordinator.enqueueJob(NAT_DS + "-" + update.getName(), natFlowUpdateWorker,
                    NatConstants.NAT_DJC_MAX_RETRIES);
        } else {
            LOG.info("update : Router-Interface Mapping not found for Interface : {}", interfaceName);
        }
    }

    void handleRouterInterfacesUpEvent(String routerName, String interfaceName, BigInteger dpId,
            WriteTransaction writeOperTxn) {
        LOG.debug("handleRouterInterfacesUpEvent : Handling UP event for router interface {} in Router {} on Dpn {}",
                interfaceName, routerName, dpId);
        NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, dpId, writeOperTxn);
        NatUtil.addToDpnRoutersMap(dataBroker, routerName, interfaceName, dpId, writeOperTxn);
    }

    void handleRouterInterfacesDownEvent(String routerName, String interfaceName, BigInteger dpnId,
                                         WriteTransaction writeOperTxn) {
        LOG.debug("handleRouterInterfacesDownEvent : Handling DOWN event for router Interface {} in Router {}",
                interfaceName, routerName);
        NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, interfaceName, dpnId, writeOperTxn);
        NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, interfaceName, dpnId, odlInterfaceRpcService,
                writeOperTxn);
    }

    private IntfTransitionState getTransitionState(Interface.OperStatus original , Interface.OperStatus updated) {
        IntfTransitionState transitionState = stateTable.get(original, updated);

        if (transitionState == null) {
            return IntfTransitionState.STATE_IGNORE;
        }
        return transitionState;
    }

    private class NatInterfaceStateAddWorker implements Callable<List<ListenableFuture<Void>>> {
        private String interfaceName;
        private String routerName;
        private BigInteger intfDpnId;

        NatInterfaceStateAddWorker(String interfaceName, BigInteger intfDpnId, String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                LOG.trace("call : Received interface {} PORT UP OR ADD event ", interfaceName);
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                handleRouterInterfacesUpEvent(routerName, interfaceName, intfDpnId, writeOperTxn);
                futures.add(writeOperTxn.submit());
            } catch (Exception e) {
                LOG.error("call : Exception caught in Interface {} Operational State Up event",
                        interfaceName, e);
            }
            return futures;
        }
    }

    private class NatInterfaceStateRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private String interfaceName;
        private String routerName;
        private BigInteger intfDpnId;

        NatInterfaceStateRemoveWorker(String interfaceName, BigInteger intfDpnId, String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                LOG.trace("call : Received interface {} PORT DOWN or REMOVE event", interfaceName);
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                handleRouterInterfacesDownEvent(routerName, interfaceName, intfDpnId, writeOperTxn);
                futures.add(writeOperTxn.submit());
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling deletion of VPN Interface {}.", interfaceName, e);
            }
            return futures;
        }
    }

    private class NatInterfaceStateUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        private Interface original;
        private Interface update;
        private BigInteger intfDpnId;
        private String routerName;

        NatInterfaceStateUpdateWorker(Interface original, Interface update, BigInteger intfDpnId, String routerName) {
            this.original = original;
            this.update = update;
            this.intfDpnId = intfDpnId;
            this.routerName = routerName;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                final String interfaceName = update.getName();
                LOG.trace("call : Received interface {} state change event", interfaceName);
                LOG.debug("call : DPN ID {} for the interface {} ", intfDpnId, interfaceName);
                WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
                IntfTransitionState state = getTransitionState(original.getOperStatus(), update.getOperStatus());
                if (state.equals(IntfTransitionState.STATE_IGNORE)) {
                    LOG.info("NAT Service: Interface {} state original {} updated {} not handled",
                            interfaceName, original.getOperStatus(), update.getOperStatus());
                    return futures;
                }
                if (state.equals(IntfTransitionState.STATE_DOWN)) {
                    LOG.debug("call : DPN {} connnected to the interface {} has gone down."
                            + "Hence clearing the dpn-vpninterfaces-list entry from the"
                            + " neutron-router-dpns model in the ODL:L3VPN", intfDpnId, interfaceName);
                    // If the interface state is unknown, it means that the corresponding DPN has gone down.
                    // So remove the dpn-vpninterfaces-list from the neutron-router-dpns model.
                    NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, intfDpnId, writeOperTxn);
                } else if (state.equals(IntfTransitionState.STATE_UP)) {
                    LOG.debug("call : DPN {} connnected to the interface {} has come up. Hence adding"
                            + " the dpn-vpninterfaces-list entry from the neutron-router-dpns model"
                            + " in the ODL:L3VPN", intfDpnId, interfaceName);
                    handleRouterInterfacesUpEvent(routerName, interfaceName, intfDpnId, writeOperTxn);
                }
                futures.add(writeOperTxn.submit());
            } catch (Exception e) {
                LOG.error("call : Exception observed in handling updation of VPN Interface {}.", update.getName(), e);
            }
            return futures;
        }
    }

    private void processInterfaceAdded(String portName, String routerId, List<ListenableFuture<Void>> futures) {
        LOG.trace("processInterfaceAdded : Processing Interface Add Event for interface {}", portName);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList == null || intExtPortMapList.isEmpty()) {
            LOG.debug("processInterfaceAdded : Ip Mapping list is empty/null for portname {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        WriteTransaction installFlowInvTx = dataBroker.newWriteOnlyTransaction();
        for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
            floatingIPListener.createNATFlowEntries(portName, intExtPortMap, portIid, routerId, installFlowInvTx);
        }
        //final submit call for installFlowInvTx
        futures.add(NatUtil.waitForTransactionToComplete(installFlowInvTx));
    }

    private List<InternalToExternalPortMap> getIntExtPortMapListForPortName(String portName, String routerId) {
        InstanceIdentifier<Ports> portToIpMapIdentifier = NatUtil.buildPortToIpMapIdentifier(routerId, portName);
        Optional<Ports> port =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, portToIpMapIdentifier);
        if (!port.isPresent()) {
            LOG.info("getIntExtPortMapListForPortName : Unable to read router port entry for router ID {} "
                    + "and port name {}", routerId, portName);
            return null;
        }
        return port.get().getInternalToExternalPortMap();
    }

    private BigInteger getNaptSwitchforRouter(DataBroker broker, String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> rtrNaptSw = InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
        Optional<RouterToNaptSwitch> routerToNaptSwitchData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, rtrNaptSw);
        if (routerToNaptSwitchData.isPresent()) {
            RouterToNaptSwitch routerToNaptSwitchInstance = routerToNaptSwitchData.get();
            return routerToNaptSwitchInstance.getPrimarySwitchId();
        }
        return null;
    }

    private void removeNatFlow(BigInteger dpnId, short tableId, Long routerId, String ipAddress, int ipPort) {

        String switchFlowRef = NatUtil.getNaptFlowRef(dpnId, tableId, String.valueOf(routerId), ipAddress, ipPort);
        FlowEntity snatFlowEntity = NatUtil.buildFlowEntity(dpnId, tableId, switchFlowRef);

        mdsalManager.removeFlow(snatFlowEntity);
        LOG.debug("removeNatFlow : Removed the flow in table {} for the switch with the DPN ID {} for "
            + "router {} ip {} port {}", tableId, dpnId, routerId, ipAddress, ipPort);
    }

    private List<String> getFixedIpsForPort(String interfname) {
        LOG.debug("getFixedIpsForPort : getFixedIpsForPort method is called for interface {}", interfname);
        try {
            Future<RpcResult<GetFixedIPsForNeutronPortOutput>> result =
                neutronVpnService.getFixedIPsForNeutronPort(new GetFixedIPsForNeutronPortInputBuilder()
                    .setPortId(new Uuid(interfname)).build());

            RpcResult<GetFixedIPsForNeutronPortOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("getFixedIpsForPort : RPC Call to GetFixedIPsForNeutronPortOutput returned with Errors {}",
                    rpcResult.getErrors());
            } else {
                return rpcResult.getResult().getFixedIPs();
            }
        } catch (InterruptedException | ExecutionException | NullPointerException ex) {
            LOG.error("getFixedIpsForPort : Exception while receiving fixedIps for port {}", interfname, ex);
        }
        return null;
    }

    private void processInterfaceRemoved(String portName, BigInteger dpnId, String routerId,
            List<ListenableFuture<Void>> futures) {
        LOG.trace("processInterfaceRemoved : Processing Interface Removed Event for interface {} on DPN ID {}",
                portName, dpnId);
        List<InternalToExternalPortMap> intExtPortMapList = getIntExtPortMapListForPortName(portName, routerId);
        if (intExtPortMapList == null || intExtPortMapList.isEmpty()) {
            LOG.debug("processInterfaceRemoved : Ip Mapping list is empty/null for portName {}", portName);
            return;
        }
        InstanceIdentifier<RouterPorts> portIid = NatUtil.buildRouterPortsIdentifier(routerId);
        WriteTransaction removeFlowInvTx = dataBroker.newWriteOnlyTransaction();
        for (InternalToExternalPortMap intExtPortMap : intExtPortMapList) {
            LOG.trace("processInterfaceRemoved : Removing DNAT Flow entries for dpnId {} ", dpnId);
            floatingIPListener.removeNATFlowEntries(portName, intExtPortMap, portIid, routerId, dpnId, removeFlowInvTx);
        }
        // final submit call for removeFlowInvTx
        futures.add(NatUtil.waitForTransactionToComplete(removeFlowInvTx));
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void removeSnatEntriesForPort(String interfaceName, String routerName) {
        Long routerId = NatUtil.getVpnId(dataBroker, routerName);
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("removeSnatEntriesForPort : routerId not found for routername {}", routerName);
            return;
        }
        BigInteger naptSwitch = getNaptSwitchforRouter(dataBroker, routerName);
        if (naptSwitch == null || naptSwitch.equals(BigInteger.ZERO)) {
            LOG.error("removeSnatEntriesForPort : NaptSwitch is not elected for router {} with Id {}",
                    routerName, routerId);
            return;
        }
        //getInternalIp for port
        List<String> fixedIps = getFixedIpsForPort(interfaceName);
        if (fixedIps == null) {
            LOG.warn("removeSnatEntriesForPort : Internal Ips not found for InterfaceName {} in router {} with id {}",
                interfaceName, routerName, routerId);
            return;
        }

        for (String internalIp : fixedIps) {
            LOG.debug("removeSnatEntriesForPort : Internal Ip retrieved for interface {} is {} in router with Id {}",
                interfaceName, internalIp, routerId);
            IpPort ipPort = NatUtil.getInternalIpPortInfo(dataBroker, routerId, internalIp);
            if (ipPort == null) {
                LOG.debug("removeSnatEntriesForPort : no snatint-ip-port-map found for ip:{}", internalIp);
                continue;
            }

            for (IntIpProtoType protoType: ipPort.getIntIpProtoType()) {
                ProtocolTypes protocol = protoType.getProtocol();
                for (Integer portnum : protoType.getPorts()) {
                    //build and remove the flow in outbound table
                    try {
                        removeNatFlow(naptSwitch, NwConstants.OUTBOUND_NAPT_TABLE, routerId, internalIp, portnum);
                    } catch (Exception ex) {
                        LOG.error("removeSnatEntriesForPort : Failed to remove snat flow for internalIP {} with "
                                + "Port {} protocol {} for routerId {} in OUTBOUNDTABLE of NaptSwitch {}",
                            internalIp, portnum, protocol, routerId, naptSwitch, ex);
                    }
                    //Get the external IP address and the port from the model
                    NAPTEntryEvent.Protocol proto = protocol.toString().equals(ProtocolTypes.TCP.toString())
                        ? NAPTEntryEvent.Protocol.TCP : NAPTEntryEvent.Protocol.UDP;
                    IpPortExternal ipPortExternal = NatUtil.getExternalIpPortMap(dataBroker, routerId,
                        internalIp, String.valueOf(portnum), proto);
                    if (ipPortExternal == null) {
                        LOG.error("removeSnatEntriesForPort : Mapping for internalIp {} with port {} is not found in "
                            + "router with Id {}", internalIp, portnum, routerId);
                        return;
                    }
                    String externalIpAddress = ipPortExternal.getIpAddress();
                    Integer portNumber = ipPortExternal.getPortNum();

                    //build and remove the flow in inboundtable
                    try {
                        removeNatFlow(naptSwitch, NwConstants.INBOUND_NAPT_TABLE, routerId,
                            externalIpAddress, portNumber);
                    } catch (Exception ex) {
                        LOG.error("removeSnatEntriesForPort : Failed to remove snat flow internalIP {} with "
                                + "Port {} protocol {} for routerId {} in INBOUNDTABLE of naptSwitch {}",
                            externalIpAddress, portNumber, protocol, routerId, naptSwitch, ex);
                    }

                    String internalIpPort = internalIp + ":" + portnum;
                    // delete the entry from IntExtIpPortMap DS
                    try {
                        naptManager.removeFromIpPortMapDS(routerId, internalIpPort, proto);
                        naptManager.removePortFromPool(internalIpPort, externalIpAddress);
                    } catch (Exception ex) {
                        LOG.error("removeSnatEntriesForPort : releaseIpExtPortMapping failed, Removal of "
                            + "ipportmap {} for router {} failed", internalIpPort, routerId, ex);
                    }
                }
            }
            // delete the entry from SnatIntIpPortMap DS
            LOG.debug("removeSnatEntriesForPort : Removing InternalIp:{} on router {}", internalIp, routerId);
            naptManager.removeFromSnatIpPortDS(routerId, internalIp);
        }
    }

    private class NatFlowAddWorker implements Callable<List<ListenableFuture<Void>>> {
        private String interfaceName;
        private String routerName;

        NatFlowAddWorker(String interfaceName,String routerName) {
            this.interfaceName = interfaceName;
            this.routerName = routerName;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            LOG.trace("call : Interface {} up event received", interfaceName);
            try {
                LOG.trace("call : Port added event received for interface {} ", interfaceName);
                processInterfaceAdded(interfaceName, routerName, futures);
            } catch (Exception ex) {
                LOG.error("call : Exception caught in Interface {} Operational State Up event",
                        interfaceName, ex);
            }
            return futures;
        }
    }

    private class NatFlowUpdateWorker implements Callable<List<ListenableFuture<Void>>> {
        private Interface original;
        private Interface update;
        private String routerName;

        NatFlowUpdateWorker(Interface original, Interface update, String routerName) {
            this.original = original;
            this.update = update;
            this.routerName = routerName;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            String interfaceName = update.getName();
            IntfTransitionState state = getTransitionState(original.getOperStatus(), update.getOperStatus());
            if (state.equals(IntfTransitionState.STATE_IGNORE)) {
                LOG.info("NAT Service: Interface {} state original {} updated {} not handled",
                        interfaceName, original.getOperStatus(), update.getOperStatus());
                return futures;
            }
            if (state.equals(IntfTransitionState.STATE_UP)) {
                LOG.debug("call : Port UP event received for interface {} ", interfaceName);
            } else if (state.equals(IntfTransitionState.STATE_DOWN)) {
                LOG.debug("call : Port DOWN event received for interface {} ", interfaceName);
                try {
                    removeSnatEntriesForPort(interfaceName, routerName);
                } catch (Exception ex) {
                    LOG.error("call : Exception caught in Interface {} OperationalStateDown", interfaceName, ex);
                }
            }
            return futures;
        }
    }

    private class NatFlowRemoveWorker implements Callable<List<ListenableFuture<Void>>> {
        private Interface delintrf;
        private String routerName;
        private BigInteger intfDpnId;

        NatFlowRemoveWorker(Interface delintrf, BigInteger intfDpnId, String routerName) {
            this.delintrf = delintrf;
            this.routerName = routerName;
            this.intfDpnId = intfDpnId;
        }

        @Override
        @SuppressWarnings("checkstyle:IllegalCatch")
        public List<ListenableFuture<Void>> call() {
            final List<ListenableFuture<Void>> futures = new ArrayList<>();
            final String interfaceName = delintrf.getName();
            LOG.trace("call : Interface {} removed event received", delintrf);
            try {
                LOG.trace("call : Port removed event received for interface {} ", interfaceName);
                processInterfaceRemoved(interfaceName, intfDpnId, routerName, futures);
                removeSnatEntriesForPort(interfaceName, routerName);
            } catch (Exception e) {
                LOG.error("call : Exception caught in Interface {} OperationalStateRemove", interfaceName, e);
            }
            return futures;
        }
    }
}
