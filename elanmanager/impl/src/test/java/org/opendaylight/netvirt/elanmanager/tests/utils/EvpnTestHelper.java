/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests.utils;

import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.DCGW_TEPIP;
import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.ELAN1;
import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.ELAN1_SEGMENT_ID;
import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.EVPN1;
import static org.opendaylight.netvirt.elanmanager.tests.ElanServiceTestBase.RD;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.StringUtils;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.elanmanager.tests.ExpectedObjects;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.EvpnAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.to.networks.EvpnToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@Singleton
public class EvpnTestHelper  {
    private static final Logger LOG = LoggerFactory.getLogger(EvpnTestHelper.class);
    private SingleTransactionDataBroker singleTxdataBroker;

    @Inject
    public EvpnTestHelper(SingleTransactionDataBroker singleTxdataBroker) {
        this.singleTxdataBroker = singleTxdataBroker;
    }

    public void updateRdtoNetworks(ElanInstance actualElanInstances) throws TransactionCommitFailedException {
        EvpnToNetworkBuilder evpnToNetworkBuilder = new EvpnToNetworkBuilder().setKey(new EvpnToNetworkKey(RD));
        evpnToNetworkBuilder.setVpnInstanceName(EVPN1);
        evpnToNetworkBuilder.setRd(RD);
        evpnToNetworkBuilder.setNetworkId(ELAN1);
        LOG.info("updating Evpn {} with elaninstance {} and rd {}", "evpn1", actualElanInstances, RD);
        singleTxdataBroker.syncWrite(LogicalDatastoreType.CONFIGURATION, getEvpnToNetworkIdentifier(EVPN1),
                evpnToNetworkBuilder.build());
    }

    public InstanceIdentifier<EvpnToNetwork> getEvpnToNetworkIdentifier(String vpnName) {
        return InstanceIdentifier.builder(EvpnToNetworks.class)
                .child(EvpnToNetwork.class, new EvpnToNetworkKey(vpnName)).build();
    }

    public void updateEvpnNameInElan(String elanInstanceName, String evpnName)
            throws ReadFailedException, TransactionCommitFailedException {
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        ElanInstance  elanInstance = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, elanIid);
        EvpnAugmentationBuilder evpnAugmentationBuilder = new EvpnAugmentationBuilder();
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstance);
        evpnAugmentationBuilder.setEvpnName(evpnName);
        LOG.debug("Writing Elan-EvpnAugmentation evpnName {} with key {}", evpnName, elanInstanceName);
        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        singleTxdataBroker.syncWrite(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build());
    }

    public void deleteEvpnNameInElan(String elanInstanceName)
            throws ReadFailedException, TransactionCommitFailedException {
        InstanceIdentifier<ElanInstance> elanIid = ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        ElanInstance  elanInstance = singleTxdataBroker.syncRead(LogicalDatastoreType.CONFIGURATION, elanIid);

        EvpnAugmentationBuilder evpnAugmentationBuilder = new EvpnAugmentationBuilder();
        ElanInstanceBuilder elanInstanceBuilder = new ElanInstanceBuilder(elanInstance);
        evpnAugmentationBuilder.setEvpnName(null);
        LOG.debug("deleting evpn name from Elan-EvpnAugmentation {} ",  elanInstanceName);
        elanInstanceBuilder.addAugmentation(EvpnAugmentation.class, evpnAugmentationBuilder.build());
        singleTxdataBroker.syncWrite(LogicalDatastoreType.CONFIGURATION, elanIid, elanInstanceBuilder.build());
    }

    public void deleteRdtoNetworks() throws TransactionCommitFailedException {
        LOG.info("deleting  rd {}", "evpn1", RD);
        singleTxdataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION, getEvpnToNetworkIdentifier(EVPN1));
    }

    public void addMacVrfEntryToDS(String vpnName, String rd, String macAddress, String prefix,
                                   List<String> nextHopList, VrfEntry.EncapType encapType,
                                   long l2vni, String gatewayMacAddress, RouteOrigin origin)
            throws TransactionCommitFailedException {
        MacVrfEntryBuilder macEntryBuilder = new MacVrfEntryBuilder().setOrigin(origin.getValue());
        buildVpnEncapSpecificInfo(macEntryBuilder, encapType, l2vni, macAddress,
                gatewayMacAddress, nextHopList);
        macEntryBuilder.setMac(macAddress);
        macEntryBuilder.setDestPrefix(prefix);
        InstanceIdentifier<MacVrfEntry> macEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnName))
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(macAddress)).build();

        singleTxdataBroker.syncWrite(LogicalDatastoreType.CONFIGURATION, macEntryId, macEntryBuilder.build());
    }

    public void deleteMacVrfEntryToDS(String vpnName,
                                      String rd, String macAddress) throws TransactionCommitFailedException {
        InstanceIdentifier<MacVrfEntry> macEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnName))
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(MacVrfEntry.class, new MacVrfEntryKey(macAddress)).build();

        singleTxdataBroker.syncDelete(LogicalDatastoreType.CONFIGURATION, macEntryId);
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

    public void attachEvpnToNetwork(ElanInstance elanInstance)
            throws TransactionCommitFailedException, ReadFailedException {
        // update RD to networks
        updateRdtoNetworks(elanInstance);

        // Attach EVPN to a network
        updateEvpnNameInElan(ExpectedObjects.ELAN1, EVPN1);
    }

    public void detachEvpnToNetwork(String elanName) throws TransactionCommitFailedException, ReadFailedException {
        deleteEvpnNameInElan(elanName);
        deleteRdtoNetworks();
    }

    public void handleEvpnRt2Recvd(String macRecvd, String prefixRecvd) throws TransactionCommitFailedException {
        List<String> nextHopList = new ArrayList<>();
        nextHopList.add(DCGW_TEPIP);
        addMacVrfEntryToDS(EVPN1, RD, macRecvd, prefixRecvd, nextHopList, VrfEntry.EncapType.Vxlan,
                ELAN1_SEGMENT_ID, null, RouteOrigin.BGP);
    }

    public InstanceIdentifier<MacVrfEntry> buildMacVrfEntryIid(String mac)  {
        return InstanceIdentifier.builder(FibEntries.class)
                .child(VpnInstanceNames.class, new VpnInstanceNamesKey(EVPN1))
                .child(VrfTables.class, new VrfTablesKey(RD))
                .child(MacVrfEntry.class, new MacVrfEntryKey(mac)).build();
    }

    public InstanceIdentifier<Networks> buildBgpNetworkIid(String prefix)  {
        return InstanceIdentifier.builder(Bgp.class)
                .child(Networks.class, new NetworksKey(prefix, RD)).build();
    }

}
