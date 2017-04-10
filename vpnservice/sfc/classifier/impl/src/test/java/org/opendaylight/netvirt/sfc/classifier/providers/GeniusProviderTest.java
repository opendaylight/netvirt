/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;

@RunWith(MockitoJUnitRunner.class)
public class GeniusProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    @Mock
    private RpcProviderRegistry rpcProviderRegistry;
    private GeniusProvider geniusProvider;

    @Before
    public void setUp() throws Exception {
        // Mock the rpcProviderRegistry.getRpcService() method
        // to return an instance of TestOdlInterfaceRpcService
        when(rpcProviderRegistry.getRpcService(any())).thenReturn(TestOdlInterfaceRpcService.newInstance());
        geniusProvider = new GeniusProvider(getDataBroker(), rpcProviderRegistry, TestInterfaceManager.newInstance());
    }

    @Test
    public void bindPortOnIngressClassifier() {
        // void bindPortOnIngressClassifier(String interfaceName)
    }

    @Test
    public void bindPortOnEgressClassifier() {
        // void bindPortOnEgressClassifier(String interfaceName)
    }

    @Test
    public void unbindPortOnIngressClassifier() {
        // void unbindPortOnIngressClassifier(String interfaceName)
    }

    @Test
    public void unbindPortOnEgressClassifier() {
        // void unbindPortOnEgressClassifier(String interfaceName)
    }

    @Test
    public void getNodeIdFromLogicalInterface() {
        //Optional<NodeId> getNodeIdFromLogicalInterface(String logicalInterface)
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<NodeId> nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                TestOdlInterfaceRpcService.INTERFACE_NAME_NO_EXIST);
        assertFalse(nodeId.isPresent());

        // Test that it correctly handles RPC errors
        nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                TestOdlInterfaceRpcService.INTERFACE_NAME_INVALID);
        assertFalse(nodeId.isPresent());

        // Test that it correctly returns the DpnId when everything is correct
        nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                TestOdlInterfaceRpcService.INTERFACE_NAME);
        assertTrue(nodeId.isPresent());
        assertEquals(nodeId.get().getValue(), TestOdlInterfaceRpcService.NODE_ID);
    }

    @Test
    public void getNodeIdFromDpnId() {
        // Test that it correctly handles null input
        Optional<NodeId> nodeId = this.geniusProvider.getNodeIdFromDpnId(null);
        assertFalse(nodeId.isPresent());

        // Test that it correctly returns the nodeId when everything is correct
        nodeId = this.geniusProvider.getNodeIdFromDpnId(new DpnIdType(TestOdlInterfaceRpcService.DPN_ID));
        assertTrue(nodeId.isPresent());
        assertEquals(nodeId.get().getValue(), TestOdlInterfaceRpcService.NODE_ID);
    }

    @Test
    public void getIpFromInterfaceName() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<String> ipStr = this.geniusProvider.getIpFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_NO_EXIST);
        assertFalse(ipStr.isPresent());

        // Test that it correctly handles RPC errors
        ipStr = this.geniusProvider.getIpFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_INVALID);
        assertFalse(ipStr.isPresent());

        // Test that it correctly returns the ipStr when everything is correct
        ipStr = this.geniusProvider.getIpFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME);
        assertTrue(ipStr.isPresent());
        assertEquals(ipStr.get(), TestOdlInterfaceRpcService.IPV4_ADDRESS_STR);
    }

    @Test
    public void getDpnIdFromInterfaceName() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<DpnIdType> dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_NO_EXIST);
        assertFalse(dpnId.isPresent());

        // Test that it correctly handles RPC errors
        dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_INVALID);
        assertFalse(dpnId.isPresent());

        // Test that it correctly returns the DpnId when everything is correct
        dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME);
        assertTrue(dpnId.isPresent());
        assertEquals(dpnId.get().getValue(), TestOdlInterfaceRpcService.DPN_ID);
    }

    @Test
    public void getNodeConnectorIdFromInterfaceName() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<String> nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_NO_EXIST);
        assertFalse(nodeConnStr.isPresent());

        // Test that it correctly handles RPC errors
        nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME_INVALID);
        assertFalse(nodeConnStr.isPresent());

        // Test that it correctly returns the NodeConnectorId when everything is correct
        nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                TestOdlInterfaceRpcService.INTERFACE_NAME);
        assertTrue(nodeConnStr.isPresent());
        assertEquals(nodeConnStr.get(), TestOdlInterfaceRpcService.NODE_CONNECTOR_ID_PREFIX
                + TestOdlInterfaceRpcService.INTERFACE_NAME);
    }

    @Test
    public void getEgressVxlanPortForNode() {
        // Test that it correctly handles the case when the dpnId doesnt exist
        Optional<Long> ofPort = this.geniusProvider.getEgressVxlanPortForNode(TestInterfaceManager.DPN_ID_NO_EXIST);
        assertFalse(ofPort.isPresent());

        // Test that it correctly handles when there are no tunnel ports on the bridge
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(TestInterfaceManager.DPN_ID_NO_PORTS);
        assertFalse(ofPort.isPresent());

        // Test that it correctly handles when there are no VXGPE tunnel ports on the bridge
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(TestInterfaceManager.DPN_ID_NO_VXGPE_PORTS);
        assertFalse(ofPort.isPresent());

        // Test that is correctly handles when a terminationPoint has no options
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(TestInterfaceManager.DPN_ID_NO_OPTIONS);
        assertFalse(ofPort.isPresent());

        // Test that it correctly returns the OpenFlow port when everything is correct
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(TestInterfaceManager.DPN_ID);
        assertTrue(ofPort.isPresent());
        assertEquals(ofPort.get().longValue(), TestInterfaceManager.OF_PORT);
    }
}
