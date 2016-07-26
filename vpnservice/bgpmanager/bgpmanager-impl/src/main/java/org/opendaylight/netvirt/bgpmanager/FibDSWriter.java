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
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;

import java.util.*;


public class FibDSWriter {
    private static final Logger logger = LoggerFactory.getLogger(FibDSWriter.class);
    private final DataBroker broker;

    public FibDSWriter(final DataBroker db) {
        broker = db;
    }

    public synchronized void addFibEntryToDS(String rd, String prefix, List<String> nextHopList,
                                             int label, RouteOrigin origin) {
        if (rd == null || rd.isEmpty() ) {
            logger.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");

        for ( String nextHop: nextHopList){
            if (nextHop == null || nextHop.isEmpty()){
                logger.error("nextHop list contains null element");
                return;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nextHop, label);
            }

        }


        // Looking for existing prefix in MDSAL database
        Optional<FibEntries> fibEntries = Optional.absent();
        try{
            InstanceIdentifier<FibEntries> idRead = InstanceIdentifier.create(FibEntries.class);
            fibEntries = BgpUtil.read(broker, LogicalDatastoreType.CONFIGURATION, idRead);

            InstanceIdentifier<VrfEntry> vrfEntryId =
                    InstanceIdentifier.builder(FibEntries.class)
                            .child(VrfTables.class, new VrfTablesKey(rd))
                            .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
            Optional<VrfEntry> entry = BgpUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

            if (! entry.isPresent()) {
                VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(nextHopList)
                        .setLabel((long)label).setOrigin(origin.getValue()).build();

                BgpUtil.write(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);

            } else { // Found in MDSAL database
                List<String> nh = entry.get().getNextHopAddressList();
                for (String nextHop : nextHopList) {
                    if (!nh.contains(nextHop))
                        nh.add(nextHop);
                }
                VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(nh)
                        .setLabel((long) label).setOrigin(origin.getValue()).build();

                BgpUtil.update(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            }
        } catch (Exception e) {
            logger.error("addFibEntryToDS: error ", e);
        }

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
