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
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.ALL_IVPN_LINKS;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_12;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_34;
import static org.opendaylight.netvirt.vpnmanager.intervpnlink.InterVpnLinkTestCatalog.I_VPN_LINK_56;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.vpnmanager.VpnOperDsUtils;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.intervpnlink.L3VpnTestCatalog.L3VpnComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStatesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinksBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterVpnLinkLocatorTest extends ConstantSchemaAbstractDataBrokerTest {

    static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkLocatorTest.class);

    DataBroker dataBroker;

    InterVpnLinkLocator sut;

    InterVpnLinkCacheImpl interVpnLinkCache;

    VpnUtil vpnUtil;

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

        interVpnLinkCache = new InterVpnLinkCacheImpl(dataBroker);
        interVpnLinkCache.initialFeed();

        // Prepare
        populateL3Vpns(dataBroker, L3VpnTestCatalog.ALL_VPNS);
        InterVpnLinkTestCatalog.populateIvpnLinks(dataBroker, ALL_IVPN_LINKS);

        for (InterVpnLinkDataComposite ivl : ALL_IVPN_LINKS) {
            interVpnLinkCache.addInterVpnLinkToCaches(ivl.getInterVpnLinkConfig());
            interVpnLinkCache.addInterVpnLinkStateToCaches(ivl.getInterVpnLinkState());
        }

        // SUT
        sut = new InterVpnLinkLocator(dataBroker, interVpnLinkCache, vpnUtil);
    }


    @Test
    @Ignore //TODO: Modify tests to bind instances using Guice Rules
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
        interVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_12.getInterVpnLinkState());

        List<BigInteger> vpnLink34Dpns = sut.selectSuitableDpns(I_VPN_LINK_34.getInterVpnLinkConfig());
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_34, true, vpnLink34Dpns);
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_34, false, vpnLink34Dpns);
        interVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_34.getInterVpnLinkState());

        List<BigInteger> vpnLink56Dpns = sut.selectSuitableDpns(I_VPN_LINK_56.getInterVpnLinkConfig());
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_56, true, vpnLink56Dpns);
        InterVpnLinkTestCatalog.updateEndpointDpns(I_VPN_LINK_56, false, vpnLink56Dpns);
        interVpnLinkCache.addInterVpnLinkStateToCaches(I_VPN_LINK_56.getInterVpnLinkState());

        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink34Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink12Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
        assertTrue(vpnLink34Dpns.stream().filter(dpn -> vpnLink56Dpns.contains(dpn)).count() == 0);
    }

    //////////////
    // Stubbing //
    //////////////

    public void populateL3Vpns(DataBroker broker, List<L3VpnComposite> vpns)
        throws TransactionCommitFailedException {
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

    public void cleanL3Vpns(DataBroker broker, List<L3VpnComposite> vpns)
        throws TransactionCommitFailedException {
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

}
