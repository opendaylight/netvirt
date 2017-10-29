/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.evpn.manager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.SettableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnManager;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.af.config.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
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
    public Future<RpcResult<CreateEVPNOutput>> createEVPN(CreateEVPNInput input) {
        CreateEVPNOutputBuilder opBuilder = new CreateEVPNOutputBuilder();
        SettableFuture<RpcResult<CreateEVPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();
        int failurecount = 0;
        int warningcount = 0;
        List<String> existingRDs = neutronvpnUtils.getExistingRDs();

        List<Evpn> vpns = input.getEvpn();
        for (Evpn vpn : vpns) {
            RpcError error = null;
            String msg;

            if (vpn.getRouteDistinguisher() == null || vpn.getImportRT() == null || vpn.getExportRT() == null) {
                msg = String.format("Creation of EVPN failed for VPN %s due to absence of RD/iRT/eRT input",
                        vpn.getId().getValue());
                LOG.warn(msg);
                error = RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input", msg);
                errorList.add(error);
                warningcount++;
                continue;
            }
            VpnInstance.Type vpnInstanceType = VpnInstance.Type.L2;
            if (vpn.getRouteDistinguisher().size() > 1) {
                msg = String.format("Creation of EVPN failed for VPN %s due to multiple RD input %s",
                        vpn.getId().getValue(), vpn.getRouteDistinguisher());
                LOG.warn(msg);
                error = RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input", msg);
                errorList.add(error);
                warningcount++;
                continue;
            }
            if (existingRDs.contains(vpn.getRouteDistinguisher().get(0))) {
                msg = String.format("Creation of EVPN failed for VPN %s as another VPN with",
                        "the same RD %s is already configured",
                        vpn.getId().getValue(), vpn.getRouteDistinguisher().get(0));
                LOG.warn(msg);
                error = RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-input", msg);
                errorList.add(error);
                warningcount++;
                continue;
            }
            try {
                neutronvpnManager.createVpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), vpn.getRouteDistinguisher(),
                        vpn.getImportRT(), vpn.getExportRT(), null /*router-id*/, null /*network-id*/,
                        vpnInstanceType, 0 /*l2vni*/);
            } catch (Exception ex) {
                msg = String.format("Creation of EVPN failed for VPN %s", vpn.getId().getValue());
                LOG.error(msg, ex);
                error = RpcResultBuilder.newError(RpcError.ErrorType.APPLICATION, msg, ex.getMessage());
                errorList.add(error);
                failurecount++;
            }
        }
        if (failurecount != 0) {
            result.set(RpcResultBuilder.<CreateEVPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    String errorResponse = String.format("ErrorType: %s, ErrorTag: %s, ErrorMessage: %s", rpcError
                            .getErrorType(), rpcError.getTag(), rpcError.getMessage());
                    errorResponseList.add(errorResponse);
                }
            } else {
                errorResponseList.add("EVPN creation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<CreateEVPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }

    public Future<RpcResult<GetEVPNOutput>> getEVPN(GetEVPNInput input) {
        GetEVPNOutputBuilder opBuilder = new GetEVPNOutputBuilder();
        SettableFuture<RpcResult<GetEVPNOutput>> result = SettableFuture.create();
        Uuid inputVpnId = input.getId();
        List<VpnInstance> vpns = new ArrayList<>();
        if (inputVpnId == null) {
            vpns = VpnHelper.getAllVpnInstances(dataBroker);
            if (!vpns.isEmpty()) {
                for (VpnInstance vpn : vpns) {
                    if (vpn.getIpv4Family().getRouteDistinguisher() != null
                            && vpn.getType() == VpnInstance.Type.L2) {
                        vpns.add(vpn);
                    }
                }
            } else {
                // No VPN present
                result.set(RpcResultBuilder.<GetEVPNOutput>success().withResult(opBuilder.build()).build());
                return result;
            }
        } else {
            String name = inputVpnId.getValue();
            VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, name);
            if (vpnInstance != null && vpnInstance.getIpv4Family().getRouteDistinguisher() != null
                    && vpnInstance.getType() == VpnInstance.Type.L2) {
                vpns.add(vpnInstance);
            } else {
                String message = String.format("GetEVPN failed because VPN %s is not present", name);
                LOG.error(message);
                result.set(RpcResultBuilder.<GetEVPNOutput>failed().withWarning(RpcError.ErrorType.PROTOCOL,
                        "invalid-value", message).build());
            }
        }
        List<EvpnInstances> evpnList = new ArrayList<>();
        for (VpnInstance vpnInstance : vpns) {
            Uuid vpnId = new Uuid(vpnInstance.getVpnInstanceName());
            InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap
                    .class, new VpnMapKey(vpnId)).build();
            EvpnInstancesBuilder evpn = new EvpnInstancesBuilder();
            List<String> rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            List<VpnTarget> vpnTargetList = vpnInstance.getIpv4Family().getVpnTargets().getVpnTarget();
            List<String> ertList = new ArrayList<>();
            List<String> irtList = new ArrayList<>();
            for (VpnTarget vpnTarget : vpnTargetList) {
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
        result.set(RpcResultBuilder.<GetEVPNOutput>success().withResult(opBuilder.build()).build());
        return result;
    }

    public Future<RpcResult<DeleteEVPNOutput>> deleteEVPN(DeleteEVPNInput input) {
        DeleteEVPNOutputBuilder opBuilder = new DeleteEVPNOutputBuilder();
        SettableFuture<RpcResult<DeleteEVPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();

        int failurecount = 0;
        int warningcount = 0;
        List<Uuid> vpns = input.getId();
        for (Uuid vpn : vpns) {
            RpcError error;
            String msg;
            VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpn.getValue());
            if (vpnInstance != null) {
                neutronvpnManager.removeVpn(vpn);
            } else {
                msg = String.format("EVPN with vpnid: %s does not exist", vpn.getValue());
                LOG.warn(msg);
                error = RpcResultBuilder.newWarning(RpcError.ErrorType.PROTOCOL, "invalid-value", msg);
                errorList.add(error);
                warningcount++;
            }
        }
        if (failurecount != 0) {
            result.set(RpcResultBuilder.<DeleteEVPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    String errorResponse = String.format("ErrorType: %s, ErrorTag: %s, ErrorMessage: %s", rpcError
                            .getErrorType(), rpcError.getTag(), rpcError.getMessage());
                    errorResponseList.add(errorResponse);
                }
            } else {
                errorResponseList.add("Deletion of EVPN operation successful");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<DeleteEVPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }
}
