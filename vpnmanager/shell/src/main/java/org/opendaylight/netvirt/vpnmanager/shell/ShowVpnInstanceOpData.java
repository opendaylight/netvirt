/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.shell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "vpninstance-op-show", description = "List name of all vpnInstances that is "
        + "present or absent in vpnInstanceOpDataEntry")
public class ShowVpnInstanceOpData extends OsgiCommandSupport {

    @Option(name = "--detail", aliases = {"--vpnInstanceOp"}, description = "Display vpnInstanceOpDataEntry detail "
            + "for given vpnInstanceName", required = false, multiValued = false)
    private String detail;
    private static final Logger LOG = LoggerFactory.getLogger(ShowVpnInstanceOpData.class);
    private DataBroker dataBroker;
    private List<VpnInstance> vpnInstanceList = new ArrayList<>();
    private Map<String, VpnInstanceOpDataEntry> vpnInstanceOpDataEntryMap = new HashMap<>();

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    @Nullable
    protected Object doExecute() {
        if (detail == null) {
            getVpnInstanceOpData();
            session.getConsole().println("For following vpnInstances vpnInstanceOpDataEntry is present: \n");
            for (VpnInstance vpnInstance : vpnInstanceList) {
                VpnInstanceOpDataEntry check = vpnInstanceOpDataEntryMap.get(vpnInstance.getVpnInstanceName());
                if (check != null) {
                    session.getConsole().println(vpnInstance.getVpnInstanceName() + "\n");
                }
            }
            session.getConsole().println("\n\nFor following vpnInstances vpnInstanceOpDataEntry is not present: \n");
            for (VpnInstance vpnInstance : vpnInstanceList) {
                VpnInstanceOpDataEntry check = vpnInstanceOpDataEntryMap.get(vpnInstance.getVpnInstanceName());
                if (check == null) {
                    session.getConsole().println(vpnInstance.getVpnInstanceName() + "\n");
                }
            }
            session.getConsole().println(getshowVpnCLIHelp());
        } else {
            getVpnInstanceOpData();
            session.getConsole().println("Fetching details of given vpnInstance\n");
            session.getConsole().println(
                    "------------------------------------------------------------------------------");
            VpnInstanceOpDataEntry check = vpnInstanceOpDataEntryMap.get(detail);
            session.getConsole().println(
                    "VpnInstanceName: " + check.getVpnInstanceName() + "\nVpnId: " + check.getVpnId() + "\nVrfId: "
                            + check.getVrfId() + "\nVpnToDpnList: " + check.getVpnToDpnList() + "\n");
            session.getConsole().println(
                    "------------------------------------------------------------------------------");
        }

        return null;
    }

    private void getVpnInstanceOpData() {
        List<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryList = new ArrayList<>();
        InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class).build();
        InstanceIdentifier<VpnInstanceOpData> vpnInstanceOpDataEntryIdentifier =
                InstanceIdentifier.builder(VpnInstanceOpData.class).build();
        Optional<VpnInstances> optionalVpnInstances = read(LogicalDatastoreType.CONFIGURATION, vpnsIdentifier);

        if (!optionalVpnInstances.isPresent() || optionalVpnInstances.get().getVpnInstance() == null
                || optionalVpnInstances.get().getVpnInstance().isEmpty()) {
            LOG.trace("No VPNInstances configured.");
            session.getConsole().println("No VPNInstances configured.");
        } else {
            vpnInstanceList = new ArrayList<VpnInstance>(optionalVpnInstances.get().getVpnInstance().values());
        }

        Optional<VpnInstanceOpData> optionalOpData = read(LogicalDatastoreType.OPERATIONAL,
                vpnInstanceOpDataEntryIdentifier);

        if (!optionalOpData.isPresent()) {
            LOG.trace("No VPNInstanceOpDataEntry present.");
            session.getConsole().println("No VPNInstanceOpDataEntry present.");
        } else {
            vpnInstanceOpDataEntryList = new ArrayList<VpnInstanceOpDataEntry>(optionalOpData.get()
                    .nonnullVpnInstanceOpDataEntry().values());
        }

        for (VpnInstanceOpDataEntry vpnInstanceOpDataEntry : vpnInstanceOpDataEntryList) {
            vpnInstanceOpDataEntryMap.put(vpnInstanceOpDataEntry.getVpnInstanceName(), vpnInstanceOpDataEntry);
        }
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        try (ReadTransaction tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private String getshowVpnCLIHelp() {
        return "\nUsage:"
                + "To display vpn-instance-op-data for given vpnInstanceName vpnInstanceOpData-show --detail "
                + "[<vpnInstanceName>]";
    }
}
