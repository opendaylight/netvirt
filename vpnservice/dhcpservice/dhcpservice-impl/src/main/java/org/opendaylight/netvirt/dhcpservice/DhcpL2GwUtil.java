/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.utils.L2GatewayCacheUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DhcpL2GwUtil {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpL2GwUtil.class);

    private static final Predicate<List> EMPTY_LIST = (list) -> list == null || list.isEmpty();

    private static final Predicate<Optional<Node>> CONTAINS_GLOBAL_AUGMENTATION = (optionalNode) -> {
        return optionalNode.isPresent() && optionalNode.get().getAugmentation(HwvtepGlobalAugmentation.class) != null;
    };

    private static final Predicate<Optional<Node>> CONTAINS_SWITCH_AUGMENTATION = (optionalNode) -> {
        return optionalNode.isPresent() && optionalNode.get().getAugmentation(PhysicalSwitchAugmentation.class) != null;
    };

    private final DataBroker dataBroker;

    @Inject
    public DhcpL2GwUtil(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    public IpAddress getHwvtepNodeTunnelIp(InstanceIdentifier<Node> nodeIid) {
        ConcurrentMap<String, L2GatewayDevice> devices = L2GatewayCacheUtils.getCache();
        String nodeId = nodeIid.firstKeyOf(Node.class).getNodeId().getValue();
        L2GatewayDevice targetDevice = null;
        for (L2GatewayDevice device : devices.values()) {
            if (nodeId.equals(device.getHwvtepNodeId())) {
                targetDevice = device;
                break;
            }
        }
        return targetDevice != null ? targetDevice.getTunnelIp() : getTunnelIp(nodeIid);
    }


    private IpAddress getTunnelIp(InstanceIdentifier<Node> nodeIid) {
        Optional<Node> nodeOptional =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, nodeIid);
        if (!CONTAINS_GLOBAL_AUGMENTATION.test(nodeOptional)) {
            return null;
        }
        List<Switches> switchIids = nodeOptional.get().getAugmentation(HwvtepGlobalAugmentation.class).getSwitches();
        if (EMPTY_LIST.test(switchIids)) {
            return null;
        }
        InstanceIdentifier<Node> psIid = (InstanceIdentifier<Node>) switchIids.get(0).getSwitchRef().getValue();
        nodeOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, psIid);
        if (!CONTAINS_SWITCH_AUGMENTATION.test(nodeOptional)) {
            return null;
        }
        List<TunnelIps> tunnelIps = nodeOptional.get().getAugmentation(PhysicalSwitchAugmentation.class).getTunnelIps();
        if (EMPTY_LIST.test(tunnelIps)) {
            return null;
        }
        return tunnelIps.get(0).getKey().getTunnelIpsKey();
    }

}