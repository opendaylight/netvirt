/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.ELAN1;
import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.RD;

import com.google.common.base.Optional;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class EvpnTestHelper  {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnTestHelper.class);
    private DataBroker dataBroker;
    private SingleTransactionDataBroker singleTxdataBroker;

    public EvpnTestHelper(DataBroker dataBroker, SingleTransactionDataBroker singleTxdataBroker) {
        this.dataBroker = dataBroker;
        this.singleTxdataBroker = singleTxdataBroker;
    }

    public void updateRdtoNetworks(ElanInstance actualElanInstances) throws TransactionCommitFailedException {
        EvpnRdToNetworkBuilder evpnRdToNetworkBuilder = new EvpnRdToNetworkBuilder().setKey(new EvpnRdToNetworkKey(RD));
        evpnRdToNetworkBuilder.setRd(RD);
        evpnRdToNetworkBuilder.setNetworkId(ELAN1);
        LOG.info("updating Evpn {} with elaninstance {} and rd {}", "evpn1", actualElanInstances, RD);
        singleTxdataBroker.syncWrite(LogicalDatastoreType.CONFIGURATION, getRdToNetworkIdentifier(RD),
                evpnRdToNetworkBuilder.build());
    }

    public InstanceIdentifier<EvpnRdToNetwork> getRdToNetworkIdentifier(String vrfId) {
        return InstanceIdentifier.builder(EvpnRdToNetworks.class)
                .child(EvpnRdToNetwork.class, new EvpnRdToNetworkKey(vrfId)).build();
    }

    public void updateEvpnNameInElan(String elanInstanceName, String evpnName) {
        ReadWriteTransaction transaction = dataBroker.newReadWriteTransaction();
        Optional<ElanInstance> elanInstanceOptional = null;
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
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
        evpnAugmentationBuilder.setEvpnName(evpnName);
        LOG.debug("Writing Elan-EvpnAugmentation evpnName {} with key {}", evpnName, elanInstanceName);
        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        transaction.put(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
        transaction.submit();

    }

    public void deleteEvpnNameInElan(String elanInstanceName) {
        ReadWriteTransaction transaction = dataBroker.newReadWriteTransaction();
        Optional<ElanInstance> elanInstanceOptional = null;
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
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
        evpnAugmentationBuilder.setEvpnName(null);
        LOG.debug("deleting evpn name from Elan-EvpnAugmentation {} ",  elanInstanceName);
        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        transaction.put(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
        transaction.submit();

    }

    public void deleteRdtoNetworks() throws TransactionCommitFailedException {
        LOG.info("deleting  rd {}", "evpn1", RD);
        singleTxdataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION, getRdToNetworkIdentifier(RD));
    }

    public void addMacVrfEntryToDS(String rd, String macAddress, String prefix,
                                   List<String> nextHopList, VrfEntry.EncapType encapType,
                                   long l2vni, String gatewayMacAddress, RouteOrigin origin) {
        MacVrfEntryBuilder macEntryBuilder = new MacVrfEntryBuilder().setOrigin(origin.getValue());
        buildVpnEncapSpecificInfo(macEntryBuilder, encapType, l2vni, macAddress,
                gatewayMacAddress, nextHopList);
        macEntryBuilder.setMac(macAddress);
        macEntryBuilder.setDestPrefix(prefix);
        InstanceIdentifier<MacVrfEntry> macEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(macAddress)).build();

        ReadWriteTransaction transaction = dataBroker.newReadWriteTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, macEntryId, macEntryBuilder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS);
        transaction.submit();
    }

    public void deleteMacVrfEntryToDS(String rd, String macAddress) {
        ReadWriteTransaction transaction = dataBroker.newReadWriteTransaction();
        InstanceIdentifier<MacVrfEntry> macEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(macAddress)).build();

        transaction.delete(LogicalDatastoreType.CONFIGURATION, macEntryId);
        transaction.submit();
    }

    private static void buildVpnEncapSpecificInfo(MacVrfEntryBuilder builder,
                                                  VrfEntry.EncapType encapType, long l2vni, String macAddress,
                                                  String gatewayMac, List<String> nextHopList) {
        builder.setEncapType(encapType);
        builder.setGatewayMacAddress(gatewayMac);
        builder.setL2vni(l2vni);
        List<RoutePaths> routePaths = nextHopList.stream()
                .filter(StringUtils::isNotEmpty)
                .map(nextHop -> {
                    return FibHelper.buildRoutePath(nextHop, null);
                }).collect(Collectors.toList());
        builder.setRoutePaths(routePaths);
    }
}
