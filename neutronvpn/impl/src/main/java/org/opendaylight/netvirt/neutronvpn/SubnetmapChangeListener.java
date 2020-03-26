/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.evpn.utils.NeutronEvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev150602.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev150602.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev150602.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev150602.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SubnetmapChangeListener extends AsyncDataTreeChangeListenerBase<Subnetmap, SubnetmapChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetmapChangeListener.class);
    private final DataBroker dataBroker;
    private final IVpnManager vpnManager;
    private final NeutronvpnUtils neutronvpnUtils;
    private final NeutronEvpnUtils neutronEvpnUtils;
    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner managedNewTransactionRunner;
    // This class is exclusively used for subnet-associated-to-RT-handling scenario upon a cluster reboot
    // Use vpnmanager/subnetmapChangeListener for anything else in general

    @Inject
    public SubnetmapChangeListener(final DataBroker dataBroker, final IVpnManager vpnManager,
                                   final NeutronvpnUtils neutronvpnUtils, final NeutronEvpnUtils neutronEvpnUtils,
                                   final JobCoordinator jobCoordinator) {
        super(Subnetmap.class, SubnetmapChangeListener.class);
        this.dataBroker = dataBroker;
        this.vpnManager = vpnManager;
        this.neutronvpnUtils = neutronvpnUtils;
        this.neutronEvpnUtils = neutronEvpnUtils;
        this.jobCoordinator = jobCoordinator;
        this.managedNewTransactionRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @PostConstruct
    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Subnetmap> getWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class).child(Subnetmap.class);
    }

    @Override
    protected void add(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
        LOG.trace("add:SubnetmapChangeListener add subnetmap method: key {}, value {}", identifier, subnetmap);
        Uuid vpnId = subnetmap.getVpnId();
        if (vpnId != null) {
            //network associated to bgpvpn when subnet not present initially and added later
            LOG.debug("add: Adding subnet {} to vpn {}", vpnId.getValue());
            addVpnInterfacesForSubnet(vpnId, subnetmap);
            // reboot case
            //Populate subnet-associated-to-route-targets for network associated bgpvpn
            boolean isBgpVpn = !vpnId.equals(subnetmap.getRouterId());
            if (isBgpVpn && subnetmap.getRouterId() == null) {
                Set<VpnTarget> routeTargets = neutronvpnUtils.getRtListForVpn(vpnId.getValue());
                if (!routeTargets.isEmpty()) {
                    synchronized (subnetmap.getSubnetIp().intern()) {
                        neutronvpnUtils.updateRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                vpnId.getValue(), null);
                    }
                }
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmap) {
    }

    @Override
    protected void update(InstanceIdentifier<Subnetmap> identifier, Subnetmap subnetmapOriginal,
                          Subnetmap subnetmapUpdate) {
        final Uuid originalVpnId = subnetmapOriginal.getVpnId();
        final Uuid updatedVpnId = subnetmapUpdate.getVpnId();
        if (originalVpnId == null && updatedVpnId != null) {
            //network associated to bgpvpn or subnet added to a router
            LOG.debug("update: Adding subnet {} to vpn {}", subnetmapUpdate.getId(), updatedVpnId.getValue());
            addVpnInterfacesForSubnet(updatedVpnId, subnetmapUpdate);
        } else if (originalVpnId != null && updatedVpnId == null) {
            //network dissociated to bgpvpn or subnet removed to a router
            LOG.debug("update: Removing subnet {} from vpn {}", subnetmapUpdate.getId(), originalVpnId.getValue());
            removeVpnInterfacesForSubnet(originalVpnId, subnetmapOriginal);
        } else if (originalVpnId != null && updatedVpnId != null && (!updatedVpnId.equals(originalVpnId))) {
            //Router associated-dissociated with vpn
            LOG.debug("update: Moving subnet {} from vpn {} to vpn {}", subnetmapUpdate.getId(),
                    originalVpnId.getValue(), updatedVpnId.getValue());
            boolean isBeingAssociated = !subnetmapUpdate.getVpnId().equals(subnetmapUpdate.getRouterId());
            updateVpnInterfacesForSubnet(updatedVpnId, originalVpnId, subnetmapUpdate, isBeingAssociated);
        }
    }

    private void removeVpnInterfacesForSubnet(@Nonnull final Uuid vpnId, final Subnetmap subnetmap) {
        Uuid internetVpnId = subnetmap.getInternetVpnId();
        Uuid subnetId = subnetmap.getId();
        LOG.debug("removeVpnInterfacesForSubnet: Removing subnet {} from vpn {}/{}", subnetId.getValue(),
                vpnId, internetVpnId);
        VpnMap vpnMap;
        if (internetVpnId == null) {
            internetVpnId = subnetmap.getInternetVpnId();
        }
        if (internetVpnId != null) {
            vpnMap = neutronvpnUtils.getVpnMap(internetVpnId);
            if (vpnMap == null) {
                LOG.error("removeVpnInterfacesForSubnet: No vpnMap for vpnId {}, cannot remove subnet {} from Internet"
                                + " VPN", internetVpnId.getValue(), subnetId.getValue());
                return;
            }
        }
        boolean subnetVpnAssociation = false;
        if (subnetmap.getVpnId() != null
                && subnetmap.getVpnId().getValue().equals(vpnId.getValue())) {
            subnetVpnAssociation = true;
        } else if (internetVpnId != null && subnetmap.getInternetVpnId() != null
                && subnetmap.getInternetVpnId().getValue().matches(internetVpnId.getValue())) {
            subnetVpnAssociation = true;
        }
        if (!subnetVpnAssociation) {
            LOG.error("removeVpnInterfacesForSubnet: Subnetmap is not in VPN {}/{}, owns {} and {}",
                    vpnId, internetVpnId, subnetmap.getVpnId(), subnetmap.getInternetVpnId());
            return;
        }
        // Check if there are ports on this subnet; remove corresponding vpn-interfaces
        List<Uuid> portList = subnetmap.getPortList();
        final Uuid internetId = internetVpnId;
        if (portList != null) {
            for (final Uuid portId : portList) {
                jobCoordinator.enqueueJob("PORT-" + portId.getValue(), () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    ListenableFuture<Void> future = managedNewTransactionRunner
                            .callWithNewReadWriteTransactionAndSubmit(readWriteTransaction -> {
                                neutronvpnUtils.processVpnInterfaceDeletion(vpnId, internetId, portId, subnetmap,
                                        readWriteTransaction);
                            });
                    ListenableFutures.addErrorLogging(future, LOG,
                            "removeSubnetFromVpn: Exception while processing deletion of VPN interfaces "
                                    + "for port {} belonging to subnet {} and vpnId {}", portId.getValue(),
                            subnetId.getValue(), vpnId.getValue());
                    futures.add(future);
                    return futures;
                });
            }
        }
    }


    private void updateVpnInterfacesForSubnet(Uuid newVpnId, Uuid oldVpnId, Subnetmap sn, boolean isBeingAssociated) {
        Uuid internetVpnId = null;
        Uuid routerId = sn.getRouterId();
        IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
        if (ipVers == IpVersionChoice.IPV6 && routerId != null) {
            internetVpnId = neutronvpnUtils.getInternetvpnUuidBoundToRouterId(routerId);
        }
        /* vpnExtUuid will contain the value only on if the subnet is V6 and it is already been
         * associated with internet BGP-VPN.
         */
        if (internetVpnId != null) {
            //Update V6 Internet default route match with new VPN metadata
            if (isBeingAssociated) {
                neutronvpnUtils.updateVpnInstanceWithFallback(routerId, newVpnId, internetVpnId, isBeingAssociated);
            } else {
                neutronvpnUtils.updateVpnInstanceWithFallback(newVpnId, routerId,internetVpnId, !isBeingAssociated);
            }
        }
        //Update Router Interface first synchronously.
        //CAUTION:  Please DONOT make the router interface VPN Movement as an asynchronous commit again !
        ListenableFuture<Void> routerInterfaceFuture = managedNewTransactionRunner
                .callWithNewReadWriteTransactionAndSubmit(readWrtConfigTxn -> {
                    neutronvpnUtils.updateVpnInterface(newVpnId, oldVpnId, neutronvpnUtils.getNeutronPort(
                            sn.getRouterInterfacePortId(), readWrtConfigTxn), isBeingAssociated, true,
                            readWrtConfigTxn);
                });
        try {
            //Make synchronous submit call for router interface
            routerInterfaceFuture.get();
        } catch (ExecutionException | InterruptedException ex) {
            LOG.error("updateVpnInterfacesForSubnet: Exception occur during update router interface {} in subnet {} "
                            + "from oldVpnId {} to newVpnId {}", sn.getRouterInterfacePortId().getValue(),
                    sn.getId().getValue(), oldVpnId, newVpnId , ex);
            return;
        }
        // Check for ports on this subnet and update association of
        // corresponding vpn-interfaces to external vpn
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (Uuid port : portList) {
                LOG.debug("updateVpnInterfacesForSubnet: Updating vpn-interface for port {} isBeingAssociated {}",
                        port.getValue(), isBeingAssociated);
                jobCoordinator.enqueueJob("PORT-" + port.getValue(), () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    ListenableFuture<Void> future = managedNewTransactionRunner
                            .callWithNewReadWriteTransactionAndSubmit(wrtConfigTxn -> {
                                neutronvpnUtils.updateVpnInterface(newVpnId, oldVpnId, neutronvpnUtils.getNeutronPort(
                                        port), isBeingAssociated, false, wrtConfigTxn);
                            });
                    ListenableFutures.addErrorLogging(future, LOG, "updateVpnInterfacesForSubnet: Failed to"
                            + " update VPN interface {} for vpnId {}", port.getValue(), newVpnId);
                    futures.add(future);
                    return futures;
                });
            }
        }
    }

    private void addVpnInterfacesForSubnet(Uuid vpnId, final Subnetmap sn) {
        Uuid internetVpnId = sn.getInternetVpnId();
        if (vpnId != null) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap == null) {
                LOG.error("addVpnInterfacesForSubnet: No vpnMap for vpnId {}, cannot add subnet {} to VPN",
                        vpnId.getValue(), sn.getId().getValue());
                return;
            }
            final VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
            LOG.debug("addVpnInterfacesForSubnet: VpnInstance {}", vpnInstance.toString());
            if (vpnInstance.isL2vpn()) {
                neutronEvpnUtils.updateElanAndVpn(vpnInstance, sn.getNetworkId().getValue(),
                        NeutronEvpnUtils.Operation.ADD);
            }
        }
        if (internetVpnId != null) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(internetVpnId);
            if (vpnMap == null) {
                LOG.error("addVpnInterfacesForSubnet: No vpnMap for InternetVpnId {}, cannot add "
                                + "subnet {} to VPN", internetVpnId.getValue(), sn.getId().getValue());
                return;
            }
        }
        final Uuid internetId = internetVpnId;
        // Check if there are ports on this subnet and add corresponding vpn-interfaces
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (final Uuid portId : portList) {
                String vpnInfName = portId.getValue();
                VpnInterface vpnIface = VpnHelper.getVpnInterface(dataBroker, vpnInfName);
                Port port = neutronvpnUtils.getNeutronPort(portId);
                if (port == null) {
                    LOG.error("addVpnInterfacesForSubnet: Cannot proceed with addSubnetToVpn for port {} in subnet {} "
                            + "since port is absent in Neutron config DS", portId.getValue(), sn.getId().getValue());
                    continue;
                }
                final Boolean isRouterInterface = port.getDeviceOwner()
                        .equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF) ? true : false;
                jobCoordinator.enqueueJob("PORT-" + portId.getValue(), () -> {
                    ListenableFuture<Void> future = managedNewTransactionRunner
                            .callWithNewWriteOnlyTransactionAndSubmit(wrtConfigTxn -> {
                                Adjacencies portAdj = neutronvpnUtils.createPortIpAdjacencies(port, isRouterInterface,
                                        wrtConfigTxn, sn, vpnIface);
                                if (vpnIface == null) {
                                    LOG.trace("addVpnInterfacesForSubnet: create new VpnInterface for Port {}",
                                            vpnInfName);
                                    Set<Uuid> listVpn = new HashSet<Uuid>();
                                    if (vpnId != null) {
                                        listVpn.add(vpnId);
                                    }
                                    if (internetId != null) {
                                        listVpn.add(internetId);
                                    }
                                    neutronvpnUtils.writeVpnInterfaceToDs(listVpn, vpnInfName, portAdj,
                                            port.getNetworkId(), isRouterInterface, wrtConfigTxn);
                                    if (sn.getRouterId() != null) {
                                        neutronvpnUtils.addToNeutronRouterInterfacesMap(sn.getRouterId(),
                                                portId.getValue());
                                    }
                                } else {
                                    LOG.trace("addVpnInterfacesForSubnet: update VpnInterface for Port {} with adj {}",
                                            vpnInfName, portAdj);
                                    if (vpnId != null) {
                                        neutronvpnUtils.updateVpnInterfaceWithAdjacencies(vpnId, vpnInfName, portAdj,
                                                wrtConfigTxn);
                                    }
                                    if (internetId != null) {
                                        neutronvpnUtils.updateVpnInterfaceWithAdjacencies(internetId, vpnInfName,
                                                portAdj, wrtConfigTxn);
                                    }
                                }
                            });

                    ListenableFutures.addErrorLogging(future, LOG,
                            "addVpnInterfacesForSubnet: Failed while creating VPN interface for vpnId {}, portId {}"
                                    + "{}, subnetId",
                            vpnId.getValue(), portId, sn.getId().getValue());
                    return Collections.singletonList(future);
                });
            }
        }
    }


    @Override
    protected SubnetmapChangeListener getDataTreeChangeListener() {
        return this;
    }
}