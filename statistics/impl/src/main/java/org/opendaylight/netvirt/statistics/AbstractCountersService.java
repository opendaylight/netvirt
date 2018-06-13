/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;

public abstract class AbstractCountersService {

    protected final DataBroker db;
    protected final IInterfaceManager interfaceManager;
    protected final IMdsalApiManager mdsalManager;

    public AbstractCountersService(DataBroker db, IInterfaceManager interfaceManager, IMdsalApiManager mdsalManager) {
        this.db = db;
        this.interfaceManager = interfaceManager;
        this.mdsalManager = mdsalManager;
    }

    public abstract void bind(String interfaceId);

    public abstract void unBindService(String interfaceId);

    public abstract void installDefaultCounterRules(String interfaceId);

    public abstract void syncCounterFlows(ElementCountersRequest ecr, int operation);

    public void bindService(String interfaceId) {
        bind(interfaceId);
        installDefaultCounterRules(interfaceId);
    }

    public void installCounterRules(ElementCountersRequest ecr) {
        syncCounterFlows(ecr, NwConstants.ADD_FLOW);
    }

    public void deleteCounterRules(ElementCountersRequest ecr) {
        syncCounterFlows(ecr, NwConstants.DEL_FLOW);
    }

    protected void syncFlow(BigInteger dpId, short tableId, String flowId, int priority, String flowName,
            int idleTimeOut, int hardTimeOut, BigInteger cookie, List<? extends MatchInfoBase> matches,
            List<InstructionInfo> instructions, int operation) {
        if (NwConstants.DEL_FLOW == operation) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, null);
            mdsalManager.removeFlow(flowEntity);
        } else if (NwConstants.ADD_FLOW == operation) {
            FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, tableId, flowId, priority, flowName, idleTimeOut,
                    hardTimeOut, cookie, matches, instructions);
            mdsalManager.installFlow(flowEntity);
        }
    }

}
