package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanForwardingTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.forwarding.tables.MacTableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.Elan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.state.ElanKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by eriytal on 2/7/2017.
 */
public class ElanEvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnUtils.class);
    private final DataBroker broker;
    private final IBgpManager bgpManager;
    private final IInterfaceManager interfaceManager;
    private final ElanUtils elanUtils;
    private final IdManagerService idManager;
    private final IMdsalApiManager mdsalManager;

    public ElanEvpnUtils(DataBroker broker, IBgpManager bgpManager, IInterfaceManager interfaceManager, ElanUtils elanUtils, IdManagerService idManager, IMdsalApiManager mdsalManager) {
        this.broker = broker;
        this.bgpManager = bgpManager;
        this.interfaceManager = interfaceManager;
        this.elanUtils = elanUtils;
        this.idManager = idManager;
        this.mdsalManager = mdsalManager;
    }

    public void init() {
    }

    public void close() {
    }

/*    public boolean isNetAttachedToEvpn(ElanEvpn elanEvpn) {
        return (elanEvpn.getEvpn() != null);
    }*/

    public boolean isEvpnPresent(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName != null) && (updatedEvpnName != null)){
            return true;
        }
        return false;
    }

/*    public boolean isNetAttachedToEvpn(ElanEvpn elanEvpnOriginal, ElanEvpn elanEvpnUpdated) {
        return elanEvpnUpdated.getEvpn() != null && elanEvpnOriginal.getEvpn() == null;
    }*/

    public boolean isNetAttachedToEvpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName == null) && (updatedEvpnName != null)){
            return true;
        }
        return false;
    }

/*    public boolean isNetAttachedToL3vpn(ElanEvpn elanEvpnOriginal, ElanEvpn elanEvpnUpdated) {
        return elanEvpnUpdated.getL3vpn() != null && elanEvpnOriginal.getL3vpn() == null;
    }*/

    public boolean isNetAttachedToL3vpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalL3vpnName = getL3vpnNameFromElan(elanOriginal);
        String updatedL3vpnName = getL3vpnNameFromElan(elanUpdated);
        if ((originalL3vpnName == null) && (updatedL3vpnName != null)){
            return true;
        }

        return false;
    }

/*    public boolean isNetworkDetachedFromEvpn(ElanEvpn elanEvpnOriginal, ElanEvpn elanEvpnUpdated) {
        return ((elanEvpnOriginal.getEvpn() != null) && (elanEvpnUpdated.getEvpn() == null));
    }*/

    public boolean isNetworkDetachedFromEvpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalEvpnName = getEvpnNameFromElan(elanOriginal);
        String updatedEvpnName = getEvpnNameFromElan(elanUpdated);
        if ((originalEvpnName != null) && (updatedEvpnName == null)) {
            return true;
        }

        return false;
    }

    private String getEvpnNameFromElan(ElanInstance elanInstance) {
        EvpnAugmentation evpnAugmentation = elanInstance.getAugmentation(EvpnAugmentation.class);
        if (evpnAugmentation != null) {
            return evpnAugmentation.getEvpnName();
        }
        return null;
    }

    private String getL3vpnNameFromElan(ElanInstance elanInstance) {
        EvpnAugmentation evpnAugmentation = elanInstance.getAugmentation(EvpnAugmentation.class);
        if (evpnAugmentation != null) {
            return evpnAugmentation.getL3vpnName();
        }
        return null;
    }

