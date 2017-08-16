/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.cli;

import com.google.common.base.Optional;
import java.io.PrintStream;
import java.math.BigInteger;

import javax.annotation.Nonnull;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.BridgeRefInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.bridge.ref.info.BridgeRefEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbBridgeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Command(scope = "odl", name = "display-napt-switches", description = "Display the napt switch for the routers.")
public class DisplayNaptSwithcesCli extends OsgiCommandSupport {

    private DataBroker dataBroker;
    private static final String LOCAL_IP = "local_ip";

    public void setDataBroker(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
    }

    protected Object doExecute() throws Exception {
        PrintStream ps = session.getConsole();
        Optional<NaptSwitches> npatSwitches = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getNaptSwitchesIdentifier());
        ps.printf(String.format(" %-36s  %-20s  %-20s \n", "Router Id ", "Datapath Node Id", "Managment Ip Address",
                "IP Address"));
        ps.printf("-------------------------------------------------------------------------------------------\n");
        if (npatSwitches.isPresent()) {
            for (RouterToNaptSwitch routerToNaptSwitch : npatSwitches.get().getRouterToNaptSwitch()) {
                ps.printf(String.format(" %-36s  %-20s  %-20s \n", routerToNaptSwitch.getRouterName(),
                     routerToNaptSwitch.getPrimarySwitchId(), getDpnLocalIp(routerToNaptSwitch.getPrimarySwitchId())));
            }
        }
        return null;
    }

    private InstanceIdentifier<NaptSwitches> getNaptSwitchesIdentifier() {
        return InstanceIdentifier.builder(NaptSwitches.class).build();
    }

    @SuppressWarnings("unchecked")
    private Optional<Node> getPortsNode(BigInteger dpnId) {
        InstanceIdentifier<BridgeRefEntry> bridgeRefInfoPath = InstanceIdentifier.create(BridgeRefInfo.class)
                .child(BridgeRefEntry.class, new BridgeRefEntryKey(dpnId));

        Optional<BridgeRefEntry> bridgeRefEntry =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, bridgeRefInfoPath);
        if (!bridgeRefEntry.isPresent()) {
            return Optional.absent();
        }

        InstanceIdentifier<Node> nodeId =
                bridgeRefEntry.get().getBridgeReference().getValue().firstIdentifierOf(Node.class);

        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, nodeId);
    }

    private String getDpnLocalIp(BigInteger dpId) {
        Optional<Node> optionalPortsNode = getPortsNode(dpId);
        // Don’t use Optional.transform() here, getOpenvswitchOtherConfig() can return null
        return optionalPortsNode.isPresent() ? getOpenvswitchOtherConfig(optionalPortsNode.get(), LOCAL_IP) : null;
    }

    private String getOpenvswitchOtherConfig(Node node, String key) {
        OvsdbNodeAugmentation ovsdbNode = node.getAugmentation(OvsdbNodeAugmentation.class);
        if (ovsdbNode == null) {
            Optional<Node> nodeFromReadOvsdbNode = readOvsdbNode(node);
            if (nodeFromReadOvsdbNode.isPresent()) {
                ovsdbNode = nodeFromReadOvsdbNode.get().getAugmentation(OvsdbNodeAugmentation.class);
            }
        }

        if (ovsdbNode != null && ovsdbNode.getOpenvswitchOtherConfigs() != null) {
            for (OpenvswitchOtherConfigs openvswitchOtherConfigs : ovsdbNode.getOpenvswitchOtherConfigs()) {
                if (openvswitchOtherConfigs.getOtherConfigKey().equals(key)) {
                    return openvswitchOtherConfigs.getOtherConfigValue();
                }
            }
        }

        return null;
    }

    @Nonnull
    private Optional<Node> readOvsdbNode(Node bridgeNode) {
        OvsdbBridgeAugmentation bridgeAugmentation = extractBridgeAugmentation(bridgeNode);
        if (bridgeAugmentation != null) {
            InstanceIdentifier<Node> ovsdbNodeIid =
                    (InstanceIdentifier<Node>) bridgeAugmentation.getManagedBy().getValue();
            return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, ovsdbNodeIid);
        }
        return Optional.absent();

    }

    private OvsdbBridgeAugmentation extractBridgeAugmentation(Node node) {
        if (node == null) {
            return null;
        }
        return node.getAugmentation(OvsdbBridgeAugmentation.class);
    }

}
