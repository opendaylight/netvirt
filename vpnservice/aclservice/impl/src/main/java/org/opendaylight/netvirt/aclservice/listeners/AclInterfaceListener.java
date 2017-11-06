/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.HashSet;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
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
        implements ClusteredDataTreeChangeListener<Interface> {
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
            AclServiceUtils aclServicUtils) {
        super(Interface.class, AclInterfaceListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclInterfaceCache = aclInterfaceCache;
        this.aclServiceUtils = aclServicUtils;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Interface> getWildCardPath() {
        return InstanceIdentifier
                .create(Interfaces.class)
                .child(Interface.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Interface> key, Interface port) {
        LOG.trace("Received AclInterface remove event, port={}", port);
        String interfaceId = port.getName();
        AclInterface aclInterface = aclInterfaceCache.remove(interfaceId);
        if (AclServiceUtils.isOfInterest(aclInterface)) {
            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On remove event, notify ACL service manager to unbind ACL from interface: {}", port);
                aclServiceManager.notify(aclInterface, null, Action.UNBIND);
                AclServiceUtils.deleteSubnetIpPrefixes(dataBroker, interfaceId);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Interface> key, Interface portBefore, Interface portAfter) {
        if (portBefore.getAugmentation(ParentRefs.class) == null
                && portAfter.getAugmentation(ParentRefs.class) != null) {
            LOG.trace("Ignoring event for update in ParentRefs for {} ", portAfter.getName());
            return;
        }
        LOG.trace("Received AclInterface update event, portBefore={}, portAfter={}", portBefore, portAfter);
        InterfaceAcl aclInPortAfter = portAfter.getAugmentation(InterfaceAcl.class);
        InterfaceAcl aclInPortBefore = portBefore.getAugmentation(InterfaceAcl.class);

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
            boolean isSgChanged =
                    isSecurityGroupsChanged(aclInPortBefore.getSecurityGroups(), aclInPortAfter.getSecurityGroups());
            AclInterface aclInterfaceAfter =
                    addOrUpdateAclInterfaceCache(interfaceId, aclInPortAfter, isSgChanged, interfaceState);

            if (aclClusterUtil.isEntityOwner()) {
                // Handle bind/unbind service irrespective of interface state (up/down)
                boolean isPortSecurityEnable = aclInterfaceAfter.isPortSecurityEnabled();
                boolean isPortSecurityEnableBefore = aclInterfaceBefore.isPortSecurityEnabled();
                // if port security enable is changed, bind/unbind ACL service
                if (isPortSecurityEnableBefore != isPortSecurityEnable) {
                    LOG.debug("Notify bind/unbind ACL service for interface={}, isPortSecurityEnable={}", interfaceId,
                            isPortSecurityEnable);
                    if (isPortSecurityEnable) {
                        aclServiceManager.notify(aclInterfaceAfter, null, Action.BIND);
                    } else {
                        aclServiceManager.notify(aclInterfaceAfter, null, Action.UNBIND);
                    }
                }
                if (interfaceState != null && interfaceState.getOperStatus().equals(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                            .state.Interface.OperStatus.Up)) {
                    LOG.debug("On update event, notify ACL service manager to update ACL for interface: {}",
                            interfaceId);
                    aclServiceManager.notify(aclInterfaceAfter, aclInterfaceBefore, AclServiceManager.Action.UPDATE);
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
            List<Uuid> sgs = aclInPort.getSecurityGroups();
            builder.portSecurityEnabled(aclInPort.isPortSecurityEnabled()).securityGroups(sgs)
                    .allowedAddressPairs(aclInPort.getAllowedAddressPairs());

            if ((prevAclInterface == null || prevAclInterface.getLPortTag() == null) && interfaceState != null) {
                builder.dpId(AclServiceUtils.getDpIdFromIterfaceState(interfaceState))
                        .lPortTag(interfaceState.getIfIndex()).isMarkedForDelete(false);
            }

            if (prevAclInterface == null) {
                builder.subnetIpPrefixes(AclServiceUtils.getSubnetIpPrefixes(dataBroker, interfaceId));
            }
            if (prevAclInterface == null || prevAclInterface.getElanId() == null) {
                builder.elanId(AclServiceUtils.getElanIdFromInterface(interfaceId, dataBroker));
            }
            if (prevAclInterface == null || isSgChanged) {
                builder.ingressRemoteAclTags(aclServiceUtils.getRemoteAclTags(sgs, DirectionIngress.class, dataBroker))
                        .egressRemoteAclTags(aclServiceUtils.getRemoteAclTags(sgs, DirectionEgress.class, dataBroker));
            }
        });
        // Clone and return the ACL interface object
        return AclInterface.builder(aclInterface).build();
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface port) {
        LOG.trace("Received AclInterface add event, port={}", port);
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            String interfaceId = port.getName();
            AclInterface aclInterface = addOrUpdateAclInterfaceCache(interfaceId, aclInPort);

            if (aclClusterUtil.isEntityOwner()) {
                LOG.debug("On add event, notify ACL service manager to bind ACL for interface: {}", port);
                aclServiceManager.notify(aclInterface, null, Action.BIND);
            }
        }
    }

    @Override
    protected AclInterfaceListener getDataTreeChangeListener() {
        return this;
    }
}
