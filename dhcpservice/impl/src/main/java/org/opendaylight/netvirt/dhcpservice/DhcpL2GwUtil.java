/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.dhcpservice;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayCache;
import org.opendaylight.netvirt.neutronvpn.api.l2gw.L2GatewayDevice;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.HwvtepGlobalAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.PhysicalSwitchAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.global.attributes.Switches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.hwvtep.rev150901.hwvtep.physical._switch.attributes.TunnelIps;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class DhcpL2GwUtil {

    private static final Predicate<List<?>> EMPTY_LIST = (list) -> list == null || list.isEmpty();

    private static final Predicate<Optional<Node>> CONTAINS_GLOBAL_AUGMENTATION =
        (optionalNode) -> optionalNode.isPresent()
                && optionalNode.get().augmentation(HwvtepGlobalAugmentation.class) != null;

    private static final Predicate<Optional<Node>> CONTAINS_SWITCH_AUGMENTATION =
        (optionalNode) -> optionalNode.isPresent()
                && optionalNode.get().augmentation(PhysicalSwitchAugmentation.class) != null;

    private final DataBroker dataBroker;
    private final L2GatewayCache l2GatewayCache;

    @Inject
    public DhcpL2GwUtil(DataBroker dataBroker, L2GatewayCache l2GatewayCache) {
        this.dataBroker = dataBroker;
        this.l2GatewayCache = l2GatewayCache;
    }

    @Nullable
    public IpAddress getHwvtepNodeTunnelIp(InstanceIdentifier<Node> nodeIid) {
        String nodeId = nodeIid.firstKeyOf(Node.class).getNodeId().getValue();
        L2GatewayDevice targetDevice = null;
        for (L2GatewayDevice device : l2GatewayCache.getAll()) {
            if (nodeId.equals(device.getHwvtepNodeId())) {
                targetDevice = device;
                break;
            }
        }
        return targetDevice != null ? targetDevice.getTunnelIp() : getTunnelIp(nodeIid);
    }


    @Nullable
    private IpAddress getTunnelIp(InstanceIdentifier<Node> nodeIid) {
        Optional<Node> nodeOptional;
        try {
            nodeOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, nodeIid);
            if (!CONTAINS_GLOBAL_AUGMENTATION.test(nodeOptional)) {
                return null;
            }
            List<Switches> switchIids = nodeOptional.get().augmentation(HwvtepGlobalAugmentation.class).getSwitches();
            if (EMPTY_LIST.test(switchIids)) {
                return null;
            }
            InstanceIdentifier<Node> psIid = (InstanceIdentifier<Node>) switchIids.get(0).getSwitchRef().getValue();
            nodeOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    psIid);
            if (!CONTAINS_SWITCH_AUGMENTATION.test(nodeOptional)) {
                return null;
            }
            List<TunnelIps> tunnelIps = nodeOptional.get().augmentation(PhysicalSwitchAugmentation.class)
                    .getTunnelIps();
            if (EMPTY_LIST.test(tunnelIps)) {
                return null;
            }
            return tunnelIps.get(0).key().getTunnelIpsKey();
        } catch (ExecutionException | InterruptedException e) {
            return null;
        }
    }

}
