/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.test;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.SubnetOpDpnManager;
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
import org.opendaylight.yangtools.yang.common.Uint64;


@RunWith(MockitoJUnitRunner.class)
public class SubnetOpDpnManagerTest {

    Uint64 dpId = Uint64.valueOf(1);
    Uuid subnetId = Uuid.getDefaultInstance("067e6162-3b6f-4ae2-a171-2470b63dff00");
    String infName = "VPN";
    SubnetToDpn subnetToDpn = null;
    PortOpDataEntry portOp = null;
    String portId = "abc";

    InstanceIdentifier<SubnetOpDataEntry> subOpIdentifier =
        InstanceIdentifier.builder(SubnetOpData.class).child(SubnetOpDataEntry.class,
            new SubnetOpDataEntryKey(subnetId)).build();
    InstanceIdentifier<SubnetToDpn> dpnOpId = subOpIdentifier.child(SubnetToDpn.class, new SubnetToDpnKey(dpId));
    InstanceIdentifier<PortOpDataEntry> portOpIdentifier =
        InstanceIdentifier.builder(PortOpData.class).child(PortOpDataEntry.class,
            new PortOpDataEntryKey(infName)).build();

    @Mock
    DataBroker dataBroker;
    @Mock
    ReadTransaction mockReadTx;
    @Mock
    WriteTransaction mockWriteTx;

    SubnetOpDpnManager subOpDpnManager;

    Optional<SubnetToDpn> optionalSubDpn;
    Optional<PortOpDataEntry> optionalPortOp;

    @Before
    public void setUp() throws Exception {
        setupMocks();

        subOpDpnManager = new SubnetOpDpnManager(dataBroker);

        optionalSubDpn = Optional.of(subnetToDpn);
        optionalPortOp = Optional.of(portOp);

        doReturn(Futures.immediateCheckedFuture(optionalPortOp)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifier);
        doReturn(Futures.immediateCheckedFuture(optionalSubDpn)).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, dpnOpId);

    }

    private void setupMocks() {

        List<VpnInterfaces> vpnInterfaces = new ArrayList<>();
        List<Uuid> subnetIdList = new ArrayList<>();
        subnetIdList.add(subnetId);
        subnetToDpn = new SubnetToDpnBuilder().setDpnId(dpId).withKey(new SubnetToDpnKey(dpId)).setVpnInterfaces(
            vpnInterfaces).build();
        portOp = new PortOpDataEntryBuilder().setDpnId(dpId).withKey(new PortOpDataEntryKey(infName)).setSubnetIds(
            subnetIdList).setPortId(portId).build();
        doReturn(mockReadTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(mockWriteTx).when(dataBroker).newWriteOnlyTransaction();
    }

    @Test
    public void testAddInterfaceToDpn() {

        subOpDpnManager.addInterfaceToDpn(subnetId, dpId, infName);
        List<VpnInterfaces> vpnIntfList = new ArrayList<>();
        VpnInterfaces vpnIntfs =
                new VpnInterfacesBuilder().withKey(new VpnInterfacesKey(infName)).setInterfaceName(infName).build();
        vpnIntfList.add(vpnIntfs);
        SubnetToDpn subnetToDpnLocal = new SubnetToDpnBuilder().setDpnId(dpId).withKey(new SubnetToDpnKey(dpId))
                .setVpnInterfaces(vpnIntfList).build();
        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, dpnOpId, subnetToDpnLocal,
                WriteTransaction.CREATE_MISSING_PARENTS);

    }

    @Test
    public void testAddPortOpDataEntryPortOpPresent() {

        subOpDpnManager.addPortOpDataEntry(infName, subnetId, dpId);

        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, portOpIdentifier, portOp,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    @Test
    public void testAddPortOpDataEntryPortOpAbsent() {

        doReturn(Futures.immediateCheckedFuture(Optional.empty())).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifier);

        subOpDpnManager.addPortOpDataEntry(infName, subnetId, dpId);

        verify(mockWriteTx).put(LogicalDatastoreType.OPERATIONAL, portOpIdentifier, portOp,
                WriteTransaction.CREATE_MISSING_PARENTS);
    }

    @Test
    public void testRemoveInterfaceFromDpn() {

        subOpDpnManager.removeInterfaceFromDpn(subnetId, dpId, infName);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, dpnOpId);
    }

    @Test
    public void testRemovePortOpDataEntryPortOpPresent() {

        subOpDpnManager.removePortOpDataEntry(infName, null);

        verify(mockWriteTx).delete(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);
    }

    @Test
    public void testRemovePortOpDataEntryPortOpAbsent() {

        doReturn(Futures.immediateCheckedFuture(Optional.empty())).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifier);

        subOpDpnManager.removePortOpDataEntry(infName, null);

        verify(mockReadTx).read(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);

    }

    @Test
    public void testGetPortOpDataEntryPortOpPresent() {

        subOpDpnManager.getPortOpDataEntry(infName);

        verify(mockReadTx).read(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);

    }

    @Test
    public void testGetPortOpDataEntryPortOpAbsent() {

        doReturn(Futures.immediateCheckedFuture(Optional.empty())).when(mockReadTx).read(LogicalDatastoreType
            .OPERATIONAL, portOpIdentifier);

        subOpDpnManager.getPortOpDataEntry(infName);

        verify(mockReadTx).read(LogicalDatastoreType.OPERATIONAL, portOpIdentifier);

    }

}
