/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.DefaultBatchHandler;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.encap_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.LayerType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Vrfs;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfs.AddressFamiliesVrf;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetworkKey;
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

    private final DataBroker dataBroker;

    private final BlockingQueue<ActionableResource> bgpResourcesBufferQ = new LinkedBlockingQueue<>();

    @Inject
    public BgpUtil(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
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
        if (!argPrefix.contains("/")) {
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
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    public <T extends DataObject> void write(final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    public <T extends DataObject> void delete(final InstanceIdentifier<T> path) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(null);
        bgpResourcesBufferQ.add(actResource);
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
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = MDSALUtil.read(dataBroker,
                LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vpnName)).build();
    }

    private String getElanNamefromRd(String vpnName)  {
        InstanceIdentifier<EvpnToNetwork> id = getEvpnToNetworkIdentifier(vpnName);
        Optional<EvpnToNetwork> evpnToNetworkOpData = MDSALUtil.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id);
        if (evpnToNetworkOpData.isPresent()) {
            return evpnToNetworkOpData.get().getNetworkId();
        }
        return null;
    }

    private static InstanceIdentifier<EvpnToNetwork> getEvpnToNetworkIdentifier(String vpnName) {
        return InstanceIdentifier.builder(EvpnToNetworks.class)
                .child(EvpnToNetwork.class, new EvpnToNetworkKey(vpnName)).build();
    }

    public void addTepToElanInstance(String vpnName, String tepIp) {
        if (vpnName == null || tepIp == null) {
            LOG.error("addTepToElanInstance : Null parameters returning");
            return;
        }
        String elanName = getElanNamefromRd(vpnName);
        if (elanName == null) {
            LOG.error("Elan null while processing RT2 for vpnName {}", vpnName);
            return;
        }
        LOG.debug("Adding tepIp {} to elan {}", tepIp, elanName);
        InstanceIdentifier<ExternalTeps> externalTepsId = getExternalTepsIdentifier(elanName, tepIp);
        ExternalTepsBuilder externalTepsBuilder = new ExternalTepsBuilder();
        ExternalTepsKey externalTepsKey = externalTepsId.firstKeyOf(ExternalTeps.class);
        externalTepsBuilder.setKey(externalTepsKey);
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
        IpAddress tepAdress = tepIp == null ? null : new IpAddress(tepIp.toCharArray());
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class,
                new ElanInstanceKey(elanInstanceName)).child(ExternalTeps.class,
                new ExternalTepsKey(tepAdress)).build();
    }

    public String getVpnNameFromRd(String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
        return vpnInstanceOpData != null ? vpnInstanceOpData.getVpnInstanceName() : null;
    }

    /** get the vrf with the RouterDistinguisher pass in param.
     * @param rd is the RouteDistinguisher of vrf
     * @return the vrf of rd or null if no exist
     */
    public Vrfs getVrfFromRd(String rd) {
        Vrfs vrfs = null;
        KeyedInstanceIdentifier<Vrfs, VrfsKey> id = InstanceIdentifier.create(Bgp.class)
                .child(Vrfs.class, new VrfsKey(rd));
        Optional<Vrfs> vrfsFromDs = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
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
        if (adf.getSafi() == af_safi.SAFI_EVPN.getValue()) {
            layerTypeValue = LayerType.LAYER2;
        } else if (adf.getSafi() == af_safi.SAFI_MPLS_VPN.getValue()) {
            layerTypeValue = LayerType.LAYER3;
        }
        return layerTypeValue;
    }

    public void removeVrfEntry(String vpnName, String rd, VrfEntry vrfEntry) {
        LOG.debug("removeVrfEntry : vpn {} vrf {} prefix {}", vpnName,
                rd, vrfEntry.getDestPrefix());
        InstanceIdentifier<VrfEntry> vrfEntryId =
                   InstanceIdentifier.builder(FibEntries.class)
                           .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnName))
                           .child(VrfTables.class, new VrfTablesKey(rd))
                           .child(VrfEntry.class, new VrfEntryKey(vrfEntry.getDestPrefix())).build();
        delete(vrfEntryId);
    }
}
