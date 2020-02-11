/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import com.google.common.collect.ImmutableSet;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.DirectionIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.rev160608.SecurityRuleAttr;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AclEventListener extends AsyncDataTreeChangeListenerBase<Acl, AclEventListener> implements
        ClusteredDataTreeChangeListener<Acl>, RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(AclEventListener.class);

    private final AclServiceManager aclServiceManager;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;
    private final AclDataUtil aclDataUtil;
    private final AclServiceUtils aclServiceUtils;
    private final AclInterfaceCache aclInterfaceCache;

    @Inject
    public AclEventListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil, DataBroker dataBroker,
            AclDataUtil aclDataUtil, AclServiceUtils aclServicUtils, AclInterfaceCache aclInterfaceCache,
            ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(Acl.class, AclEventListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServicUtils;
        this.aclInterfaceCache = aclInterfaceCache;
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
    protected InstanceIdentifier<Acl> getWildCardPath() {
        return InstanceIdentifier.create(AccessLists.class).child(Acl.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Acl> key, Acl acl) {
        LOG.trace("On remove event, remove ACL: {}", acl);
        String aclName = acl.getAclName();
        this.aclDataUtil.removeAcl(aclName);
        Integer aclTag = this.aclDataUtil.getAclTag(aclName);
        if (aclTag != null) {
            this.aclDataUtil.removeAclTag(aclName);
        }

        updateRemoteAclCache(AclServiceUtils.getAceListFromAcl(acl), aclName, AclServiceManager.Action.REMOVE);
        if (aclClusterUtil.isEntityOwner()) {
            // Handle Rule deletion If SG Remove event is received before SG Rule delete event
            List<Ace> aceList = AclServiceUtils.aceList(acl);
            if (!aceList.isEmpty()) {
                Collection<AclInterface> aclInterfaces =
                        ImmutableSet.copyOf(aclDataUtil.getInterfaceList(new Uuid(aclName)));
                updateAceRules(aclInterfaces, aclName, aceList, AclServiceManager.Action.REMOVE);
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Acl> key, Acl aclBefore, Acl aclAfter) {
        String aclName = aclAfter.getAclName();
        Collection<AclInterface> interfacesBefore =
                ImmutableSet.copyOf(aclDataUtil.getInterfaceList(new Uuid(aclName)));
        // Find and update added ace rules in acl
        List<Ace> addedAceRules = getChangedAceList(aclAfter, aclBefore);

        // Find and update deleted ace rules in acl
        List<Ace> deletedAceRules = getDeletedAceList(aclAfter);

        if (aclClusterUtil.isEntityOwner()) {
            LOG.debug("On update event, remove Ace rules: {} for ACL: {}", deletedAceRules, aclName);
            updateAceRules(interfacesBefore, aclName, deletedAceRules, AclServiceManager.Action.REMOVE);
            if (!deletedAceRules.isEmpty()) {
                aclServiceUtils.deleteAcesFromConfigDS(aclName, deletedAceRules);
            }
        }
        updateAclCaches(aclBefore, aclAfter, interfacesBefore);

        if (aclClusterUtil.isEntityOwner()) {
            LOG.debug("On update event, add Ace rules: {} for ACL: {}", addedAceRules, aclName);
            updateAceRules(interfacesBefore, aclName, addedAceRules, AclServiceManager.Action.ADD);

            aclServiceManager.notifyAcl(aclBefore, aclAfter, interfacesBefore, AclServiceManager.Action.UPDATE);
        }
    }

    private void updateAceRules(Collection<AclInterface> interfaceList, String aclName, List<Ace> aceList,
            AclServiceManager.Action action) {
        LOG.trace("update ace rules - action: {} , ace rules: {}", action.name(), aceList);
        for (AclInterface port : interfaceList) {
            BigInteger dpId = port.getDpId();
            Long elanId = port.getElanId();
            if (dpId != null && elanId != null) {
                for (Ace aceRule : aceList) {
                    aclServiceManager.notifyAce(port, action, aclName, aceRule);
                }
            } else {
                LOG.debug("Skip update ACE rules as DP ID or ELAN ID for interface {} is not present. "
                        + "DP Id: {} ELAN ID: {}", port.getInterfaceId(), dpId, elanId);
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Acl> key, Acl acl) {
        LOG.trace("On add event, add ACL: {}", acl);
        this.aclDataUtil.addAcl(acl);

        String aclName = acl.getAclName();
        Integer aclTag = AclServiceUtils.getAclTag(acl);
        if (aclTag != null && aclTag != AclConstants.INVALID_ACL_TAG) {
            this.aclDataUtil.addAclTag(aclName, aclTag);
        }

        updateRemoteAclCache(AclServiceUtils.getAceListFromAcl(acl), aclName, AclServiceManager.Action.ADD);
    }

    /**
     * Update remote acl cache.
     *
     * @param aceList the ace list
     * @param aclName the acl name
     * @param action the action
     */
    private void updateRemoteAclCache(@NonNull List<Ace> aceList, String aclName, AclServiceManager.Action action) {
        for (Ace ace : aceList) {
            SecurityRuleAttr aceAttributes = ace.augmentation(SecurityRuleAttr.class);
            if (AclServiceUtils.doesAceHaveRemoteGroupId(aceAttributes)) {
                if (action == AclServiceManager.Action.ADD) {
                    aclDataUtil.addRemoteAclId(aceAttributes.getRemoteGroupId(), new Uuid(aclName),
                            aceAttributes.getDirection());
                } else {
                    aclDataUtil.removeRemoteAclId(aceAttributes.getRemoteGroupId(), new Uuid(aclName),
                            aceAttributes.getDirection());
                }
            }
        }
    }

    private void updateAclCaches(Acl aclBefore, Acl aclAfter, Collection<AclInterface> aclInterfaces) {
        String aclName = aclAfter.getAclName();
        Integer aclTag = this.aclDataUtil.getAclTag(aclName);
        if (aclTag == null) {
            aclTag = AclServiceUtils.getAclTag(aclAfter);
            if (aclTag != null && aclTag != AclConstants.INVALID_ACL_TAG) {
                this.aclDataUtil.addAclTag(aclName, aclTag);
            }
        }
        this.aclDataUtil.addAcl(aclAfter);

        updateAclCaches(aclBefore, aclAfter, aclInterfaces, DirectionEgress.class);
        updateAclCaches(aclBefore, aclAfter, aclInterfaces, DirectionIngress.class);
    }

    private void updateAclCaches(Acl aclBefore, Acl aclAfter, Collection<AclInterface> aclInterfaces,
            Class<? extends DirectionBase> direction) {
        Uuid aclId = new Uuid(aclAfter.getAclName());
        Set<Uuid> remoteAclsBefore = AclServiceUtils.getRemoteAclIdsByDirection(aclBefore, direction);
        Set<Uuid> remoteAclsAfter = AclServiceUtils.getRemoteAclIdsByDirection(aclAfter, direction);

        Set<Uuid> remoteAclsDeleted = new HashSet<>(remoteAclsBefore);
        remoteAclsDeleted.removeAll(remoteAclsAfter);
        for (Uuid remoteAcl : remoteAclsDeleted) {
            aclDataUtil.removeRemoteAclId(remoteAcl, aclId, direction);
        }

        Set<Uuid> remoteAclsAdded = new HashSet<>(remoteAclsAfter);
        remoteAclsAdded.removeAll(remoteAclsBefore);
        for (Uuid remoteAcl : remoteAclsAdded) {
            aclDataUtil.addRemoteAclId(remoteAcl, aclId, direction);
        }

        if (remoteAclsDeleted.isEmpty() && remoteAclsAdded.isEmpty()) {
            return;
        }

        for (AclInterface aclInterface : aclInterfaces) {
            AclInterface aclInterfaceInCache =
                    aclInterfaceCache.addOrUpdate(aclInterface.getInterfaceId(), (prevAclInterface, builder) -> {
                        SortedSet<Integer> remoteAclTags =
                                aclServiceUtils.getRemoteAclTags(aclInterface.getSecurityGroups(), direction);
                        if (DirectionEgress.class.equals(direction)) {
                            builder.egressRemoteAclTags(remoteAclTags);
                        } else {
                            builder.ingressRemoteAclTags(remoteAclTags);
                        }
                    });

            aclDataUtil.addOrUpdateAclInterfaceMap(aclInterface.getSecurityGroups(), aclInterfaceInCache);
        }
    }

    @Override
    protected AclEventListener getDataTreeChangeListener() {
        return this;
    }

    private static @NonNull List<Ace> getChangedAceList(Acl updatedAcl, Acl currentAcl) {
        if (updatedAcl == null) {
            return Collections.emptyList();
        }
        List<Ace> updatedAceList = AclServiceUtils.aceList(updatedAcl);
        if (currentAcl == null) {
            return updatedAceList;
        }

        List<Ace> currentAceList = AclServiceUtils.aceList(currentAcl);
        updatedAceList = new ArrayList<>(updatedAceList);
        for (Iterator<Ace> iterator = updatedAceList.iterator(); iterator.hasNext();) {
            Ace ace1 = iterator.next();
            for (Ace ace2 : currentAceList) {
                if (Objects.equals(ace1.getRuleName(), ace2.getRuleName())) {
                    iterator.remove();
                }
            }
        }
        return updatedAceList;
    }

    private List<Ace> getDeletedAceList(Acl acl) {
        if (acl == null || acl.getAccessListEntries() == null || acl.getAccessListEntries().getAce() == null) {
            return Collections.emptyList();
        }
        List<Ace> aceList = acl.getAccessListEntries().getAce();
        List<Ace> deletedAceList = new ArrayList<>();
        for (Ace ace: aceList) {
            if (ace.augmentation(SecurityRuleAttr.class).isDeleted()) {
                deletedAceList.add(ace);
            }
        }
        return deletedAceList;
    }
}
