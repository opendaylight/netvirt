/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.stfw.northbound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsBridge;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsSimulator;
import org.opendaylight.netvirt.stfw.simulator.ovs.OvsdbSwitch;
import org.opendaylight.netvirt.stfw.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NorthBoundConfigurationManager {

    private static final Logger LOG = LoggerFactory.getLogger(NorthBoundConfigurationManager.class);
    private final DataBroker dataBroker;
    private HashMap<Uuid, NeutronNetwork> neutronNetworkMap;
    private HashMap<Uuid, NeutronSubNet> subNetMap;
    private HashMap<Uuid, NeutronBgpvpn> bgpvpnMap;
    private HashMap<Uuid, NeutronPort> neutronPortMap;
    private final OvsSimulator ovsSimulator;

    @Inject
    public NorthBoundConfigurationManager(final DataBroker dataBroker, final OvsSimulator ovsSimulator) {
        this.ovsSimulator = ovsSimulator;
        this.dataBroker = dataBroker;
        neutronNetworkMap = new HashMap<Uuid, NeutronNetwork>();
        subNetMap = new HashMap<Uuid, NeutronSubNet>();
        bgpvpnMap = new HashMap<Uuid, NeutronBgpvpn>();
        neutronPortMap = new HashMap<Uuid, NeutronPort>();
        LOG.info("NorthBoundConfigurationManage started");
    }

    public void createNetwork(int count, boolean createVpn) {
        WriteTransaction txNetwork = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txSubnet = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txBgpvpn = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txAssociate = dataBroker.newWriteOnlyTransaction();
        int existingNetworkNumber = neutronNetworkMap.keySet().size();
        int existingSubnetNumber = subNetMap.keySet().size();
        int existingBgpvpnNumber = bgpvpnMap.keySet().size();
        for (int index = 0; index < count; index++) {
            NeutronNetwork neutronNetwork = createNeutronNetwork(txNetwork, existingNetworkNumber + index);
            createNeutronSubnet(txSubnet, neutronNetwork, existingSubnetNumber + index);
            if (createVpn) {
                NeutronBgpvpn bgpvpn = createNeutronBgpvpn(txBgpvpn, neutronNetwork, existingBgpvpnNumber + index);
                bgpvpn.associate(txAssociate, neutronNetwork);
                LOG.debug("created bgpvpn {} and associated with network {}", bgpvpn, neutronNetwork.getName());
            }
        }
        MdsalUtils.submitTransaction(txNetwork);
        MdsalUtils.submitTransaction(txSubnet);
        if (createVpn) {
            MdsalUtils.submitTransaction(txBgpvpn);
            MdsalUtils.submitTransaction(txAssociate);
        }
    }

    public void delete() {
        WriteTransaction txNetwork = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txSubnet = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txBgpvpn = dataBroker.newWriteOnlyTransaction();
        if (bgpvpnMap.keySet().size() > 0) {
            for (NeutronBgpvpn neutronBgpvpn : bgpvpnMap.values()) {
                deleteNeutronBgpvpn(txBgpvpn, neutronBgpvpn);
            }
            MdsalUtils.submitTransaction(txBgpvpn);
            bgpvpnMap.clear();
        }
        for (NeutronSubNet neutronSubNet : subNetMap.values()) {
            deleteNeutronSubnet(txSubnet, neutronSubNet);
            NeutronNetwork neutronNetwork = neutronNetworkMap.get(neutronSubNet.getNetworkId());
            deleteNeutronNetwork(txNetwork, neutronNetwork);
        }
        MdsalUtils.submitTransaction(txSubnet);
        MdsalUtils.submitTransaction(txNetwork);
        subNetMap.clear();
        neutronNetworkMap.clear();
    }

    private NeutronNetwork createNeutronNetwork(WriteTransaction tx, int index) {
        NeutronNetwork neutronNetwork = new NeutronNetwork(index);
        tx.put(LogicalDatastoreType.CONFIGURATION, neutronNetwork.getNetworkIdentifier(), neutronNetwork.build(), true);
        neutronNetworkMap.put(neutronNetwork.getId(), neutronNetwork);
        return neutronNetwork;
    }

    private void createNeutronSubnet(WriteTransaction tx, NeutronNetwork neutronNetwork, int index) {
        NeutronSubNet neutronSubNet = new NeutronSubNet(neutronNetwork, index);
        NeutronSubNet subnet = neutronSubNet.createNeutronSubNet(tx, neutronNetwork, index);
        subNetMap.put(subnet.getSubnetId(), subnet);
    }

    private NeutronBgpvpn createNeutronBgpvpn(WriteTransaction tx, NeutronNetwork neutronNetwork, int index) {
        NeutronBgpvpn neutronBgpvpn = new NeutronBgpvpn(neutronNetwork, index);
        NeutronBgpvpn bgpvpn = neutronBgpvpn.createNeutronBgpvpn(tx, neutronNetwork, index);
        bgpvpnMap.put(bgpvpn.getUuid(), bgpvpn);
        return bgpvpn;
    }

    public void createVms(int count) {
        WriteTransaction txNeutron = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txTopo = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txInv = dataBroker.newWriteOnlyTransaction();
        int existingPortNumber = neutronPortMap.keySet().size();
        List<NeutronSubNet> subnets = new ArrayList<NeutronSubNet>(subNetMap.values());
        int totalNumberOfPorts = subnets.size() * count;
        //Assuming that switch has just one bridge.
        List<OvsdbSwitch> listOfSwitches = new ArrayList<OvsdbSwitch>(ovsSimulator.getOvsSwitches().values());
        int numberOfSwitches = listOfSwitches.size();
        NeutronSubNet subnet = null;
        for (int index = 0; index < totalNumberOfPorts; index++) {
            if (index % count == 0) {
                subnet = subnets.get(index / count);
            }
            NeutronPort port = createNeutronPort(txNeutron, existingPortNumber + index, subnet);
            neutronPortMap.put(port.getPortId(), port);
            OvsBridge ovsBridge = listOfSwitches.get(index % numberOfSwitches).getBridge("br-int");

            ovsSimulator.addPort(txTopo, txInv, ovsBridge, port);
        }
        MdsalUtils.submitTransaction(txNeutron);
        MdsalUtils.submitTransaction(txTopo);
        MdsalUtils.submitTransaction(txInv);
    }

    public void deleteVms() {
        WriteTransaction txNeutron = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txTopo = dataBroker.newWriteOnlyTransaction();
        WriteTransaction txInv = dataBroker.newWriteOnlyTransaction();

        for (NeutronPort neutronPort : neutronPortMap.values()) {
            deleteNeutronPort(txNeutron, neutronPort);
            for (OvsdbSwitch ovsdbSwitch : ovsSimulator.getOvsSwitches().values()) {
                OvsBridge bridge = ovsdbSwitch.getBridge("br-int");
                ovsSimulator.delPort(txTopo, txInv, bridge, neutronPort);
            }
        }
        MdsalUtils.submitTransaction(txNeutron);
        MdsalUtils.submitTransaction(txTopo);
        MdsalUtils.submitTransaction(txInv);
        neutronPortMap.clear();
    }

    private NeutronPort createNeutronPort(WriteTransaction tx, int index, NeutronSubNet subnet) {
        NeutronPort neutronPort = new NeutronPort(index, subnet);
        tx.put(LogicalDatastoreType.CONFIGURATION, neutronPort.getIdentifier(), neutronPort.build(), true);
        neutronPortMap.put(neutronPort.getPortId(), neutronPort);
        return neutronPort;
    }

    private void deleteNeutronPort(WriteTransaction tx, NeutronPort neutronPort) {
        tx.delete(LogicalDatastoreType.CONFIGURATION, neutronPort.getIdentifier());
    }

    private void deleteNeutronNetwork(WriteTransaction tx, NeutronNetwork neutronNetwork) {
        tx.delete(LogicalDatastoreType.CONFIGURATION, neutronNetwork.getNetworkIdentifier());
    }

    private void deleteNeutronSubnet(WriteTransaction tx, NeutronSubNet neutronSubNet) {
        tx.delete(LogicalDatastoreType.CONFIGURATION, neutronSubNet.getSubnetIdentifier());
    }

    private void deleteNeutronBgpvpn(WriteTransaction tx, NeutronBgpvpn neutronBgpvpn) {
        tx.delete(LogicalDatastoreType.CONFIGURATION, neutronBgpvpn.getBgpvpnIdentifier());
    }

    public HashMap<Uuid, NeutronNetwork> getNeutronNetworkMap() {
        return neutronNetworkMap;
    }

    public HashMap<Uuid, NeutronSubNet> getSubNetMap() {
        return subNetMap;
    }

    public HashMap<Uuid, NeutronBgpvpn> getBgpvpnMap() {
        return bgpvpnMap;
    }

    public HashMap<Uuid, NeutronPort> getNeutronPortMap() {
        return neutronPortMap;
    }

}
