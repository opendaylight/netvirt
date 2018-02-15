/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import java.util.List;
import java.util.concurrent.Future;

import org.mockito.Mockito;
import org.opendaylight.genius.infra.FutureRpcResults;
import org.opendaylight.genius.interfacemanager.IfmUtil;
import org.opendaylight.genius.interfacemanager.commons.InterfaceManagerCommonUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class ElanEgressActionsHelper implements OdlInterfaceRpcService {
    private static final Logger LOG = LoggerFactory.getLogger(ElanEgressActionsHelper.class);
    private InterfaceManagerCommonUtils interfaceManagerCommonUtils;

    public static ElanEgressActionsHelper newInstance(InterfaceManagerCommonUtils interfaceManagerCommonUtils) {
        ElanEgressActionsHelper instance = Mockito.mock(ElanEgressActionsHelper.class, realOrException());
        instance.interfaceManagerCommonUtils = interfaceManagerCommonUtils;
        return instance;
    }

    @Override
    public Future<RpcResult<GetEgressActionsForInterfaceOutput>> getEgressActionsForInterface(
            GetEgressActionsForInterfaceInput input) {
        return FutureRpcResults.fromBuilder(LOG, input, () -> {
            List<Action> actionsList = IfmUtil.getEgressActionsForInterface(input.getIntfName(), input.getTunnelKey(),
                    input.getActionKey(), interfaceManagerCommonUtils, false);
            return new GetEgressActionsForInterfaceOutputBuilder().setAction(actionsList);
        }).build();
    }
}
