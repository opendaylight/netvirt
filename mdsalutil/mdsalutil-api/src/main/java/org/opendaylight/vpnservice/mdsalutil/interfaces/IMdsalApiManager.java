/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil.interfaces;

import java.math.BigInteger;
import java.util.List;

import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.FlowEntity;
import org.opendaylight.vpnservice.mdsalutil.GroupEntity;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;

public interface IMdsalApiManager {

    public void installFlow(FlowEntity flowEntity);

    public CheckedFuture<Void,TransactionCommitFailedException> installFlow(BigInteger dpId, Flow flowEntity);

    public CheckedFuture<Void,TransactionCommitFailedException> removeFlow(BigInteger dpId, FlowEntity flowEntity);

    public void removeFlow(FlowEntity flowEntity);

    public void installGroup(GroupEntity groupEntity);

    public void modifyGroup(GroupEntity groupEntity);

    public void removeGroup(GroupEntity groupEntity);

    public void sendPacketOut(BigInteger dpnId, int groupId, byte[] payload);

    public void sendPacketOutWithActions(BigInteger dpnId, long groupId, byte[] payload, List<ActionInfo> actionInfos);

    public void sendARPPacketOutWithActions(BigInteger dpnId, byte[] payload, List<ActionInfo> action_info);

}
