/*
 * Copyright (c) 2016 Inocybe Technologies and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.nic.utils.MdsalUtils;
import org.opendaylight.vpnservice.utils.IidFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.constraints.rev150122.FailoverType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.Intents;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.IntentsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.Actions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.ActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.Constraints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.ConstraintsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.Subjects;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.SubjectsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.actions.action.allow.AllowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.actions.action.block.BlockBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints.constraints.failover.constraint.FailoverConstraintBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints.constraints.protection.constraint.ProtectionConstraintBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.end.point.group.EndPointGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.end.point.group.EndPointGroupBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intents.Intent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intents.IntentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intents.IntentKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.types.rev150122.Uuid;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;


public class IntentServiceManager {

    /**
     * This class is used to build Intents object and
     * write it to Network Intent Composition md-sal tree
     * in order to create/delete intents between two endpoint groups.
     */

    private static final Logger LOG = LoggerFactory.getLogger(IntentServiceManager.class);
    private static final short FIRST_SUBJECT = 1;
    private static final short SECOND_SUBJECT = 2;
    public static final String ACTION_ALLOW = "ALLOW";
    public static final String ACTION_BLOCK = "BLOCK";
    public static final String FAST_REROUTE = "fast-reroute";
    public static final String SLOW_REROUTE = "slow-reroute";
    private final DataBroker dataBroker;
    private static final InstanceIdentifier<Intents> INTENTS_IID = IidFactory.getIntentsIid();
    private MdsalUtils mdsal;

    public IntentServiceManager(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.mdsal = new MdsalUtils(dataBroker);
    }

    /**
     * Create Intents object and write to to config tree to trigger intents
     * @param src :Source Site Name
     * @param dst :Destination Site Name
     * @param intentAction :Intent verb: ALLOW or BLOCK
     * @param failOverType
     */
    public void addIntent(String src, String dst, String intentAction, String failOverType) {
        Preconditions.checkNotNull(src);
        Preconditions.checkNotNull(dst);
        Preconditions.checkNotNull(intentAction);

        List<Intent> intentList = null;
        List<Subjects> subjects = createSubjects(dst, src);
        List<Actions> intentActions = createActions(intentAction);
        List<Constraints> intentConstraints = createConstraints(failOverType);

        Intent intent  = new IntentBuilder().setId(new Uuid(UUID.randomUUID().toString()))
                .setSubjects(subjects).setActions(intentActions)
                .setConstraints(intentConstraints)
                .build();

        Intents currentIntents = mdsal.read(LogicalDatastoreType.CONFIGURATION, INTENTS_IID);
        if (currentIntents == null) {
            intentList = new ArrayList<>();
        } else {
            intentList = currentIntents.getIntent();
        }
        intentList.add(intent);
        Intents intents = new IntentsBuilder().setIntent(intentList).build();
        mdsal.put(LogicalDatastoreType.CONFIGURATION, INTENTS_IID, intents);
        LOG.info("AddIntent: config populated: {}", intents);
    }

    /**
     * Delete an Intent
     * @param id :Uuid of the Intent to be deleted
     */
    public void removeIntent(Uuid id) {
        Preconditions.checkNotNull(id);
        InstanceIdentifier<Intent> iid = InstanceIdentifier.create(Intents.class).child(Intent.class, new IntentKey(id));
        mdsal.delete(LogicalDatastoreType.CONFIGURATION, iid);
        LOG.info("RemoveIntent succeeded");
    }

    /**
     * Remove all associated intents by endpointGroupName
     * @param endpointGroupName
     */
    public void removeIntentsByEndpoint(String endpointGroupName) {
        Preconditions.checkNotNull(endpointGroupName);

        Intents intents = mdsal.read(LogicalDatastoreType.CONFIGURATION, INTENTS_IID);

        if (intents != null && intents.getIntent() != null) {
            for (Intent intent : intents.getIntent()) {
                if (intent.getSubjects() != null && intent.getSubjects().size() > 0) {
                    String endpointValue = "";
                    for (Subjects subject : intent.getSubjects()) {
                        if (subject
                                .getSubject() instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroup) {
                            org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroup epg = (org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroup) subject
                                    .getSubject();
                            endpointValue = epg.getEndPointGroup().getName();
                        } else if (subject
                                .getSubject() instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointSelector) {
                            org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointSelector epg = (org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointSelector) subject
                                    .getSubject();
                            endpointValue = epg.getEndPointSelector().getEndPointSelector();
                        } else if (subject
                                .getSubject() instanceof org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroupSelector) {
                            org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroupSelector epg = (org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroupSelector) subject
                                    .getSubject();
                            endpointValue = epg.getEndPointGroupSelector().getEndPointGroupSelector();
                        }
                        if (endpointValue.equalsIgnoreCase(endpointGroupName)) {
                            removeIntent(intent.getId());
                            LOG.info("Deleted Intent ID : {} for endpoint: {}", intent.getId(), endpointGroupName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Create a list of Intent actions
     * @param intentAction
     * @return :a list of Actions
     */
    private List<Actions> createActions(String intentAction) {
        List<Actions> actionsList = new ArrayList<Actions>();
        short order = 1;
        org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.actions.Action action = null;
        if (intentAction.equalsIgnoreCase(ACTION_ALLOW)) {
            action = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.actions.action
                    .AllowBuilder().setAllow(new AllowBuilder().build()).build();
        } else if (intentAction.equalsIgnoreCase(ACTION_BLOCK)) {
            action = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.actions.action
                    .BlockBuilder().setBlock(new BlockBuilder().build()).build();
        }

        Actions intentActions = new ActionsBuilder().setOrder(order).setAction(action).build();
        actionsList.add(intentActions);
        return actionsList;
    }

    /**
     * Create a list of Intent subjects
     * @param src :Source Site Name
     * @param dst :Destination Site Name
     * @return :a list of Subjects
     */
    private List<Subjects> createSubjects(String src, String dst) {
        List<Subjects> subjectList = new ArrayList<Subjects>();

        EndPointGroup endpointGroupFrom = new EndPointGroupBuilder().setName(src).build();
        org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroup fromEPG =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject
                .EndPointGroupBuilder().setEndPointGroup(endpointGroupFrom).build();
        Subjects subjects1 = new SubjectsBuilder().setOrder(FIRST_SUBJECT).setSubject(fromEPG).build();

        EndPointGroup endpointGroupTo = new EndPointGroupBuilder().setName(dst).build();
        org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject.EndPointGroup toEPG =
                new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.subjects.subject
                .EndPointGroupBuilder().setEndPointGroup(endpointGroupTo).build();
        Subjects subjects2 = new SubjectsBuilder().setOrder(SECOND_SUBJECT).setSubject(toEPG).build();

        subjectList.add(subjects1);
        subjectList.add(subjects2);
        return subjectList;
    }

    /**
     * Create a list of Intent constraints
     * @param failOverType :Type of failover, fast-reroute or slow-reroute
     * @return
     */
    private List<Constraints> createConstraints(String failOverType) {
        List<Constraints> intentConstraints = new ArrayList<Constraints>();
        if (failOverType==null) {
            return intentConstraints;
        }
        short order = 1;
        org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints.Constraints
            protectionConstraint = null;
        org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints.Constraints
            failoverConstraint = null;
        if (failOverType.equals(FAST_REROUTE)) {
            protectionConstraint = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent
                    .constraints.constraints.ProtectionConstraintBuilder()
                    .setProtectionConstraint(new ProtectionConstraintBuilder().setIsProtected(true).build()).build();
            failoverConstraint = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints
                    .constraints.FailoverConstraintBuilder()
                    .setFailoverConstraint(new FailoverConstraintBuilder().setFailoverSelector(FailoverType.FastReroute).build())
                    .build();
        } else if (failOverType.equals(SLOW_REROUTE)) {
            protectionConstraint = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent
                    .constraints.constraints.ProtectionConstraintBuilder()
                    .setProtectionConstraint(new ProtectionConstraintBuilder().setIsProtected(true).build()).build();
            failoverConstraint = new org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intent.constraints
                    .constraints.FailoverConstraintBuilder()
                    .setFailoverConstraint(new FailoverConstraintBuilder().setFailoverSelector(FailoverType.SlowReroute).build())
                    .build();
        }
        Constraints constraint1 = new ConstraintsBuilder().setOrder(order).setConstraints(protectionConstraint).build();
        Constraints constraint2 = new ConstraintsBuilder().setOrder(++order).setConstraints(failoverConstraint).build();
        intentConstraints.add(constraint1);
        intentConstraints.add(constraint2);
        return intentConstraints;
    }
}
