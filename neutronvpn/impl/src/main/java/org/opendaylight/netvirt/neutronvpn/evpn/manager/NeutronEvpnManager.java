/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.evpn.manager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnManager;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateEVPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteEVPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetEVPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.createevpn.input.Evpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getevpn.output.EvpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getevpn.output.EvpnInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;


public class NeutronEvpnManager {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronEvpnManager.class);
    private final DataBroker dataBroker;
    private final NeutronvpnManager neutronvpnManager;
    private final NeutronvpnUtils neutronvpnUtils;

    public NeutronEvpnManager(DataBroker dataBroker, NeutronvpnManager neutronvpnManager,
            NeutronvpnUtils neutronvpnUtils) {
        this.dataBroker = dataBroker;
        this.neutronvpnManager = neutronvpnManager;
        this.neutronvpnUtils = neutronvpnUtils;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<CreateEVPNOutput>> createEVPN(CreateEVPNInput input) {
        CreateEVPNOutputBuilder opBuilder = new CreateEVPNOutputBuilder();
        SettableFuture<RpcResult<CreateEVPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();
        int failurecount = 0;
        List<String> existingRDs = neutronvpnUtils.getExistingRDs();

        for (Evpn vpn : input.nonnullEvpn()) {
            if (vpn.getRouteDistinguisher() == null || vpn.getImportRT() == null || vpn.getExportRT() == null) {
                errorList.add(RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn, "Creation of EVPN failed for VPN {} due to absence of RD/iRT/eRT input",
                                vpn.getId().getValue())));
                continue;
            }
            if (vpn.getRouteDistinguisher().size() > 1) {
                errorList.add(RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn, "Creation of EVPN failed for VPN {} due to multiple RD input {}",
                                vpn.getId().getValue(), vpn.getRouteDistinguisher())));
                continue;
            }
            if (existingRDs.contains(vpn.getRouteDistinguisher().get(0))) {
                errorList.add(RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn,
                                "Creation of EVPN failed for VPN {} as another VPN with the same RD {} is already "
                                        + "configured",
                                vpn.getId().getValue(), vpn.getRouteDistinguisher().get(0))));
                continue;
            }
            try {
                List<String> rdList = vpn.getRouteDistinguisher() != null
                        ? new ArrayList<>(vpn.getRouteDistinguisher()) : new ArrayList<>();
                List<String> importRdList = vpn.getImportRT() != null
                        ? new ArrayList<>(vpn.getImportRT()) : new ArrayList<>();
                List<String> exportRdList = vpn.getExportRT() != null
                        ? new ArrayList<>(vpn.getExportRT()) : new ArrayList<>();
                neutronvpnManager.createVpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), rdList,
                        importRdList, exportRdList, null /*router-id*/, null /*network-id*/,
                        true /*isL2Vpn*/, 0 /*l2vni*/);
            } catch (Exception ex) {
                errorList.add(RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION,
                        formatAndLog(LOG::error, "Creation of EVPN failed for VPN {}", vpn.getId().getValue(), ex),
                        ex.getMessage()));
                failurecount++;
            }
        }
        if (failurecount != 0) {
            result.set(RpcResultBuilder.<CreateEVPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    errorResponseList.add("ErrorType: " + rpcError.getErrorType() + ", ErrorTag: " + rpcError.getTag()
                            + ", ErrorMessage: " + rpcError.getMessage());
                }
            } else {
                errorResponseList.add("EVPN creation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.success(opBuilder.build()).build());
        }
        return result;
    }

    public ListenableFuture<RpcResult<GetEVPNOutput>> getEVPN(GetEVPNInput input) {
        GetEVPNOutputBuilder opBuilder = new GetEVPNOutputBuilder();
        SettableFuture<RpcResult<GetEVPNOutput>> result = SettableFuture.create();
        Uuid inputVpnId = input.getId();
        List<VpnInstance> vpns = new ArrayList<>();
        if (inputVpnId == null) {
            vpns = VpnHelper.getAllVpnInstances(dataBroker);
            if (!vpns.isEmpty()) {
                for (VpnInstance vpn : vpns) {
                    if (vpn.getRouteDistinguisher() != null
                            && vpn.isL2vpn()) {
                        vpns.add(vpn);
                    }
                }
            } else {
                // No VPN present
                result.set(RpcResultBuilder.success(opBuilder.build()).build());
                return result;
            }
        } else {
            String name = inputVpnId.getValue();
            VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, name);
            if (vpnInstance != null && vpnInstance.getRouteDistinguisher() != null
                    && vpnInstance.isL2vpn()) {
                vpns.add(vpnInstance);
            } else {
                result.set(RpcResultBuilder.<GetEVPNOutput>failed().withWarning(RpcError.ErrorType.PROTOCOL,
                        "invalid-value",
                        formatAndLog(LOG::error, "GetEVPN failed because VPN {} is not present", name)).build());
            }
        }
        List<EvpnInstances> evpnList = new ArrayList<>();
        for (VpnInstance vpnInstance : vpns) {
            Uuid vpnId = new Uuid(vpnInstance.getVpnInstanceName());
            InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap
                    .class, new VpnMapKey(vpnId)).build();
            EvpnInstancesBuilder evpn = new EvpnInstancesBuilder();
            List<String> rd = vpnInstance.getRouteDistinguisher();
            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();
            for (VpnTarget vpnTarget : vpnInstance.getVpnTargets().nonnullVpnTarget()) {
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                    ertList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                    irtList.add(vpnTarget.getVrfRTValue());
                }
                if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                    ertList.add(vpnTarget.getVrfRTValue());
                    irtList.add(vpnTarget.getVrfRTValue());
                }
            }
            evpn.setId(vpnId).setRouteDistinguisher(rd).setImportRT(irtList).setExportRT(ertList);
            try {
                Optional<VpnMap> optionalVpnMap =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnMapIdentifier);
                if (optionalVpnMap.isPresent()) {
                    VpnMap vpnMap = optionalVpnMap.get();
                    evpn.setTenantId(vpnMap.getTenantId()).setName(vpnMap.getName());
                }
            } catch (ReadFailedException e) {
                LOG.error("Error reading the VPN map for {}", vpnMapIdentifier, e);
                result.set(RpcResultBuilder.<GetEVPNOutput>failed().withError(RpcError.ErrorType.APPLICATION,
                        "Error reading the VPN map for " + vpnMapIdentifier, e).build());
                return result;
            }
            evpnList.add(evpn.build());
        }

        opBuilder.setEvpnInstances(evpnList);
        result.set(RpcResultBuilder.success(opBuilder.build()).build());
        return result;
    }

    public ListenableFuture<RpcResult<DeleteEVPNOutput>> deleteEVPN(DeleteEVPNInput input) {
        List<RpcError> errorList = new ArrayList<>();

        if (input.getId() != null) {
            for (Uuid vpn : input.getId()) {
                VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpn.getValue());
                if (vpnInstance != null) {
                    neutronvpnManager.removeVpn(vpn);
                } else {
                    errorList.add(RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-value",
                        formatAndLog(LOG::warn, "EVPN with vpnid: {} does not exist", vpn.getValue())));
                }
            }
        }
        List<String> errorResponseList = new ArrayList<>();
        if (!errorList.isEmpty()) {
            for (RpcError rpcError : errorList) {
                errorResponseList.add("ErrorType: " + rpcError.getErrorType() + ", ErrorTag: " + rpcError.getTag()
                        + ", ErrorMessage: " + rpcError.getMessage());
            }
        } else {
            errorResponseList.add("Deletion of EVPN operation successful");
        }
        DeleteEVPNOutputBuilder opBuilder = new DeleteEVPNOutputBuilder();
        opBuilder.setResponse(errorResponseList);
        SettableFuture<RpcResult<DeleteEVPNOutput>> result = SettableFuture.create();
        result.set(RpcResultBuilder.success(opBuilder.build()).build());
        return result;
    }

    private String formatAndLog(Consumer<String> logger, String template, Object arg) {
        return logAndReturnMessage(logger, MessageFormatter.format(template, arg));
    }

    private String formatAndLog(Consumer<String> logger, String template, Object arg1, Object arg2) {
        return logAndReturnMessage(logger, MessageFormatter.format(template, arg1, arg2));
    }

    private String logAndReturnMessage(Consumer<String> logger, FormattingTuple tuple) {
        String message = tuple.getMessage();
        logger.accept(message);
        return message;
    }
}
