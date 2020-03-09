/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.shell;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.vpnnexthops.VpnNexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "vpn-show", description = "Display all present vpnInstances with their "
        + "respective count of oper and config vpnInterfaces")
public class ShowVpn extends OsgiCommandSupport {

    @Option(name = "--detail", aliases = {"--vpninstance"}, description = "Display oper and config interfaces for "
            + "given vpnInstanceName", required = false, multiValued = false)
    private String detail;

    private static final Logger LOG = LoggerFactory.getLogger(ShowVpn.class);
    private DataBroker dataBroker;
    private int configCount = 0;
    private int operCount = 0;
    private int totalOperCount = 0;
    private int totalConfigCount = 0;
    int nexthopCount = 0;
    int totalNexthopCount = 0;
    int subnetRouteCount = 0;
    int totalSubnetRouteCount = 0;
    private Integer ifPresent;
    private List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
            .VpnInstance> vpnInstanceList = new ArrayList<>();
    private List<VpnInterface> vpnInterfaceConfigList = new ArrayList<>();
    private List<VpnInterfaceOpDataEntry> vpnInterfaceOpList = new ArrayList<>();
    List<SubnetOpDataEntry> subnetOpDataEntryList = new ArrayList<>();

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    @Nullable
    protected Object doExecute() {
        Map<String, Integer> vpnNameToConfigInterfaceMap = new HashMap<>();
        Map<String, Integer> vpnNameToOperInterfaceMap = new HashMap<>();
        Map<String, Integer> instanceNameToSubnetRouteCountMap = new HashMap<>();
        if (detail == null) {
            showVpn();
            Set<String> vpnInstances = new HashSet<>();
            for (VpnInterface vpnInterface : vpnInterfaceConfigList) {
                if (vpnInterface.getVpnInstanceNames() != null) {
                    for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInterface.getVpnInstanceNames()) {
                        String vpnName = vpnInterfaceVpnInstance.getVpnName();
                        if (vpnName != null) {
                            vpnInstances.add(vpnName);
                        }
                    }
                }
            }
            for (String routerId : vpnInstances) {
                ifPresent = vpnNameToConfigInterfaceMap.get(routerId);
                if (ifPresent == null) {
                    vpnNameToConfigInterfaceMap.put(routerId, 1);
                } else {
                    vpnNameToConfigInterfaceMap.put(routerId,
                                      vpnNameToConfigInterfaceMap.get(routerId) + 1);
                }
            }
            for (VpnInterfaceOpDataEntry vpnInterfaceOp : vpnInterfaceOpList) {
                ifPresent = vpnNameToOperInterfaceMap.get(vpnInterfaceOp.getVpnInstanceName());
                if (ifPresent == null) {
                    vpnNameToOperInterfaceMap.put(vpnInterfaceOp.getVpnInstanceName(), 1);
                } else {
                    vpnNameToOperInterfaceMap.put(vpnInterfaceOp.getVpnInstanceName(),
                        vpnNameToOperInterfaceMap.get(vpnInterfaceOp.getVpnInstanceName()) + 1);
                }
            }
            for (SubnetOpDataEntry subnetOpDataEntry : subnetOpDataEntryList) {
                ifPresent = instanceNameToSubnetRouteCountMap.get(subnetOpDataEntry.getVpnName());
                if (ifPresent == null) {
                    instanceNameToSubnetRouteCountMap.put(subnetOpDataEntry.getVpnName(), 1);
                } else {
                    instanceNameToSubnetRouteCountMap.put(subnetOpDataEntry.getVpnName(),
                        instanceNameToSubnetRouteCountMap.get(subnetOpDataEntry.getVpnName()) + 1);
                }
            }
            session.getConsole().println("+--------------------------------------+------------+------------------"
                    + "--------+-------------------------------+---------------------------+");
            session.getConsole().println(String.format("|                                      |            |%23s   "
                    + "| %29s | %25s |", "Vpn-Interfaces-Count", "",""));
            session.getConsole().println("|                                      |            +------------+-------"
                    + "------+                               |                           |");
            session.getConsole().println(String.format("|             %s          |%7s     | %10s | %10s | %18s "
                            + "| %5s |", "VpnInstanceName", "RD","Configured", "Operational",
                    "Operational subnetRoute Count","Operational NextHop Count"));
            session.getConsole().println("+--------------------------------------+------------+------------+---------"
                    + "----+-------------------------------+---------------------------+");
            for (VpnInstance vpnInstance : vpnInstanceList) {
                configCount = 0;
                operCount = 0;
                subnetRouteCount = 0;
                nexthopCount = 0;
                Integer count = vpnNameToConfigInterfaceMap.get(vpnInstance.getVpnInstanceName());
                if (count != null) {
                    configCount = vpnNameToConfigInterfaceMap.get(vpnInstance.getVpnInstanceName());
                    totalConfigCount = totalConfigCount + configCount;
                }
                count = vpnNameToOperInterfaceMap.get(vpnInstance.getVpnInstanceName());
                if (count != null) {
                    operCount = vpnNameToOperInterfaceMap.get(vpnInstance.getVpnInstanceName());
                    totalOperCount = totalOperCount + operCount;
                }
                count = instanceNameToSubnetRouteCountMap.get(vpnInstance.getVpnInstanceName());
                if (count != null) {
                    subnetRouteCount = instanceNameToSubnetRouteCountMap.get(vpnInstance.getVpnInstanceName());
                    totalSubnetRouteCount = totalSubnetRouteCount + subnetRouteCount;
                }
                InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                        .instance.to.vpn.id.VpnInstance> vpnInstanceIdIdentifier = InstanceIdentifier
                        .builder(VpnInstanceToVpnId.class).child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
                                .l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
                                new VpnInstanceKey(vpnInstance.getVpnInstanceName())).build();
                Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
                        .l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnIdInstance =
                        read(LogicalDatastoreType.CONFIGURATION, vpnInstanceIdIdentifier);
                if (vpnIdInstance.isPresent()) {
                    InstanceIdentifier<VpnNexthops> vpnNexthopidentifier = InstanceIdentifier.builder(L3nexthop.class)
                            .child(VpnNexthops.class, new VpnNexthopsKey(vpnIdInstance.get().getVpnId())).build();
                    Optional<VpnNexthops> vpnNexthops = read(LogicalDatastoreType.OPERATIONAL, vpnNexthopidentifier);
                    if (vpnNexthops.isPresent()) {
                        List<VpnNexthop> nexthops = vpnNexthops.get().getVpnNexthop();
                        nexthopCount = nexthops.size();
                        totalNexthopCount = totalNexthopCount + nexthopCount;
                    }
                }
                session.getConsole().println(String.format("|%-37s | %-10s |     %-6s |     %-7s |               "
                                + "%-15s |           %-16s|", vpnInstance.getVpnInstanceName(),
                        vpnInstance.getRouteDistinguisher(), configCount, operCount, subnetRouteCount, nexthopCount));
            }
            session.getConsole().println("+--------------------------------------+------------+------------+---------"
                    + "----+-------------------------------+---------------------------+");
            session.getConsole().println(String.format("|             %-24s | %-10s |     %-6s |     %-7s |           "
                            + "    %-15s |           %-16s|", "Total Count", "", totalConfigCount,
                    totalOperCount, totalSubnetRouteCount, totalNexthopCount));
            session.getConsole().println("+--------------------------------------+------------+------------+----------"
                    + "---+-------------------------------+---------------------------+");
            session.getConsole().println(getshowVpnCLIHelp());
        } else {
            showVpn();
            session.getConsole().println("Present Config VpnInterfaces are:");
            for (VpnInterface vpnInterface : vpnInterfaceConfigList) {
                if (VpnHelper.doesVpnInterfaceBelongToVpnInstance(detail, vpnInterface.getVpnInstanceNames())) {
                    session.getConsole().println(vpnInterface.getName());
                }
            }
            session.getConsole().println("Present Oper VpnInterfaces are:");
            for (VpnInterfaceOpDataEntry vpnInterfaceOp : vpnInterfaceOpList) {
                if (Objects.equals(vpnInterfaceOp.getVpnInstanceName(), detail)) {
                    session.getConsole().println(vpnInterfaceOp.getName());
                }
            }
        }
        return null;
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private void showVpn() {
        InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class).build();
        InstanceIdentifier<VpnInterfaces> vpnInterfacesIdentifier =
                InstanceIdentifier.builder(VpnInterfaces.class).build();
        final InstanceIdentifier<SubnetOpData> subOpIdentifier
                = InstanceIdentifier.builder(SubnetOpData.class).build();
        Optional<VpnInstances> optionalVpnInstances = read(LogicalDatastoreType.CONFIGURATION, vpnsIdentifier);
        if (!optionalVpnInstances.isPresent()) {
            LOG.trace("No VPNInstances configured.");
            session.getConsole().println("No VPNInstances configured.");
        } else {
            vpnInstanceList = optionalVpnInstances.get().getVpnInstance();
        }

        Optional<VpnInterfaces> optionalVpnInterfacesConfig =
                read(LogicalDatastoreType.CONFIGURATION, vpnInterfacesIdentifier);

        if (!optionalVpnInterfacesConfig.isPresent()) {
            LOG.trace("No Config VpnInterface is present");
            session.getConsole().println("No Config VpnInterface is present");
        } else {
            vpnInterfaceConfigList = optionalVpnInterfacesConfig.get().getVpnInterface();
        }


        InstanceIdentifier<VpnInterfaceOpData> id = InstanceIdentifier.create(VpnInterfaceOpData.class);
        Optional<VpnInterfaceOpData> optionalVpnInterfacesOper = read(LogicalDatastoreType.OPERATIONAL, id);

        if (!optionalVpnInterfacesOper.isPresent()) {
            LOG.trace("No Oper VpnInterface is present");
            session.getConsole().println("No Oper VpnInterface is present");
        } else {
            vpnInterfaceOpList = optionalVpnInterfacesOper.get().getVpnInterfaceOpDataEntry();
        }
        Optional<SubnetOpData> optionalSubnetRouteOper = read(LogicalDatastoreType.OPERATIONAL, subOpIdentifier);
        if (!optionalSubnetRouteOper.isPresent()) {
            LOG.info("showVpn: No Oper Subnet Route  is present");
            session.getConsole().println("No Oper Subnet Route is present");
        } else {
            subnetOpDataEntryList = optionalSubnetRouteOper.get().getSubnetOpDataEntry();
        }
    }

    private String getshowVpnCLIHelp() {
        StringBuilder help = new StringBuilder("\nUsage:");
        help.append("To display vpn-interfaces for a particular vpnInstance vpn-show --detail [<vpnInstanceName>]");
        return help.toString();
    }
}
