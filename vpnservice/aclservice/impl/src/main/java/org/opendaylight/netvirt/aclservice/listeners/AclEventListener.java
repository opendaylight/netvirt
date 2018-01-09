/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclInterfaceCache;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
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
        ClusteredDataTreeChangeListener<Acl> {

    private static final Logger LOG = LoggerFactory.getLogger(AclEventListener.class);

    private final AclServiceManager aclServiceManager;
    private final AclClusterUtil aclClusterUtil;
    private final DataBroker dataBroker;
    private final AclDataUtil aclDataUtil;
    private final AclServiceUtils aclServiceUtils;
    private final AclInterfaceCache aclInterfaceCache;

    @Inject
    public AclEventListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil, DataBroker dataBroker,
            AclDataUtil aclDataUtil, AclServiceUtils aclServicUtils, AclInterfaceCache aclInterfaceCache) {
        super(Acl.class, AclEventListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
        this.aclDataUtil = aclDataUtil;
        this.aclServiceUtils = aclServicUtils;
        this.aclInterfaceCache = aclInterfaceCache;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<Acl> getWildCardPath() {
        return InstanceIdentifier
                .create(AccessLists.class)
                .child(Acl.class);
    }

    @Override
    protected void remove(InstanceIdentifier<Acl> key, Acl acl) {
        String aclName = acl.getAclName();
        if (!AclServiceUtils.isOfAclInterest(acl)) {
            LOG.trace("{} does not have SecurityRuleAttr augmentation", aclName);
            return;
        }

        LOG.trace("On remove event, remove ACL: {}", acl);
        this.aclServiceUtils.releaseAclTag(aclName);
        updateRemoteAclCache(acl.getAccessListEntries().getAce(), aclName, AclServiceManager.Action.REMOVE);

        if (aclClusterUtil.isEntityOwner()) {
            synchronized (aclName.intern()) {
                try {
                    AclServiceUtils.deleteAclPortsLookupEntry(aclName, this.dataBroker);
                } catch (TransactionCommitFailedException e) {
                    LOG.error("Failed to delete AclPortsByIp data for ACL={}", aclName, e);
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<Acl> key, Acl aclBefore, Acl aclAfter) {
        if (!AclServiceUtils.isOfAclInterest(aclAfter) && !AclServiceUtils.isOfAclInterest(aclBefore)) {
            LOG.trace("before {} and after {} does not have SecurityRuleAttr augmentation",
                    aclBefore.getAclName(), aclAfter.getAclName());
            return;
        }

        String aclName = aclAfter.getAclName();
        Collection<AclInterface> interfacesBefore =
                ImmutableSet.copyOf(aclDataUtil.getInterfaceList(new Uuid(aclName)));
        // Find and update added ace rules in acl
        List<Ace> addedAceRules = getChangedAceList(aclAfter, aclBefore);

        // Find and update deleted ace rules in acl
        List<Ace> deletedAceRules = getChangedAceList(aclBefore, aclAfter);

        if (interfacesBefore != null && aclClusterUtil.isEntityOwner()) {
            LOG.debug("On update event, remove Ace rules: {} for ACL: {}", deletedAceRules, aclName);
            updateAceRules(interfacesBefore, aclName, deletedAceRules, AclServiceManager.Action.REMOVE);

        }
        updateAclCaches(aclBefore, aclAfter, interfacesBefore);

        if (interfacesBefore != null && aclClusterUtil.isEntityOwner()) {
            LOG.debug("On update event, add Ace rules: {} for ACL: {}", addedAceRules, aclName);
            updateAceRules(interfacesBefore, aclName, addedAceRules, AclServiceManager.Action.ADD);

            aclServiceManager.notifyAcl(aclBefore, aclAfter, AclServiceManager.Action.UPDATE);
        }
    }

    private void updateAceRules(Collection<AclInterface> interfaceList, String aclName, List<Ace> aceList,
            AclServiceManager.Action action) {
        if (null != aceList && !aceList.isEmpty()) {
            LOG.trace("update ace rules - action: {} , ace rules: {}", action.name(), aceList);
            for (AclInterface port : interfaceList) {
                for (Ace aceRule : aceList) {
                    aclServiceManager.notifyAce(port, action, aclName, aceRule);
                }
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Acl> key, Acl acl) {
        String aclName = acl.getAclName();
        if (!AclServiceUtils.isOfAclInterest(acl)) {
            LOG.trace("{} does not have SecurityRuleAttr augmentation", aclName);
            return;
        }

        LOG.trace("On add event, add ACL: {}", acl);
        Integer aclTag = this.aclServiceUtils.allocateAclTag(aclName);
        if (aclTag != null && aclTag != AclConstants.INVALID_ACL_TAG) {
            this.aclDataUtil.addAclTag(aclName, aclTag);
        }

        updateRemoteAclCache(acl.getAccessListEntries().getAce(), aclName, AclServiceManager.Action.ADD);
    }

    /**
     * Update remote acl cache.
     *
     * @param aceList the ace list
     * @param aclName the acl name
     * @param action the action
     */
    private void updateRemoteAclCache(List<Ace> aceList, String aclName, AclServiceManager.Action action) {
        if (null == aceList) {
            return;
        }
        for (Ace ace : aceList) {
            SecurityRuleAttr aceAttributes = ace.getAugmentation(SecurityRuleAttr.class);
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

        SortedSet<Integer> remoteAclTagsAdded = remoteAclsAdded.stream().map(id -> aclServiceUtils.getAclTag(id))
                .collect(Collectors.toCollection(TreeSet::new));
        SortedSet<Integer> remoteAclTagsDeleted = remoteAclsDeleted.stream().map(id -> aclServiceUtils.getAclTag(id))
                .collect(Collectors.toCollection(TreeSet::new));

        if (aclInterfaces != null) {
            for (AclInterface aclInterface : aclInterfaces) {
                AclInterface aclInterfaceInCache =
                        aclInterfaceCache.addOrUpdate(aclInterface.getInterfaceId(), (prevAclInterface, builder) -> {
                            List<Uuid> aclIds = aclInterface.getSecurityGroups();
                            SortedSet<Integer> remoteAclTags;
                            if (aclIds != null && aclIds.size() == 1) {
                                SortedSet<Integer> remoteAclTagsInCache = prevAclInterface.getRemoteAclTags(direction);
                                if (remoteAclTagsInCache == null) {
                                    remoteAclTags = remoteAclTagsAdded;
                                } else {
                                    remoteAclTags = new TreeSet<>(remoteAclTagsInCache);
                                    remoteAclTags.removeAll(remoteAclTagsDeleted);
                                    remoteAclTags.addAll(remoteAclTagsAdded);
                                }
                            } else {
                                // In case if interface is having multiple ACLs.
                                remoteAclTags = aclServiceUtils.getRemoteAclTags(aclIds, direction, dataBroker);
                            }
                            if (DirectionEgress.class.equals(direction)) {
                                builder.egressRemoteAclTags(remoteAclTags);
                            } else {
                                builder.ingressRemoteAclTags(remoteAclTags);
                            }
                        });

                aclDataUtil.addOrUpdateAclInterfaceMap(aclInterface.getSecurityGroups(), aclInterfaceInCache);
            }
        }
    }

    @Override
    protected AclEventListener getDataTreeChangeListener() {
        return this;
    }

    private List<Ace> getChangedAceList(Acl updatedAcl, Acl currentAcl) {
        if (updatedAcl == null) {
            return null;
        }
        List<Ace> updatedAceList = new ArrayList<>(updatedAcl.getAccessListEntries().getAce());
        if (currentAcl == null) {
            return updatedAceList;
        }
        List<Ace> currentAceList = new ArrayList<>(currentAcl.getAccessListEntries().getAce());
        for (Iterator<Ace> iterator = updatedAceList.iterator(); iterator.hasNext();) {
            Ace ace1 = iterator.next();
            for (Ace ace2 : currentAceList) {
                if (ace1.getRuleName().equals(ace2.getRuleName())) {
                    iterator.remove();
                }
            }
        }
        return updatedAceList;
    }
}
