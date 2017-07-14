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

import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class GeniusProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    private GeniusProvider geniusProvider;

    @Before
    public void setUp() throws Exception {
        geniusProvider = new GeniusProvider(getDataBroker(), TestOdlInterfaceRpcService.newInstance(),
                TestInterfaceManager.newInstance());
    }

    @Test
    public void bindPortOnIngressClassifier() {
        // Bind the Ingress service
        geniusProvider.bindPortOnIngressClassifier(GeniusProviderTestParams.INTERFACE_NAME);

        // Now make sure its in the data store
        InstanceIdentifier<BoundServices> id = geniusProvider.getBindServiceId(NwConstants.SFC_CLASSIFIER_INDEX,
                GeniusProviderTestParams.INTERFACE_NAME, true);
        Optional<BoundServices> boundServices = getBoundServices(id);
        assertTrue(boundServices.isPresent());

        // UnBind the Ingress Service
        geniusProvider.unbindPortOnIngressClassifier(GeniusProviderTestParams.INTERFACE_NAME);

        // Now make sure its NOT in the data store
        assertFalse(getBoundServices(id).isPresent());
    }

    @Test
    public void bindPortOnEgressClassifier() {
        // Bind the Egress service
        geniusProvider.bindPortOnEgressClassifier(
                GeniusProviderTestParams.INTERFACE_NAME,
                GeniusProviderTestParams.IPV4_ADDRESS_STR);

        // Now make sure its in the data store
        InstanceIdentifier<BoundServices> id = geniusProvider.getBindServiceId(
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                GeniusProviderTestParams.INTERFACE_NAME,
                false);
        Optional<BoundServices> boundServices = getBoundServices(id);
        assertTrue(boundServices.isPresent());

        // UnBind the Egress Service
        geniusProvider.unbindPortOnEgressClassifier(GeniusProviderTestParams.INTERFACE_NAME);

        // Now make sure its NOT in the data store
        assertFalse(getBoundServices(id).isPresent());
    }

    @Test
    public void getNodeIdFromLogicalInterface() {
        //Optional<NodeId> getNodeIdFromLogicalInterface(String logicalInterface)
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<NodeId> nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                GeniusProviderTestParams.INTERFACE_NAME_NO_EXIST);
        assertFalse(nodeId.isPresent());

        // Test that it correctly handles RPC errors
        nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                GeniusProviderTestParams.INTERFACE_NAME_INVALID);
        assertFalse(nodeId.isPresent());

        // Test that it correctly returns the DpnId when everything is correct
        nodeId = this.geniusProvider.getNodeIdFromLogicalInterface(
                GeniusProviderTestParams.INTERFACE_NAME);
        assertTrue(nodeId.isPresent());
        assertEquals(nodeId.get().getValue(), GeniusProviderTestParams.NODE_ID);
    }

    @Test
    public void getNodeIdFromDpnId() {
        // Test that it correctly handles null input
        Optional<NodeId> nodeId = this.geniusProvider.getNodeIdFromDpnId(null);
        assertFalse(nodeId.isPresent());

        // Test that it correctly returns the nodeId when everything is correct
        nodeId = this.geniusProvider.getNodeIdFromDpnId(new DpnIdType(GeniusProviderTestParams.DPN_ID));
        assertTrue(nodeId.isPresent());
        assertEquals(nodeId.get().getValue(), GeniusProviderTestParams.NODE_ID);
    }

    @Test
    public void getIpFromDpnId() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<String> ipStr = this.geniusProvider.getIpFromDpnId(
                new DpnIdType(GeniusProviderTestParams.DPN_ID_NO_EXIST));
        assertFalse(ipStr.isPresent());

        // Test that it correctly handles RPC errors
        ipStr = this.geniusProvider.getIpFromDpnId(
                new DpnIdType(GeniusProviderTestParams.DPN_ID_INVALID));
        assertFalse(ipStr.isPresent());

        // Test that it correctly returns the ipStr when everything is correct
        ipStr = this.geniusProvider.getIpFromDpnId(
                new DpnIdType(GeniusProviderTestParams.DPN_ID));
        assertTrue(ipStr.isPresent());
        assertEquals(ipStr.get(), GeniusProviderTestParams.IPV4_ADDRESS_STR);
    }

    @Test
    public void getDpnIdFromInterfaceName() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<DpnIdType> dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME_NO_EXIST);
        assertFalse(dpnId.isPresent());

        // Test that it correctly handles RPC errors
        dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME_INVALID);
        assertFalse(dpnId.isPresent());

        // Test that it correctly returns the DpnId when everything is correct
        dpnId = this.geniusProvider.getDpnIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME);
        assertTrue(dpnId.isPresent());
        assertEquals(dpnId.get().getValue(), GeniusProviderTestParams.DPN_ID);
    }

    @Test
    public void getNodeConnectorIdFromInterfaceName() {
        // Test that it correctly handles the case when the ifName doesnt exist
        Optional<String> nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME_NO_EXIST);
        assertFalse(nodeConnStr.isPresent());

        // Test that it correctly handles RPC errors
        nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME_INVALID);
        assertFalse(nodeConnStr.isPresent());

        // Test that it correctly returns the NodeConnectorId when everything is correct
        nodeConnStr = this.geniusProvider.getNodeConnectorIdFromInterfaceName(
                GeniusProviderTestParams.INTERFACE_NAME);
        assertTrue(nodeConnStr.isPresent());
        assertEquals(nodeConnStr.get(), GeniusProviderTestParams.NODE_CONNECTOR_ID_PREFIX
                + GeniusProviderTestParams.INTERFACE_NAME);
    }

    @Test
    public void getEgressVxlanPortForNode() {
        // Test that it correctly handles the case when the dpnId doesnt exist
        Optional<Long> ofPort = this.geniusProvider.getEgressVxlanPortForNode(
                GeniusProviderTestParams.DPN_ID_NO_EXIST);
        assertFalse(ofPort.isPresent());

        // Test that it correctly handles when there are no tunnel ports on the bridge
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(GeniusProviderTestParams.DPN_ID_NO_PORTS);
        assertFalse(ofPort.isPresent());

        // Test that it correctly handles when there are no VXGPE tunnel ports on the bridge
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(GeniusProviderTestParams.DPN_ID_NO_VXGPE_PORTS);
        assertFalse(ofPort.isPresent());

        // Test that is correctly handles when a terminationPoint has no options
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(GeniusProviderTestParams.DPN_ID_NO_OPTIONS);
        assertFalse(ofPort.isPresent());

        // Test that it correctly returns the OpenFlow port when everything is correct
        ofPort = this.geniusProvider.getEgressVxlanPortForNode(GeniusProviderTestParams.DPN_ID);
        assertTrue(ofPort.isPresent());
        assertEquals(ofPort.get().longValue(), GeniusProviderTestParams.OF_PORT);
    }

    Optional<BoundServices> getBoundServices(InstanceIdentifier<BoundServices> id) {
        return Optional.ofNullable(MDSALUtil.read(getDataBroker(), LogicalDatastoreType.CONFIGURATION, id).orNull());
    }

}
