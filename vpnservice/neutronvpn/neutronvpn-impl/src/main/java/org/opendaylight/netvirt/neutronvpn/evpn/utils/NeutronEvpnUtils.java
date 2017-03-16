/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.evpn.utils;

import com.google.common.base.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by eriytal on 4/4/2017.
 */
public class NeutronEvpnUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NeutronEvpnUtils.class);
    public static ConcurrentHashMap<String, VpnInstance> vpnMap = new ConcurrentHashMap<>();

    public static VpnInstance getVpnInstance(DataBroker broker, Uuid vpnId) {
        VpnInstance vpnInstance = null;
        vpnInstance = vpnMap.get(vpnId.getValue());
        if (vpnInstance != null) {
            return vpnInstance;
        }
        LOG.debug("getVpnInstance for {}", vpnId.getValue());
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.create(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue()));
        Optional<VpnInstance> optionalVpn = NeutronvpnUtils.read(broker,
                LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
        if (optionalVpn.isPresent()) {
            vpnInstance = optionalVpn.get();
        }
        return vpnInstance;
    }

    public static void addToVpnCache(VpnInstance vpnInstance) {
        vpnMap.put(vpnInstance.getVpnInstanceName(), vpnInstance);
    }

    public static void removeFromVpnCache(String vpnInstanceName) {
        vpnMap.remove(vpnInstanceName);
    }

    public static boolean isVpnAssociatedWithNetwork(DataBroker broker, VpnInstance vpnInstance,
                                                        IVpnManager vpnManager) {
        //String vrfId = VpnUtil.getPrimaryRd(vpnInstance);
        String vrfId = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);
        InstanceIdentifier<EvpnRdToNetwork> id = InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(vrfId)).build();
        Optional<EvpnRdToNetwork> optionalEvpnRdToNetwork =
                NeutronvpnUtils.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (optionalEvpnRdToNetwork.isPresent()) {
            LOG.debug("vpn is associated with network {}", optionalEvpnRdToNetwork);
            return true;
        }
        return false;
    }

    public static InstanceIdentifier<ElanInstance> getConfigElanInstanceIdentifier(String elanInstanceName) {
        return InstanceIdentifier.create(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName));
    }

    public static InstanceIdentifier<EvpnRdToNetwork> getRdToNetworkIdentifier(String vrfId) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(vrfId)).build();
    }

    public static void updateElanWithVpnInfo(DataBroker broker, String elanInstanceName, Uuid vpnId,
                                                boolean isDelete) throws ReadFailedException {
        final VpnInstance vpnInstance = getVpnInstance(broker, vpnId);
        String vpnName = vpnInstance.getVpnInstanceName();
        String l2vpnName = null;
        if (vpnInstance.getType().equals(VpnInstance.Type.L2)) {
            l2vpnName = vpnName;
        }

        EvpnAugmentationBuilder evpnAugmentationBuilder = new EvpnAugmentationBuilder();
        InstanceIdentifier<ElanInstance> elanIid = getConfigElanInstanceIdentifier(elanInstanceName);
        ReadWriteTransaction transaction = broker.newReadWriteTransaction();
        Optional<ElanInstance> elanInstanceOptional =
                transaction.read(LogicalDatastoreType.CONFIGURATION, elanIid).checkedGet();
        if (!elanInstanceOptional.isPresent()) {
            return;
        }
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstanceOptional.get());
        if (elanInstanceBuilder.getAugmentation(EvpnAugmentation.class) != null) {
            evpnAugmentationBuilder =
                    new EvpnAugmentationBuilder(elanInstanceBuilder.getAugmentation(EvpnAugmentation.class));
        }
        if (!isDelete) {
            evpnAugmentationBuilder.setEvpnName(l2vpnName);
            LOG.debug("Writing Elan-EvpnAugmentation with key {}", elanInstanceName);
        } else {
            evpnAugmentationBuilder.setEvpnName(null);
            LOG.debug("Deleting Elan-EvpnAugmentation with key {}", elanInstanceName);
            //elanInstanceBuilder.removeAugmentation(EvpnAugmentation.class);
        }

        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        LOG.debug("transaction.merge with elanid {} for elan instance {}",elanIid, elanInstanceName);
        transaction.put(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build());
        transaction.submit();
        return;
    }

    public static void updateVpnWithElanInfo(DataBroker broker, Uuid vpnId, String elanInstanceName,
                                                IVpnManager vpnManager, boolean isDelete) {
        final VpnInstance vpnInstance = getVpnInstance(broker, vpnId);
        //String vrfId = VpnUtil.getPrimaryRd(vpnInstance);
        String vrfId = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);
        InstanceIdentifier<EvpnRdToNetwork> rdToNetworkIdentifier = getRdToNetworkIdentifier(vrfId);
        if (isDelete) {
            LOG.debug("Deleting Evpn-Network with key {}", vrfId);
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, rdToNetworkIdentifier);
            return;
        }
        EvpnRdToNetworkBuilder evpnRdToNetworkBuilder = new EvpnRdToNetworkBuilder()
                .setKey(new EvpnRdToNetworkKey(vrfId));
        evpnRdToNetworkBuilder.setVrfId(vrfId);
        evpnRdToNetworkBuilder.setNetworkId(elanInstanceName);
        LOG.debug("Writing Evpn-Network with key {}", vrfId);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, rdToNetworkIdentifier,
                evpnRdToNetworkBuilder.build());
    }

    public static Optional<VpnInstances> getConfigVpnInstances(DataBroker dataBroker) {
        InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .build();
        return NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnsIdentifier);
    }

    public static Optional<VpnInstance> getConfigVpnInstanceByName(DataBroker dataBroker, String name) {
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(name)).build();
        return NeutronvpnUtils.read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                vpnIdentifier);
    }
}