/*    public boolean isNetDettachedFromL3vpn(ElanEvpn elanEvpnOriginal, ElanEvpn elanEvpnUpdated) {
        return ((elanEvpnOriginal.getL3vpn() != null) && (elanEvpnUpdated.getL3vpn() == null));
    }*/

    public boolean isNetDettachedFromL3vpn(ElanInstance elanOriginal, ElanInstance elanUpdated) {
        String originalL3vpnName = getL3vpnNameFromElan(elanOriginal);
        String updatedL3vpnName = getL3vpnNameFromElan(elanUpdated);
        if ((originalL3vpnName != null) && (updatedL3vpnName == null)) {
            return true;
        }

        return false;
    }

    public static InstanceIdentifier<Elan> getElanWithInterfaces(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanState.class).child(Elan.class, new ElanKey(elanInstanceName))
                .build();

    }

    public static String getEndpointIpAddressForDPN(DataBroker broker, BigInteger dpnId) {
        String nextHopIp = null;
        InstanceIdentifier<DPNTEPsInfo> tunnelInfoId =
                InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpnId)).build();
        Optional<DPNTEPsInfo> tunnelInfo = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, tunnelInfoId);

        if (tunnelInfo.isPresent()) {
            List<TunnelEndPoints> nexthopIpList = tunnelInfo.get().getTunnelEndPoints();
            if (nexthopIpList != null && !nexthopIpList.isEmpty()) {
                nextHopIp = nexthopIpList.get(0).getIpAddress().getIpv4Address().getValue();
            }
        }
        return nextHopIp;
    }

    private Optional<String> getGatewayMacAddressForInterface(String vpnName, String ifName, String ipAddress) {
        Optional<String> routerGwMac = Optional.absent();
        VpnPortipToPort gwPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(broker, vpnName, ipAddress);
        //Check if a router gateway interface is available for the subnet gw is so then use Router interface
        // else use connected interface
        routerGwMac = Optional.of((gwPort != null && gwPort.isSubnetIp())
                ? gwPort.getMacAddress() : InterfaceUtils.getMacAddressForInterface(broker, ifName).get());
        return routerGwMac;
    }

