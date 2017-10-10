/*
 * Copyright (c) 2015 - 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.nxmatches.NxMatchRegister;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput.ArpReponderInputBuilder;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderUtil;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnManagerImpl implements IVpnManager {

    private static final Logger LOG = LoggerFactory.getLogger(VpnManagerImpl.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnInstanceListener vpnInstanceListener;
    private final IdManagerService idManager;
    private final IMdsalApiManager mdsalManager;
    private final IElanService elanService;
    private final IInterfaceManager interfaceManager;
    private final VpnSubnetRouteHandler vpnSubnetRouteHandler;

    public VpnManagerImpl(final DataBroker dataBroker,
                          final IdManagerService idManagerService,
                          final VpnInstanceListener vpnInstanceListener,
                          final VpnInterfaceManager vpnInterfaceManager,
                          final IMdsalApiManager mdsalManager,
                          final VpnFootprintService vpnFootprintService,
                          final IElanService elanService,
                          final IInterfaceManager interfaceManager,
                          final VpnSubnetRouteHandler vpnSubnetRouteHandler) {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnInstanceListener = vpnInstanceListener;
        this.idManager = idManagerService;
        this.mdsalManager = mdsalManager;
        this.elanService = elanService;
        this.interfaceManager = interfaceManager;
        this.vpnSubnetRouteHandler = vpnSubnetRouteHandler;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
            .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
            .setLow(VpnConstants.VPN_IDPOOL_LOW)
            .setHigh(VpnConstants.VPN_IDPOOL_HIGH)
            .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for VPN Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service", e);
        }

        // Now an IdPool for InterVpnLink endpoint's pseudo ports
        CreateIdPoolInput createPseudoLporTagPool =
            new CreateIdPoolInputBuilder().setPoolName(VpnConstants.PSEUDO_LPORT_TAG_ID_POOL_NAME)
                .setLow(VpnConstants.LOWER_PSEUDO_LPORT_TAG)
                .setHigh(VpnConstants.UPPER_PSEUDO_LPORT_TAG)
                .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPseudoLporTagPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Created IdPool for Pseudo Port tags");
            } else {
                Collection<RpcError> errors = result.get().getErrors();
                StringBuilder errMsg = new StringBuilder();
                for (RpcError err : errors) {
                    errMsg.append(err.getMessage()).append("\n");
                }
                LOG.error("IdPool creation for PseudoPort tags failed. Reasons: {}", errMsg);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Pseudo Port tags", e);
        }
    }

    @Override
    public void addExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID,
        int label,RouteOrigin origin) {
        LOG.info("Adding extra route with destination {}, nextHop {}, label{} and origin {}",
            destination, nextHop, label, origin);
        VpnInstanceOpDataEntry vpnOpEntry = VpnUtil.getVpnInstanceOpData(dataBroker, rd);
        Boolean isVxlan = VpnUtil.isL3VpnOverVxLan(vpnOpEntry.getL3vni());
        VrfEntry.EncapType encapType = VpnUtil.getEncapType(isVxlan);
        vpnInterfaceManager.addExtraRoute(vpnName, destination, nextHop, rd, routerID, label, vpnOpEntry.getL3vni(),
                origin,/*intfName*/ null, null /*Adjacency*/, encapType, null);
    }

    @Override
    public void delExtraRoute(String vpnName, String destination, String nextHop, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {} and nextHop {}", destination, nextHop);
        vpnInterfaceManager.delExtraRoute(vpnName, destination, nextHop, rd, routerID, null, null);
    }

    @Override
    public boolean isVPNConfigured() {
        return vpnInstanceListener.isVPNConfigured();
    }

    @Override
    public List<BigInteger> getDpnsOnVpn(String vpnInstanceName) {
        return VpnUtil.getDpnsOnVpn(dataBroker, vpnInstanceName);
    }

    @Override
    public boolean existsVpn(String vpnName) {
        return VpnUtil.getVpnInstance(dataBroker, vpnName) != null;
    }

    @Override
    public void addSubnetMacIntoVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx) {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> addGwMac(srcMacAddress, tx, vpnId, dpId, subnetVpnId));
    }

    @Override
    public void removeSubnetMacFromVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, WriteTransaction tx) {
        setupSubnetMacInVpnInstance(vpnName, subnetVpnName, srcMacAddress, dpnId,
            (vpnId, dpId, subnetVpnId) -> removeGwMac(srcMacAddress, tx, vpnId, dpId, subnetVpnId));
    }

    @FunctionalInterface
    private interface VpnInstanceSubnetMacSetupMethod {
        void process(long vpnId, BigInteger dpId, long subnetVpnId);
    }

    private void setupSubnetMacInVpnInstance(String vpnName, String subnetVpnName, String srcMacAddress,
            BigInteger dpnId, VpnInstanceSubnetMacSetupMethod consumer) {
        if (vpnName == null) {
            LOG.warn("Cannot setup subnet MAC {} on DPN {}, null vpnName", srcMacAddress, dpnId);
            return;
        }

        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        long subnetVpnId = VpnUtil.getVpnId(dataBroker, subnetVpnName);
        if (dpnId.equals(BigInteger.ZERO)) {
            /* Apply the MAC on all DPNs in a VPN */
            for (BigInteger dpId : VpnUtil.getDpnsOnVpn(dataBroker, vpnName)) {
                consumer.process(vpnId, dpId, subnetVpnId);
            }
        } else {
            consumer.process(vpnId, dpnId, subnetVpnId);
        }
    }

    private void addGwMac(String srcMacAddress, WriteTransaction tx, long vpnId, BigInteger dpId, long subnetVpnId) {
        FlowEntity flowEntity = VpnUtil.buildL3vpnGatewayFlow(dpId, srcMacAddress, vpnId, subnetVpnId);
        mdsalManager.addFlowToTx(flowEntity, tx);
    }

    private void removeGwMac(String srcMacAddress, WriteTransaction tx, long vpnId, BigInteger dpId, long subnetVpnId) {
        FlowEntity flowEntity = VpnUtil.buildL3vpnGatewayFlow(dpId, srcMacAddress, vpnId, subnetVpnId);
        mdsalManager.removeFlowToTx(flowEntity, tx);
    }

    @Override
    public void addRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx) {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId, writeTx,
            (vpnId, tx) -> addSubnetMacIntoVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, tx), "Installing");
    }

    @Override
    public void removeRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            String subnetVpnName, WriteTransaction writeTx) {
        setupRouterGwMacFlow(routerName, routerGwMac, dpnId, extNetworkId, writeTx,
            (vpnId, tx) -> removeSubnetMacFromVpnInstance(vpnId, subnetVpnName, routerGwMac, dpnId, tx), "Removing");
    }

    @FunctionalInterface
    private interface RouterGwMacFlowSetupMethod {
        void process(String vpnId, WriteTransaction tx);
    }

    private void setupRouterGwMacFlow(String routerName, String routerGwMac, BigInteger dpnId, Uuid extNetworkId,
            WriteTransaction writeTx, RouterGwMacFlowSetupMethod consumer, String operation) {
        if (routerGwMac == null) {
            LOG.warn("Failed to handle router GW flow in GW-MAC table. MAC address is missing for router-id {}",
                    routerName);
            return;
        }

        if (dpnId == null || BigInteger.ZERO.equals(dpnId)) {
            LOG.info("setupRouterGwMacFlow: DPN id is missing for router-id {}",
                    routerName);
            return;
        }

        Uuid vpnId = VpnUtil.getExternalNetworkVpnId(dataBroker, extNetworkId);
        if (vpnId == null) {
            LOG.warn("Network {} is not associated with VPN", extNetworkId.getValue());
            return;
        }

        LOG.info("{} router GW MAC flow for router-id {} on switch {}", operation, routerName, dpnId);
        if (writeTx == null) {
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> consumer.process(vpnId.getValue(), tx));
        } else {
            consumer.process(vpnId.getValue(), writeTx);
        }
    }

    @Override
    public void setupArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, Uuid extNetworkId, WriteTransaction writeTx, int addOrRemove) {

        if (dpnId == null || BigInteger.ZERO.equals(dpnId)) {
            LOG.warn("Failed to install arp responder flows for router {}. DPN id is missing.", id);
            return;
        }

        String extInterfaceName = elanService.getExternalElanInterface(extNetworkId.getValue(), dpnId);
        if (extInterfaceName == null) {
            LOG.warn("Failed to install responder flows for {}. No external interface found for DPN id {}", id, dpnId);
            return;
        }

        Interface extInterfaceState = InterfaceUtils.getInterfaceStateFromOperDS(dataBroker, extInterfaceName);
        if (extInterfaceState == null) {
            LOG.debug("No interface state found for interface {}. Delaying responder flows for {}", extInterfaceName,
                    id);
            return;
        }

        Integer lportTag = extInterfaceState.getIfIndex();
        if (lportTag == null) {
            LOG.debug("No Lport tag found for interface {}. Delaying flows for router-id {}", extInterfaceName, id);
            return;
        }

        if (macAddress == null) {

            LOG.debug("Failed to install arp responder flows for router-gateway-ip {} for router {}."
                    + "External Gw MacAddress is missing.", fixedIps,  id);
            return;
        }

        long vpnId = (addOrRemove == NwConstants.ADD_FLOW) ? getVpnIdFromExtNetworkId(extNetworkId)
                : VpnConstants.INVALID_ID;
        setupArpResponderFlowsToExternalNetworkIps(id, fixedIps, macAddress, dpnId, vpnId, extInterfaceName, lportTag,
                writeTx, addOrRemove);
    }


    @Override
    public void setupArpResponderFlowsToExternalNetworkIps(String id, Collection<String> fixedIps, String macAddress,
            BigInteger dpnId, long vpnId, String extInterfaceName, int lportTag, WriteTransaction writeTx,
            int addOrRemove) {
        if (fixedIps == null || fixedIps.isEmpty()) {
            LOG.debug("No external IPs defined for {}", id);
            return;
        }

        LOG.info("{} ARP responder flows for {} fixed-ips {} on switch {}",
                addOrRemove == NwConstants.ADD_FLOW ? "Installing" : "Removing", id, fixedIps, dpnId);

        boolean submit = false;
        if (writeTx == null) {
            submit = true;
            writeTx = dataBroker.newWriteOnlyTransaction();
        }

        for (String fixedIp : fixedIps) {
            if (addOrRemove == NwConstants.ADD_FLOW) {
                installArpResponderFlowsToExternalNetworkIp(macAddress, dpnId, extInterfaceName, lportTag, vpnId,
                        fixedIp, writeTx);
            } else {
                removeArpResponderFlowsToExternalNetworkIp(dpnId, lportTag, fixedIp, writeTx,extInterfaceName);
            }
        }

        if (submit) {
            writeTx.submit();
        }
    }

    @Override
    public String getPrimaryRdFromVpnInstance(VpnInstance vpnInstance) {
        return VpnUtil.getPrimaryRd(vpnInstance);
    }

    @Override
    public List<MatchInfoBase> getEgressMatchesForVpn(String vpnName) {
        long vpnId = VpnUtil.getVpnId(dataBroker, vpnName);
        if (vpnId == VpnConstants.INVALID_ID) {
            LOG.warn("No VPN id found for {}", vpnName);
            return Collections.emptyList();
        }

        return Collections
                .singletonList(new NxMatchRegister(VpnConstants.VPN_REG_ID, vpnId, MetaDataUtil.getVpnIdMaskForReg()));
    }

    private void installArpResponderFlowsToExternalNetworkIp(String macAddress, BigInteger dpnId,
            String extInterfaceName, int lportTag, long vpnId, String fixedIp, WriteTransaction writeTx) {
        // reset the split-horizon bit to allow traffic to be sent back to the
        // provider port
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(
                new InstructionWriteMetadata(BigInteger.ZERO, MetaDataUtil.METADATA_MASK_SH_FLAG).buildInstruction(1));
        instructions.addAll(
                ArpResponderUtil.getExtInterfaceInstructions(interfaceManager, extInterfaceName, fixedIp, macAddress));
        ArpReponderInputBuilder builder = new ArpReponderInputBuilder().setDpId(dpnId)
                .setInterfaceName(extInterfaceName).setSpa(fixedIp).setSha(macAddress).setLportTag(lportTag);
        builder.setInstructions(instructions);
        elanService.addArpResponderFlow(builder.buildForInstallFlow());
    }

    private void removeArpResponderFlowsToExternalNetworkIp(BigInteger dpnId, Integer lportTag, String fixedIp,
            WriteTransaction writeTx,String extInterfaceName) {
        ArpResponderInput arpInput = new ArpReponderInputBuilder().setDpId(dpnId).setInterfaceName(extInterfaceName)
                .setSpa(fixedIp).setLportTag(lportTag).buildForRemoveFlow();
        elanService.removeArpResponderFlow(arpInput);
    }

    private long getVpnIdFromExtNetworkId(Uuid extNetworkId) {
        Uuid vpnInstanceId = VpnUtil.getExternalNetworkVpnId(dataBroker, extNetworkId);
        if (vpnInstanceId == null) {
            LOG.debug("Network {} is not associated with VPN", extNetworkId.getValue());
            return VpnConstants.INVALID_ID;
        }

        return VpnUtil.getVpnId(dataBroker, vpnInstanceId.getValue());
    }

    @Override
    public void onSubnetAddedToVpn(Subnetmap subnetmap, boolean isBgpVpn, Long elanTag) {
        vpnSubnetRouteHandler.onSubnetAddedToVpn(subnetmap, isBgpVpn, elanTag);
    }

    @Override
    public void onSubnetDeletedFromVpn(Subnetmap subnetmap, boolean isBgpVpn) {
        vpnSubnetRouteHandler.onSubnetDeletedFromVpn(subnetmap, isBgpVpn);
    }

    public VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        return VpnUtil.getVpnInstance(broker, vpnInstanceName);
    }

    public String getVpnRd(DataBroker broker, String vpnName) {
        return VpnUtil.getVpnRd(broker, vpnName);
    }

    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        return VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnName, fixedIp);
    }
}
