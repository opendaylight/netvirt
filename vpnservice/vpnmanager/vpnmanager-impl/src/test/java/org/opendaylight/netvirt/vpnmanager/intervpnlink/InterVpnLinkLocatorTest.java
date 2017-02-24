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
// import static org.opendaylight.netvirt.vpnmanager.intervpnlink.L3VpnTestCatalog.ALL_VPNS;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import org.opendaylight.controller.md.sal.binding.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.vpnmanager.VpnOperDsUtils;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.L3VpnTestCatalog.L3VpnComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
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
public class InterVpnLinkLocatorTest extends AbstractConcurrentDataBrokerTest {

    static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkLocatorTest.class);

    @Mock DataBroker dataBroker;
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

        dataBroker = getDataBroker();

        // Creating both empty containers: InterVpnLinks and InterVpnLinkStates
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.builder(InterVpnLinks.class).build(),
                      new InterVpnLinksBuilder().setInterVpnLink(Collections.emptyList()).build(), true);
        writeTx.merge(LogicalDatastoreType.CONFIGURATION, InstanceIdentifier.builder(InterVpnLinkStates.class).build(),
                      new InterVpnLinkStatesBuilder().setInterVpnLinkState(Collections.emptyList()).build(), true);
        writeTx.submit().checkedGet();

        InterVpnLinkCache.createInterVpnLinkCaches(dataBroker);

        // Prepare
        populateL3Vpns(dataBroker, L3VpnTestCatalog.ALL_VPNS);
        populateIvpnLinks(dataBroker, ALL_IVPN_LINKS);

        for (InterVpnLinkDataComposite ivl : ALL_IVPN_LINKS) {
            InterVpnLinkCache.addInterVpnLinkToCaches(ivl.getInterVpnLinkConfig());
            InterVpnLinkCache.addInterVpnLinkStateToCaches(ivl.getInterVpnLinkState());
        }
