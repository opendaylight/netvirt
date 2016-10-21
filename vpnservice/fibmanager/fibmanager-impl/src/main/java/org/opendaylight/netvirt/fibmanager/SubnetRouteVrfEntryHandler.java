package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.ActionType;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.InstructionType;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.SubnetRoute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.label.route.map.LabelRouteInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Created by esumams on 10/19/2016.
 */
public class SubnetRouteVrfEntryHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(SubnetRouteVrfEntryHandler.class);

    private static final int DEFAULT_FIB_FLOW_PRIORITY = 10;


    protected static void createFibEntries(final InstanceIdentifier<VrfEntry> vrfEntryIid,
                                           final VrfEntry vrfEntry,
                                           Collection<VpnToDpnList> vpnToDpnList,
                                           Long vpnId,
                                           DataBroker dataBroker)
    {

        final VrfTablesKey vrfTableKey = vrfEntryIid.firstKeyOf(VrfTables.class);
        final String rd = vrfTableKey.getRouteDistinguisher();

        SubnetRoute subnetRoute = vrfEntry.getAugmentation(SubnetRoute.class);

        if (subnetRoute != null) {
            final long elanTag = subnetRoute.getElantag();
            LOG.trace("SubnetRoute augmented vrfentry found for rd {} prefix {} with elantag {}",
                    rd, vrfEntry.getDestPrefix(), elanTag);

            if (vpnToDpnList != null) {
                DataStoreJobCoordinator dataStoreCoordinator = DataStoreJobCoordinator.getInstance();
                dataStoreCoordinator.enqueueJob("FIB" + rd.toString() + vrfEntry.getDestPrefix(),
                        new Callable<List<ListenableFuture<Void>>>() {
                            @Override
                            public List<ListenableFuture<Void>> call() throws Exception {
                                WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
                                for (final VpnToDpnList curDpn : vpnToDpnList) {
                                    if (curDpn.getDpnState() == VpnToDpnList.DpnState.Active) {
                                        installSubnetRouteInFib(curDpn.getDpnId(), elanTag, rd, vpnId.longValue(), vrfEntry, tx, dataBroker);
                                    }
                                }
                                List<ListenableFuture<Void>> futures = new ArrayList<>();
                                futures.add(tx.submit());
                                return futures;
                            }
                        });
            }
            return;
        }
    }

    static void installSubnetRouteInFib(final BigInteger dpnId, final long elanTag, final String rd,
                                         final long vpnId, final VrfEntry vrfEntry, WriteTransaction tx, DataBroker dataBroker){
        Boolean wrTxPresent = true;
        if (tx == null) {
            wrTxPresent = false;
            tx = dataBroker.newWriteOnlyTransaction();
        }
        synchronized (vrfEntry.getLabel().toString().intern()) {
            LabelRouteInfo lri = VrfEntryHelper.getLabelRouteInfo(vrfEntry.getLabel(), dataBroker);
            if (lri != null && lri.getPrefix().equals(vrfEntry.getDestPrefix()) &&
                    vrfEntry.getNextHopAddressList().contains(lri.getNextHopIpList().get(0))) {

                if (RouteOrigin.value(vrfEntry.getOrigin()) == RouteOrigin.SELF_IMPORTED) {
                    Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = FibUtil.getVpnInstanceOpData(dataBroker, rd);
                    if (vpnInstanceOpDataEntryOptional.isPresent()) {
                        String vpnInstanceName = vpnInstanceOpDataEntryOptional.get().getVpnInstanceName();
                        if (!lri.getVpnInstanceList().contains(vpnInstanceName)) {
                            VrfEntryHelper.updateVpnReferencesInLri( dataBroker, lri, vpnInstanceName, false);
                        }
                    }
                }
                LOG.debug("Fetched labelRouteInfo for label {} interface {} and got dpn {}",
                        vrfEntry.getLabel(), lri.getVpnInterfaceName(), lri.getDpnId());
            }
        }
        final List<InstructionInfo> instructions = new ArrayList<InstructionInfo>();
        BigInteger subnetRouteMeta =  ((BigInteger.valueOf(elanTag)).shiftLeft(32)).or((BigInteger.valueOf(vpnId).shiftLeft(1)));
        instructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));
        VrfEntryHelper.makeConnectedRoute(dpnId,vpnId,vrfEntry,rd,instructions,NwConstants.ADD_FLOW, tx, dataBroker);

        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.SELF_IMPORTED) {
            List<ActionInfo> actionsInfos = new ArrayList<ActionInfo>();
            // reinitialize instructions list for LFIB Table
            final List<InstructionInfo> LFIBinstructions = new ArrayList<InstructionInfo>();

            actionsInfos.add(new ActionInfo(ActionType.pop_mpls, new String[]{}));
            LFIBinstructions.add(new InstructionInfo(InstructionType.apply_actions, actionsInfos));
            LFIBinstructions.add(new InstructionInfo(InstructionType.write_metadata,  new BigInteger[] { subnetRouteMeta, MetaDataUtil.METADATA_MASK_SUBNET_ROUTE }));
            LFIBinstructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.L3_SUBNET_ROUTE_TABLE }));

            VrfEntryHelper.makeLFibTableEntry(dpnId,vrfEntry.getLabel(), LFIBinstructions, DEFAULT_FIB_FLOW_PRIORITY, NwConstants.ADD_FLOW, tx, dataBroker);
        }
        if (!wrTxPresent ) {
            tx.submit();
        }
    }

}
