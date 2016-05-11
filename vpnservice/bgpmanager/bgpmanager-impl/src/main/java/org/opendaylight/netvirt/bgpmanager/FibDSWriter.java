/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by emhamla on 4/14/2015.
 */
public class FibDSWriter {
    private static final Logger logger = LoggerFactory.getLogger(FibDSWriter.class);
    private final DataBroker broker;

    public FibDSWriter(final DataBroker db) {
        broker = db;
    }

    public synchronized void addFibEntryToDS(String rd, String prefix,
                                       String nexthop, int label) {
        if (rd == null || rd.isEmpty()) {
            logger.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).
            setNextHopAddress(nexthop).setLabel((long)label).build();

        logger.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nexthop, label);

        InstanceIdentifier.InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(vrfEntry.getDestPrefix()));
        InstanceIdentifier<VrfEntry> vrfEntryId= idBuilder.build();

        BgpUtil.write(broker, LogicalDatastoreType.CONFIGURATION,
                vrfEntryId, vrfEntry);
    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix) {

        if (rd == null || rd.isEmpty()) {
            logger.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        logger.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        BgpUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

    }

    public synchronized void removeVrfFromDS(String rd) {
        logger.debug("Removing vrf table for  rd {}", rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        BgpUtil.delete(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId);

    }



}