//
//        for (L3VpnComposite vpn : ALL_VPNS) {
//            stubGetVpnRd(readTx, vpn);
//            stubGetVpnInstanceOpData(readTx, vpn);
//        }

        // SUT
        sut = new InterVpnLinkLocator(dataBroker);
    }

    private void populateL3Vpns(DataBroker broker, List<L3VpnComposite> vpns) throws TransactionCommitFailedException {
        for (L3VpnComposite vpn : vpns) {
            VpnInstance vpnInstance = new VpnInstanceBuilder().setVpnId(vpn.vpnOpData.getVpnId())
                .setVpnInstanceName(vpn.vpnOpData.getVpnInstanceName())
                .setVrfId(vpn.vpnOpData.getVrfId())
                .build();
            WriteTransaction writeTx1 = broker.newWriteOnlyTransaction();
            writeTx1.merge(LogicalDatastoreType.CONFIGURATION,
                          VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpn.vpnCfgData.getVpnInstanceName()),
                          vpnInstance, true);
            writeTx1.submit().checkedGet();
            WriteTransaction writeTx2 = broker.newWriteOnlyTransaction();
            writeTx2.merge(LogicalDatastoreType.OPERATIONAL,
                          VpnUtil.getVpnInstanceOpDataIdentifier(vpn.vpnOpData.getVrfId()), vpn.vpnOpData, true);
            writeTx2.submit().checkedGet();
        }
    }

    private void cleanL3Vpns(DataBroker broker, List<L3VpnComposite> vpns) throws TransactionCommitFailedException {
        for (L3VpnComposite vpn : vpns) {
            WriteTransaction writeTx1 = broker.newWriteOnlyTransaction();
            writeTx1.delete(LogicalDatastoreType.OPERATIONAL,
                           VpnUtil.getVpnInstanceOpDataIdentifier(vpn.vpnOpData.getVrfId()));
            writeTx1.submit().checkedGet();

            WriteTransaction writeTx2 = broker.newWriteOnlyTransaction();
            writeTx2.delete(LogicalDatastoreType.CONFIGURATION,
                           VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpn.vpnCfgData.getVpnInstanceName()));
            writeTx2.submit().checkedGet();
        }
    }

    private void populateIvpnLinks(DataBroker broker2, List<InterVpnLinkDataComposite> ivpnLinks)
        throws TransactionCommitFailedException {

        for (InterVpnLinkDataComposite ivpnLink : ivpnLinks) {
            WriteTransaction writeTx1 = broker2.newWriteOnlyTransaction();
            writeTx1.merge(LogicalDatastoreType.CONFIGURATION,
                          InterVpnLinkUtil.getInterVpnLinkPath(ivpnLink.getInterVpnLinkName()),
                          ivpnLink.getInterVpnLinkConfig());
            writeTx1.submit().checkedGet();
            WriteTransaction writeTx2 = broker2.newWriteOnlyTransaction();
            writeTx2.merge(LogicalDatastoreType.OPERATIONAL,
                           InterVpnLinkUtil.getInterVpnLinkStateIid(ivpnLink.getInterVpnLinkName()),
                           ivpnLink.getInterVpnLinkState());
            writeTx2.submit().checkedGet();
        }
    }

    private void cleanIvpnLinks(DataBroker broker2, InterVpnLinkDataComposite... ivpnLinks)
        throws TransactionCommitFailedException {

        for (InterVpnLinkDataComposite ivpnLink : ivpnLinks) {
            WriteTransaction writeTx1 = broker2.newWriteOnlyTransaction();
            writeTx1.delete(LogicalDatastoreType.OPERATIONAL,
                            InterVpnLinkUtil.getInterVpnLinkStateIid(ivpnLink.getInterVpnLinkName()));
            writeTx1.submit().checkedGet();

            WriteTransaction writeTx2 = broker2.newWriteOnlyTransaction();
            writeTx2.delete(LogicalDatastoreType.CONFIGURATION,
                            InterVpnLinkUtil.getInterVpnLinkPath(ivpnLink.getInterVpnLinkName()));
            writeTx2.submit().checkedGet();

        }
    }

    @Test
    public void testFindInterVpnLinksSameGroup() throws TransactionCommitFailedException {

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

        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink34Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink34Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
    }

    //////////////
    // Stubbing //
    //////////////

    private void stubNwUtilsGetOperativeDpns(int maxNbrOfOperativeDpns) throws Exception {
        WriteTransaction writeTx1 = dataBroker.newWriteOnlyTransaction();
        for (int i = 1; i <= maxNbrOfOperativeDpns; i++) {
            NodeId nodeId = new NodeId("openflow:" + i);
            Node node = new NodeBuilder().setId(nodeId).build();
            writeTx1.merge(LogicalDatastoreType.OPERATIONAL,
                          InstanceIdentifier.builder(Nodes.class).child(Node.class, new NodeKey(nodeId)).build(), node);
        }
        writeTx1.submit().checkedGet();
    }

    private void stubNwUtilsGetOperativeDpns_bak(int maxNbrOfOperativeDpns) throws Exception {

        List<Node> nodeList = new ArrayList<>();
        for (int i = 1; i <= maxNbrOfOperativeDpns; i++) {
            nodeList.add(new NodeBuilder().setId(new NodeId("openflow:" + i)).build());
        }
        CheckedFuture chkdFuture = mock(CheckedFuture.class);
        when(chkdFuture.checkedGet()).thenReturn(Optional.of(new NodesBuilder().setNode(nodeList).build()));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), eq(InstanceIdentifier.builder(Nodes.class).build())))
            .thenReturn(chkdFuture);

    }


    private void stubGetVpnRd(ReadOnlyTransaction readTx, L3VpnComposite vpn) throws Exception {

        VpnInstance vpnInstance = new VpnInstanceBuilder().setVpnId(vpn.vpnOpData.getVpnId())
                                                          .setVpnInstanceName(vpn.vpnOpData.getVpnInstanceName())
                                                          .setVrfId(vpn.vpnOpData.getVrfId())
                                                          .build();
        CheckedFuture chkdFuture = mock(CheckedFuture.class/*, Mockito.withSettings().verboseLogging()*/);
        when(chkdFuture.get()).thenReturn(Optional.of(vpnInstance));
        when(readTx.read(eq(LogicalDatastoreType.CONFIGURATION),
                         eq(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpn.vpnCfgData.getVpnInstanceName()))))
            .thenReturn(chkdFuture);
    }

    private void stubGetVpnInstanceOpData(ReadOnlyTransaction readTx, L3VpnComposite vpn) throws Exception {
        CheckedFuture chkdFuture = mock(CheckedFuture.class/*, Mockito.withSettings().verboseLogging()*/);
        when(chkdFuture.get()).thenReturn(Optional.of(vpn.vpnOpData));
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL),
                         eq(VpnUtil.getVpnInstanceOpDataIdentifier(vpn.vpnOpData.getVrfId()))))
            .thenReturn(chkdFuture);

    }
}
