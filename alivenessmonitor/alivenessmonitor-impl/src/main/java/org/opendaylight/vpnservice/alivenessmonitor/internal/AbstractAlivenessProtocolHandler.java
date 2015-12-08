/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetNodeconnectorIdFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetNodeconnectorIdFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rpcs.rev151003.GetNodeconnectorIdFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;

import com.google.common.base.Optional;
import com.google.common.base.Strings;

abstract class AbstractAlivenessProtocolHandler implements AlivenessProtocolHandler {

    protected ServiceProvider serviceProvider;
    private InventoryReader inventoryReader;

    public AbstractAlivenessProtocolHandler(ServiceProvider serviceProvider) {
        this.serviceProvider = serviceProvider;
        inventoryReader = new InventoryReader(serviceProvider.getDataBroker());
    }

    private InstanceIdentifier<NodeConnector> getNodeConnectorId(String interfaceName) {
        InstanceIdentifier<Interface> id =  InstanceIdentifier.builder(Interfaces.class)
                                      .child(Interface.class, new InterfaceKey(interfaceName)).build();

        Optional<Interface> port = read(LogicalDatastoreType.CONFIGURATION, id);
        if(port.isPresent()) {
            NodeConnectorId ncId = getNodeConnectorIdFromInterface(interfaceName);
            NodeId nodeId = getNodeIdFromNodeConnectorId(ncId);

            InstanceIdentifier<NodeConnector> ncIdentifier =
                                          InstanceIdentifier.builder(Nodes.class)
                                                      .child(Node.class, new NodeKey(nodeId))
                                                      .child(NodeConnector.class, new NodeConnectorKey(ncId)).build();
            return ncIdentifier;
        }
        return null;
    }

    private NodeConnectorId getNodeConnectorIdFromInterface(String interfaceName) {
        GetNodeconnectorIdFromInterfaceInput input = new GetNodeconnectorIdFromInterfaceInputBuilder().setIntfName(interfaceName).build();
        Future<RpcResult<GetNodeconnectorIdFromInterfaceOutput>> output =  serviceProvider.getInterfaceManager().getNodeconnectorIdFromInterface(input);
        RpcResult<GetNodeconnectorIdFromInterfaceOutput> result = null;
        try {
             result = output.get();
             if(result.isSuccessful()) {
                 GetNodeconnectorIdFromInterfaceOutput ncIdOutput = result.getResult();
                 return ncIdOutput.getNodeconnectorId();
             }
        } catch(ExecutionException | InterruptedException e) {
            //TODO: Handle exception
        }

        return null;
    }

    private NodeId getNodeIdFromNodeConnectorId(NodeConnectorId ncId) {
        return new NodeId(ncId.getValue().substring(0,ncId.getValue().lastIndexOf(":")));
    }

    protected byte[] getMacAddress(String interfaceName) {
        InstanceIdentifier<NodeConnector> ncId = getNodeConnectorId(interfaceName);
        if(ncId != null) {
            String macAddress = inventoryReader.getMacAddress(ncId);
            if(!Strings.isNullOrEmpty(macAddress)) {
                return AlivenessMonitorUtil.parseMacAddress(macAddress);
            }
        }
        return null;
    }

    private InstanceIdentifier<Interface> getInterfaceIdentifier(InterfaceKey interfaceKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> interfaceInstanceIdentifierBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, interfaceKey);
        return interfaceInstanceIdentifierBuilder.build();
    }

    protected Interface getInterfaceFromConfigDS(String interfaceName) {
        InterfaceKey interfaceKey = new InterfaceKey(interfaceName);
        InstanceIdentifier<Interface> interfaceId = getInterfaceIdentifier(interfaceKey);
        Optional<Interface> interfaceOptional = read(LogicalDatastoreType.CONFIGURATION, interfaceId);
        if (!interfaceOptional.isPresent()) {
            return null;
        }

        return interfaceOptional.get();
    }


    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = serviceProvider.getDataBroker().newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tx.close();
        }

        return result;
    }

}
