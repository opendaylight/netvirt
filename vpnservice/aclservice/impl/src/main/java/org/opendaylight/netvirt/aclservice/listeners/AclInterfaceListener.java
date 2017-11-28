/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface.Builder;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.InterfaceAcl;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.IpPrefixOrAddress;
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

    @Inject
    public AclInterfaceListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil,
            DataBroker dataBroker, AclDataUtil aclDataUtil, AclInterfaceCache aclInterfaceCache) {
        super(Interface.class, AclInterfaceListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclInterfaceCache = aclInterfaceCache;
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
        LOG.trace("Received AclInterface update event, portBefore={}, portAfter={}", portBefore, portAfter);
        InterfaceAcl aclInPortAfter = portAfter.getAugmentation(InterfaceAcl.class);
        InterfaceAcl aclInPortBefore = portBefore.getAugmentation(InterfaceAcl.class);
        if (aclInPortAfter != null && aclInPortAfter.isPortSecurityEnabled()
                || aclInPortBefore != null && aclInPortBefore.isPortSecurityEnabled()) {
            String interfaceId = portAfter.getName();
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state
                .Interface interfaceState = AclServiceUtils.getInterfaceStateFromOperDS(dataBroker, interfaceId);

            AtomicBoolean added = new AtomicBoolean(false);
            AclInterface aclInterface = aclInterfaceCache.addOrUpdate(interfaceId, (prevAclInterface, builder) -> {
                builder.portSecurityEnabled(aclInPortAfter.isPortSecurityEnabled())
                    .securityGroups(aclInPortAfter.getSecurityGroups())
                    .allowedAddressPairs(aclInPortAfter.getAllowedAddressPairs());

                if ((prevAclInterface == null || prevAclInterface.getLPortTag() == null) && interfaceState != null) {
                    builder.dpId(AclServiceUtils.getDpIdFromIterfaceState(interfaceState))
                            .lPortTag(interfaceState.getIfIndex()).isMarkedForDelete(false);
                }

                if (prevAclInterface == null) {
                    added.set(true);
                    builder.subnetIpPrefixes(AclServiceUtils.getSubnetIpPrefixes(dataBroker, interfaceId))
                        .elanId(AclServiceUtils.getElanIdFromInterface(interfaceId, dataBroker))
                        .vpnId(AclServiceUtils.getVpnIdFromInterface(dataBroker, interfaceId));
                }
            });

            if (!added.get()) {
                aclInterface = buildAclInterfaceFromCache(aclInterface, aclInPortAfter);
            }

            AclInterface oldAclInterface = buildAclInterfaceFromCache(aclInterface, aclInPortBefore);
            List<Uuid> deletedAclList = AclServiceUtils.getUpdatedAclList(oldAclInterface.getSecurityGroups(),
                    aclInterface.getSecurityGroups());
            if (aclClusterUtil.isEntityOwner()) {
                // Handle bind/unbind service irrespective of interface state (up/down)
                boolean isPortSecurityEnable = aclInterface.isPortSecurityEnabled();
                boolean isPortSecurityEnableBefore = oldAclInterface.isPortSecurityEnabled();
                // if port security enable is changed, bind/unbind ACL service
                if (isPortSecurityEnableBefore != isPortSecurityEnable) {
                    LOG.debug("Notify bind/unbind ACL service for interface={}, isPortSecurityEnable={}", interfaceId,
                            isPortSecurityEnable);
                    if (isPortSecurityEnable) {
                        aclServiceManager.notify(aclInterface, null, Action.BIND);
                    } else {
                        aclServiceManager.notify(aclInterface, null, Action.UNBIND);
                    }
                }
                if (interfaceState != null && interfaceState.getOperStatus().equals(
                        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
                            .state.Interface.OperStatus.Up)) {
                    LOG.debug("On update event, notify ACL service manager to update ACL for interface: {}",
                            interfaceId);
                    aclServiceManager.notify(aclInterface, oldAclInterface, AclServiceManager.Action.UPDATE);
                }
            }

            if (deletedAclList != null && !deletedAclList.isEmpty()) {
                aclDataUtil.removeAclInterfaceMap(deletedAclList, aclInterface);
            }
        }
    }

    private AclInterface buildAclInterfaceFromCache(AclInterface cachedAclInterface, InterfaceAcl aclInPort) {
        Builder builder = AclInterface.builder();
        if (aclInPort != null) {
            builder.interfaceId(cachedAclInterface.getInterfaceId());
            builder.dpId(cachedAclInterface.getDpId());
            builder.lPortTag(cachedAclInterface.getLPortTag());
            builder.elanId(cachedAclInterface.getElanId());
            builder.vpnId(cachedAclInterface.getVpnId());
            builder.portSecurityEnabled(aclInPort.isPortSecurityEnabled());
            builder.allowedAddressPairs(aclInPort.getAllowedAddressPairs());
            builder.securityGroups(aclInPort.getSecurityGroups());
        }

        return builder.build();
    }

    @Override
    protected void add(InstanceIdentifier<Interface> key, Interface port) {
        LOG.trace("Received AclInterface add event, port={}", port);
        InterfaceAcl aclInPort = port.getAugmentation(InterfaceAcl.class);
        if (aclInPort != null && aclInPort.isPortSecurityEnabled()) {
            String interfaceId = port.getName();
            List<IpPrefixOrAddress> subnetIpPrefixes = AclServiceUtils.getSubnetIpPrefixes(dataBroker, interfaceId);

            AclInterface aclInterface = aclInterfaceCache.addOrUpdate(interfaceId, (prevAclInterface, builder) -> {
                builder.portSecurityEnabled(aclInPort.isPortSecurityEnabled())
                    .securityGroups(aclInPort.getSecurityGroups())
                    .allowedAddressPairs(aclInPort.getAllowedAddressPairs())
                    .elanId(AclServiceUtils.getElanIdFromInterface(interfaceId, dataBroker))
                    .vpnId(AclServiceUtils.getVpnIdFromInterface(dataBroker, interfaceId))
                    .subnetIpPrefixes(subnetIpPrefixes);
            });

            if (aclInterface.getElanId() == null) {
                LOG.debug("On add event, skip BIND since ElanId is not updated");
                return;
            }
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
