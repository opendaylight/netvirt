/*
 * Copyright © 2017 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.netvirt.elan.l2gw.utils.ElanL2GatewayUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEgressActionsForInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetEgressActionsForTunnelOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetExternalTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.GetTunnelInterfaceNameOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ElanItmUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ElanItmUtils.class);

    private final DataBroker broker;
    private final ItmRpcService itmRpcService;
    private final OdlInterfaceRpcService interfaceManagerRpcService;
    private final IInterfaceManager interfaceManager;

    @Inject
    public ElanItmUtils(final DataBroker broker, final ItmRpcService itmRpcService,
            final OdlInterfaceRpcService interfaceManagerRpcService, final IInterfaceManager interfaceManager) {
        this.broker = broker;
        this.itmRpcService = itmRpcService;
        this.interfaceManagerRpcService = interfaceManagerRpcService;
        this.interfaceManager = interfaceManager;
    }

    /**
     * Builds the list of actions to be taken when sending the packet over an
     * external VxLan tunnel interface, such as stamping the VNI on the VxLAN
     * header, setting the vlanId if it proceeds and output the packet over the
     * right port.
     *
     * @param srcDpnId
     *            Dpn where the tunnelInterface is located
     * @param torNode
     *            NodeId of the ExternalDevice where the packet must be sent to.
     * @param vni
     *            Vni to be stamped on the VxLAN Header.
     * @return the external itm egress action
     */
    public List<Action> getExternalTunnelItmEgressAction(BigInteger srcDpnId, NodeId torNode, long vni) {
        List<Action> result = Collections.emptyList();

        GetExternalTunnelInterfaceNameInput input = new GetExternalTunnelInterfaceNameInputBuilder()
                .setDestinationNode(torNode.getValue()).setSourceNode(srcDpnId.toString())
                .setTunnelType(TunnelTypeVxlan.class).build();
        Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = itmRpcService
                .getExternalTunnelInterfaceName(input);
        try {
            if (output.get().isSuccessful()) {
                GetExternalTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
                String tunnelIfaceName = tunnelInterfaceNameOutput.getInterfaceName();
                LOG.debug("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelIfaceName);

                result = buildTunnelItmEgressActions(tunnelIfaceName, vni);
            }

        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {}", e);
        }

        return result;
    }

    public List<Action> getExternalTunnelItmEgressAction(BigInteger srcDpnId, String nexthopIP, long vni) {
        List<Action> result = Collections.emptyList();

        GetExternalTunnelInterfaceNameInput input = new GetExternalTunnelInterfaceNameInputBuilder()
                .setDestinationNode(nexthopIP).setSourceNode(srcDpnId.toString())
                .setTunnelType(TunnelTypeVxlan.class).build();
        Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = itmRpcService
                .getExternalTunnelInterfaceName(input);
        try {
            if (output.get().isSuccessful()) {
                GetExternalTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
                String tunnelIfaceName = tunnelInterfaceNameOutput.getInterfaceName();
                LOG.debug("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelIfaceName);

                result = buildTunnelItmEgressActions(tunnelIfaceName, vni);
            }

        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {}", e);
        }

        return result;
    }

    /**
     * Builds the list of actions to be taken when sending the packet over an internal VxLAN tunnel interface, such
     * as setting the serviceTag/segmentationID on the VNI field of the VxLAN header, setting the vlanId if it proceeds
     * and output the packet over the right port.
     *
     * @param sourceDpnId
     *            Dpn where the tunnelInterface is located
     * @param destinationDpnId
     *            Dpn where the packet must be sent to. It is used here in order
     *            to select the right tunnel interface.
     * @param tunnelKey
     *            Tunnel key to be sent on the VxLAN header.
     * @return the internal itm egress action
     */
    public List<Action> getInternalTunnelItmEgressAction(BigInteger sourceDpnId, BigInteger destinationDpnId, long
            tunnelKey) {
        List<Action> result = Collections.emptyList();
        LOG.trace("In getInternalItmEgressAction Action source {}, destination {}, serviceTag/Vni {}", sourceDpnId,
                destinationDpnId, tunnelKey);
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        GetTunnelInterfaceNameInput input = new GetTunnelInterfaceNameInputBuilder()
                .setDestinationDpid(destinationDpnId).setSourceDpid(sourceDpnId).setTunnelType(tunType).build();
        Future<RpcResult<GetTunnelInterfaceNameOutput>> output = itmRpcService.getTunnelInterfaceName(input);
        try {
            if (output.get().isSuccessful()) {
                GetTunnelInterfaceNameOutput tunnelInterfaceNameOutput = output.get().getResult();
                String tunnelIfaceName = tunnelInterfaceNameOutput.getInterfaceName();
                LOG.info("Received tunnelInterfaceName from getTunnelInterfaceName RPC {}", tunnelIfaceName);
                result = buildTunnelItmEgressActions(tunnelIfaceName, tunnelKey);
            } else {
                LOG.trace("Tunnel interface doesn't exist between srcDpId {} dstDpId {}", sourceDpnId,
                        destinationDpnId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error in RPC call getTunnelInterfaceName {}", e);
        }
        return result;
    }

    /**
     * Builds the list of actions to be taken when sending the packet over a VxLan Tunnel Interface, such as setting
     * the network VNI in the tunnel_id field.
     *
     * @param tunnelIfaceName
     *            the tunnel iface name
     * @param tunnelKey
     *            the tunnel key
     * @return the list
     */
    public List<Action> buildTunnelItmEgressActions(String tunnelIfaceName, Long tunnelKey) {
        if (tunnelIfaceName != null && !tunnelIfaceName.isEmpty()) {
            return buildItmEgressActions(tunnelIfaceName, tunnelKey);
        }

        return Collections.emptyList();
    }

    /**
     * Build the list of actions to be taken when sending the packet to external
     * (physical) port.
     *
     * @param interfaceName
     *            Interface name
     * @return the external port itm egress actions
     */
    public List<Action> getExternalPortItmEgressAction(String interfaceName) {
        return buildItmEgressActions(interfaceName, null);
    }

    /**
     * Builds the list of actions to be taken when sending the packet over external port such as tunnel, physical
     * port etc.
     *
     * @param interfaceName
     *            the interface name
     * @param tunnelKey
     *            can be VNI for VxLAN tunnel interfaces, Gre Key for GRE
     *            tunnels, etc.
     * @return the list
     */
    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<Action> buildItmEgressActions(String interfaceName, Long tunnelKey) {
        try {
            GetEgressActionsForInterfaceInput getEgressActInput = new GetEgressActionsForInterfaceInputBuilder()
                    .setIntfName(interfaceName).setTunnelKey(tunnelKey).build();

            Future<RpcResult<GetEgressActionsForInterfaceOutput>> egressActionsOutputFuture = interfaceManagerRpcService
                    .getEgressActionsForInterface(getEgressActInput);

            GetEgressActionsForTunnelInput getEgressActInputItm = new GetEgressActionsForTunnelInputBuilder()
                    .setIntfName(interfaceName).setTunnelKey(tunnelKey).build();

            Future<RpcResult<GetEgressActionsForTunnelOutput>> egressActionsOutputItm =
                    itmRpcService.getEgressActionsForTunnel(getEgressActInputItm);
            if (egressActionsOutputFuture.get().isSuccessful() && !interfaceManager.isItmDirectTunnelsEnabled()) {
                return egressActionsOutputFuture.get().getResult().getAction();
            } else if (egressActionsOutputItm.get().isSuccessful() && interfaceManager.isItmDirectTunnelsEnabled()) {
                return egressActionsOutputItm.get().getResult().getAction();
            }
        } catch (Exception e) {
            LOG.error("Error in RPC call getEgressActionsForInterface {}", e);
        }
        LOG.warn("Could not build Egress actions for interface {} and tunnelId {}", interfaceName, tunnelKey);
        return Collections.emptyList();
    }

    /**
     * Gets the source dpn tep ip.
     *
     * @param srcDpnId
     *            the src dpn id
     * @param dstHwVtepNodeId
     *            the dst hw vtep node id
     * @return the dpn tep ip
     */
    public IpAddress getSourceDpnTepIp(BigInteger srcDpnId,
            org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId dstHwVtepNodeId) {
        IpAddress dpnTepIp = null;
        String tunnelInterfaceName = getExternalTunnelInterfaceName(String.valueOf(srcDpnId),
                dstHwVtepNodeId.getValue());
        if (tunnelInterfaceName != null) {
            Interface tunnelInterface =
                    ElanL2GatewayUtils.getInterfaceFromConfigDS(new InterfaceKey(tunnelInterfaceName), broker);
            if (tunnelInterface != null) {
                dpnTepIp = tunnelInterface.getAugmentation(IfTunnel.class).getTunnelSource();
            } else {
                LOG.warn("Tunnel interface not found for tunnelInterfaceName {}", tunnelInterfaceName);
            }
        } else {
            LOG.warn("Tunnel interface name not found for srcDpnId {} and dstHwVtepNodeId {}", srcDpnId,
                    dstHwVtepNodeId);
        }
        return dpnTepIp;
    }

    /**
     * Gets the external tunnel interface name.
     *
     * @param sourceNode
     *            the source node
     * @param dstNode
     *            the dst node
     * @return the external tunnel interface name
     */
    public String getExternalTunnelInterfaceName(String sourceNode, String dstNode) {
        Class<? extends TunnelTypeBase> tunType = TunnelTypeVxlan.class;
        String tunnelInterfaceName = null;
        try {
            Future<RpcResult<GetExternalTunnelInterfaceNameOutput>> output = itmRpcService
                    .getExternalTunnelInterfaceName(new GetExternalTunnelInterfaceNameInputBuilder()
                            .setSourceNode(sourceNode).setDestinationNode(dstNode).setTunnelType(tunType).build());

            RpcResult<GetExternalTunnelInterfaceNameOutput> rpcResult = output.get();
            if (rpcResult.isSuccessful()) {
                tunnelInterfaceName = rpcResult.getResult().getInterfaceName();
                LOG.debug("Tunnel interface name: {} for sourceNode: {} and dstNode: {}", tunnelInterfaceName,
                        sourceNode, dstNode);
            } else {
                LOG.warn("RPC call to ITM.GetExternalTunnelInterfaceName failed with error: {}", rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("Failed to get external tunnel interface name for sourceNode: {} and dstNode: {}",
                    sourceNode, dstNode, e);
        }
        return tunnelInterfaceName;
    }
}
