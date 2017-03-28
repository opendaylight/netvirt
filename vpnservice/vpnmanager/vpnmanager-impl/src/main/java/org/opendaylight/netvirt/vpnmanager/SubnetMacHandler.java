/*
 * Copyright (c) 2016 - 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class SubnetMacHandler extends AsyncDataTreeChangeListenerBase<VpnPortipToPort, SubnetMacHandler> {

    private final DataBroker dataBroker;
    private final IMdsalApiManager mdsalManager;

    public SubnetMacHandler(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        super(VpnPortipToPort.class, SubnetMacHandler.class);
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public void start() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        if (value.isSubnetIp()) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            VpnUtil.setupSubnetMacIntoVpnInstance(dataBroker, mdsalManager, value.getVpnName(), null,
                    value.getMacAddress(), BigInteger.ZERO /* On all DPNs */, writeTx, NwConstants.DEL_FLOW);

            String networkName = getNetworkInstanceName(value.getPortName());
            if (networkName != null
                    && VpnUtil.isFlatOrVlanNetwork(VpnUtil.getNeutronNetwork(dataBroker, new Uuid(networkName)))) {
                VpnUtil.setupSubnetMacIntoVpnInstanceOnDpns(dataBroker, mdsalManager, networkName,
                        value.getVpnName(), value.getMacAddress(), VpnUtil.getDpnsOnVpn(dataBroker, value.getVpnName()),
                        writeTx, NwConstants.DEL_FLOW);
            }
            writeTx.submit();
        }
    }

    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort dataObjectModificationBefore,
            VpnPortipToPort dataObjectModificationAfter) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        if (value.isSubnetIp()) {
            WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
            VpnUtil.setupSubnetMacIntoVpnInstance(dataBroker, mdsalManager, value.getVpnName(), null,
                    value.getMacAddress(), BigInteger.ZERO /* On all DPNs */, writeTx, NwConstants.ADD_FLOW);

            String networkName = getNetworkInstanceName(value.getPortName());
            if (networkName != null
                    && VpnUtil.isFlatOrVlanNetwork(VpnUtil.getNeutronNetwork(dataBroker, new Uuid(networkName)))) {
                VpnUtil.setupSubnetMacIntoVpnInstanceOnDpns(dataBroker, mdsalManager, networkName,
                        value.getVpnName(), value.getMacAddress(), VpnUtil.getDpnsOnVpn(dataBroker, value.getVpnName()),
                        writeTx, NwConstants.ADD_FLOW);
            }

            writeTx.submit();
        }
    }

    private String getNetworkInstanceName(String routerInterface) {
        Uuid subnetUuid = getSubnetIdfFromVpnInterfaceName(routerInterface);
        if (subnetUuid != null) {
            Subnet subnet = VpnUtil.getSubnetById(dataBroker, subnetUuid);
            if (subnet != null) {
                return subnet.getNetworkId().getValue();
            }
        }
        return null;
    }

    private Uuid getSubnetIdfFromVpnInterfaceName(String rotuerInterfaceId) {
        List<Adjacency> adjacencies = VpnUtil.getAdjacenciesForVpnInterfaceFromConfig(dataBroker, rotuerInterfaceId);
        if (adjacencies != null) {
            for (Adjacency adj : adjacencies) {
                if (adj.isPrimaryAdjacency()) {
                    return adj.getSubnetId();
                }
            }
        }
        return null;
    }

    @Override
    protected SubnetMacHandler getDataTreeChangeListener() {
        return this;
    }

}