/*    public void withdrawEVPNRT2Routes(ElanEvpn elanEvpn) {
        LOG.info("withdrawEVPNRT2Routes : elan name {}", elanEvpn.getName());

        Optional<MacTable> existingMacTable = getMacTableFromOperationalDS(elanEvpn.getName());
        if (!existingMacTable.isPresent()) {
            LOG.error("withdrawEVPNRT2Routes : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            VpnInstance vpnInstance = VpnUtil.getVpnInstance(broker, elanEvpn.getEvpn());

            //String rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            String rd = VpnUtil.getVpnRd(broker, elanEvpn.getEvpn());
            String prefix = macEntry.getPrefix().toString();

            LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
            bgpManager.withdrawPrefix(rd, prefix);
        }

        return;
    }*/

    public void withdrawEVPNRT2Routes(ElanInstance elanInstance) {
        LOG.info("withdrawEVPNRT2Routes : elan name {}", elanInstance.getElanInstanceName());

        String evpnName = getEvpnNameFromElan(elanInstance);
        Optional<MacTable> existingMacTable = getMacTableFromOperationalDS(elanInstance.getElanInstanceName());
        if (!existingMacTable.isPresent()) {
            LOG.error("withdrawEVPNRT2Routes : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            //VpnInstance vpnInstance = VpnUtil.getVpnInstance(broker, getEvpnNameFromElanInstance(elanInstance));
            //String rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            String rd = VpnUtil.getVpnRd(broker, getEvpnNameFromElan(elanInstance));
            String prefix = macEntry.getPrefix().toString();

            LOG.info("Withdrawing routes with rd {}, prefix {}", rd, prefix);
            bgpManager.withdrawPrefix(rd, prefix);
        }

        return;
    }

    private Optional<MacTable> getMacTableFromOperationalDS(String elanName) {
        InstanceIdentifier<MacTable> macTableIid = getMacTableIidFromOpertionalDS(elanName);
        Optional<MacTable> existingMacTable = elanUtils.read(broker, LogicalDatastoreType.OPERATIONAL, macTableIid);
        return existingMacTable;
    }

    public static InstanceIdentifier<MacTable> getMacTableIidFromOpertionalDS(String elanName) {
        return InstanceIdentifier.builder(ElanForwardingTables.class).child(MacTable.class, new MacTableKey(elanName)).build();
    }

/*    public void advertiseEVPNRT2Route(ElanEvpn elanEvpn) throws Exception {
        LOG.info("advertiseEVPNRT2Route : elan name {}", elanEvpn.getName());

        Optional<MacTable> existingMacTable = getMacTableFromOperationalDS(elanEvpn.getName());
        if (!existingMacTable.isPresent()) {
            LOG.error("advertiseEVPNRT2Route : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            String macAddress = macEntry.getMacAddress().toString();
            String prefix = macEntry.getPrefix().toString();
            VpnInstance vpnInstance = VpnUtil.getVpnInstance(broker, elanEvpn.getEvpn());
            //String rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            String rd = VpnUtil.getVpnRd(broker, elanEvpn.getEvpn());
            String interfaceName = macEntry.getInterface();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            String nextHop = getEndpointIpAddressForDPN(broker, interfaceInfo.getDpId());
            int vpnLabel = 0;
            long l3vni = 0;
            ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanEvpn.getName());
            long l2vni = elanInstance.getSegmentationId();
            String gatewayMacAddr = null;

            if (elanEvpn.getL3vpn() != null) {
                l3vni = vpnInstance.getL3vni();
                Optional<String> gatewayMac = getGatewayMacAddressForInterface(vpnInstance.getVpnInstanceName(), interfaceName, prefix);
                gatewayMacAddr = gatewayMac.get();
                LOG.info("advertiseEVPNRT2Route : l3vni  is {},  gatewayMacAddr", l3vni, gatewayMacAddr);
            }

            LOG.info("Advertising routes with rd {},  macAddress {}, prefix {}, nextHop {}," +
                            " vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {}", rd, macAddress, prefix, nextHop,
                    vpnLabel, l3vni, l2vni, gatewayMacAddr);

            bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                    VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);

        }

        return;
    }*/

    public void advertiseEVPNRT2Route(ElanInstance elanInstance) throws Exception {
        LOG.info("advertiseEVPNRT2Route : elan name {}", elanInstance.getElanInstanceName());


        Optional<MacTable> existingMacTable = getMacTableFromOperationalDS(elanInstance.getElanInstanceName());
        if (!existingMacTable.isPresent()) {
            LOG.error("advertiseEVPNRT2Route : existingMacTable  is not present ");
            return;
        }

        List<MacEntry> macEntries = existingMacTable.get().getMacEntry();
        for (MacEntry macEntry : macEntries) {
            String macAddress = macEntry.getMacAddress().toString();
            String prefix = macEntry.getPrefix().toString();
            String evpnName = getEvpnNameFromElan(elanInstance);
            VpnInstance vpnInstance = VpnUtil.getVpnInstance(broker, evpnName);
            //String rd = vpnInstance.getIpv4Family().getRouteDistinguisher();
            String rd = VpnUtil.getVpnRd(broker, evpnName);
            String interfaceName = macEntry.getInterface();
            InterfaceInfo interfaceInfo = interfaceManager.getInterfaceInfo(interfaceName);
            String nextHop = getEndpointIpAddressForDPN(broker, interfaceInfo.getDpId());
            int vpnLabel = 0;
            long l3vni = 0;
            //ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanEvpn.getName());
            long l2vni = elanInstance.getSegmentationId();
            String gatewayMacAddr = null;

            if (getL3vpnNameFromElan(elanInstance) != null) {
                l3vni = vpnInstance.getL3vni();
                Optional<String> gatewayMac = getGatewayMacAddressForInterface(vpnInstance.getVpnInstanceName(),
                        interfaceName, prefix);
                gatewayMacAddr = gatewayMac.get();
                LOG.info("advertiseEVPNRT2Route : l3vni  is {},  gatewayMacAddr", l3vni, gatewayMacAddr);
            }

            LOG.info("Advertising routes with rd {},  macAddress {}, prefix {}, nextHop {}," +
                            " vpnLabel {}, l3vni {}, l2vni {}, gatewayMac {}", rd, macAddress, prefix, nextHop,
                    vpnLabel, l3vni, l2vni, gatewayMacAddr);

            bgpManager.advertisePrefix(rd, macAddress, prefix, nextHop,
                    VrfEntryBase.EncapType.Vxlan, vpnLabel, l3vni, l2vni, gatewayMacAddr);

        }

        return;
    }

    public Long getElanTag(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet = InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork = tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            //elanName = evpnRdToNetwork.get().getEvpnNetworks().get(0).getNetworkId();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            e.printStackTrace();
        }

        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
        Long elanTag = elanInstance.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = elanUtils.retrieveNewElanTag(idManager, elanName);
        }

        return elanTag;
    }

    public String getElanName(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet = InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork = tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            //elanName = evpnRdToNetwork.get().getEvpnNetworks().get(0).getNetworkId();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            LOG.error("getElanName: Error : tx.read throws exception e {} ", e);
            e.printStackTrace();
        }

        return elanName;
    }

    public ElanInstance getElanInstance(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet = InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork = tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            //elanName = evpnRdToNetwork.get().getEvpnNetworks().get(0).getNetworkId();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            LOG.error("getElanInstance: Error : tx.read throws exception e {} ", e);
            e.printStackTrace();
        }

        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(broker, elanName);
        return elanInstance;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public Flow evpnBuildDmacFlowForExternalRemoteMac(BigInteger dpId, String nexthopIP, long elanTag,
                                                      Long vni, String dstMacAddress, String displayName) throws ElanException {
        List<MatchInfo> mkMatches = elanUtils.buildMatchesForElanTagShFlagAndDstMac(elanTag, /* shFlag */ false, dstMacAddress);
        List<Instruction> mkInstructions = new ArrayList<>();
        try {
            List<Action> actions = elanUtils.getExternalTunnelItmEgressAction(dpId, nexthopIP, vni);
            mkInstructions.add(MDSALUtil.buildApplyActionsInstruction(actions));
        } catch (Exception e) {
            LOG.error("Could not get Egress Actions for DpId=" + dpId + ", externalNode=" + nexthopIP, e);
        }

        Flow flow = MDSALUtil.buildFlowNew(NwConstants.ELAN_DMAC_TABLE,
                elanUtils.getKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIP, dstMacAddress, elanTag,
                        false),
                20, /* prio */
                displayName, 0, /* idleTimeout */
                0, /* hardTimeout */
                ElanConstants.COOKIE_ELAN_KNOWN_DMAC.add(BigInteger.valueOf(elanTag)), mkMatches, mkInstructions);

        return flow;
    }

    /**
     * Delete dmac flows to external mac.
     *
     * @param elanTag     the elan tag
     * @param dpId        the dp id
     * @param nexthopIp   the nexthopIP to which port is connected
     * @param macToRemove the mac to remove
     * @return dmac flow
     */
    public List<ListenableFuture<Void>> evpnDeleteDmacFlowsToExternalMac(long elanTag, BigInteger dpId,
                                                                         String nexthopIp, String macToRemove) {
        List<ListenableFuture<Void>> futures = new ArrayList<>();
        synchronized (elanUtils.getElanMacDPNKey(elanTag, macToRemove, dpId)) {
            // Removing the flows that sends the packet on an external tunnel
            evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(elanTag, dpId, nexthopIp, macToRemove, futures);

            evpnDeleteEtreeDmacFlowsToExternalMac(elanTag, dpId, nexthopIp, macToRemove, futures);
        }
        return futures;
    }

    private void evpnDeleteEtreeDmacFlowsToExternalMac(long elanTag, BigInteger dpId, String nexthopIp,
                                                       String macToRemove, List<ListenableFuture<Void>> futures) {
        EtreeLeafTagName etreeLeafTag = elanUtils.getEtreeLeafTagByElanTag(elanTag);
        if (etreeLeafTag != null) {
            evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(etreeLeafTag.getEtreeLeafTag().getValue(), dpId,
                    nexthopIp, macToRemove, futures);
            evpnRemoveTheDropFlow(etreeLeafTag.getEtreeLeafTag().getValue(), dpId, nexthopIp, macToRemove, futures);
        }
    }

    private void evpnRemoveTheDropFlow(long elanTag, BigInteger dpId, String nexthopIp, String macToRemove,
                                       List<ListenableFuture<Void>> futures) {
        String flowId = evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp, macToRemove,
                elanTag, true);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(mdsalManager.removeFlow(dpId, flowToRemove));
    }

    private void evpnRemoveFlowThatSendsThePacketOnAnExternalTunnel(long elanTag, BigInteger dpId,
                                                                    String nexthopIp, String macToRemove, List<ListenableFuture<Void>> futures) {
        String flowId = evpnGetKnownDynamicmacFlowRef(NwConstants.ELAN_DMAC_TABLE, dpId, nexthopIp, macToRemove,
                elanTag, false);
        Flow flowToRemove = new FlowBuilder().setId(new FlowId(flowId)).setTableId(NwConstants.ELAN_DMAC_TABLE).build();
        futures.add(mdsalManager.removeFlow(dpId, flowToRemove));
    }

    private static String evpnGetKnownDynamicmacFlowRef(short elanDmacTable, BigInteger dpId, String nexthopIp,
                                                        String dstMacAddress, long elanTag, boolean shFlag) {
        return new StringBuffer().append(elanDmacTable).append(elanTag).append(dpId).append(nexthopIp)
                .append(dstMacAddress).append(shFlag).toString();
    }

}
