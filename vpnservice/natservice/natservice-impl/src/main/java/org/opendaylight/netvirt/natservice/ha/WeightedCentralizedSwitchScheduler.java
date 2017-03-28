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

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.netvirt.natservice.api.CentralizedSwitchScheduler;
import org.opendaylight.netvirt.natservice.internal.NatUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class WeightedCentralizedSwitchScheduler implements CentralizedSwitchScheduler {
    private final Map<BigInteger,Integer> switchWeightsMap = new HashMap<>();
    private final DataBroker dataBroker;
    final OdlInterfaceRpcService interfaceManager;
    private final int initialSwitchWeight = 0;

    public WeightedCentralizedSwitchScheduler(DataBroker dataBroker, OdlInterfaceRpcService interfaceManager) {
        this.dataBroker = dataBroker;
        this.interfaceManager = interfaceManager;
    }

    public void init(){

    }

    @Override
    public boolean scheduleCentralizedSwitch(String routerName) {
        BigInteger nextSwitchId = getSwitchWithLowestWeight();
        RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
        RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(nextSwitchId).build();
        try {
            WriteTransaction writeOperTxn = dataBroker.newWriteOnlyTransaction();
            NatUtil.addToNeutronRouterDpnsMap(dataBroker, routerName, routerName, nextSwitchId, writeOperTxn);
            NatUtil.addToDpnRoutersMap(dataBroker, routerName, routerName, nextSwitchId, writeOperTxn);
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
            NatUtil.removeFromNeutronRouterDpnsMap(dataBroker, routerName, primarySwitchId, writeOperTxn);
            NatUtil.removeFromDpnRoutersMap(dataBroker, routerName, routerName, interfaceManager, writeOperTxn);
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
        switchWeightsMap.put(dpnId, initialSwitchWeight);
        return true;

    }

    @Override
    public boolean removeSwitch(BigInteger dpnId) {
        if (switchWeightsMap.get(dpnId) != initialSwitchWeight) {
            NaptSwitches naptSwitches = getNaptSwitches(dataBroker);
            for (RouterToNaptSwitch routerToNaptSwitch : naptSwitches.getRouterToNaptSwitch()) {
                if (dpnId == routerToNaptSwitch.getPrimarySwitchId()) {
                    releaseCentralizedSwitch(routerToNaptSwitch.getRouterName());
                    scheduleCentralizedSwitch(routerToNaptSwitch.getRouterName());
                }
            }
        }
        switchWeightsMap.remove(dpnId);
        return true;
    }

    public static NaptSwitches getNaptSwitches(DataBroker dataBroker) {
        InstanceIdentifier<NaptSwitches> id = InstanceIdentifier.builder(NaptSwitches.class).build();
        Optional<NaptSwitches> naptSwitches = NatUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        return naptSwitches.isPresent() ? naptSwitches.get() : null;
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

    @Override
    public boolean getCentralizedSwitch(String routerName) {
        // TODO Auto-generated method stub
        return false;
    }

}
