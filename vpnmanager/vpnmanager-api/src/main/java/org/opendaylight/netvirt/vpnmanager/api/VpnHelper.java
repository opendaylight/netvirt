/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api;

import com.google.common.base.Optional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames.AssociatedSubnetType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNamesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;


public final class VpnHelper {
    private VpnHelper() { }

    //FIXME: Implement caches for DS reads
    public static VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class,
                new VpnInstanceKey(vpnInstanceName)).build();
        Optional<VpnInstance> vpnInstance = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return (vpnInstance.isPresent()) ? vpnInstance.get() : null;
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<VpnInstance> getAllVpnInstances(DataBroker broker) {
        InstanceIdentifier<VpnInstances> id = InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> optVpnInstances = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optVpnInstances.isPresent()) {
            return optVpnInstances.get().getVpnInstance();
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Retrieves the dataplane identifier of a specific VPN, searching by its
     * VpnInstance name.
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the dataplane identifier of the VPN, the VrfTag.
     */
    public static long getVpnId(DataBroker broker, String vpnName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn
                .id.VpnInstance>
                id
                = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance>
                vpnInstance
                = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        long vpnId = -1;
        if (vpnInstance.isPresent()) {
            vpnId = vpnInstance.get().getVpnId();
        }
        return vpnId;
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
            .vpn.instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class).child(org.opendaylight.yang.gen.v1.urn
                .opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstanceKey(vpnName)).build();
    }

    public static VpnInterface getVpnInterface(DataBroker broker, String vpnInterfaceName) {
        InstanceIdentifier<VpnInterface> id = getVpnInterfaceIdentifier(vpnInterfaceName);
        Optional<VpnInterface> vpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return vpnInterface.isPresent() ? vpnInterface.get() : null;
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    public static String getFirstVpnNameFromVpnInterface(final VpnInterface original) {
        List<VpnInstanceNames> optList = original.getVpnInstanceNames();
        if (optList != null && !optList.isEmpty()) {
            return optList.get(0).getVpnName();
        } else {
            return null;
        }
    }

    public static List<String> getVpnInterfaceVpnInstanceNamesString(List<VpnInstanceNames> vpnInstanceList) {
        List listVpn = new ArrayList<String>();
        for (VpnInstanceNames vpnInterfaceVpnInstance : vpnInstanceList) {
            listVpn.add(vpnInterfaceVpnInstance.getVpnName());
        }
        return listVpn;
    }

    public static VpnInstanceNames getVpnInterfaceVpnInstanceNames(String vpnName, AssociatedSubnetType subnetType) {
        return new VpnInstanceNamesBuilder().setVpnName(vpnName).setAssociatedSubnetType(subnetType).build();
    }

    public static void removeVpnInterfaceVpnInstanceNamesFromList(String vpnName,
                               List<VpnInstanceNames> vpnInstanceList) {
        vpnInstanceList.removeIf(instance -> instance.getVpnName().equals(vpnName));
    }

    public static boolean doesVpnInterfaceBelongToVpnInstance(String vpnName,
                                                          List<VpnInstanceNames> vpnInstanceList) {
        for (VpnInstanceNames vpnInstance : vpnInstanceList) {
            if (vpnInstance.getVpnName().equals(vpnName)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSubnetPartOfVpn(Subnetmap sn, String vpnName) {
        if (vpnName == null || sn == null) {
            return false;
        }
        if (sn.getVpnId() == null || !sn.getVpnId().getValue().equals(vpnName)) {
            return false;
        }
        return true;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetmapIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(Subnetmaps.class)
        .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();

    }

    /** Get Subnetmap from its Uuid.
     * @param broker the data broker for look for data
     * @param subnetUuid the subnet's Uuid
     * @return the Subnetmap of Uuid or null if it is not found
     */
    public static Subnetmap getSubnetmapFromItsUuid(DataBroker broker, Uuid subnetUuid) {
        Subnetmap sn = null;
        InstanceIdentifier<Subnetmap> id = buildSubnetmapIdentifier(subnetUuid);
        Optional<Subnetmap> optionalSn = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalSn.isPresent()) {
            sn = optionalSn.get();
        }
        return sn;
    }

}
