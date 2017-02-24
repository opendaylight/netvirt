/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.ALL_IVPN_LINKS;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_12;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_34;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_56;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.L3VpnCatalog.ALL_VPNS;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.VpnOperDsUtils;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.L3VpnCatalog.L3VpnComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStatesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinksBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.class)
public class InterVpnLinkLocatorTest {

    static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkLocatorTest.class);

    @Mock DataBroker broker;
    @Mock ReadOnlyTransaction readTx;
    @Mock WriteTransaction writeTx;

    InterVpnLinkLocator sut;


    private <T extends DataObject> Matcher<InstanceIdentifier<T>> isIIdType(final Class<T> klass) {
        return new TypeSafeMatcher<InstanceIdentifier<T>>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("Instance Identifier should have Target Type " + klass);
            }

            @Override
            protected boolean matchesSafely(InstanceIdentifier<T> id) {
                return id.getTargetType().equals(klass);
            }
        };
    }


    @Before
    public void setUp() throws Exception {

        when(broker.newReadOnlyTransaction()).thenReturn(readTx);
        when(broker.newWriteOnlyTransaction()).thenReturn(writeTx);
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(writeTx.submit()).thenReturn(chkdFuture);

        // Feed Cache. These next lines are used to 'bypass' the InterVpnLinkCache.initialFeed()
        CheckedFuture chkdFuture2 = mock(CheckedFuture.class);
        when(chkdFuture2.checkedGet())
            .thenReturn(Optional.of(new InterVpnLinksBuilder().setInterVpnLink(Collections.EMPTY_LIST).build()));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(InstanceIdentifier.builder(InterVpnLinks.class).build())))
            .thenReturn(chkdFuture2);

        CheckedFuture chkdFuture3 = mock(CheckedFuture.class);
        when(chkdFuture3.checkedGet())
            .thenReturn(Optional.of(new InterVpnLinkStatesBuilder().setInterVpnLinkState(Collections.EMPTY_LIST)
                                                                   .build()));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(InstanceIdentifier.builder(InterVpnLinkStates.class).build()))).thenReturn(chkdFuture3);
        InterVpnLinkCache.createInterVpnLinkCaches(broker);

        ALL_IVPN_LINKS.stream().forEach(ivl -> {
            InterVpnLinkCache.addInterVpnLinkToCaches(ivl.getInterVpnLinkConfig());
            InterVpnLinkCache.addInterVpnLinkStateToCaches(ivl.getInterVpnLinkState());
        });

        ALL_VPNS.stream().forEach(vpn -> {
            stubGetVpnRd(readTx, vpn);
            stubGetVpnInstanceOpData(readTx, vpn);
        });

        // SUT
        sut = new InterVpnLinkLocator(broker);
    }


    @Test
    public void testFindInterVpnLinksSameGroup() {

        // I_VPN_LINK_56 is similar to I_VPN_LINK_12 (both link VPNs with same iRTs)
        List<InterVpnLinkDataComposite> ivpnLinksSimilarTo12 =
            sut.findInterVpnLinksSameGroup(I_VPN_LINK_12.getInterVpnLinkConfig(), ALL_IVPN_LINKS);
        assertTrue(ivpnLinksSimilarTo12.size() == 1);
        assertEquals(ivpnLinksSimilarTo12.get(0), I_VPN_LINK_56);

        // There's no InterVpnLink similar to I_VPN_LINK_34
        List<InterVpnLinkDataComposite> ivpnLinksSimilarTo34 =
            sut.findInterVpnLinksSameGroup(I_VPN_LINK_34.getInterVpnLinkConfig(), ALL_IVPN_LINKS);

        assertTrue(ivpnLinksSimilarTo34.isEmpty());
    }

    @Test
    public void testSelectSuitableDpns_moreDpnsThanIvpnLinks() throws Exception {
        stubNwUtilsGetOperativeDpns(5); // 5 operative DPNs - 3 IvpnLinks

        System.setProperty(InterVpnLinkLocator.NBR_OF_DPNS_PROPERTY_NAME, "1");
        List<BigInteger> vpnLink12Dpns = sut.selectSuitableDpns(I_VPN_LINK_12.getInterVpnLinkConfig());
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_12, true, vpnLink12Dpns);
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_12, false, vpnLink12Dpns);
        InterVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_12.getInterVpnLinkState());

        List<BigInteger> vpnLink34Dpns = sut.selectSuitableDpns(I_VPN_LINK_34.getInterVpnLinkConfig());
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_34, true, vpnLink34Dpns);
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_34, false, vpnLink34Dpns);
        InterVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_34.getInterVpnLinkState());

        List<BigInteger> vpnLink56Dpns = sut.selectSuitableDpns(I_VPN_LINK_56.getInterVpnLinkConfig());
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_56, true, vpnLink56Dpns);
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_56, false, vpnLink56Dpns);
        InterVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_56.getInterVpnLinkState());

        // All lists must disjointed
        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink34Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink34Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
    }

    //////////////
    // Stubbing //
    //////////////

    private void stubNwUtilsGetOperativeDpns(int maxNbrOfOperativeDpns) throws Exception {

        List<Node> nodeList = new ArrayList<>();
        for (int i = 1; i <= maxNbrOfOperativeDpns; i++) {
            nodeList.add(new NodeBuilder().setId(new NodeId("openflow:" + i)).build());
        }
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.of(new NodesBuilder().setNode(nodeList).build()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), eq(InstanceIdentifier.builder(Nodes.class).build())))
            .thenReturn(chkdFuture);
    }


    private void stubGetVpnRd(ReadOnlyTransaction readTx, L3VpnComposite vpn) {

        VpnInstance vpnInstance = new VpnInstanceBuilder().setVpnId(vpn.vpnOpData.getVpnId())
                                                          .setVpnInstanceName(vpn.vpnOpData.getVpnInstanceName())
                                                          .setVrfId(vpn.vpnOpData.getVrfId())
                                                          .build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class/*, Mockito.withSettings().verboseLogging()*/);
        try {
            when(chkdFuture.get()).thenReturn(Optional.of(vpnInstance));
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            LOG.error("Error on chkdFuture.get()", e);
        }
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpn.vpnCfgData.getVpnInstanceName()))))
            .thenReturn(chkdFuture);
    }

    private void stubGetVpnInstanceOpData(ReadOnlyTransaction readTx, L3VpnComposite vpn) {
        CheckedFuture chkdFuture = mock(CheckedFuture.class/*, Mockito.withSettings().verboseLogging()*/);
        try {
            when(chkdFuture.get()).thenReturn(Optional.of(vpn.vpnOpData));
        } catch (InterruptedException | ExecutionException e) {
            // TODO Auto-generated catch block
            LOG.error("Error on chkdFuture.get()", e);
        }
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL),
                         eq(VpnUtil.getVpnInstanceOpDataIdentifier(vpn.vpnOpData.getVrfId()))))
            .thenReturn(chkdFuture);

    }
}
