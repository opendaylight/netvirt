package org.opendaylight.netvirt.elan.evpn;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanEvpnUtils;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by eriytal on 1/31/2017.
 * <p>
 * When RT2 route (advertise of withdraw) is received from peer side.
 * BGPManager will receive the msg.
 * NeutronvpnManager will check if the EVPN is configured and Network is attached to EVPN or not.
 * <p>
 * BGPManager will write in path (FibEntries.class).child(VrfTables.class).child(MacVrfEntry.class)
 * which MacVrfEntryListener is listening to.
 * When RT2 advertise route is received: add method of MacVrfEntryListener will install DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 * When RT2 withdraw route is received: remove method of MacVrfEntryListener will remove DMAC flows for the
 * received dest MAC in all the DPN's (with this network footprint).
 */
public class MacVrfEntryListener extends AsyncDataTreeChangeListenerBase<MacVrfEntry,
        MacVrfEntryListener> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(MacVrfEntryListener.class);
    private final DataBroker broker;
    private final ElanUtils elanUtils;
    private final IMdsalApiManager mdsalManager;
    private final ElanEvpnUtils elanEvpnUtils;

    public MacVrfEntryListener(DataBroker broker, ElanUtils elanUtils, IMdsalApiManager mdsalManager, ElanEvpnUtils elanEvpnUtils) {
        this.broker = broker;
        this.elanUtils = elanUtils;
        this.mdsalManager = mdsalManager;
        this.elanEvpnUtils = elanEvpnUtils;
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    public void init() {
    }

    public void close() {
    }

    @Override
    protected InstanceIdentifier<MacVrfEntry> getWildCardPath() {
        return InstanceIdentifier.create(FibEntries.class).child(VrfTables.class).child(MacVrfEntry.class);
    }


    @Override
    protected MacVrfEntryListener getDataTreeChangeListener() {
        return MacVrfEntryListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        Preconditions.checkNotNull(macVrfEntry, "macVrfEntry should not be null or empty.");
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        LOG.debug("ADD: Adding DMAC Entry for MACVrfEntry {} ", macVrfEntry);

        String elanName = elanEvpnUtils.getElanName(instanceIdentifier);

        ElanDpnInterfacesList elanDpnInterfacesList = elanUtils.getElanDpnInterfacesList(elanName);
        List<DpnInterfaces> dpnInterfaceLists = null;
        if (elanDpnInterfacesList != null) {
            dpnInterfaceLists = elanDpnInterfacesList.getDpnInterfaces();
            if (dpnInterfaceLists == null) {
            /*Can this case ever hit?*/
                LOG.error("ADD: Error : Unable to get dpnInterfaceLists with elan {} }", elanName);
                return;
            }
        }

        //String nexthopIP = macVrfEntry.getNextHopAddressList().get(0);
        //TODO(Riyaz) : Check if accessing first nexthop address is right
        String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
        Long elanTag = elanEvpnUtils.getElanTag(instanceIdentifier);
        String dstMacAddress = macVrfEntry.getMacAddress();
        long vni = macVrfEntry.getL2vni();
        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            BigInteger dpId = dpnInterfaces.getDpId();
            List<ListenableFuture<Void>> futures = new ArrayList<>();
            try {
                LOG.info("ADD: Build DMAC flow with dpId {}, nexthopIP {}, elanTag {}, \n" +
                                "vni {}, dstMacAddress {}, displayName {} ",
                        dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);

                Flow flow = elanEvpnUtils.evpnBuildDmacFlowForExternalRemoteMac(dpId, nexthopIP, elanTag, vni, dstMacAddress, elanName);
                futures.add(mdsalManager.installFlow(dpId, flow));
            } catch (ElanException e) {
                LOG.error("ADD: Error : evpnBuildDmacFlowForExternalRemoteMac throws exception e {} for elan {}", e, elanName);
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry, MacVrfEntry t1) {

    }

    @Override
    protected void remove(InstanceIdentifier<MacVrfEntry> instanceIdentifier, MacVrfEntry macVrfEntry) {
        Preconditions.checkNotNull(macVrfEntry, "macVrfEntry should not be null or empty.");
        ReadWriteTransaction tx = broker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        LOG.info("REMOVE: Removing DMAC Entry for MACVrfEntry {} ", macVrfEntry);

        String elanName = elanEvpnUtils.getElanName(instanceIdentifier);

        ElanDpnInterfacesList elanDpnInterfacesList = elanUtils.getElanDpnInterfacesList(elanName);
        List<DpnInterfaces> dpnInterfaceLists = null;
        if (elanDpnInterfacesList != null) {
            dpnInterfaceLists = elanDpnInterfacesList.getDpnInterfaces();
            if (dpnInterfaceLists == null) {
            /*Can this case ever hit?*/
                LOG.error("REMOVE: Error : Unable to get dpnInterfaceLists with elan {} }", elanName);
                return;
            }
        }

        Long elanTag = elanEvpnUtils.getElanTag(instanceIdentifier);
        //String nexthopIP = macVrfEntry.getNextHopAddressList().get(0);
        //TODO(Riyaz) : Check if accessing first nexthop address is right
        String nexthopIP = macVrfEntry.getRoutePaths().get(0).getNexthopAddress();
        String macToRemove = macVrfEntry.getMacAddress();

        for (DpnInterfaces dpnInterfaces : dpnInterfaceLists) {
            BigInteger dpId = dpnInterfaces.getDpId();
            List<ListenableFuture<Void>> futures = new ArrayList<>();

            LOG.info("REMOVE: Deleting DMAC Flows to external MAC. elanTag {}, dpId {},\n" +
                    "nexthopIP {}, macToRemove {}", elanTag, dpId, nexthopIP, macToRemove);

            elanEvpnUtils.evpnDeleteDmacFlowsToExternalMac(elanTag, dpId, nexthopIP, macToRemove);
            return;
        }
    }

}
