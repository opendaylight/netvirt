package org.opendaylight.bgpmanager;

import com.google.common.util.concurrent.Futures;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.VrfEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.fibmanager.rev150330.FibEntries;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.base.Optional;

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

        VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).
            setNextHopAddress(nexthop).setLabel((long)label).build();

        logger.info("Created vrfEntry for " + prefix + " nexthop " + nexthop + " label " + label);
        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));


        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        Optional<VrfTables> vrfTable = read(LogicalDatastoreType.CONFIGURATION, vrfTableId);
        if (vrfTable.isPresent()) {
            List<VrfEntry> vrfEntryListExisting = vrfTable.get().getVrfEntry();
            vrfEntryListExisting.add(vrfEntry);


            VrfTables vrfTableUpdate = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryListExisting).build();
            write(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableUpdate);
        }
        else {
            List<VrfEntry> vrfEntryList = new ArrayList<VrfEntry>();
            vrfEntryList.add(vrfEntry);

            //add a new vrf table with this vrf entry
            VrfTables vrfTableNew = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryList).build();


            InstanceIdentifier<VrfTables> vrfTableNewId = InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd)).build();

            write(LogicalDatastoreType.CONFIGURATION, vrfTableNewId, vrfTableNew);
        }

    }

    public synchronized void removeFibEntryFromDS(String rd, String prefix) {

        logger.debug("Removing fib entry with destination prefix " + prefix + " from vrf table for rd " + rd);

        InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        Optional<VrfTables> vrfTable = read(LogicalDatastoreType.CONFIGURATION, vrfTableId);
        if (vrfTable.isPresent()) {
            String searchPfx = prefix;

            List<VrfEntry> vrfEntryListExisting = vrfTable.get().getVrfEntry();
            for (Iterator<VrfEntry> it = vrfEntryListExisting.iterator(); it.hasNext(); ) {
                VrfEntry elem = it.next();
                if (elem.getDestPrefix().equals(searchPfx)) {
                    it.remove();
                    break;
                }
            }

            VrfTables vrfTableUpdate = new VrfTablesBuilder().setRouteDistinguisher(rd).
                setVrfEntry(vrfEntryListExisting).build();
            write(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTableUpdate);
        }
    }

    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                                                    InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private <T extends DataObject> void write(LogicalDatastoreType datastoreType,
                                                   InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        tx.submit();
    }
}
