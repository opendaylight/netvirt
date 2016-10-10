/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.binding.api.*;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfL2vlanBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAclBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.interfaces._interface.AllowedAddressPairs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortAddedToSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.PortRemovedFromSubnetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.qos.ext.rev160613.QosPortExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.util.logging.resources.logging;

public class NeutronPortChangeListener extends AsyncDataTreeChangeListenerBase<Port, NeutronPortChangeListener>
        implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronPortChangeListener.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager nvpnManager;
    private final NeutronvpnNatManager nvpnNatManager;
    private final LockManagerService lockManager;
    private final NotificationPublishService notificationPublishService;
    private final NeutronSubnetGwMacResolver gwMacResolver;
    private OdlInterfaceRpcService odlInterfaceRpcService;
    private final IElanService elanService;

    public NeutronPortChangeListener(final DataBroker dataBroker,
                                     final NeutronvpnManager nVpnMgr, final NeutronvpnNatManager nVpnNatMgr,
                                     final NotificationPublishService notiPublishService,
                                     final LockManagerService lockManager, NeutronSubnetGwMacResolver gwMacResolver,
                                     final OdlInterfaceRpcService odlInterfaceRpcService,
                                     final IElanService elanService) {
        super(Port.class, NeutronPortChangeListener.class);
        this.dataBroker = dataBroker;
        nvpnManager = nVpnMgr;
        nvpnNatManager = nVpnNatMgr;
        notificationPublishService = notiPublishService;
        this.lockManager = lockManager;
        this.gwMacResolver = gwMacResolver;
        this.odlInterfaceRpcService = odlInterfaceRpcService;
        this.elanService = elanService;
    }


    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Port> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Ports.class).child(Port.class);
    }

    @Override
    protected NeutronPortChangeListener getDataTreeChangeListener() {
        return NeutronPortChangeListener.this;
    }


    @Override
    protected void add(InstanceIdentifier<Port> identifier, final Port input) {
        LOG.trace("Adding Port : key: {}, value={}", identifier, input);
        final Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} "
                    + "which is part of network {}.", input.getName(), network);
            return;
        }
        NeutronvpnUtils.addToPortCache(input);

        /* check if router interface has been created */
        if ((input.getDeviceOwner() != null) && (input.getDeviceId() != null)) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(input);
                /* nothing else to do here */
                return;
            }
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())) {
                handleRouterGatewayUpdated(input);
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                elanService.handleKnownL3DmacAddress(input.getMacAddress().getValue(), input.getNetworkId().getValue(),
                        NwConstants.ADD_FLOW);
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            final DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("NPORT" + input.getKey().toString(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                            //TODO: Below reads can be optimized using ReadOnlyTransaction
                            Uuid subnetId = input.getFixedIps().get(0).getSubnetId();
                            InstanceIdentifier<Subnetmap> SubnetMapid = InstanceIdentifier.builder(Subnetmaps.class)
                                    .child(Subnetmap.class, new SubnetmapKey(subnetId))
                                    .build();
                            SubnetmapBuilder builder = null;
                            Uuid vpnId = null;
                            Router rtr = null;
                            Uuid routerId = null;
                            String elanInstanceName = null;
                            long etag = 0l;
                            try {
                                Optional<Subnetmap> sn = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, SubnetMapid);
                                if (sn.isPresent()) {
                                    builder = new SubnetmapBuilder(sn.get());
                                    vpnId = builder.getVpnId();
                                    LOG.debug("updating Subnet :existing: ");
                                    elanInstanceName = sn.get().getNetworkId().getValue();
                                } else {
                                    builder = new SubnetmapBuilder().setKey(new SubnetmapKey(subnetId)).setId(subnetId);
                                    LOG.debug("updating Subnet :new: ");
                                }

                            } catch (Exception e) {
                                LOG.error("Updation of subnetMap failed for node: {}", subnetId.getValue());
                            }
                            if (vpnId != null) {
                                // create vpn-interface on this neutron port
                                LOG.debug("Adding VPN Interface");
                                routerId = NeutronvpnUtils.getVpnMap(dataBroker, vpnId).getRouterId();
                                if (routerId != null) {
                                    rtr = NeutronvpnUtils.getNeutronRouter(dataBroker, routerId);
                                }
                            }
                            InstanceIdentifier<RouterInterfaces> routerInterfacesId = nvpnManager.getRouterInterfacesId(routerId);
                            Optional<RouterInterfaces> optRouterInterfaces = null;
                            RouterInterfaces routerInterfaces = null;
                            try {
                                optRouterInterfaces = NeutronvpnUtils.read(dataBroker,
                                        LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
                                routerInterfaces = optRouterInterfaces.isPresent() ?
                                        optRouterInterfaces.get() : null;

                            } catch (Exception ex) {
                                LOG.error("Failed to read Rouoter interface {} from routerID: {}  exception: {}",
                                        routerInterfacesId, routerId, ex);
                            }
                            final RouterInterfaces routerInterfacesF = routerInterfaces;

                            InstanceIdentifier<ElanInstance> elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                                    .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
                            Optional<ElanInstance> elanInstance = null;
                            try {
                                elanInstance = NeutronvpnUtils.read(dataBroker,
                                        LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
                                etag = elanInstance.isPresent() ? elanInstance.get().getElanTag() : null;
                            } catch (Exception ex) {
                                LOG.error("Failed to read elan instance {} exception: {}",
                                        elanInstanceName, ex);
                            }

                            final long elanTag = etag;
                            final SubnetmapBuilder sNetMapBuilder = builder;
                            final Uuid Uuidrouter = routerId;
                            final Router rtrFinal = rtr;
                            final Interface inf = createInterface(input);
                            final String infName = inf.getName();
                            InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
                            try {
                                Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                        interfaceIdentifier);
                                if (optionalInf.isPresent()) {
                                    LOG.error("Interface {} is already present", infName);
                                    return null;
                                }
                            } catch (Exception e) {
                                LOG.error("failed to create interface {} due to the exception {} ", infName, e.getMessage());
                            }

                            final Uuid vpnIdFinal = vpnId;
                            dataStoreCoordinator.enqueueJob("NPORT" + input.getName() + input.getUuid().toString(),
                                    new Callable<List<ListenableFuture<Void>>>() {
                                        WriteTransaction wrTx = dataBroker.newWriteOnlyTransaction();
                                        List<ListenableFuture<Void>> futures = new ArrayList<>();
                                        String elanInstanceName = input.getNetworkId().getValue();

                                        @Override
                                        public List<ListenableFuture<Void>> call() throws Exception {
                                            boolean isLockAcquired = false;
                                            try {
                                                if(NeutronvpnUtils.lock(lockManager, input.getUuid().getValue())) {
                                                    if (NeutronvpnUtils.lock(lockManager,
                                                            input.getFixedIps().get(0).getSubnetId().getValue())) {
                                                        isLockAcquired = true;
                                                    } else {
                                                        LOG.error("Unable to acquire lock for subnet ID {}, belongs to port: {}",
                                                                input.getFixedIps().get(0).getSubnetId().getValue(),
                                                                input.getUuid().getValue());
                                                        NeutronvpnUtils.unlock(lockManager, input.getUuid().getValue());
                                                    }
                                                } else {
                                                    LOG.error("Unable to acquire for port: {}", input.getUuid().getValue());
                                                }
                                                LOG.debug("Adding vpn interface {}", infName);
                                                handleNeutronPortCreated(input, elanTag, sNetMapBuilder, Uuidrouter,
                                                        rtrFinal, inf, vpnIdFinal, routerInterfacesF, wrTx);
                                                NeutronvpnUtils.addToPortCache(input);
                                            } catch (Exception ex) {
                                                LOG.error("Creation of vpninterface {} failed due to {}", infName, ex);
                                            } finally {
                                                if (isLockAcquired) {
                                                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                                                    futures.add(wrTx.submit());
                                                    //Unlocking shall be in reverse way of acquiring locks.
                                                    NeutronvpnUtils.unlock(lockManager, input.getFixedIps().get(0).getSubnetId().getValue());
                                                    NeutronvpnUtils.unlock(lockManager, input.getUuid().getValue());
                                                }
                                            }
                                            return futures;
                                        }
                                    });
                            return null;
                        }
                    });
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Port> identifier, Port input) {
        LOG.trace("Removing Port : key: {}, value={}", identifier, input);
        final Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, input.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            //FIXME: This should be removed when support for VLAN and GRE network types is added
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of " +
                    "network {}.", input.getName(), network);
            return;
        }
        NeutronvpnUtils.removeFromPortCache(input);

        if ((input.getDeviceOwner() != null) && (input.getDeviceId() != null)) {
            if (input.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceRemoved(input);
                /* nothing else to do here */
                return;
            } else if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(input.getDeviceOwner())
                    || NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(input.getDeviceOwner())) {
                elanService.handleKnownL3DmacAddress(input.getMacAddress().getValue(), input.getNetworkId().getValue(),
                        NwConstants.DEL_FLOW);
            }
        }
        if (input.getFixedIps() != null && !input.getFixedIps().isEmpty()) {
            final DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
            dataStoreCoordinator.enqueueJob("NPORT"+ input.getKey().toString(),
                    new Callable<List<ListenableFuture<Void>>>() {
                        @Override
                        public List<ListenableFuture<Void>> call() throws Exception {
                        // Preparation (couple of MDSAL-Reads) for Batching...
                        String elanInstanceName = input.getNetworkId().getValue();
                        InstanceIdentifier<ElanInstance>elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
                        Optional<ElanInstance> elanInstance = null;
                        long etag = 0L;
                        try {
                            elanInstance = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
                            etag = elanInstance.isPresent() ? elanInstance.get().getElanTag() : null;

                        } catch (Exception ex) {
                            LOG.error("Failed to read elan instance {} exception: {}",
                                    elanInstanceName, ex);
                        }
                        final long elanTag = etag;

                        SubnetmapBuilder builder = null;
                        Uuid routerId = null;
                        InstanceIdentifier<Subnetmap> SubnetMapid = InstanceIdentifier.builder(Subnetmaps.class)
                                .child(Subnetmap.class, new SubnetmapKey(input.getFixedIps().get(0).getSubnetId()))
                                .build();

                        final Optional<Subnetmap> sn = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, SubnetMapid);
                        if (sn.isPresent()) {
                            builder = new SubnetmapBuilder(sn.get());
                            routerId = builder.getRouterId();
                        } else {
                            LOG.warn("removing from non-existing subnetmap node: {} ", input.getFixedIps().get(0).getSubnetId());
                        }
                        InstanceIdentifier<RouterInterfaces> routerInterfacesId = nvpnManager.getRouterInterfacesId(routerId);
                        Optional<RouterInterfaces> optRouterInterfaces = null;
                        RouterInterfaces routerInterfaces = null;
                        try {
                            optRouterInterfaces = NeutronvpnUtils.read(dataBroker,
                                    LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
                            routerInterfaces = optRouterInterfaces.isPresent() ?
                                    optRouterInterfaces.get() : null;

                        } catch (Exception ex) {
                            LOG.info("Failed to read Rouoter interface {} from routerID: {}  exception: {}",
                                    routerInterfacesId, routerId, ex);
                        }
                        final RouterInterfaces routerInterfacesF = routerInterfaces;
                        final SubnetmapBuilder snbuilder = builder;
                            // Write/PUT operations.
                        dataStoreCoordinator.enqueueJob("NPORT"+ input.getKey().toString(),
                                new Callable<List<ListenableFuture<Void>>>() {
                                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                                    WriteTransaction wrTx = dataBroker.newWriteOnlyTransaction();
                                    @Override
                                    public List<ListenableFuture<Void>> call() throws Exception {
                                        boolean isLockAcquired = false;
                                        try {
                                            if(NeutronvpnUtils.lock(lockManager, input.getUuid().getValue())) {
                                                if (NeutronvpnUtils.lock(lockManager,
                                                        input.getFixedIps().get(0).getSubnetId().getValue())) {
                                                    isLockAcquired = true;
                                                } else {
                                                    LOG.error("Unable to acquire lock for subnet ID {}, belongs to port: {}",
                                                            input.getFixedIps().get(0).getSubnetId().getValue(),
                                                            input.getUuid().getValue());
                                                    NeutronvpnUtils.unlock(lockManager, input.getUuid().getValue());
                                                }
                                            } else {
                                                LOG.error("Unable to acquire for port: {}", input.getUuid().getValue());
                                            }
                                            LOG.debug("Lock acquired on interface name {}", input.getUuid().getValue().toString());
                                            handleNeutronPortDeleted(input, elanTag,  snbuilder, routerInterfacesF, wrTx);
                                            NeutronvpnUtils.removeFromPortCache(input);
                                        } catch (Exception e) {
                                            LOG.error("Received exception during NeutronPortRemove of interface name: {}, exception: {}",
                                                    input.getUuid().getValue().toString(), e);
                                        } finally {
                                            if (isLockAcquired) {
                                                    futures.add(wrTx.submit());
                                                //Unlocking shall be in reverse way of acquiring locks.
                                                NeutronvpnUtils.unlock(lockManager, input.getFixedIps().get(0).getSubnetId().getValue());
                                                NeutronvpnUtils.unlock(lockManager, input.getUuid().getValue());                                            }
                                        }
                                        return futures;
                                    }
                                });
                    return null;
                }
            });
        }
    }

    @Override
    protected void update(InstanceIdentifier<Port> identifier, Port original, Port update) {
        LOG.trace("Updating Port : key: {}, original value={}, update value={}", identifier, original, update);
        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, update.getNetworkId());
        if (network == null || !NeutronvpnUtils.isNetworkTypeSupported(network)) {
            LOG.error("neutron vpn doesn't support vlan/gre network provider type for the port {} which is part of network {}."
                    + " Skipping the processing of Port update DCN", update.getName(), network);
            return;
        }
        /* check if router interface has been updated */
        if ((update.getDeviceOwner() != null) && (update.getDeviceId() != null)) {
            if (update.getDeviceOwner().equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF)) {
                handleRouterInterfaceAdded(update);
                /* nothing else to do here */
                return;
            }

        final List<FixedIps> oldIPs = (original.getFixedIps() != null) ? original.getFixedIps() : new ArrayList<FixedIps>();
        final List<FixedIps> newIPs = (update.getFixedIps() != null) ? update.getFixedIps() : new ArrayList<FixedIps>();

        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        // key here should be based on existing port information, so that earlier operation on port
        // gets executed before update gets executed.
        dataStoreCoordinator.enqueueJob("NPORT"+ original.getKey().toString(),
                new Callable<List<ListenableFuture<Void>>>() {
                    @Override
                    public List<ListenableFuture<Void>> call() throws Exception {

                        // Preparation (couple of MDSAL-Reads) for Batching...
                        String elanInstanceName = update.getNetworkId().getValue();
                        InstanceIdentifier<ElanInstance>elanIdentifierId = InstanceIdentifier.builder(ElanInstances.class)
                                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
                        Optional<ElanInstance> elanInstance = null;
                        long etag = 0L;
                        try {
                            elanInstance = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId);
                            etag = elanInstance.isPresent()?elanInstance.get().getElanTag():null;
                        } catch (Exception ex) {
                            LOG.error("Failed to read elan instance {} exception: {}",
                                    elanInstanceName, ex);
                        }
                        final long elanTag = etag;
                        SubnetmapBuilder builder = null;
                        Uuid routerId = null;
                        Router rtr = null;

                        InstanceIdentifier<Subnetmap> SubnetMapid = InstanceIdentifier.builder(Subnetmaps.class)
                                .child(Subnetmap.class, new SubnetmapKey(update.getFixedIps().get(0).getSubnetId()))
                                .build();

                        final Optional<Subnetmap> sn = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, SubnetMapid);
                        if (sn.isPresent()) {
                            builder = new SubnetmapBuilder(sn.get());
                            routerId = builder.getRouterId();
                            if(routerId!=null){
                                rtr = NeutronvpnUtils.getNeutronRouter(dataBroker, routerId);
                            }

                        } else {
                            LOG.warn("removing from non-existing subnetmap node: {} ", update.getFixedIps().get(0).getSubnetId());
                        }
                        InstanceIdentifier<RouterInterfaces> routerInterfacesId = nvpnManager.getRouterInterfacesId(routerId);
                        Optional<RouterInterfaces> optRouterInterfaces = null;
                        RouterInterfaces routerInterfaces = null;
                        try {
                            optRouterInterfaces = NeutronvpnUtils.read(dataBroker,
                                    LogicalDatastoreType.CONFIGURATION, routerInterfacesId);
                            routerInterfaces = optRouterInterfaces.isPresent() ?
                                    optRouterInterfaces.get() : null;

                        } catch (Exception ex) {
                            LOG.error("Failed to read Rouoter interface {} from routerID: {}  exception: {}",
                                    routerInterfacesId, routerId, ex);
                        }
                        final RouterInterfaces routerInterfacesF = routerInterfaces;
                        final SubnetmapBuilder snbuilder = builder;
                        final Router rtrFinal = rtr;

                        final Interface inf = updateInterface(original, update);
                        final String infName = inf.getName();
                        LOG.debug("Reading OFPort Interface {}", infName);
                        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
                        try {
                            Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                    interfaceIdentifier);
                            if (optionalInf.isPresent()) {
                            } else {
                                LOG.error("Interface {} doesn't exist", infName);
                            }
                        } catch (Exception e) {
                            LOG.error("failed to update interface {} due to the exception {} ", infName, e.getMessage());
                        }
                        DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                        // key here should be based on existing port information, so that earlier operation on port
                        // gets executed before update gets executed.
                        dataStoreCoordinator.enqueueJob("NPORT" + original.getKey().toString(),
                                new Callable<List<ListenableFuture<Void>>>() {
                                    @Override
                                    public List<ListenableFuture<Void>> call() throws Exception {
                                        WriteTransaction wrTx = dataBroker.newWriteOnlyTransaction();
                                        List<ListenableFuture<Void>> futures = new ArrayList<>();

                                        /* check if VIF type updated as part of port binding */
                                        if (NeutronvpnUtils.isPortVifTypeUpdated(original, update)) {
                                            updateOfPortInterface(original, update, wrTx);
                                        }

                                        if (!oldIPs.equals(newIPs)) {
                                            boolean isLockAcquired = false;
                                            try {
                                                if(NeutronvpnUtils.lock(lockManager, update.getUuid().getValue())) {
                                                    if (NeutronvpnUtils.lock(lockManager,
                                                            update.getFixedIps().get(0).getSubnetId().getValue())) {
                                                        isLockAcquired = true;
                                                    } else {
                                                        LOG.error("Unable to acquire lock for subnet ID {}, belongs to port: {}",
                                                                update.getFixedIps().get(0).getSubnetId().getValue(),
                                                                update.getUuid().getValue());
                                                        NeutronvpnUtils.unlock(lockManager, update.getUuid().getValue());
                                                    }
                                                } else {
                                                    LOG.error("Unable to acquire for port: {}", update.getUuid().getValue());
                                                }
                                                LOG.debug("Lock acquired on interface name {}", update.getUuid().getValue());

                                                Iterator<FixedIps> iterator = newIPs.iterator();
                                                while (iterator.hasNext()) {
                                                    FixedIps ip = iterator.next();
                                                    if (oldIPs.remove(ip)) {
                                                        iterator.remove();
                                                    }
                                                }

                                                handleNeutronPortUpdated(original, update, elanTag, snbuilder, rtrFinal,
                                                        inf, routerInterfacesF, wrTx);
                                                NeutronvpnUtils.addToPortCache(update);
                                            } catch (Exception e) {
                                                LOG.error("Received exception on NeutronPort Update interface name: {}, exception: {}",
                                                        update.getUuid().getValue().toString(), e);
                                            } finally {
                                                if (isLockAcquired) {
                                                    futures.add(wrTx.submit());
                                                    NeutronvpnUtils.unlock(lockManager, update.getFixedIps().get(0).getSubnetId().getValue());
                                                    NeutronvpnUtils.unlock(lockManager, update.getUuid().getValue());                                                  }
                                            }
                                        } else {
                                            futures.add(wrTx.submit());
                                        }
                                        return futures;
                                    }
                                });
            return null;
            }
        });
            if (NeutronConstants.DEVICE_OWNER_GATEWAY_INF.equals(update.getDeviceOwner())) {
                handleRouterGatewayUpdated(update);
            } else if (NeutronConstants.DEVICE_OWNER_FLOATING_IP.equals(update.getDeviceOwner())) {
                elanService.handleKnownL3DmacAddress(update.getMacAddress().getValue(), update.getNetworkId().getValue(),
                        NwConstants.ADD_FLOW);
            }
        }

        handlePortSecurityUpdated(original, update);

        // check for QoS updates
        QosPortExtension updateQos = update.getAugmentation(QosPortExtension.class);
        QosPortExtension originalQos = original.getAugmentation(QosPortExtension.class);
        if (originalQos == null && updateQos != null) {
            // qos policy add
            NeutronvpnUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos != null
                && !originalQos.getQosPolicyId().equals(updateQos.getQosPolicyId())) {
            // qos policy update
            NeutronvpnUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
            NeutronvpnUtils.addToQosPortsCache(updateQos.getQosPolicyId(), update);
            NeutronQosUtils.handleNeutronPortQosUpdate(dataBroker, odlInterfaceRpcService,
                    update, updateQos.getQosPolicyId());
        } else if (originalQos != null && updateQos == null) {
            // qos policy delete
            NeutronQosUtils.handleNeutronPortQosRemove(dataBroker, odlInterfaceRpcService,
                    original, originalQos.getQosPolicyId());
            NeutronvpnUtils.removeFromQosPortsCache(originalQos.getQosPolicyId(), original);
        }
    }

    private void handleRouterInterfaceAdded(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();
            Uuid existingVpnId = NeutronvpnUtils.getVpnForNetwork(dataBroker, infNetworkId);

            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.ADD_FLOW);
            if (existingVpnId == null) {
                for (FixedIps portIP : routerPort.getFixedIps()) {
                    if (portIP.getIpAddress().getIpv4Address() != null) {
                        Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                        if (vpnId == null) {
                            vpnId = routerId;
                        }
                        nvpnManager.addSubnetToVpn(vpnId, portIP.getSubnetId());
                        String ipValue = portIP.getIpAddress().getIpv4Address().getValue();
                        nvpnManager.updateSubnetNodeWithFixedIps(portIP.getSubnetId(), routerId,
                                routerPort.getUuid(), ipValue, routerPort.getMacAddress().getValue());
                        nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                        PhysAddress mac = new PhysAddress(routerPort.getMacAddress().getValue());
                        LOG.trace("NeutronPortChangeListener Add Subnet Gateway IP {} MAC {} Interface {} VPN {}",
                                portIP.getIpAddress().getIpv4Address(),routerPort.getMacAddress(),
                                routerPort.getUuid().getValue(), vpnId.getValue());
                        NeutronvpnUtils.createVpnPortFixedIpToPort(dataBroker, vpnId.getValue(), ipValue, routerPort
                                .getUuid().getValue(), routerPort.getMacAddress().getValue(), true, true, false);
                    } else {
                        LOG.info("Skip router port {} with the following address {}",
                                routerPort.getUuid().getValue(), portIP.getIpAddress().getIpv6Address());
                    }
                }
            } else {
                LOG.error("Neutron network {} corresponding to router interface port {} for neutron router {} already" +
                        " associated to VPN {}", infNetworkId.getValue(), routerPort.getUuid().getValue(), routerId
                        .getValue(), existingVpnId.getValue());
            }
        }
    }

    private void handleRouterInterfaceRemoved(Port routerPort) {
        if (routerPort.getDeviceId() != null) {
            Uuid routerId = new Uuid(routerPort.getDeviceId());
            Uuid infNetworkId = routerPort.getNetworkId();

            elanService.handleKnownL3DmacAddress(routerPort.getMacAddress().getValue(), infNetworkId.getValue(),
                    NwConstants.DEL_FLOW);
            for (FixedIps portIP : routerPort.getFixedIps()) {
                if (portIP.getIpAddress().getIpv4Address() != null) {
                    Uuid vpnId = NeutronvpnUtils.getVpnForRouter(dataBroker, routerId, true);
                    if(vpnId == null) {
                        vpnId = routerId;
                    }
                    nvpnManager.removeSubnetFromVpn(vpnId, portIP.getSubnetId());
                    nvpnManager.updateSubnetNodeWithFixedIps(portIP.getSubnetId(), null,
                            null, null, null);
                    nvpnNatManager.handleSubnetsForExternalRouter(routerId, dataBroker);
                    String ipValue = portIP.getIpAddress().getIpv4Address().getValue();
                    NeutronvpnUtils.removeVpnPortFixedIpToPort(dataBroker, vpnId.getValue(), ipValue);
                } else {
                    LOG.info("Skip router port {} with the following address {}",
                            routerPort.getUuid().getValue(), portIP.getIpAddress().getIpv6Address());
                }
            }
        }
    }

    private void handleRouterGatewayUpdated(Port routerGwPort) {
        Uuid routerId = new Uuid(routerGwPort.getDeviceId());
        Uuid networkId = routerGwPort.getNetworkId();
        elanService.handleKnownL3DmacAddress(routerGwPort.getMacAddress().getValue(), networkId.getValue(),
                NwConstants.ADD_FLOW);

        Router router = NeutronvpnUtils.getNeutronRouter(dataBroker, routerId);
        if (router == null) {
            LOG.warn("No router found for router GW port {} router id {}", routerGwPort.getUuid(), routerId.getValue());
            return;
        }

        gwMacResolver.sendArpRequestsToExtGateways(router);
    }

    private Long getVpnIdFromUuid(Uuid vpnId) {
        long vpn = 1;
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstanceToVpnId.class).
                child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue())).build();
        try {
            Optional<VpnInstance> optional = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    id);
            if (optional.isPresent()) {
                vpn = optional.get().getVpnId();
            }
        } catch (Exception e) {
            LOG.error("Failed to retrieve vpn instance for the Subnet .", e.getMessage());
        }
        return vpn;
    }

    private void handleNeutronPortCreated(final Port port, final long elantag, final SubnetmapBuilder snetMapBuilder,
                                          final Uuid routerId, final Router rtr, final Interface inf, final Uuid vpnId,
                                          final RouterInterfaces routerInterfaces, final WriteTransaction wrTx) {

        if (!NeutronUtils.isPortVnicTypeNormal(port)) {
            nvpnManager.updateSubnetmapNodeWithPorts(port.getFixedIps().get(0).getSubnetId(), null, port.getUuid(),
                    snetMapBuilder, wrTx);
            LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created",
                    port.getUuid().getValue());
            return;
        }
        LOG.info("Of-port-interface creation");
        LOG.error("port is getting created using DJC");
        // Create of-port interface for this neutron port
        String portInterfaceName = createOfPortInterface(port, inf, wrTx);
        LOG.debug("Creating ELAN Interface");
        createElanInterface(port, portInterfaceName, wrTx);
        LOG.debug("Add port to subnet");
        // add port to local Subnets DS
        addPortToSubnets(port, elantag, snetMapBuilder, vpnId, wrTx);

        if (vpnId != null) {
            // create vpn-interface on this neutron port
            LOG.debug("Adding VPN Interface");
            nvpnManager.createVpnInterface(vpnId, port, rtr, wrTx);
            if(routerId != null) {
                nvpnManager.addToNeutronRouterInterfacesMap(routerId, port.getUuid().getValue(), routerInterfaces, wrTx);
            }
        }
    }

    private void handleNeutronPortDeleted(Port port, long elanTag, SubnetmapBuilder snb, RouterInterfaces routerInterfaces, WriteTransaction wrTx ) {
        if (!NeutronUtils.isPortVnicTypeNormal(port)) {
            nvpnManager.removePortsFromSubnetmapNode(port.getFixedIps().get(0).getSubnetId(), null, port.getUuid(),
                    snb, wrTx);
            LOG.info("Port {} is not a NORMAL VNIC Type port; OF Port interfaces are not created",
                    port.getUuid().getValue());
            return;
        }
        //dissociate fixedIP from floatingIP if associated
        nvpnManager.dissociatefixedIPFromFloatingIP(port.getUuid().getValue());
        LOG.debug("Remove port from subnet");
        // remove port from local Subnets DS
        Uuid vpnId = removePortFromSubnets(port, elanTag, snb, wrTx);

        if (vpnId != null) {
            // remove vpn-interface for this neutron port
            LOG.debug("removing VPN Interface");
            nvpnManager.deleteVpnInterface(vpnId, port, wrTx);
        }
        // remove port from local Subnets DS
        removePortFromSubnets(port, elanTag, snb, wrTx);
        // Remove of-port interface for this neutron port
        // ELAN interface is also implicitly deleted as part of this operation
        LOG.debug("Of-port-interface removal");
        deleteOfPortInterface(port, wrTx);
        if (snb.getVpnId() != null) {
            // remove vpn-interface for this neutron port
            LOG.debug("removing VPN Interface");
            nvpnManager.deleteVpnInterface(vpnId, port, wrTx);
            Uuid routerId = NeutronvpnUtils.getVpnMap(dataBroker, vpnId).getRouterId();
            if (routerId != null) {
                nvpnManager.removeFromNeutronRouterInterfacesMap(routerId, port.getUuid().getValue(),  routerInterfaces, wrTx);
            }
        }
    }

    private void handleNeutronPortUpdated(Port portoriginal, Port portupdate,
                                          long elantag, SubnetmapBuilder snbuilder, Router rtr, Interface intf,
                                          RouterInterfaces routerInterfaces, WriteTransaction wrTx) {
        if (portoriginal.getFixedIps() == null || portoriginal.getFixedIps().isEmpty()) {
            handleNeutronPortCreated(portupdate, elantag, snbuilder, snbuilder.getRouterId(), rtr, intf,
                    snbuilder.getVpnId(), routerInterfaces, wrTx);
            return;
        }
        LOG.debug("Add port to subnet");
        // add port FixedIP to local Subnets DS
        Uuid vpnIdup = addPortToSubnets(portupdate, elantag, snbuilder, snbuilder.getVpnId(), wrTx);

        if (vpnIdup != null) {
            nvpnManager.createVpnInterface(vpnIdup, portupdate, rtr, wrTx);
            Uuid routerId = NeutronvpnUtils.getVpnMap(dataBroker, vpnIdup).getRouterId();
            if(routerId != null) {
                nvpnManager.addToNeutronRouterInterfacesMap(routerId, portupdate.getUuid().getValue(), routerInterfaces, wrTx);
            }
        }

        // remove port FixedIP from local Subnets DS
        Uuid vpnIdor = removePortFromSubnets(portoriginal, elantag, snbuilder, wrTx);

        if (vpnIdor != null) {
            nvpnManager.deleteVpnInterface(vpnIdor, portoriginal, wrTx);
            Uuid routerId = NeutronvpnUtils.getVpnMap(dataBroker, vpnIdor).getRouterId();
            if(routerId != null) {
                nvpnManager.removeFromNeutronRouterInterfacesMap(routerId, portoriginal.getUuid().getValue(),
                        routerInterfaces, wrTx);
            }
        if(rtr != null) {
                nvpnManager.removeFromNeutronRouterInterfacesMap(snbuilder.getRouterId(), portoriginal.getUuid().getValue(),
                        routerInterfaces, wrTx);
        }
    }
}
    private void handlePortSecurityUpdated(Port portOriginal, Port portUpdated) {
        Boolean origSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(portOriginal);
        Boolean updatedSecurityEnabled = NeutronvpnUtils.getPortSecurityEnabled(portUpdated);
        String interfaceName = portUpdated.getUuid().getValue();
        Interface portInterface = NeutronvpnUtils.getOfPortInterface(dataBroker, portUpdated);
        if (portInterface != null) {
            InterfaceAclBuilder interfaceAclBuilder = null;
            if (origSecurityEnabled != updatedSecurityEnabled) {
                interfaceAclBuilder = new InterfaceAclBuilder();
                interfaceAclBuilder.setPortSecurityEnabled(updatedSecurityEnabled);
                if (updatedSecurityEnabled) {
                    // Handle security group enabled
                    NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, portUpdated);
                } else {
                    // Handle security group disabled
                    interfaceAclBuilder.setSecurityGroups(Lists.newArrayList());
                    interfaceAclBuilder.setAllowedAddressPairs(Lists.newArrayList());
                }
            } else {
                if (updatedSecurityEnabled) {
                    // handle SG add/delete delta
                    InterfaceAcl interfaceAcl = portInterface.getAugmentation(InterfaceAcl.class);
                    interfaceAclBuilder = new InterfaceAclBuilder(interfaceAcl);
                    interfaceAclBuilder.setSecurityGroups(
                            NeutronvpnUtils.getUpdatedSecurityGroups(interfaceAcl.getSecurityGroups(),
                                    portOriginal.getSecurityGroups(), portUpdated.getSecurityGroups()));
                    List<AllowedAddressPairs> updatedAddressPairs = NeutronvpnUtils.getUpdatedAllowedAddressPairs(
                            interfaceAcl.getAllowedAddressPairs(), portOriginal.getAllowedAddressPairs(),
                            portUpdated.getAllowedAddressPairs());
                    interfaceAclBuilder.setAllowedAddressPairs(NeutronvpnUtils.getAllowedAddressPairsForFixedIps(
                            updatedAddressPairs, portOriginal.getMacAddress(), portOriginal.getFixedIps(),
                            portUpdated.getFixedIps()));
                }
            }

            if (interfaceAclBuilder != null) {
                InterfaceBuilder builder = new InterfaceBuilder(portInterface).addAugmentation(InterfaceAcl.class,
                        interfaceAclBuilder.build());
                InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(interfaceName);
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, builder.build());
            }
        } else {
            LOG.error("Interface {} is not present", interfaceName);
        }
    }

    private String createOfPortInterface(Port port, Interface inf, WriteTransaction wrTx) {
        String infName = inf.getName();

        LOG.debug("Creating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        if (wrTx == null) {
            try {
                Optional<Interface> optionalInf = NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        interfaceIdentifier);
                if (!optionalInf.isPresent()) {
                    MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf);
                } else {
                    LOG.error("Interface {} is already present", infName);
                }
            } catch (Exception e) {
                LOG.error("failed to create interface {} due to the exception {} ", infName, e.getMessage());
            }
        } else {
            wrTx.put(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf, true);
        }
        return infName;
    }

    private Interface createInterface(Port port) {
        String parentRefName = NeutronvpnUtils.getVifPortName(port);
        String interfaceName = port.getUuid().getValue();
        IfL2vlan.L2vlanMode l2VlanMode = IfL2vlan.L2vlanMode.Trunk;
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();
        IfL2vlanBuilder ifL2vlanBuilder = new IfL2vlanBuilder();

        Network network = NeutronvpnUtils.getNeutronNetwork(dataBroker, port.getNetworkId());
        ifL2vlanBuilder.setL2vlanMode(l2VlanMode);

        if(parentRefName != null) {
            ParentRefsBuilder parentRefsBuilder = new ParentRefsBuilder().setParentInterface(parentRefName);
            interfaceBuilder.addAugmentation(ParentRefs.class, parentRefsBuilder.build());
        }

        interfaceBuilder.setEnabled(true).setName(interfaceName).setType(L2vlan.class)
                .addAugmentation(IfL2vlan.class, ifL2vlanBuilder.build());

        if (NeutronvpnUtils.isPortSecurityEnabled(port)) {
            InterfaceAclBuilder interfaceAclBuilder = new InterfaceAclBuilder();
            interfaceAclBuilder.setPortSecurityEnabled(true);
            NeutronvpnUtils.populateInterfaceAclBuilder(interfaceAclBuilder, port);
            interfaceBuilder.addAugmentation(InterfaceAcl.class, interfaceAclBuilder.build());
        }
        return interfaceBuilder.build();
    }

    private void deleteOfPortInterface(Port port, WriteTransaction wrTx) {
        String name = port.getUuid().getValue();
        LOG.debug("Removing OFPort Interface {}", name);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(name);
        //MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
        // If interface exists, will delete. No further check is required (TODO) ?
        wrTx.delete(LogicalDatastoreType.CONFIGURATION, interfaceIdentifier);
    }

    private Interface updateInterface(Port original, Port update) {
        String parentRefName = NeutronvpnUtils.getVifPortName(update);
        String interfaceName = original.getUuid().getValue();
        InterfaceBuilder interfaceBuilder = new InterfaceBuilder();

        if(parentRefName != null) {
            ParentRefsBuilder parentRefsBuilder = new ParentRefsBuilder().setParentInterface(parentRefName);
            interfaceBuilder.addAugmentation(ParentRefs.class, parentRefsBuilder.build());
        }

        interfaceBuilder.setName(interfaceName);
        return interfaceBuilder.build();
    }

    private String updateOfPortInterface(Port original, Port updated, WriteTransaction wrTx) {
        Interface inf = updateInterface(original, updated);
        String infName = inf.getName();

        LOG.debug("Updating OFPort Interface {}", infName);
        InstanceIdentifier interfaceIdentifier = NeutronvpnUtils.buildVlanInterfaceIdentifier(infName);
        wrTx.merge( LogicalDatastoreType.CONFIGURATION, interfaceIdentifier, inf, true);
        return infName;
    }

    private void createElanInterface(Port port, String name, WriteTransaction tx) {
        String elanInstanceName = port.getNetworkId().getValue();
        List<PhysAddress> physAddresses = new ArrayList<>();
        physAddresses.add(new PhysAddress(port.getMacAddress().getValue()));

        InstanceIdentifier<ElanInterface> id = InstanceIdentifier.builder(ElanInterfaces.class).child(ElanInterface
                .class, new ElanInterfaceKey(name)).build();
        ElanInterface elanInterface = new ElanInterfaceBuilder().setElanInstanceName(elanInstanceName)
                .setName(name).setStaticMacEntries(physAddresses).setKey(new ElanInterfaceKey(name)).build();
        if (tx == null) {
            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, elanInterface);
        } else {
            tx.put(LogicalDatastoreType.CONFIGURATION, id, elanInterface, true);
        }
        LOG.debug("Creating new ELan Interface {}", elanInterface);
    }

    // adds port to subnet list and creates vpnInterface
    private Uuid addPortToSubnets(Port port, long elanTag, SubnetmapBuilder snetMapBuilder, Uuid vpnId, WriteTransaction wrTx) {

        Uuid subnetId = null;
        String infName = port.getUuid().getValue();
        Subnetmap subnetmap = null;
        // find the subnet to which this port is associated
        FixedIps ip = port.getFixedIps().get(0);
        String ipValue = ip.getIpAddress().getIpv4Address().getValue();
            subnetId = ip.getSubnetId();
        subnetmap = nvpnManager.updateSubnetmapNodeWithPorts(subnetId, port.getUuid(), null, snetMapBuilder, wrTx);
        if (subnetmap != null) {
            vpnId = subnetmap.getVpnId();
        }
        if(vpnId != null) {
            checkAndPublishPortAddNotification(subnetmap.getSubnetIp(), subnetId, port.getUuid(), elanTag);
            LOG.debug("Port added to subnet notification sent");
        }
        return vpnId;
    }

    private Uuid removePortFromSubnets(Port port, long elanTag, SubnetmapBuilder snbuilder, WriteTransaction wrTx) {
        Uuid subnetId = null;
        Uuid vpnId = null;
        Subnetmap subnetmap = null;
        // find the subnet to which this port is associated
        FixedIps ip = port.getFixedIps().get(0);
        String ipValue = ip.getIpAddress().getIpv4Address().getValue();
        LOG.debug("fixedIp-name map for neutron port with fixedIp: {} deleted from NeutronPortData DS", ipValue);
        subnetId = ip.getSubnetId();
        subnetmap = nvpnManager.removePortsFromSubnetmapNode(subnetId, port.getUuid(), null, snbuilder, wrTx);
        if (subnetmap != null) {
            vpnId = subnetmap.getVpnId();
        }
        if(vpnId != null) {
            try {
                checkAndPublishPortRemoveNotification(subnetmap.getSubnetIp(), subnetId, port.getUuid(), elanTag);
            } catch (Exception e) {
                LOG.error("Exception occurred during port {} remove notification :{}", port.getUuid().getValue(), e);
            }
        }
        return vpnId;
    }

    private void checkAndPublishPortAddNotification(String subnetIp, Uuid subnetId, Uuid portId, Long elanTag){
        PortAddedToSubnetBuilder builder = new PortAddedToSubnetBuilder();
        LOG.info("publish notification called");

        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);
        builder.setPortId(portId);
        builder.setElanTag(elanTag);

        try {
            notificationPublishService.putNotification(builder.build());
        } catch (Exception ex) {
            LOG.error("Exception while publishing PORT-ADD notification subnetIP: {}: subnetID: {} Port: {} elanTag: {} ex: {}",
                    subnetIp, subnetId, portId, elanTag, ex);
        }
    }

    private void checkAndPublishPortRemoveNotification(String subnetIp, Uuid subnetId, Uuid portId, Long elanTag)
            throws InterruptedException {
        PortRemovedFromSubnetBuilder builder = new PortRemovedFromSubnetBuilder();

        LOG.info("publish notification called");

        builder.setPortId(portId);
        builder.setSubnetIp(subnetIp);
        builder.setSubnetId(subnetId);
        builder.setElanTag(elanTag);

        notificationPublishService.putNotification(builder.build());
    }

}
