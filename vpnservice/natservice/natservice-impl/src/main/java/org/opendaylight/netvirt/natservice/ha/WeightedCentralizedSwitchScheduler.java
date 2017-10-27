/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.ha;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.netvirt.vpnmanager.api.IVpnFootprintService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WeightedCentralizedSwitchScheduler implements CentralizedSwitchScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(SnatNodeEventListener.class);
    private final Map<BigInteger,Integer> switchWeightsMap = new HashMap<>();
    private final DataBroker dataBroker;
    private final NatDataUtil natDataUtil;
    final OdlInterfaceRpcService interfaceManager;
    private final int initialSwitchWeight = 0;
    private final IVpnFootprintService vpnFootprintService;

    @Inject
    public WeightedCentralizedSwitchScheduler(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager,
            NatDataUtil natDataUtil, IVpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
        this.natDataUtil = natDataUtil;
        this.vpnFootprintService = vpnFootprintService;
    }

    @Override
    public boolean scheduleCentralizedSwitch(String routerName) {
        BigInteger nextSwitchId = getSwitchWithLowestWeight();
        RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
        RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(nextSwitchId).build();
        try {
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            Routers router = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
            String vpnName = router.getRouterName();
            String primaryRd = NatUtil.getPrimaryRd(dataBroker, vpnName);
            for (Uuid subnetUuid :router.getSubnetIds()) {
                Optional<Subnetmap> subnetMapEntry =
                        SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                dataBroker, LogicalDatastoreType.CONFIGURATION, getSubnetMapIdentifier(subnetUuid));
                if (subnetMapEntry.isPresent()) {

                    Uuid routerPortUuid = subnetMapEntry.get().getRouterInterfacePortId();

                    vpnFootprintService.updateVpnToDpnMapping(nextSwitchId, vpnName, primaryRd,
                            routerPortUuid.getValue(), null, true);
                    NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, routerPortUuid.getValue(),
                            nextSwitchId, writeOperTxn);
                    NatUtil.addToDpnRoutersMap(dataBroker, routerName, routerPortUuid.getValue(),
                            nextSwitchId, writeOperTxn);
                }
            }
            writeOperTxn.submit();
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName), id);
            switchWeightsMap.put(nextSwitchId,switchWeightsMap.get(nextSwitchId) + 1);

        } catch (TransactionCommitFailedException e) {
            // TODO Auto-generated catch block
        }
        return true;

    }

    @Override
    public boolean releaseCentralizedSwitch(String routerName) {
        BigInteger primarySwitchId = NatUtil.getPrimaryNaptfromRouterName(dataBroker, routerName);
        try {
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            Routers router = natDataUtil.getRouter(routerName);
            String vpnName = router.getRouterName();
            String primaryRd = NatUtil.getPrimaryRd(dataBroker, vpnName);
            for (Uuid subnetUuid :router.getSubnetIds()) {
                Optional<Subnetmap> subnetMapEntry =
                        SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                                dataBroker, LogicalDatastoreType.CONFIGURATION, getSubnetMapIdentifier(subnetUuid));
                if (subnetMapEntry.isPresent()) {
                    Uuid routerPortUuid = subnetMapEntry.get().getRouterInterfacePortId();
                    vpnFootprintService.updateVpnToDpnMapping(primarySwitchId, vpnName, primaryRd,
                            routerPortUuid.getValue(), null, false);
                    NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, primarySwitchId, writeOperTxn);
                    NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, routerName, interfaceManager,
                            writeOperTxn);
                }
            }
            writeOperTxn.submit();
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    getNaptSwitchesIdentifier(routerName));
            switchWeightsMap.put(primarySwitchId,switchWeightsMap.get(primarySwitchId) - 1);
        } catch (TransactionCommitFailedException e) {
            return false;
        }
        return true;
    }

    @Override
    public boolean addSwitch(BigInteger dpnId) {
        /* Initialize the switch in the map with weight 0 */
        LOG.info("addSwitch: Adding {} dpnId to switchWeightsMap", dpnId);
        switchWeightsMap.put(dpnId, initialSwitchWeight);
        return true;

    }

    @Override
    public boolean removeSwitch(BigInteger dpnId) {
        LOG.info("removeSwitch: Removing {} dpnId to switchWeightsMap", dpnId);
        if (switchWeightsMap.get(dpnId) != initialSwitchWeight) {
            NaptSwitches naptSwitches = getNaptSwitches(dataBroker);
            for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
                if (dpnId.equals(routerToNaptSwitch.getPrimarySwitchId())) {
                    releaseCentralizedSwitch(routerToNaptSwitch.getRouterName());
                    switchWeightsMap.remove(dpnId);
                    scheduleCentralizedSwitch(routerToNaptSwitch.getRouterName());
                    break;
                }
            }
        } else {
            switchWeightsMap.remove(dpnId);
        }
        return true;
    }

    public static NaptSwitches getNaptSwitches(DataBroker dataBroker) {
        InstanceIdentifier<NaptSwitches> id = InstanceIdentifier.builder(NaptSwitches.class).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, id).orNull();
    }

    private BigInteger getSwitchWithLowestWeight() {
        int lowestWeight = Integer.MAX_VALUE;
        BigInteger nextSwitchId = BigInteger.valueOf(0);
        for (BigInteger dpnId : switchWeightsMap.keySet()) {
            if (lowestWeight > switchWeightsMap.get(dpnId)) {
                lowestWeight = switchWeightsMap.get(dpnId);
                nextSwitchId =  dpnId;
            }
        }
        return nextSwitchId;
    }

    private InstanceIdentifier<RouterToNaptSwitch> getNaptSwitchesIdentifier(String routerName) {
        return InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
    }

    private InstanceIdentifier<Subnetmap> getSubnetMapIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
    }

    @Override
    public boolean getCentralizedSwitch(String routerName) {
        // TODO Auto-generated method stub
        return false;
    }
}