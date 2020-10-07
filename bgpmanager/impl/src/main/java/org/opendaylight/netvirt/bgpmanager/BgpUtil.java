/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResources;
import org.opendaylight.genius.utils.batching.DefaultBatchHandler;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.encap_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebfd.rev190219.BfdConfig;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.DcgwTepList;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfsContainer;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.dcgw.tep.list.DcgwTep;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.dcgw.tep.list.DcgwTepKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.Vrfs;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.VrfsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfscontainer.vrfs.AddressFamiliesVrf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeMplsOverGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BgpUtil implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static final String RESOURCE_TYPE = "BGP-RESOURCES";
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int DEFAULT_BATCH_INTERVAL = 500;
    private int enableBfdFlag = -1;

    private final DataBroker dataBroker;
    private final IFibManager fibManager;


    private final BlockingQueue<ActionableResource<?>> bgpResourcesBufferQ = new LinkedBlockingQueue<>();

    @Inject
    public BgpUtil(DataBroker dataBroker, final IFibManager fibManager) {
        this.dataBroker = dataBroker;
        this.fibManager = fibManager;
    }

    @PostConstruct
    public void init() {
        ResourceBatchingManager resBatchingManager = ResourceBatchingManager.getInstance();

        Integer batchSize = Integer.getInteger("batch.size", DEFAULT_BATCH_SIZE);
        Integer batchInterval = Integer.getInteger("batch.wait.time", DEFAULT_BATCH_INTERVAL);

        resBatchingManager.registerBatchableResource(RESOURCE_TYPE, bgpResourcesBufferQ,
                new DefaultBatchHandler(dataBroker, LogicalDatastoreType.CONFIGURATION, batchSize, batchInterval));
    }

    @Override
    @PreDestroy
    public void close() {
        ResourceBatchingManager.getInstance().deregisterBatchableResource(RESOURCE_TYPE);
    }

    /**
     * get a translation from prefix ipv6 to afi<br>.
     * "ffff::1/128" sets afi as 2 because is an IPv6 value
     * @param argPrefix ip address as ipv4 or ipv6
     * @return afi 1 for AFI_IP 2 for AFI_IPV6
     */
    public static int getAFItranslatedfromPrefix(String argPrefix) {
        int retValue = af_afi.AFI_IP.getValue();//default afiValue is 1 (= ipv4)
        String prefixOnly;
        if (argPrefix.indexOf("/") == -1) {
            prefixOnly = argPrefix;
        } else {
            prefixOnly = argPrefix.substring(0, argPrefix.indexOf("/"));
        }
        try {
            InetAddress address = InetAddress.getByName(prefixOnly);
            if (address instanceof Inet6Address) {
                retValue = af_afi.AFI_IPV6.getValue();
            } else if (address instanceof Inet4Address) {
                retValue = af_afi.AFI_IP.getValue();
            }
        } catch (java.net.UnknownHostException e) {
            /*if exception is catched then the prefix is not an IPv6 and IPv4*/
            LOG.error("Unrecognized ip address ipAddress: {}", argPrefix);
            retValue = af_afi.AFI_IP.getValue();//default afiValue is 1 (= ipv4)
        }
        return retValue;
    }

    public <T extends DataObject> void update(final InstanceIdentifier<T> path, final T data) {
        bgpResourcesBufferQ.add(ActionableResources.update(path, data));
    }

    public <T extends DataObject> void write(final InstanceIdentifier<T> path, final T data) {
        bgpResourcesBufferQ.add(ActionableResources.create(path, data));
    }

    public <T extends DataObject> void delete(final InstanceIdentifier<T> path) {
        bgpResourcesBufferQ.add(ActionableResources.delete(path));
    }

    // Convert ProtocolType to thrift protocol_type
    public static protocol_type convertToThriftProtocolType(BgpControlPlaneType protocolType) {
        switch (protocolType) {
            case PROTOCOLLU:
                return protocol_type.PROTOCOL_LU;
            case PROTOCOLL3VPN:
                return protocol_type.PROTOCOL_L3VPN;
            case PROTOCOLEVPN:
                return protocol_type.PROTOCOL_EVPN;
            default:
                return protocol_type.PROTOCOL_ANY;
        }
    }

    // Convert EncapType to thrift encap_type
    public static encap_type convertToThriftEncapType(EncapType encapType) {
        switch (encapType) {
            case L2TPV3OVERIP:
                return encap_type.L2TPV3_OVER_IP;
            case GRE:
                return encap_type.GRE;
            case IPINIP:
                return encap_type.IP_IN_IP;
            case VXLAN:
                return encap_type.VXLAN;
            case MPLS:
            default:
                return encap_type.MPLS;
        }
    }

    public VpnInstanceOpDataEntry getVpnInstanceOpData(String rd)  {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = Optional.empty();
        try {
            vpnInstanceOpData = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading VpnInstanceOpDataEntry DS for the Vpn Rd {}", rd, e);
        }
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    private String getElanNamefromRd(String rd)  {
        InstanceIdentifier<EvpnRdToNetwork> id = getEvpnRdToNetworkIdentifier(rd);
        Optional<EvpnRdToNetwork> evpnRdToNetworkOpData = Optional.empty();
        try {
            evpnRdToNetworkOpData = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading EvpnRdToNetwork DS for the Vpn Rd {}", rd, e);
        }
        if (evpnRdToNetworkOpData.isPresent()) {
            return evpnRdToNetworkOpData.get().getNetworkId();
        }
        return null;
    }

    private static InstanceIdentifier<EvpnRdToNetwork> getEvpnRdToNetworkIdentifier(String rd) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
    }

    public void addTepToElanInstance(String rd, String tepIp) {
        if (rd == null || tepIp == null) {
            LOG.error("addTepToElanInstance : Null parameters returning");
            return;
        }
        String elanName = getElanNamefromRd(rd);
        if (elanName == null) {
            LOG.error("Elan null while processing RT2 for RD {}", rd);
            return;
        }
        LOG.debug("Adding tepIp {} to elan {}", tepIp, elanName);
        InstanceIdentifier<ExternalTeps> externalTepsId = getExternalTepsIdentifier(elanName, tepIp);
        ExternalTepsBuilder externalTepsBuilder = new ExternalTepsBuilder();
        ExternalTepsKey externalTepsKey = externalTepsId.firstKeyOf(ExternalTeps.class);
        externalTepsBuilder.setTepIp(externalTepsKey.getTepIp());
        update(externalTepsId, externalTepsBuilder.build());
    }

    public void deleteTepFromElanInstance(String rd, String tepIp) {
        if (rd == null || tepIp == null) {
            LOG.error("deleteTepFromElanInstance : Null parameters returning");
            return;
        }
        String elanName = getElanNamefromRd(rd);
        if (elanName == null) {
            LOG.error("Elan null while processing RT2 withdraw for RD {}", rd);
            return;
        }
        LOG.debug("Deleting tepIp {} from elan {}", tepIp, elanName);
        InstanceIdentifier<ExternalTeps> externalTepsId = getExternalTepsIdentifier(elanName, tepIp);
        delete(externalTepsId);
    }

    private static InstanceIdentifier<ExternalTeps> getExternalTepsIdentifier(String elanInstanceName, String tepIp) {
        IpAddress tepAdress = tepIp == null ? null : new IpAddress(new Ipv4Address(tepIp));
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class,
            new ElanInstanceKey(elanInstanceName)).child(ExternalTeps.class,
                new ExternalTepsKey(tepAdress)).build();
    }

    public String getVpnNameFromRd(String rd) {
        final Map<String, String> rdtoVpnMap = new HashMap<>();
        if (rdtoVpnMap.get(rd) != null) {
            return rdtoVpnMap.get(rd);
        } else {
            VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
            String vpnName = vpnInstanceOpData != null ? vpnInstanceOpData.getVpnInstanceName() : null;
            rdtoVpnMap.put(rd, vpnName);
            return vpnName;
        }
    }

    /** get the vrf with the RouterDistinguisher pass in param.
     * @param rd is the RouteDistinguisher of vrf
     * @return the vrf of rd or null if no exist
     */
    public Vrfs getVrfFromRd(String rd) {
        Vrfs vrfs = null;
        KeyedInstanceIdentifier<Vrfs, VrfsKey> id = InstanceIdentifier.create(Bgp.class)
                .child(VrfsContainer.class)
                .child(Vrfs.class, new VrfsKey(rd));
        Optional<Vrfs> vrfsFromDs = Optional.empty();
        try {
            vrfsFromDs = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading BGP VRF table for the Vpn Rd {}", rd, e);
        }
        if (vrfsFromDs.isPresent()) {
            vrfs = vrfsFromDs.get();
        }
        return vrfs;
    }

    /** get layerType used from an AddressFamiliesVrf.
     * @param adf is the AddressFamiliesVrf from which the layer is asked.
     * @return the layerType to reach from the argument addressFamilyVrf or null if not found
     */
    public static LayerType getLayerType(AddressFamiliesVrf adf) {
        LayerType layerTypeValue = null;
        if (adf.getSafi().intValue() == af_safi.SAFI_EVPN.getValue()) {
            layerTypeValue = LayerType.LAYER2;
        } else if (adf.getSafi().intValue() == af_safi.SAFI_MPLS_VPN.getValue()) {
            layerTypeValue = LayerType.LAYER3;
        }
        return layerTypeValue;
    }

    public void removeVrfEntry(String rd, VrfEntry vrfEntry) {
        LOG.debug("removeVrfEntry : vrf {} prefix {}", rd, vrfEntry.getDestPrefix());
        InstanceIdentifier<VrfEntry> vrfEntryId =
                   InstanceIdentifier.builder(FibEntries.class)
                   .child(VrfTables.class, new VrfTablesKey(rd))
                   .child(VrfEntry.class, new VrfEntryKey(vrfEntry.getDestPrefix())).build();
        delete(vrfEntryId);
    }

    public void enableBfdFlag() {
        enableBfdFlag = 1;
    }

    public void disableBfdFlag() {
        enableBfdFlag = 0;
    }

    public boolean isBfdEnabled() {
        if (enableBfdFlag == 1) {
            return true;
        } else if (enableBfdFlag == 0) {
            return false;
        }
        BfdConfig bfdConfig = getBfdConfig();
        if (bfdConfig != null) {
            return bfdConfig.isBfdEnabled();
        }
        return false;
    }

    public BfdConfig getBfdConfig() {
        InstanceIdentifier<BfdConfig> id =
                InstanceIdentifier.builder(BfdConfig.class).build();
        Optional<BfdConfig> bfdConfigOptional = Optional.empty();
        try {
            bfdConfigOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading BfdConfig", e);
        }
        if (bfdConfigOptional.isPresent()) {
            return bfdConfigOptional.get();
        }
        return null;
    }

    public DcgwTepList getDcgwTepConfig() {
        InstanceIdentifier<DcgwTepList> id =
                InstanceIdentifier.builder(Bgp.class).child(DcgwTepList.class).build();
        Optional<DcgwTepList> dcgwTepListOptional = Optional.empty();
        try {
            dcgwTepListOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getDcgwTepConfig: Exception while reading DcgwTepList", e);
        }
        if (dcgwTepListOptional.isPresent()) {
            return dcgwTepListOptional.get();
        }
        return null;
    }

    public List<String> getDcgwTepConfig(String dcgwIp) {
        InstanceIdentifier<DcgwTep> id =
                InstanceIdentifier.builder(Bgp.class)
                        .child(DcgwTepList.class)
                        .child(DcgwTep.class, new DcgwTepKey(dcgwIp)).build();
        Optional<DcgwTep> tepListOptional = Optional.empty();
        try {
            tepListOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Exception while reading DcgwTep for the IP {}", dcgwIp, e);
        }
        if (tepListOptional.isPresent()) {
            return tepListOptional.get().getTepIps();
        }
        LOG.debug("No tep configured for DCGW {}", dcgwIp);
        return null;
    }

    public static List<DPNTEPsInfo> getDpnTEPsInfos(DataBroker dataBroker) {
        InstanceIdentifier<DpnEndpoints> iid = InstanceIdentifier.builder(DpnEndpoints.class).build();
        Optional<DpnEndpoints> dpnEndpoints = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, iid, dataBroker);
        if (dpnEndpoints.isPresent()) {
            return new ArrayList<DPNTEPsInfo>(dpnEndpoints.get().getDPNTEPsInfo().values());
        } else {
            return new ArrayList<>();
        }
    }

    public void removeOrUpdateLBGroups(String tepIp, int addRemoveOrUpdate) {
        getDpnTEPsInfos(dataBroker).forEach(dpnInfo -> {
            if (NwConstants.MOD_FLOW == addRemoveOrUpdate) {
                LOG.debug("Updating bucket in DPN {}", dpnInfo.getDPNID());
            } else if (NwConstants.DEL_FLOW == addRemoveOrUpdate) {
                LOG.debug("Deleting groups in DPN {}", dpnInfo.getDPNID());
            }
            Class<? extends TunnelTypeBase> tunType = TunnelTypeMplsOverGre.class;
            fibManager.programDcGwLoadBalancingGroup(dpnInfo.getDPNID(),
                    tepIp, addRemoveOrUpdate, false, tunType);
        });
    }
}
