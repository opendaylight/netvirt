/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.srm.RecoverableListener;
import org.opendaylight.genius.srm.ServiceRecoveryRegistry;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager.Action;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.ParentRefs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclInterfaceListener extends AsyncDataTreeChangeListenerBase<Interface, AclInterfaceListener>
        implements ClusteredDataTreeChangeListener<Interface>, RecoverableListener {
    private static final Logger LOG = LoggerFactory.getLogger(AclInterfaceListener.class);

    private final AclServiceManager aclServiceManager;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;
    private final AclDataUtil aclDataUtil;
    private final AclInterfaceCache aclInterfaceCache;
    private final AclServiceUtils aclServiceUtils;

    @Inject
    public AclInterfaceListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil,
            DataBroker dataBroker, AclDataUtil aclDataUtil, AclInterfaceCache aclInterfaceCache,
            AclServiceUtils aclServicUtils, ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(Interface.class, AclInterfaceListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclInterfaceCache = aclInterfaceCache;
        this.aclServiceUtils = aclServicUtils;
        serviceRecoveryRegistry.addRecoverableListener(AclServiceUtils.getRecoverServiceRegistryKey(), this);
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener();
    }

    @Override
    public void registerListener() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier.create(Interfaces.class).child(Interface.class);
    }

    @Override
    public void remove(InstanceIdentifier<Interface> key, Interface port) {
        LOG.trace("Received AclInterface remove event, port={}", port);
        String interfaceId = port.getName();
        AclInterface aclInterface = aclInterfaceCache.remove(interfaceId);
        if (AclServiceUtils.isOfInterest(aclInterface)) {
            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On remove event, notify ACL unbind/remove for interface: {}", interfaceId);
                aclServiceManager.notify(aclInterface, null, Action.UNBIND);
                if (aclInterface.getDpId() != null) {
                    aclServiceManager.notify(aclInterface, null, Action.REMOVE);
                }
                aclServiceUtils.deleteSubnetInfo(interfaceId);
            }
        }
    }

    @Override
    public void update(InstanceIdentifier<Interface> key, Interface portBefore, Interface portAfter) {
        if (portBefore.augmentation(ParentRefs.class) == null
                && portAfter.augmentation(ParentRefs.class) != null) {
            LOG.trace("Ignoring event for update in ParentRefs for {} ", portAfter.getName());
            return;
        }
        LOG.trace("Received AclInterface update event, portBefore={}, portAfter={}", portBefore, portAfter);
        InterfaceAcl aclInPortAfter = portAfter.augmentation(InterfaceAcl.class);
        InterfaceAcl aclInPortBefore = portBefore.augmentation(InterfaceAcl.class);

        String interfaceId = portAfter.getName();
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
            .Interface interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceId);

        AclInterface aclInterfaceBefore = aclInterfaceCache.get(interfaceId);
        if (aclInterfaceBefore == null || isPortSecurityEnabledNow(aclInPortBefore, aclInPortAfter)) {
            // Updating cache now as it might have not updated when
            // port-security-enable=false
            aclInterfaceBefore = addOrUpdateAclInterfaceCache(interfaceId, aclInPortBefore, true, interfaceState);
        }
        if (aclInPortAfter != null && aclInPortAfter.isPortSecurityEnabled()
                || aclInPortBefore != null && aclInPortBefore.isPortSecurityEnabled()) {
            List<Uuid> sgsBefore = null;
            if (aclInPortBefore != null) {
                sgsBefore = aclInPortBefore.getSecurityGroups();
            }
            boolean isSgChanged = isSecurityGroupsChanged(sgsBefore, aclInPortAfter.getSecurityGroups());
            AclInterface aclInterfaceAfter =
                    addOrUpdateAclInterfaceCache(interfaceId, aclInPortAfter, isSgChanged, interfaceState);

            if (aclClusterUtil.isEntityOwner()) {
                // Handle bind/unbind service irrespective of interface state (up/down)
                boolean isPortSecurityEnable = aclInterfaceAfter.isPortSecurityEnabled();
                boolean isPortSecurityEnableBefore = aclInterfaceBefore.isPortSecurityEnabled();
                // if port security enable is changed and is disabled, unbind ACL service
                if (isPortSecurityEnableBefore != isPortSecurityEnable && !isPortSecurityEnable) {
                    LOG.debug("Notify unbind ACL service for interface={}, isPortSecurityEnable={}", interfaceId,
                            isPortSecurityEnable);
                    aclServiceManager.notify(aclInterfaceAfter, null, Action.UNBIND);
                }
                if (interfaceState != null && interfaceState.getOperStatus().equals(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                            .state.Interface.OperStatus.Up)) {
                    // if port security enable is changed and is enabled, bind ACL service
                    if (isPortSecurityEnableBefore != isPortSecurityEnable && isPortSecurityEnable) {
                        LOG.debug("Notify bind ACL service for interface={}, isPortSecurityEnable={}", interfaceId,
                                isPortSecurityEnable);
                        aclServiceManager.notify(aclInterfaceAfter, null, Action.BIND);
                    }
                    LOG.debug("On update event, notify ACL service manager to update ACL for interface: {}",
                            interfaceId);
                    // handle add for AclPortsLookup before processing update
                    aclServiceUtils.addAclPortsLookupForInterfaceUpdate(aclInterfaceBefore, aclInterfaceAfter);

                    aclServiceManager.notify(aclInterfaceAfter, aclInterfaceBefore, AclServiceManager.Action.UPDATE);
                    // handle delete for AclPortsLookup after processing update
                    aclServiceUtils.deleteAclPortsLookupForInterfaceUpdate(aclInterfaceBefore, aclInterfaceAfter);
                }
            }
            updateCacheWithAclChange(aclInterfaceBefore, aclInterfaceAfter);
        }
    }

    private void updateCacheWithAclChange(AclInterface aclInterfaceBefore, AclInterface aclInterfaceAfter) {
        List<Uuid> addedAcls = AclServiceUtils.getUpdatedAclList(aclInterfaceAfter.getSecurityGroups(),
                aclInterfaceBefore.getSecurityGroups());
        List<Uuid> deletedAcls = AclServiceUtils.getUpdatedAclList(aclInterfaceBefore.getSecurityGroups(),
                aclInterfaceAfter.getSecurityGroups());
        if (deletedAcls != null && !deletedAcls.isEmpty()) {
            aclDataUtil.removeAclInterfaceMap(deletedAcls, aclInterfaceAfter);
        }
        if (addedAcls != null && !addedAcls.isEmpty()) {
            aclDataUtil.addOrUpdateAclInterfaceMap(addedAcls, aclInterfaceAfter);
        }
    }

    private boolean isPortSecurityEnabledNow(InterfaceAcl aclInPortBefore, InterfaceAcl aclInPortAfter) {
        return aclInPortBefore != null && !aclInPortBefore.isPortSecurityEnabled() && aclInPortAfter != null
                && aclInPortAfter.isPortSecurityEnabled();
    }

    private boolean isSecurityGroupsChanged(List<Uuid> sgsBefore, List<Uuid> sgsAfter) {
        if (sgsBefore == null && sgsAfter == null) {
            return false;
        }
        if ((sgsBefore == null && sgsAfter != null) || (sgsBefore != null && sgsAfter == null)) {
            return true;
        }
        if (sgsBefore != null && sgsAfter != null) {
            return !(new HashSet<>(sgsBefore)).equals(new HashSet<>(sgsAfter));
        }
        return true;
    }

    private AclInterface addOrUpdateAclInterfaceCache(String interfaceId, InterfaceAcl aclInPort) {
        return addOrUpdateAclInterfaceCache(interfaceId, aclInPort, true, null);
    }

    private AclInterface addOrUpdateAclInterfaceCache(String interfaceId, InterfaceAcl aclInPort, boolean isSgChanged,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
                    .Interface interfaceState) {
        AclInterface aclInterface = aclInterfaceCache.addOrUpdate(interfaceId, (prevAclInterface, builder) -> {
            List<Uuid> sgs = new ArrayList<>();
            if (aclInPort != null) {
                sgs = aclInPort.getSecurityGroups();
                builder.portSecurityEnabled(aclInPort.isPortSecurityEnabled()).securityGroups(sgs)
                        .allowedAddressPairs(aclInPort.getAllowedAddressPairs());
            }

            if ((prevAclInterface == null || prevAclInterface.getLPortTag() == null) && interfaceState != null) {
                builder.dpId(AclServiceUtils.getDpIdFromIterfaceState(interfaceState))
                        .lPortTag(interfaceState.getIfIndex()).isMarkedForDelete(false);
            }

            builder.subnetInfo(aclServiceUtils.getSubnetInfo(interfaceId));
            if (prevAclInterface == null || prevAclInterface.getElanId() == null) {
                builder.elanId(AclServiceUtils.getElanIdFromInterface(interfaceId, dataBroker));
            }
            if (prevAclInterface == null || isSgChanged) {
                builder.ingressRemoteAclTags(aclServiceUtils.getRemoteAclTags(sgs, DirectionIngress.class))
                        .egressRemoteAclTags(aclServiceUtils.getRemoteAclTags(sgs, DirectionEgress.class));
            }
        });
        // Clone and return the ACL interface object
        return AclInterface.builder(aclInterface).build();
    }

    @Override
    public void add(InstanceIdentifier<Interface> key, Interface port) {
        LOG.trace("Received AclInterface add event, port={}", port);
        InterfaceAcl aclInPort = port.augmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            String interfaceId = port.getName();
            AclInterface aclInterface = addOrUpdateAclInterfaceCache(interfaceId, aclInPort);

            // if interface state event comes first followed by interface config event.
            if (aclInterface.getDpId() != null && aclInterface.getElanId() != null && aclClusterUtil.isEntityOwner()) {
                LOG.debug("On add event, notify ACL bind/add for interface: {}", interfaceId);
                aclServiceManager.notify(aclInterface, null, Action.BIND);
                aclServiceManager.notify(aclInterface, null, Action.ADD);
            }
        }
    }

    @Override
    protected AclInterfaceListener getDataTreeChangeListener() {
        return this;
    }
}
