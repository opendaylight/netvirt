/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

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
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetEndpointIpForDpnOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetNodeconnectorIdFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeIngress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServicesKey;
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

    private final DataBroker dataBroker;
    private final IInterfaceManager interfaceMgr;
    private final OdlInterfaceRpcService interfaceManagerRpcService;
    private static final Logger LOG = LoggerFactory.getLogger(GeniusProvider.class);
    private static final String OPTION_KEY_EXTS = "exts";
    private static final String OPTION_VALUE_EXTS_GPE = "gpe";

    @Inject
    public GeniusProvider(final DataBroker dataBroker, RpcProviderRegistry rpcProviderRegistry,
            IInterfaceManager interfaceMgr) {
        this.dataBroker = dataBroker;
        this.interfaceMgr = interfaceMgr;
        interfaceManagerRpcService = rpcProviderRegistry.getRpcService(OdlInterfaceRpcService.class);
    }

    public void bindPortOnIngressClassifier(String interfaceName) {
        bindService(
                NwConstants.SFC_CLASSIFIER_INDEX,
                NwConstants.SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.SFC_SERVICE_INDEX,
                NwConstants.INGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.INGRESS_CLASSIFIER_FILTER_COOKIE,
                interfaceName);
    }

    public void bindPortOnEgressClassifier(String interfaceName) {
        bindService(
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_NAME,
                NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX,
                NwConstants.EGRESS_SFC_CLASSIFIER_FILTER_TABLE,
                OpenFlow13Provider.EGRESS_CLASSIFIER_FILTER_COOKIE,
                interfaceName);
    }

    public void unbindPortOnIngressClassifier(String interfaceName) {
        unbindService(interfaceName, NwConstants.SFC_CLASSIFIER_INDEX);
    }

    public void unbindPortOnEgressClassifier(String interfaceName) {
        unbindService(interfaceName, NwConstants.EGRESS_SFC_CLASSIFIER_SERVICE_INDEX);
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

    public Optional<String> getIpFromInterfaceName(String interfaceName) {
        Optional<DpnIdType> dpnId = getDpnIdFromInterfaceName(interfaceName);
        if (!dpnId.isPresent()) {
            LOG.warn("getIpFromInterfaceName empty dpnId for interfaceName [{}]", interfaceName);
            return Optional.empty();
        }

        List<IpAddress> ipList = getIpFromDpnId(dpnId.get());
        if (ipList.isEmpty()) {
            LOG.warn("getIpFromInterfaceName empty ipList for interfaceName [{}]", interfaceName);
            return Optional.empty();
        }

        // TODO need to figure out why it returns a list, using first entry for now
        return Optional.ofNullable(ipList.get(0).getIpv4Address().getValue());
    }

    // TODO Should better use the Genius InterfaceManager to avoid duplicate code
    //      https://bugs.opendaylight.org/show_bug.cgi?id=8127
    public List<IpAddress> getIpFromDpnId(DpnIdType dpnid) {
        GetEndpointIpForDpnInputBuilder builder = new GetEndpointIpForDpnInputBuilder();
        builder.setDpid(dpnid.getValue());
        GetEndpointIpForDpnInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getIpFromDpnId({}) failed (service couldn't be retrieved)", input);
        }

        List<IpAddress> ipList = Collections.emptyList();
        try {
            LOG.debug("getIpFromDpnId: invoking rpc");
            RpcResult<GetEndpointIpForDpnOutput> output = interfaceManagerRpcService.getEndpointIpForDpn(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getIpFromDpnId({}) failed: {}", input, output);
                return Collections.emptyList();
            }
            ipList = Optional.ofNullable(output.getResult().getLocalIps()).orElse(Collections.emptyList());
            LOG.debug("getDpnIdFromInterfaceName({}) succeeded: {}", input, output);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDpnIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return ipList;
    }

    public Optional<DpnIdType> getDpnIdFromInterfaceName(String interfaceName) {
        LOG.debug("getDpnIdFromInterfaceName: starting (logical interface={})", interfaceName);
        GetDpidFromInterfaceInputBuilder builder = new GetDpidFromInterfaceInputBuilder();
        builder.setIntfName(interfaceName);
        GetDpidFromInterfaceInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getDpnIdFromInterfaceName({}) failed (service couldn't be retrieved)", input);
        }

        Optional<DpnIdType> dpnid = Optional.empty();
        try {
            LOG.debug("getDpnIdFromInterfaceName: invoking rpc");
            RpcResult<GetDpidFromInterfaceOutput> output = interfaceManagerRpcService.getDpidFromInterface(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getDpnIdFromInterfaceName({}) failed: {}", input, output);
                return Optional.empty();
            }
            dpnid = Optional.ofNullable(new DpnIdType(output.getResult().getDpid()));
            LOG.debug("getDpnIdFromInterfaceName({}) succeeded: {}", input, output);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getDpnIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return dpnid;
    }

    public Optional<String> getNodeConnectorIdFromInterfaceName(String interfaceName) {
        LOG.debug("getDpnIdFromInterfaceName: starting (logical interface={})", interfaceName);
        GetNodeconnectorIdFromInterfaceInputBuilder builder = new GetNodeconnectorIdFromInterfaceInputBuilder();
        builder.setIntfName(interfaceName);
        GetNodeconnectorIdFromInterfaceInput input = builder.build();

        if (interfaceManagerRpcService == null) {
            LOG.error("getNodeConnectorIdFromInterfaceName({}) failed (service couldn't be retrieved)", input);
        }

        Optional<String> nodeConnId = Optional.empty();
        try {
            LOG.debug("getNodeConnectorIdFromInterfaceName: invoking rpc");
            RpcResult<GetNodeconnectorIdFromInterfaceOutput> output =
                    interfaceManagerRpcService.getNodeconnectorIdFromInterface(input).get();
            if (!output.isSuccessful()) {
                LOG.error("getNodeConnectorIdFromInterfaceName({}) failed: {}", input, output);
                return Optional.empty();
            }
            nodeConnId = Optional.ofNullable(output.getResult().getNodeconnectorId().getValue());
            LOG.debug("getNodeConnectorIdFromInterfaceName({}) succeeded: {}", input, output);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getNodeConnectorIdFromInterfaceName failed to retrieve target interface name: ", e);
        }

        return nodeConnId;
    }

    public Optional<Long> getEgressVxlanPortForNode(BigInteger dpnId) {
        List<OvsdbTerminationPointAugmentation> tpList = interfaceMgr.getTunnelPortsOnBridge(dpnId);
        for (OvsdbTerminationPointAugmentation tp : tpList) {
            if (tp == null) {
                continue;
            }

            Class<? extends InterfaceTypeBase> ifType = tp.getInterfaceType();
            if (ifType.equals(InterfaceTypeVxlan.class)) {
                List<Options> tpOptions = tp.getOptions();
                for (Options tpOption : tpOptions) {
                    // From the VXLAN Tunnels, we want the one with the GPE option set
                    if (tpOption.getKey().getOption().equals(OPTION_KEY_EXTS)) {
                        if (tpOption.getValue().equals(OPTION_VALUE_EXTS_GPE)) {
                            return Optional.ofNullable(tp.getOfport());
                        }
                    }
                }
            }
        }

        LOG.warn("getEgressVxlanPortForNode nothing available for dpnId [{}]", dpnId);

        return Optional.empty();
    }

    private void bindService(short serviceId, String serviceName, int servicePriority,
            short serviceDestTable, BigInteger serviceTableCookie, String interfaceName) {

        InstanceIdentifier<BoundServices> id = InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceId)).build();

        StypeOpenflow stypeOpenflow = new StypeOpenflowBuilder().setFlowCookie(serviceTableCookie)
                .setFlowPriority(servicePriority)
                .setInstruction(Collections.singletonList(
                        MDSALUtil.buildAndGetGotoTableInstruction(serviceDestTable, 0)))
                .build();
        BoundServices boundServices = new BoundServicesBuilder().setServiceName(serviceName)
                .setServicePriority(serviceId).setServiceType(ServiceTypeFlowBased.class)
                .addAugmentation(StypeOpenflow.class, stypeOpenflow).build();

        LOG.info("Binding Service ID [{}] name [{}] priority [{}] table [{}] cookie [{}] interface [{}]",
                serviceId, serviceName, servicePriority, serviceDestTable, serviceTableCookie, interfaceName);

        MDSALUtil.syncWrite(this.dataBroker, LogicalDatastoreType.CONFIGURATION, id, boundServices);
    }

    private void unbindService(String interfaceName, short serviceId) {
        InstanceIdentifier<BoundServices> id = InstanceIdentifier.builder(ServiceBindings.class)
                .child(ServicesInfo.class, new ServicesInfoKey(interfaceName, ServiceModeIngress.class))
                .child(BoundServices.class, new BoundServicesKey(serviceId)).build();

        LOG.info("Unbinding Service ID [{}] interface [{}]", serviceId, interfaceName);

        MDSALUtil.syncDelete(this.dataBroker, LogicalDatastoreType.CONFIGURATION, id);
    }
}
