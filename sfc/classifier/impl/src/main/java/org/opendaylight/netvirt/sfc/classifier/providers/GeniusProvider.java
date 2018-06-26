/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import com.google.common.net.InetAddresses;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.sfc.classifier.utils.OpenFlow13Utils;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.InstructionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.IfTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpnInterfaceListOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.get.dpn._interface.list.output.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.bound.services.instruction.instruction.apply.actions._case.apply.actions.action.action.ServiceBindingNxActionRegLoadApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.Options;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GeniusProvider {
    private static final Logger LOG = LoggerFactory.getLogger(GeniusProvider.class);
    public static final String OPTION_KEY_EXTS = "exts";
    public static final String OPTION_VALUE_EXTS_GPE = "gpe";

    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceMgr;
    private final OdlInterfaceRpcService interfaceManagerRpcService;

    @Inject
    public GeniusProvider(final DataBroker dataBroker, final RpcProviderRegistry rpcProviderRegistry,
            final IInterfaceManager interfaceMgr) {
        this.dataBroker = dataBroker;
        this.interfaceMgr = interfaceMgr;
        this.interfaceManagerRpcService = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
    }

    // Package local constructor used by UT for simplification
    GeniusProvider(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceManagerRpcService,
            final IInterfaceManager interfaceMgr) {
        this.dataBroker = dataBroker;
        this.interfaceMgr = interfaceMgr;
        this.interfaceManagerRpcService = interfaceManagerRpcService;
    }

    public void bindPortOnIngressClassifier(String interfaceName) {
        bindService(
                getBindServiceId(NwConstants.SFC_CLASSIFIER_INDEX, interfaceName, true),
                NwConstants.SFC_CLASSIFIER_INDEX,
                NwConstants.SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.SFC_SERVICE_INDEX,
                NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE);
    }

    public void bindPortOnEgressClassifier(String interfaceName, String destinationIp) {
        bindService(
                getBindServiceId(NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX, interfaceName, false),
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_COOKIE,
                Collections.singletonList(
                        createServiceBindingActionNxLoadReg0(
                                InetAddresses.coerceToInteger(
                                            InetAddresses.forString(destinationIp)) & 0xffffffffL,
                                0)
                ));
    }

    public void unbindPortOnIngressClassifier(String interfaceName) {
        unbindService(getBindServiceId(NwConstants.SFC_CLASSIFIER_INDEX, interfaceName, true));
    }

    public void unbindPortOnEgressClassifier(String interfaceName) {
        unbindService(getBindServiceId(NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX, interfaceName, false));
    }

    public Optional<NodeId> getNodeIdFromLogicalInterface(String logicalInterface) {
        Optional<DpnIdType> dpnId = getDpnIdFromInterfaceName(logicalInterface);

        if (!dpnId.isPresent()) {
            LOG.warn("getNodeIdFromLogicalInterface empty dpnId for logicalInterface [{}]", logicalInterface);
            return Optional.empty();
        }

        return getNodeIdFromDpnId(dpnId.get());
    }

    public Optional<NodeId> getNodeIdFromDpnId(DpnIdType dpnId) {
        if (dpnId == null) {
            return Optional.empty();
        }

        if (dpnId.getValue() == null) {
            return Optional.empty();
        }

        return Optional.of(new NodeId("openflow:" + dpnId.getValue()));
    }

    // TODO Should better use the Genius InterfaceManager to avoid duplicate code
    //      https://bugs.opendaylight.org/show_bug.cgi?id=8127
    public Optional<String> getIpFromDpnId(DpnIdType dpnid) {
        GetEndpointIpForDpnInputBuilder builder = new GetEndpointIpForDpnInputBuilder();
        builder.setDpid(dpnid.getValue());
        GetEndpointIpForDpnInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getIpFromDpnId({}) failed (service couldn't be retrieved)", input);
            return Optional.empty();
        }

        try {
            LOG.debug("getIpFromDpnId: invoking rpc");
            RpcResult<GetEndpointIpForDpnOutput> output = interfaceManagerRpcService.getEndpointIpForDpn(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getIpFromDpnId({}) failed: {}", input, output);
                return Optional.empty();
            }
            LOG.debug("getDpnIdFromInterfaceName({}) succeeded: {}", input, output);
            List<IpAddress> localIps = output.getResult().getLocalIps();

            // TODO need to figure out why it returns a list, using first entry for now
            return Optional.ofNullable(localIps)
                    .filter(ipAddresses -> !ipAddresses.isEmpty())
                    .map(ipAddresses -> ipAddresses.get(0))
                    .map(IpAddress::getIpv4Address)
                    .map(Ipv4Address::getValue);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDpnIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return Optional.empty();
    }

    public Optional<DpnIdType> getDpnIdFromInterfaceName(String interfaceName) {
        LOG.debug("getDpnIdFromInterfaceName: starting (logical interface={})", interfaceName);
        GetDpidFromInterfaceInputBuilder builder = new GetDpidFromInterfaceInputBuilder();
        builder.setIntfName(interfaceName);
        GetDpidFromInterfaceInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getDpnIdFromInterfaceName({}) failed (service couldn't be retrieved)", input);
            return Optional.empty();
        }

        try {
            LOG.debug("getDpnIdFromInterfaceName: invoking rpc");
            RpcResult<GetDpidFromInterfaceOutput> output = interfaceManagerRpcService.getDpidFromInterface(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getDpnIdFromInterfaceName({}) failed: {}", input, output);
                return Optional.empty();
            }

            BigInteger dpnId = output.getResult().getDpid();
            if (dpnId == null) {
                return Optional.empty();
            }
            LOG.debug("getDpnIdFromInterfaceName({}) succeeded: {}", input, output);

            return Optional.of(new DpnIdType(dpnId));
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDpnIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return Optional.empty();
    }

    public Optional<String> getNodeConnectorIdFromInterfaceName(String interfaceName) {
        LOG.debug("getDpnIdFromInterfaceName: starting (logical interface={})", interfaceName);
        GetNodeconnectorIdFromInterfaceInputBuilder builder = new GetNodeconnectorIdFromInterfaceInputBuilder();
        builder.setIntfName(interfaceName);
        GetNodeconnectorIdFromInterfaceInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getNodeConnectorIdFromInterfaceName({}) failed (service couldn't be retrieved)", input);
            return Optional.empty();
        }

        try {
            LOG.debug("getNodeConnectorIdFromInterfaceName: invoking rpc");
            RpcResult<GetNodeconnectorIdFromInterfaceOutput> output =
                    interfaceManagerRpcService.getNodeconnectorIdFromInterface(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getNodeConnectorIdFromInterfaceName({}) failed: {}", input, output);
                return Optional.empty();
            }
            NodeConnectorId nodeConnId = output.getResult().getNodeconnectorId();
            if (nodeConnId == null) {
                return Optional.empty();
            }
            LOG.debug("getNodeConnectorIdFromInterfaceName({}) succeeded: {}", input, output);

            return Optional.ofNullable(nodeConnId.getValue());
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getNodeConnectorIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return Optional.empty();
    }

    public Optional<Long> getEgressVxlanPortForNode(BigInteger dpnId) {
        List<OvsdbTerminationPointAugmentation> tpList = interfaceMgr.getTunnelPortsOnBridge(dpnId);
        if (tpList == null) {
            // Most likely the bridge doesnt exist for this dpnId
            LOG.warn("getEgressVxlanPortForNode Tunnel Port TerminationPoint list not available for dpnId [{}]",
                    dpnId);
            return Optional.empty();
        }

        for (OvsdbTerminationPointAugmentation tp : tpList) {
            if (tp == null) {
                // Technically we should never have a list with NULL entries, but
                // in a preliminary version of interfaceMgr.getTunnelPortsOnBridge()
                // we were getting a list where all termination point entries were
                // null. Leaving this check for now for protection.
                LOG.error("getEgressVxlanPortForNode received a NULL termination point from tpList on dpnId [{}]",
                        dpnId);
                continue;
            }

            Class<? extends InterfaceTypeBase> ifType = tp.getInterfaceType();
            if (ifType.equals(InterfaceTypeVxlan.class)) {
                List<Options> tpOptions = tp.getOptions();
                for (Options tpOption : tpOptions) {
                    // From the VXLAN Tunnels, we want the one with the GPE option set
                    if (tpOption.key().getOption().equals(OPTION_KEY_EXTS)) {
                        if (tpOption.getValue().equals(OPTION_VALUE_EXTS_GPE) && tp.getOfport() !=null) {
                            return Optional.ofNullable(tp.getOfport());
                        }
                    }
                }
            }
        }

        LOG.warn("getEgressVxlanPortForNode no Vxgpe tunnel ports available for dpnId [{}]", dpnId);

        return Optional.empty();
    }

    public List<Interfaces> getInterfacesFromNode(NodeId nodeId) {
        // getPortsOnBridge() only returns Tunnel ports, so instead using getDpnInterfaceList.
        GetDpnInterfaceListInputBuilder inputBuilder = new GetDpnInterfaceListInputBuilder();
        inputBuilder.setDpid(BigInteger.valueOf(Long.parseLong(nodeId.getValue().split(":")[1])));
        GetDpnInterfaceListInput input = inputBuilder.build();

        try {
            LOG.debug("getInterfacesFromNode: invoking rpc");
            RpcResult<GetDpnInterfaceListOutput> output =
                    interfaceManagerRpcService.getDpnInterfaceList(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getInterfacesFromNode({}) failed: {}", input, output);
                return Collections.emptyList();
            }
            LOG.debug("getInterfacesFromNode({}) succeeded: {}", input, output);
            return output.getResult().getInterfaces();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getInterfacesFromNode failed to retrieve target interface name: ", e);
        }

        return Collections.emptyList();
    }

    public InstanceIdentifier<BoundServices> getBindServiceId(short serviceId, String interfaceName,
            boolean isIngress) {
        ServicesInfoKey servicesInfoKey = isIngress
                ? new ServicesInfoKey(interfaceName, ServiceModeIngress.class) :
                  new ServicesInfoKey(interfaceName, ServiceModeEgress.class);
        InstanceIdentifier<BoundServices> id = InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, servicesInfoKey)
                .child(BoundServices.class, new BoundServicesKey(serviceId)).build();

        return id;
    }

    private void bindService(InstanceIdentifier<BoundServices> id, short serviceId, String serviceName,
                             int servicePriority, short serviceDestTable, BigInteger serviceTableCookie) {
        bindService(
                id,
                serviceId,
                serviceName,
                servicePriority,
                serviceDestTable,
                serviceTableCookie,
                Collections.emptyList());
    }

    private void bindService(InstanceIdentifier<BoundServices> id, short serviceId, String serviceName,
            int servicePriority, short serviceDestTable, BigInteger serviceTableCookie, List<Action> extraActions) {
        InstructionsBuilder isb = extraActions.isEmpty()
                ? new InstructionsBuilder()
                : OpenFlow13Utils.wrapActionsIntoApplyActionsInstruction(extraActions);
        isb = OpenFlow13Utils.appendGotoTableInstruction(isb, serviceDestTable);
        StypeOpenflow stypeOpenflow = new StypeOpenflowBuilder()
                .setFlowCookie(serviceTableCookie)
                .setFlowPriority(servicePriority)
                .setInstruction(isb.build().getInstruction())
                .build();
        BoundServices boundServices = new BoundServicesBuilder().setServiceName(serviceName)
                .setServicePriority(serviceId).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, stypeOpenflow).build();
        LOG.info("Binding Service ID [{}] name [{}] priority [{}] table [{}] cookie [{}] extraActions [{}]",
                serviceId, serviceName, servicePriority, serviceDestTable, serviceTableCookie, extraActions);

        MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION, id, boundServices);
    }

    private void unbindService(InstanceIdentifier<BoundServices> id) {
        MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION, id);
    }

    public Optional<String> getRemoteIpAddress(String interfaceName) {
        return Optional.ofNullable(interfaceMgr.getInterfaceInfoFromConfigDataStore(interfaceName))
                .map(anInterface -> anInterface.augmentation(IfTunnel.class))
                .map(IfTunnel::getTunnelDestination)
                .map(IpAddress::getIpv4Address)
                .map(Ipv4Address::getValue);
    }

    public static Action createServiceBindingActionNxLoadReg0(long value, int order) {
        return OpenFlow13Utils.createAction(
                new ServiceBindingNxActionRegLoadApplyActionsCaseBuilder()
                        .setNxRegLoad(OpenFlow13Utils.createNxLoadReg0(value))
                        .build(),
                order);
    }
}
