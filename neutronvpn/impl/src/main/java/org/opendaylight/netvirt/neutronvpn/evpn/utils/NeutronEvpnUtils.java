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
import java.util.Collections;
import javax.annotation.CheckReturnValue;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
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

    public enum Operation {
        ADD,
        DELETE
    }

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IVpnManager vpnManager;
    private final JobCoordinator jobCoordinator;

    public NeutronEvpnUtils(DataBroker broker, IVpnManager vpnManager, JobCoordinator jobCoordinator) {
        this.dataBroker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.vpnManager = vpnManager;
        this.jobCoordinator = jobCoordinator;
    }

    public VpnInstance getVpnInstance(Uuid vpnId) {
        return VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
    }

    public boolean isVpnAssociatedWithNetwork(VpnInstance vpnInstance) throws ReadFailedException {
        String rd = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);
        InstanceIdentifier<EvpnRdToNetwork> id = InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(rd)).build();
        Optional<EvpnRdToNetwork> optionalEvpnRdToNetwork =
                SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalEvpnRdToNetwork.isPresent()) {
            LOG.debug("vpn is associated with network {}", optionalEvpnRdToNetwork);
            return true;
        }
        return false;
    }

    public InstanceIdentifier<EvpnRdToNetwork> getRdToNetworkIdentifier(String vrfId) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(vrfId)).build();
    }

    @CheckReturnValue
    private ListenableFuture<Void> updateElanWithVpnInfo(String elanInstanceName, VpnInstance vpnInstance,
            Operation operation) {
        String vpnName = vpnInstance.getVpnInstanceName();
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        return txRunner.callWithNewReadWriteTransactionAndSubmit(transaction -> {
            Optional<ElanInstance> elanInstanceOptional =
                    transaction.read(LogicalDatastoreType.CONFIGURATION, elanIid).checkedGet();
            if (!elanInstanceOptional.isPresent()) {
                return;
            }

            EvpnAugmentationBuilder evpnAugmentationBuilder = new EvpnAugmentationBuilder();
            ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstanceOptional.get());
            if (elanInstanceBuilder.getAugmentation(EvpnAugmentation.class) != null) {
                evpnAugmentationBuilder =
                        new EvpnAugmentationBuilder(elanInstanceBuilder.getAugmentation(EvpnAugmentation.class));
            }
            if (operation == Operation.ADD) {
                evpnAugmentationBuilder.setEvpnName(vpnName);
                LOG.debug("Writing Elan-EvpnAugmentation with key {}", elanInstanceName);
            } else {
                evpnAugmentationBuilder.setEvpnName(null);
                LOG.debug("Deleting Elan-EvpnAugmentation with key {}", elanInstanceName);
            }

            elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
            transaction.put(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build(),
                    WriteTransaction.CREATE_MISSING_PARENTS);
        });
    }

    public void updateVpnWithElanInfo(VpnInstance vpnInstance, String elanInstanceName, Operation operation) {
        String rd = vpnManager.getPrimaryRdFromVpnInstance(vpnInstance);

        InstanceIdentifier<EvpnRdToNetwork> rdToNetworkIdentifier = getRdToNetworkIdentifier(rd);

        jobCoordinator.enqueueJob("EVPN_ASSOCIATE-" + rd,
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(transaction -> {
                if (operation == Operation.DELETE) {
                    LOG.debug("Deleting Evpn-Network with key {}", rd);
                    transaction.delete(LogicalDatastoreType.CONFIGURATION, rdToNetworkIdentifier);
                } else {
                    EvpnRdToNetworkBuilder evpnRdToNetworkBuilder = new EvpnRdToNetworkBuilder().setKey(
                            new EvpnRdToNetworkKey(rd));
                    evpnRdToNetworkBuilder.setRd(rd);
                    evpnRdToNetworkBuilder.setNetworkId(elanInstanceName);
                    LOG.info("updating Evpn {} with elaninstance {} and rd {}",
                            vpnInstance.getVpnInstanceName(), elanInstanceName, rd);
                    transaction.put(LogicalDatastoreType.CONFIGURATION, rdToNetworkIdentifier,
                            evpnRdToNetworkBuilder.build(), WriteTransaction.CREATE_MISSING_PARENTS);
                }
            })));
    }

    public void updateElanAndVpn(VpnInstance vpnInstance, String subnetVpn, Operation operation) {
        LOG.debug("updating elan {} in vpn {}, operation {} ", subnetVpn, vpnInstance.getVpnInstanceName(), operation);
        updateVpnWithElanInfo(vpnInstance, subnetVpn, operation);

        LOG.debug("updating vpn {}, in elan {} operation {} ", subnetVpn, vpnInstance.getVpnInstanceName(), operation);
        // this data store update has to be done for l3vpn as well once routing use case for rt2 is supported.
        ListenableFutures.addErrorLogging(updateElanWithVpnInfo(subnetVpn, vpnInstance, operation), LOG,
                "Error updating ELAN with VPN info");
        return;
    }
}
