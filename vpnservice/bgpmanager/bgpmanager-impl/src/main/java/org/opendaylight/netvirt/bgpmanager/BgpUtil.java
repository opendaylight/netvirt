/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import org.opendaylight.controller.md.sal.binding.api.BindingTransactionChain;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChain;
import org.opendaylight.controller.md.sal.common.api.data.TransactionChainListener;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.utils.batching.ActionableResource;
import org.opendaylight.genius.utils.batching.ActionableResourceImpl;
import org.opendaylight.genius.utils.batching.ResourceBatchingManager;
import org.opendaylight.genius.utils.batching.ResourceHandler;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.encap_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTeps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.elan.instance.ExternalTepsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BgpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(BgpUtil.class);
    private static DataBroker dataBroker;
    private static BindingTransactionChain fibTransact;
    public static final int PERIODICITY = 500;
    public static final int BATCH_SIZE = 1000;
    public static Integer batchSize;
    public static Integer batchInterval;
    private static int txChainAttempts = 0;

    private static BlockingQueue<ActionableResource> bgpResourcesBufferQ = new LinkedBlockingQueue<>();

    /** get a translation from prefix ipv6 to afi<br>.
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

    static ThreadFactory namedThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("bgp-util-mdsal-%d").build();

    static ExecutorService threadPool = Executors.newFixedThreadPool(1, namedThreadFactory);


    static synchronized BindingTransactionChain getTransactionChain() {
        return fibTransact;
    }

    static void registerWithBatchManager(ResourceHandler resourceHandler) {
        ResourceBatchingManager resBatchingManager = ResourceBatchingManager.getInstance();
        resBatchingManager.registerBatchableResource("BGP-RESOURCES", bgpResourcesBufferQ, resourceHandler);
    }

    static <T extends DataObject> void update(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.UPDATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    public static <T extends DataObject> void write(DataBroker broker, final LogicalDatastoreType datastoreType,
                                                    final InstanceIdentifier<T> path, final T data) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.CREATE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(data);
        bgpResourcesBufferQ.add(actResource);
    }

    static <T extends DataObject> void delete(DataBroker broker, final LogicalDatastoreType datastoreType,
                                              final InstanceIdentifier<T> path) {
        ActionableResource actResource = new ActionableResourceImpl(path.toString());
        actResource.setAction(ActionableResource.DELETE);
        actResource.setInstanceIdentifier(path);
        actResource.setInstance(null);
        bgpResourcesBufferQ.add(actResource);
    }

    public static void setBroker(final DataBroker broker) {
        BgpUtil.dataBroker = broker;
        initTransactionChain();
    }

    static synchronized void initTransactionChain() {
        if (fibTransact != null) {
            fibTransact.close();
            LOG.error("*** TxChain Close, *** Attempts: {}", txChainAttempts);
            fibTransact = null;
        }
        BgpUtil.fibTransact = dataBroker.createTransactionChain(new BgpUtilTransactionChainListener());
        txChainAttempts++;
    }

    static class BgpUtilTransactionChainListener implements TransactionChainListener {
        @Override
        public void onTransactionChainFailed(TransactionChain<?, ?> transactionChain,
                                             AsyncTransaction<?, ?> asyncTransaction, Throwable throwable) {
            LOG.error("*** TxChain Creation Failed *** Attempts: {}", txChainAttempts);
            initTransactionChain();
        }

        @Override
        public void onTransactionChainSuccessful(TransactionChain<?, ?> transactionChain) {
            LOG.trace("TxChain Creation Success");
        }
    }

    public static DataBroker getBroker() {
        return dataBroker;
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

    static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd)  {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = MDSALUtil.read(broker,
                LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    static String getElanNamefromRd(DataBroker broker, String rd)  {
        InstanceIdentifier<EvpnRdToNetwork> id = getEvpnRdToNetworkIdentifier(rd);
        Optional<EvpnRdToNetwork> evpnRdToNetworkOpData = MDSALUtil.read(broker,
                LogicalDatastoreType.CONFIGURATION, id);
        if (evpnRdToNetworkOpData.isPresent()) {
            return evpnRdToNetworkOpData.get().getNetworkId();
        }
        return null;
    }

    public static InstanceIdentifier<EvpnRdToNetwork> getEvpnRdToNetworkIdentifier(String rd) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
    }

    public static void addTepToElanInstance(DataBroker broker, String rd, String tepIp) {
        if (rd == null || tepIp == null) {
            LOG.error("addTepToElanInstance : Null parameters returning");
            return;
        }
        String elanName = getElanNamefromRd(broker, rd);
        if (elanName == null) {
            LOG.error("Elan null while processing RT2 for RD {}", rd);
            return;
        }
        LOG.debug("Adding tepIp {} to elan {}", tepIp, elanName);
        InstanceIdentifier<ExternalTeps> externalTepsId = getExternalTepsIdentifier(elanName, tepIp);
        ExternalTepsBuilder externalTepsBuilder = new ExternalTepsBuilder();
        ExternalTepsKey externalTepsKey = externalTepsId.firstKeyOf(ExternalTeps.class);
        externalTepsBuilder.setKey(externalTepsKey);
        externalTepsBuilder.setTepIp(externalTepsKey.getTepIp());
        BgpUtil.update(dataBroker, LogicalDatastoreType.CONFIGURATION, externalTepsId, externalTepsBuilder.build());
    }

    public static void deleteTepFromElanInstance(DataBroker broker, String rd, String tepIp) {
        if (rd == null || tepIp == null) {
            LOG.error("deleteTepFromElanInstance : Null parameters returning");
            return;
        }
        String elanName = getElanNamefromRd(broker, rd);
        if (elanName == null) {
            LOG.error("Elan null while processing RT2 withdraw for RD {}", rd);
            return;
        }
        LOG.debug("Deleting tepIp {} from elan {}", tepIp, elanName);
        InstanceIdentifier<ExternalTeps> externalTepsId = getExternalTepsIdentifier(elanName, tepIp);
        BgpUtil.delete(dataBroker, LogicalDatastoreType.CONFIGURATION, externalTepsId);
    }

    public static InstanceIdentifier<ExternalTeps> getExternalTepsIdentifier(String elanInstanceName, String tepIp) {
        IpAddress tepAdress = tepIp == null ? null : new IpAddress(tepIp.toCharArray());
        return InstanceIdentifier.builder(ElanInstances.class).child(ElanInstance.class,
                new ElanInstanceKey(elanInstanceName)).child(ExternalTeps.class,
                new ExternalTepsKey(tepAdress)).build();
    }

    public static String getVpnNameFromRd(DataBroker dataBroker2, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(dataBroker2, rd);
        return vpnInstanceOpData != null ? vpnInstanceOpData.getVpnInstanceName() : null;
    }
}

