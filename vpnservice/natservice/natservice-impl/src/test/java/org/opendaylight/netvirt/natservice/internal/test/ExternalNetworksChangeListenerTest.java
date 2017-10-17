/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.BucketInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.GroupEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.actions.ActionGroup;
import org.opendaylight.genius.mdsalutil.actions.ActionOutput;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.natservice.internal.ExternalNetworksChangeListener;
import org.opendaylight.netvirt.natservice.internal.ExternalRoutersListener;
import org.opendaylight.netvirt.natservice.internal.FloatingIPListener;
import org.opendaylight.netvirt.natservice.internal.NaptManager;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.config.rev170206.NatserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MDSALUtil.class)
public class ExternalNetworksChangeListenerTest {

    @Mock
    DataBroker dataBroker;
    @Mock
    IMdsalApiManager mdsalManager;
    @Mock
    FlowEntity flowMock;
    @Mock
    GroupEntity groupMock;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
        .external.networks.Networks> id = null;
    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
        .external.networks.Networks networks = null;
    private ExternalNetworksChangeListener extNetworks;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        extNetworks = new ExternalNetworksChangeListener(dataBroker,
            Mockito.mock(IMdsalApiManager.class),
            Mockito.mock(FloatingIPListener.class),
            Mockito.mock(ExternalRoutersListener.class),
            Mockito.mock(OdlInterfaceRpcService.class),
            Mockito.mock(NaptManager.class),
            Mockito.mock(IBgpManager.class),
            Mockito.mock(VpnRpcService.class),
            Mockito.mock(FibRpcService.class),
            Mockito.mock(NatserviceConfig.class),
            Mockito.mock(JobCoordinator.class));

        PowerMockito.mockStatic(MDSALUtil.class);
    }

    @Test
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void testSnatFlowEntity() {
        FlowEntity flowMock = mock(FlowEntity.class);
        final short snatTable = 40;
        final int defaultSnatFlowPriority = 0;
        final String flowidSeparator = ".";
        String routerName = "200";
        List<BucketInfo> bucketInfo = new ArrayList<>();
        List<ActionInfo> listActionInfoPrimary = new ArrayList<>();
        listActionInfoPrimary.add(new ActionOutput(new Uri("3")));
        BucketInfo bucketPrimary = new BucketInfo(listActionInfoPrimary);
        List<ActionInfo> listActionInfoSecondary = new ArrayList<>();
        listActionInfoSecondary.add(new ActionOutput(new Uri("4")));
        BucketInfo bucketSecondary = new BucketInfo(listActionInfoPrimary);
        bucketInfo.add(0, bucketPrimary);
        bucketInfo.add(1, bucketSecondary);

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        long groupId = 300;
        actionsInfos.add(new ActionGroup(groupId));
        instructions.add(new InstructionApplyActions(actionsInfos));

        BigInteger dpnId = new BigInteger("100");
        long routerId = 200;
        String snatFlowidPrefix = "SNAT.";
        String flowRef = snatFlowidPrefix + dpnId + flowidSeparator + snatTable + flowidSeparator + routerId;

        BigInteger cookieSnat = NatUtil.getCookieSnatFlow(routerId);
        try {
            PowerMockito.when(MDSALUtil.class, "buildFlowEntity", dpnId, snatTable, flowRef,
                defaultSnatFlowPriority, flowRef, 0, 0,
                cookieSnat, matches, instructions).thenReturn(flowMock);
        } catch (Exception e) {
            // Test failed anyways
            assertEquals("true", "false");
        }
        /* TODO : Fix this to mock it properly when it reads DS
        extNetworks.buildSnatFlowEntity(dpnId, routerName, groupId);
        PowerMockito.verifyStatic(); */

    }

}
