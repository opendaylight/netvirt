/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.test;

import static org.mockito.Mockito.when;

import java.math.BigInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.fibmanager.VrfEntryListener;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.vpninstancenames.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

@RunWith(MockitoJUnitRunner.class)
public class FibManagerTest {

    @Mock
    DataBroker dataBroker;
    @Mock
    ReadOnlyTransaction mockReadTx;
    @Mock
    WriteTransaction mockWriteTx;
    @Mock
    IMdsalApiManager mdsalManager;
    @Mock
    IVpnManager vpnmanager;
    @Mock
    VrfTablesKey vrfTableKey;

    VrfEntryListener fibmgr;
    private static final Long EGRESS_POINTER = 11L;
    VrfEntry vrfEntry;
    InstanceIdentifier<VrfEntry> identifier;
    private static final String TEST_VPN_INSTANCE_NAME = "95486250-4ad7-418f-9030-2df37f98ad24";
    private static final String TEST_RD = "100:1";
    private static final String PREFIX = "1.1.2.3";
    private static final String NEXTHOP = "1.1.1.1";
    private static final int LABEL = 10;
    RouteOrigin origin = RouteOrigin.STATIC;
    BigInteger dpn;
    private static final long VPN_ID = 101L;
    private static final long VPN_INTF_CNT = 2;
    private static final Boolean IS_CLEANUP_COMPLETE = Boolean.FALSE;

    private void setupMocks() {
        dpn = BigInteger.valueOf(100000L);
        identifier = buildVrfEntryId(TEST_VPN_INSTANCE_NAME, TEST_RD, PREFIX);
        vrfEntry = FibHelper.getVrfEntryBuilder(PREFIX, LABEL, NEXTHOP, origin, null).build();
        when(vrfTableKey.getRouteDistinguisher()).thenReturn(TEST_RD);
    }

    @Before
    public void setUp() throws Exception {
        setupMocks();
    }

    @Test
    public void testAdd() {
        //fibmgr.onDataChanged(dataChangeEvent);
        //Mockito.verify(mdsalManager, Mockito.times(2)).installFlow(any(FlowEntity.class));
    }

    public static InstanceIdentifier<VrfTables> buildVrfTableId(String vpnInstanceName, String rd) {
        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnInstanceName)).
                    child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        return vrfTableId;
    }

    public static InstanceIdentifier<VrfEntry> buildVrfEntryId(String vpnInstanceName, String rd, String prefix) {
        InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class)
                    .child(VpnInstanceNames.class, new VpnInstanceNamesKey(vpnInstanceName))
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        return vrfEntryId;
    }
}
