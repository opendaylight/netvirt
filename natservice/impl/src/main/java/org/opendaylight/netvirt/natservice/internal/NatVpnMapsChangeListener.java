/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.Optional;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.InternalToExternalPortMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIds;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NatVpnMapsChangeListener extends AsyncDataTreeChangeListenerBase<VpnMap, NatVpnMapsChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(NatVpnMapsChangeListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final FloatingIPListener floatingIpListener;
    private final OdlInterfaceRpcService interfaceManager;
    private final ExternalRoutersListener externalRoutersListener;

    @Inject
    public NatVpnMapsChangeListener(final DataBroker dataBroker,
                               final FloatingIPListener floatingIpListener,
                               final OdlInterfaceRpcService interfaceManager,
                               final ExternalRoutersListener externalRoutersListener) {
        super(VpnMap.class, NatVpnMapsChangeListener.class);
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.floatingIpListener = floatingIpListener;
        this.interfaceManager = interfaceManager;
        this.externalRoutersListener = externalRoutersListener;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} init", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnMap> getWildCardPath() {
        return InstanceIdentifier.create(VpnMaps.class).child(VpnMap.class);
    }

    @Override
    protected void add(InstanceIdentifier<VpnMap> identifier, VpnMap vpnMap) {
        Uuid vpnUuid = vpnMap.getVpnId();
        String vpnName = vpnUuid.getValue();
        if (vpnMap.getRouterIds() != null) {
            vpnMap.getRouterIds().stream()
                .filter(router -> !(Objects.equals(router.getRouterId(), vpnUuid)))
                .forEach(router -> {
                    String routerName = router.getRouterId().getValue();
                    LOG.info("REMOVE: Router {} is disassociated from Vpn {}", routerName, vpnName);
                    onRouterAssociatedToVpn(vpnName, routerName);
                });
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnMap> identifier, VpnMap vpnMap) {
        Uuid vpnUuid = vpnMap.getVpnId();
        String vpnName = vpnUuid.getValue();
        if (vpnMap.getRouterIds() != null) {
            vpnMap.getRouterIds().stream()
                .filter(router -> !(Objects.equals(router.getRouterId(), vpnUuid)))
                .forEach(router -> {
                    String routerName = router.getRouterId().getValue();
                    LOG.info("REMOVE: Router {} is disassociated from Vpn {}", routerName, vpnName);
                    onRouterDisassociatedFromVpn(vpnName, routerName);
                });
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnMap> identifier, VpnMap original, VpnMap updated) {
        Uuid vpnUuid = updated.getVpnId();
        String vpnName = vpnUuid.getValue();

        List<RouterIds> updatedRouterIdList = updated.getRouterIds();
        List<RouterIds> originalRouterIdList = original.getRouterIds();
        List<RouterIds> routersAddedList = null;
        List<RouterIds> routersRemovedList = null;

        if (originalRouterIdList == null && updatedRouterIdList != null) {
            routersAddedList = updatedRouterIdList;
        } else if (originalRouterIdList != null && updatedRouterIdList != null) {
            routersAddedList = updatedRouterIdList.stream()
                .filter(routerId -> (!originalRouterIdList.contains(routerId)))
                .collect(Collectors.toList());
        }

        if (originalRouterIdList != null && updatedRouterIdList == null) {
            routersRemovedList = originalRouterIdList;
        } else if (originalRouterIdList != null && updatedRouterIdList != null) {
            routersRemovedList = originalRouterIdList.stream()
                .filter(routerId -> (!updatedRouterIdList.contains(routerId)))
                .collect(Collectors.toList());
        }

        if (routersAddedList != null) {
            routersAddedList.stream()
                .filter(router -> !(Objects.equals(router.getRouterId(), updated.getVpnId())))
                .forEach(router -> {
                    String routerName = router.getRouterId().getValue();
                    onRouterAssociatedToVpn(vpnName, routerName);
                });
        }

        if (routersRemovedList != null) {
            routersRemovedList.stream()
                .filter(router -> !(Objects.equals(router.getRouterId(), original.getVpnId())))
                .forEach(router -> {
                    String routerName = router.getRouterId().getValue();
                    onRouterDisassociatedFromVpn(vpnName, routerName);
                });
        }
    }

    @Override
    protected NatVpnMapsChangeListener getDataTreeChangeListener() {
        return this;
    }

    public void onRouterAssociatedToVpn(String vpnName, String routerName) {

        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if (extNetwork != null) {
            try {
                LOG.debug("onRouterAssociatedToVpn : Router {} is associated with ext nw {}", routerName, extNetwork);
                handleDNATConfigurationForRouterAssociation(routerName, vpnName, extNetwork);
                Uuid extNetworkUuid = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
                if (extNetworkUuid == null) {
                    LOG.error("onRouterAssociatedToVpn : Unable to retrieve external network Uuid for router {}",
                            routerName);
                    return;
                }
                ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName,
                        extNetworkUuid);
                if (extNwProvType == null) {
                    LOG.error("onRouterAssociatedToVpn : External Network Provider Type missing");
                    return;
                }
                Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> externalRoutersListener.changeLocalVpnIdToBgpVpnId(routerName, routerId, extNetwork,
                        vpnName, tx, extNwProvType)).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error changling local VPN identifier to BGP VPN identifier", e);
            }
        } else {
            LOG.debug("onRouterAssociatedToVpn : Ignoring the Router {} association with VPN {} "
                    + "since it is not external router", routerName, vpnName);
        }
    }

    /**
     * router disassociation from vpn.
     */

    public void onRouterDisassociatedFromVpn(String vpnName, String routerName) {

        //check router is associated to external network
        String extNetwork = NatUtil.getAssociatedExternalNetwork(dataBroker, routerName);
        if (extNetwork != null) {
            try {
                LOG.debug("onRouterDisassociatedFromVpn : Router {} is associated with ext nw {}", routerName,
                        extNetwork);
                handleDNATConfigurationForRouterDisassociation(routerName, vpnName, extNetwork);
                Uuid extNetworkUuid = NatUtil.getNetworkIdFromRouterName(dataBroker, routerName);
                if (extNetworkUuid == null) {
                    LOG.error("onRouterDisassociatedFromVpn : Unable to retrieve external network Uuid for router {}",
                            routerName);
                    return;
                }
                ProviderTypes extNwProvType = NatEvpnUtil.getExtNwProvTypeFromRouterName(dataBroker, routerName,
                        extNetworkUuid);
                if (extNwProvType == null) {
                    LOG.error("onRouterDisassociatedFromVpn : External Network Provider Type missing");
                    return;
                }
                Uint32 routerId = NatUtil.getVpnId(dataBroker, routerName);
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> externalRoutersListener.changeBgpVpnIdToLocalVpnId(routerName, routerId, extNetwork,
                        vpnName, tx, extNwProvType)).get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error changing BGP VPN identifier to local VPN identifier", e);
            }
        } else {
            LOG.debug("onRouterDisassociatedFromVpn : Ignoring the Router {} association with VPN {} "
                    + "since it is not external router", routerName, vpnName);
        }
    }

    void handleDNATConfigurationForRouterAssociation(String routerName, String vpnName, String externalNetwork)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.debug("handleDNATConfigurationForRouterAssociation : Could not read Router Ports data "
                    + "object with id: {} to handle associate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        for (Ports port : optRouterPorts.get().nonnullPorts()) {
            String portName = port.getPortName();
            Uint64 dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
            if (dpnId.equals(Uint64.ZERO)) {
                LOG.warn("handleDNATConfigurationForRouterAssociation : DPN not found for {}, "
                        + "skip handling of router {} association with vpn {}", portName, routerName, vpnName);
                continue;
            }

            for (InternalToExternalPortMap intExtPortMap : port.nonnullInternalToExternalPortMap()) {
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, null,
                // intExtPortMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                LOG.debug("handleDNATConfigurationForRouterAssociation : Updating DNAT flows with VPN metadata {} ",
                        vpnName);
                floatingIpListener.createNATOnlyFlowEntries(dpnId, routerName, vpnName, networkId, intExtPortMap);
            }
        }
    }

    void handleDNATConfigurationForRouterDisassociation(String routerName, String vpnName, String externalNetwork)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerName);
        Optional<RouterPorts> optRouterPorts =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
        if (!optRouterPorts.isPresent()) {
            LOG.error("handleDNATConfigurationForRouterDisassociation : Could not read Router Ports "
                    + "data object with id: {} to handle disassociate vpn {}", routerName, vpnName);
            return;
        }
        Uuid networkId = Uuid.getDefaultInstance(externalNetwork);
        for (Ports port : optRouterPorts.get().nonnullPorts()) {
            String portName = port.getPortName();
            Uint64 dpnId = NatUtil.getDpnForInterface(interfaceManager, portName);
            if (dpnId.equals(Uint64.ZERO)) {
                LOG.debug("handleDNATConfigurationForRouterDisassociation : DPN not found for {}, "
                        + "skip handling of router {} association with vpn {}", portName, routerName, vpnName);
                continue;
            }
            for (InternalToExternalPortMap intExtPortMap : port.nonnullInternalToExternalPortMap()) {
                //remove all NAT related entries with routerName
                //floatingIpListener.removeNATOnlyFlowEntries(dpnId, portName, routerName, vpnName,
                // intExtPortMap.getInternalIp(), externalIp);
                //Create NAT entries with VPN Id
                floatingIpListener.createNATOnlyFlowEntries(dpnId, routerName, null, networkId, intExtPortMap);
            }
        }
    }

}
