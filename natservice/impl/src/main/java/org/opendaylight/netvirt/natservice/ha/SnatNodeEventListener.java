/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.ha;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataTreeIdentifier;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionDrop;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.tools.mdsal.listener.AbstractClusteredAsyncDataTreeChangeListener;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig.NatMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CentralizedSwitchChangeListener adds/removes the switches to scheduler pool when a switch is
 * added/removed.
 */
@Singleton
public class SnatNodeEventListener  extends AbstractClusteredAsyncDataTreeChangeListener<Node> {
    private static final Logger LOG = LoggerFactory.getLogger(SnatNodeEventListener.class);
    private final CentralizedSwitchScheduler  centralizedSwitchScheduler;
    private final NatMode natmode;
    private final IMdsalApiManager mdsalApiManager;

    @Inject
    public SnatNodeEventListener(final DataBroker dataBroker,
                                 final CentralizedSwitchScheduler centralizedSwitchScheduler,
                                 final IMdsalApiManager mdsalApiManager,
                                 final NatserviceConfig config) {

        super(dataBroker,new DataTreeIdentifier<>(LogicalDatastoreType.OPERATIONAL, InstanceIdentifier
                .create(Nodes.class).child(Node.class)),
                Executors.newSingleThreadExecutor());
        this.centralizedSwitchScheduler = centralizedSwitchScheduler;
        this.mdsalApiManager = mdsalApiManager;
        if (config != null) {
            this.natmode = config.getNatMode();
        } else {
            this.natmode = NatMode.Controller;
        }
    }



    @Override
    public void remove(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.getKey();
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        LOG.info("Dpn removed {}", dpnId);
        centralizedSwitchScheduler.removeSwitch(dpnId);
    }

    @Override
    public void update(Node dataObjectModificationBefore,
            Node dataObjectModificationAfter) {
        /*Do Nothing */
    }

    @Override
    public void add(Node dataObjectModification) {
        NodeKey nodeKey = dataObjectModification.getKey();
        BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        LOG.info("Dpn added {}", dpnId);
        centralizedSwitchScheduler.addSwitch(dpnId);
        if (natmode == NatMode.Controller) {
            addIcmpDropFlow(dpnId);
        }
    }

    private String getFlowRef(BigInteger dpnId, short tableId, String prefix) {
        return new StringBuffer().append(dpnId).append(NwConstants.FLOWID_SEPARATOR).append(tableId)
                .append(NwConstants.FLOWID_SEPARATOR).append(prefix).toString();
    }

    private void addIcmpDropFlow(BigInteger dpnId) {
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        matches.add(MatchIpProtocol.ICMP);

        List<ActionInfo> actionInfos = new ArrayList<>();
        actionInfos.add(new ActionDrop());

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionInfos));

        String flowRef = getFlowRef(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, "icmp.drop");
        FlowEntity flow = MDSALUtil.buildFlowEntity(dpnId, NwConstants.OUTBOUND_NAPT_TABLE, flowRef,
                NwConstants.TABLE_MISS_PRIORITY, "icmp drop flow", 0, 0,
                NwConstants.COOKIE_OUTBOUND_NAPT_TABLE, matches, instructions);
        mdsalApiManager.installFlow(flow);
    }
}
