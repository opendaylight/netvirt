/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.evpn.utils;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.neutronvpn.NeutronvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
        return VpnHelper.getVpnInstance(broker, vpnId.getValue());
    }

    public static void addToVpnCache(VpnInstance vpnInstance) {
        vpnMap.put(vpnInstance.getVpnInstanceName(), vpnInstance);
    }

    public static void removeFromVpnCache(String vpnInstanceName) {
        vpnMap.remove(vpnInstanceName);
    }

    public static boolean isVpnAssociatedWithNetwork(DataBroker broker, VpnInstance vpnInstance,
                                                     IVpnManager vpnManager) {
        String rd = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);
        InstanceIdentifier<EvpnRdToNetwork> id = InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
        Optional<EvpnRdToNetwork> optionalEvpnRdToNetwork =
                NeutronvpnUtils.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (optionalEvpnRdToNetwork.isPresent()) {
            LOG.debug("vpn is associated with network {}", optionalEvpnRdToNetwork);
            return true;
        }
        return false;
    }

    public static InstanceIdentifier<EvpnRdToNetwork> getRdToNetworkIdentifier(String vrfId) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(vrfId)).build();
    }

    public static void updateElanWithVpnInfo(DataBroker broker, String elanInstanceName, VpnInstance vpnInstance,
                                             boolean isDelete) {
        String vpnName = vpnInstance.getVpnInstanceName();
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        ReadWriteTransaction transaction = broker.newReadWriteTransaction();
        Optional<ElanInstance> elanInstanceOptional = null;
        try {
            elanInstanceOptional = transaction.read(LogicalDatastoreType.CONFIGURATION, elanIid).checkedGet();
        } catch (ReadFailedException e) {
            LOG.error("updateElanWithVpnInfo throws ReadFailedException e {}", e);
        }
        if (!elanInstanceOptional.isPresent()) {
            return;
        }

        EvpnAugmentationBuilder evpnAugmentationBuilder = new EvpnAugmentationBuilder();
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstanceOptional.get());
        if (elanInstanceBuilder.getAugmentation(EvpnAugmentation.class) != null) {
            evpnAugmentationBuilder =
                    new EvpnAugmentationBuilder(elanInstanceBuilder.getAugmentation(EvpnAugmentation.class));
        }
        if (!isDelete) {
            evpnAugmentationBuilder.setEvpnName(vpnName);
            LOG.debug("Writing Elan-EvpnAugmentation with key {}", elanInstanceName);
        } else {
            evpnAugmentationBuilder.setEvpnName(null);
            LOG.debug("Deleting Elan-EvpnAugmentation with key {}", elanInstanceName);
            //elanInstanceBuilder.removeAugmentation(EvpnAugmentation.class);
        }

        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        LOG.info("updating VPN name {} in elanid {} for elan instance {}", vpnName, elanIid, elanInstanceName);
        transaction.put(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build());
        transaction.submit();
    }

    public static void updateVpnWithElanInfo(DataBroker broker, VpnInstance vpnInstance, String elanInstanceName,
                                             IVpnManager vpnManager, boolean isDelete) {
        String rd = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);

        InstanceIdentifier<EvpnRdToNetwork> rdToNetworkIdentifier = getRdToNetworkIdentifier(rd);
        EvpnRdToNetworkBuilder evpnRdToNetworkBuilder = new EvpnRdToNetworkBuilder().setKey(new EvpnRdToNetworkKey(rd));

        final DataStoreJobCoordinator portDataStoreCoordinator = DataStoreJobCoordinator.getInstance();
        portDataStoreCoordinator.enqueueJob("EVPN_ASSOCIATE-" + rd, () -> {
            ReadWriteTransaction transaction = broker.newReadWriteTransaction();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            if (isDelete) {
                LOG.debug("Deleting Evpn-Network with key {}", rd);
                transaction.delete(LogicalDatastoreType.OPERATIONAL, rdToNetworkIdentifier);
            } else {
                evpnRdToNetworkBuilder.setRd(rd);
                evpnRdToNetworkBuilder.setNetworkId(elanInstanceName);
                LOG.info("updating vpn {} with elaninstance {} and rd {}", vpnInstance, elanInstanceName, rd);

                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, rdToNetworkIdentifier,
                        evpnRdToNetworkBuilder.build());
                transaction.put(LogicalDatastoreType.OPERATIONAL, rdToNetworkIdentifier,
                        evpnRdToNetworkBuilder.build());

            }

            futures.add(transaction.submit());
            return futures;
        });
    }

}
