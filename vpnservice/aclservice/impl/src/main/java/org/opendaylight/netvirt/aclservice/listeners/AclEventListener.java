/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.listeners;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.api.utils.AclInterface;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
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

    @Inject
    public AclEventListener(AclServiceManager aclServiceManager, AclClusterUtil aclClusterUtil, DataBroker dataBroker) {
        super(Acl.class, AclEventListener.class);
        this.aclServiceManager = aclServiceManager;
        this.aclClusterUtil = aclClusterUtil;
        this.dataBroker = dataBroker;
    }

    @Override
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
        updateRemoteAclCache(acl.getAccessListEntries().getAce(), acl.getAclName(), AclServiceManager.Action.REMOVE);
    }

    @Override
    protected void update(InstanceIdentifier<Acl> key, Acl aclBefore, Acl aclAfter) {
        List<AclInterface> interfaceList = AclDataUtil.getInterfaceList(new Uuid(aclAfter.getAclName()));
        // find and update added ace rules in acl
        List<Ace> addedAceRules = getChangedAceList(aclAfter, aclBefore);
        updateRemoteAclCache(addedAceRules, aclAfter.getAclName(), AclServiceManager.Action.ADD);
        if (interfaceList != null && aclClusterUtil.isEntityOwner()) {
            updateAceRules(interfaceList, addedAceRules, AclServiceManager.Action.ADD);
        }
        // find and update deleted ace rules in acl
        List<Ace> deletedAceRules = getChangedAceList(aclBefore, aclAfter);
        if (interfaceList != null && aclClusterUtil.isEntityOwner()) {
            updateAceRules(interfaceList, deletedAceRules, AclServiceManager.Action.REMOVE);
        }
        updateRemoteAclCache(deletedAceRules, aclAfter.getAclName(), AclServiceManager.Action.REMOVE);

    }

    private void updateAceRules(List<AclInterface> interfaceList, List<Ace> aceList, AclServiceManager.Action action) {
        if (null != aceList && !aceList.isEmpty()) {
            LOG.trace("update ace rules - action: {} , ace rules: {}", action.name(), aceList);
            for (AclInterface port : interfaceList) {
                for (Ace aceRule : aceList) {
                    aclServiceManager.notifyAce(port, action, aceRule);
                }
            }
        }
    }

    @Override
    protected void add(InstanceIdentifier<Acl> key, Acl acl) {
        updateRemoteAclCache(acl.getAccessListEntries().getAce(), acl.getAclName(), AclServiceManager.Action.ADD);
    }

    private void updateRemoteAclCache(List<Ace> aceList, String aclName, AclServiceManager.Action action) {
        if (null == aceList) {
            return;
        }
        for (Ace ace : aceList) {
            SecurityRuleAttr aceAttributes = ace.getAugmentation(SecurityRuleAttr.class);
            if (aceAttributes != null && aceAttributes.getRemoteGroupId() != null) {
                if (action == AclServiceManager.Action.ADD) {
                    AclDataUtil.addRemoteAclId(aceAttributes.getRemoteGroupId(), new Uuid(aclName));
                } else {
                    AclDataUtil.removeRemoteAclId(aceAttributes.getRemoteGroupId(), new Uuid(aclName));
                }
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
        for (Iterator<Ace> iterator = updatedAceList.iterator(); iterator.hasNext(); ) {
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
