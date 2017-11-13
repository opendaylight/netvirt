/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PortOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.port.op.data.PortOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.SubnetToDpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.subnet.op.data.entry.subnet.to.dpn.VpnInterfacesKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SubnetOpDpnManager {
    private static final Logger LOG = LoggerFactory.getLogger(SubnetOpDpnManager.class);

    private final DataBroker broker;

    public SubnetOpDpnManager(final DataBroker db) {
        broker = db;
    }

    private SubnetToDpn addDpnToSubnet(Uuid subnetId, BigInteger dpnId) {
        try {
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                    new SubnetOpDataEntryKey(subnetId)).build();
            InstanceIdentifier<SubnetToDpn> dpnOpId =
                subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpnId));
            Optional<SubnetToDpn> optionalSubDpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId);
            if (optionalSubDpn.isPresent()) {
                LOG.error("addDpnToSubnet: Cannot create, SubnetToDpn for subnet {} as DPN {}"
                        + " already seen in datastore", subnetId.getValue(), dpnId);
                return null;
            }
            SubnetToDpnBuilder subDpnBuilder = new SubnetToDpnBuilder().setKey(new SubnetToDpnKey(dpnId));
            List<VpnInterfaces> vpnIntfList = new ArrayList<>();
            subDpnBuilder.setVpnInterfaces(vpnIntfList);
            SubnetToDpn subDpn = subDpnBuilder.build();
            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId, subDpn);
            LOG.info("addDpnToSubnet: Created SubnetToDpn entry for subnet {} with DPNId {} ", subnetId.getValue(),
                    dpnId);
            return subDpn;
        } catch (TransactionCommitFailedException ex) {
            LOG.error("addDpnToSubnet: Creation of SubnetToDpn for subnet {} with DpnId {} failed",
                    subnetId.getValue(), dpnId, ex);
            return null;
        }
    }

    private void removeDpnFromSubnet(Uuid subnetId, BigInteger dpnId) {
        try {
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                    new SubnetOpDataEntryKey(subnetId)).build();
            InstanceIdentifier<SubnetToDpn> dpnOpId =
                subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpnId));
            Optional<SubnetToDpn> optionalSubDpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId);
            if (!optionalSubDpn.isPresent()) {
                LOG.warn("removeDpnFromSubnet: Cannot delete, SubnetToDpn for subnet {} DPN {} not available"
                        + " in datastore", subnetId.getValue(), dpnId);
                return;
            }
            LOG.trace("removeDpnFromSubnet: Deleting SubnetToDpn entry for subnet {} with DPNId {}",
                    subnetId.getValue(), dpnId);
            SingleTransactionDataBroker.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("removeDpnFromSubnet: Deletion of SubnetToDpn for subnet {} with DPN {} failed",
                    subnetId.getValue(), dpnId, ex);
        }
    }

    public SubnetToDpn addInterfaceToDpn(Uuid subnetId, BigInteger dpnId, String intfName) {
        SubnetToDpn subDpn = null;
        try {
            // Create and add SubnetOpDataEntry object for this subnet to the SubnetOpData container
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                    new SubnetOpDataEntryKey(subnetId)).build();
            //Please use a synchronize block here as we donot need a cluster-wide lock
            InstanceIdentifier<SubnetToDpn> dpnOpId =
                subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpnId));
            Optional<SubnetToDpn> optionalSubDpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId);
            if (!optionalSubDpn.isPresent()) {
                // Create a new DPN Entry
                subDpn = addDpnToSubnet(subnetId, dpnId);
            } else {
                subDpn = optionalSubDpn.get();
            }
            SubnetToDpnBuilder subDpnBuilder = new SubnetToDpnBuilder(subDpn);
            List<VpnInterfaces> vpnIntfList = subDpnBuilder.getVpnInterfaces();
            VpnInterfaces vpnIntfs =
                new VpnInterfacesBuilder().setKey(new VpnInterfacesKey(intfName)).setInterfaceName(intfName).build();
            vpnIntfList.add(vpnIntfs);
            subDpnBuilder.setVpnInterfaces(vpnIntfList);
            subDpn = subDpnBuilder.build();

            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId, subDpn);
            LOG.info("addInterfaceToDpn: Created SubnetToDpn entry for subnet {} with DPNId {} intfName {}",
                    subnetId.getValue(), dpnId, intfName);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("addInterfaceToDpn: Addition of Interface {} for SubnetToDpn on subnet {} with DPN {} failed",
                    intfName, subnetId.getValue(), dpnId, ex);
            return null;
        }
        return subDpn;
    }

    public void addPortOpDataEntry(String intfName, Uuid subnetId, BigInteger dpnId) {
        try {
            // Add to PortOpData as well.
            PortOpDataEntryBuilder portOpBuilder = null;
            PortOpDataEntry portOpEntry = null;

            InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
                InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                    new PortOpDataEntryKey(intfName)).build();
            Optional<PortOpDataEntry> optionalPortOp =
                VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
            if (!optionalPortOp.isPresent()) {
                // Create PortOpDataEntry only if not present
                portOpBuilder =
                    new PortOpDataEntryBuilder().setKey(new PortOpDataEntryKey(intfName)).setPortId(intfName);
                List<Uuid> listSubnet = new ArrayList<>();
                listSubnet.add(subnetId);
                portOpBuilder.setSubnetIds(listSubnet);
            } else {
                List<Uuid> listSubnet = optionalPortOp.get().getSubnetIds();
                portOpBuilder = new PortOpDataEntryBuilder(optionalPortOp.get());
                if (listSubnet == null) {
                    listSubnet = new ArrayList<Uuid>();
                }
                if (!listSubnet.contains(subnetId)) {
                    listSubnet.add(subnetId);
                }
                portOpBuilder.setSubnetIds(listSubnet);
            }
            if (dpnId != null && !dpnId.equals(BigInteger.ZERO)) {
                portOpBuilder.setDpnId(dpnId);
            }
            portOpEntry = portOpBuilder.build();
            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier,
                portOpEntry);
            LOG.info("addPortOpDataEntry: Created PortOpData entry for port {} with DPNId {} subnetId {}",
                     intfName, dpnId, subnetId.getValue());
        } catch (TransactionCommitFailedException ex) {
            LOG.error("addPortOpDataEntry: Addition of Interface {} for SubnetToDpn on subnet {} with DPN {} failed",
                    intfName, subnetId.getValue(), dpnId, ex);
        }
    }

    public boolean removeInterfaceFromDpn(Uuid subnetId, BigInteger dpnId, String intfName) {
        boolean dpnRemoved = false;
        try {
            InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
                InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
                    new SubnetOpDataEntryKey(subnetId)).build();
            InstanceIdentifier<SubnetToDpn> dpnOpId =
                subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpnId));
            Optional<SubnetToDpn> optionalSubDpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId);
            if (!optionalSubDpn.isPresent()) {
                LOG.debug("removeInterfaceFromDpn: Cannot delete, SubnetToDpn for intf {} subnet {} DPN {}"
                        + " not available in datastore", intfName, subnetId.getValue(), dpnId);
                return false;
            }

            SubnetToDpnBuilder subDpnBuilder = new SubnetToDpnBuilder(optionalSubDpn.get());
            List<VpnInterfaces> vpnIntfList = subDpnBuilder.getVpnInterfaces();
            VpnInterfaces vpnIntfs =
                new VpnInterfacesBuilder().setKey(new VpnInterfacesKey(intfName)).setInterfaceName(intfName).build();
            vpnIntfList.remove(vpnIntfs);
            if (vpnIntfList.isEmpty()) {
                // Remove the DPN as well
                removeDpnFromSubnet(subnetId, dpnId);
                dpnRemoved = true;
            } else {
                subDpnBuilder.setVpnInterfaces(vpnIntfList);
                SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, dpnOpId,
                    subDpnBuilder.build());
            }
            LOG.info("removeInterfaceFromDpn: Removed interface {} from sunbet {} dpn {}",
                    intfName, subnetId.getValue(), dpnId);
        } catch (TransactionCommitFailedException ex) {
            LOG.error("removeInterfaceFromDpn: Deletion of Interface {} for SubnetToDpn on subnet {}"
                    + " with DPN {} failed", intfName, subnetId.getValue(), dpnId, ex);
            return false;
        }
        return dpnRemoved;
    }

    public PortOpDataEntry removePortOpDataEntry(String intfName, Uuid subnetId) {
        // Remove PortOpData and return out
        InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
            InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                new PortOpDataEntryKey(intfName)).build();
        PortOpDataEntry portOpEntry = null;
        Optional<PortOpDataEntry> optionalPortOp =
            VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
        if (!optionalPortOp.isPresent()) {
            LOG.info("removePortOpDataEntry: Cannot delete, portOp for port {} is not available in datastore",
                    intfName);
            return null;
        } else {
            portOpEntry = optionalPortOp.get();
            List<Uuid> listSubnet = portOpEntry.getSubnetIds();
            if (listSubnet == null) {
                listSubnet = new ArrayList<Uuid>();
            }
            if (subnetId != null && listSubnet.contains(subnetId)) {
                listSubnet.remove(subnetId);
            }
            if (listSubnet.isEmpty() || subnetId == null) {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
                LOG.info("removePortOpDataEntry: Deleted portOpData entry for port {}", intfName);
            } else {
                try {
                    PortOpDataEntryBuilder portOpBuilder = null;
                    portOpBuilder = new PortOpDataEntryBuilder(portOpEntry);
                    portOpBuilder.setSubnetIds(listSubnet);
                    SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier,
                        portOpEntry);
                    LOG.info("removePortOpDataEntry: Updated PortOpData entry for port {} with removing subnetId {}",
                        intfName, subnetId.getValue());
                } catch (TransactionCommitFailedException ex) {
                    LOG.error("removePortOpDataEntry failed: Updated PortOpData entry for port {}"
                        + " with removing subnetId {}", intfName, subnetId.getValue());
                }
                return null;
            }
        }
        return portOpEntry;
    }

    public PortOpDataEntry getPortOpDataEntry(String intfName) {
        // Remove PortOpData and return out
        InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
            InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
                new PortOpDataEntryKey(intfName)).build();
        Optional<PortOpDataEntry> optionalPortOp =
            VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
        if (!optionalPortOp.isPresent()) {
            return null;
        }
        return optionalPortOp.get();
    }

}
