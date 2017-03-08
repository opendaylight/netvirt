/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatOverVxlanUtil {

    private static final Logger LOG = LoggerFactory.getLogger(NatOverVxlanUtil.class);

    public static BigInteger getInternetVpnVni(IdManagerService idManager, String vpnUuid, long vpnid) {
        BigInteger internetVpnVni = getVNI(vpnUuid, idManager);
        if (internetVpnVni.longValue() == -1) {
            LOG.warn("NAT Service : Unable to obtain Router VNI from VNI POOL for router {}."
                    + "Router ID will be used as tun_id", vpnUuid);
            return BigInteger.valueOf(vpnid);
        }
        return internetVpnVni;
    }

    public static BigInteger getVNI(String vniKey, IdManagerService idManager) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(NatConstants.ODL_VNI_POOL_NAME)
                .setIdKey(vniKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return BigInteger.valueOf(rpcResult.getResult().getIdValue());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : getVNI Exception {}", e);
        }
        return BigInteger.valueOf(-1);
    }

    public static void releaseVNI(String vniKey, IdManagerService idManager) {
        ReleaseIdInput releaseIdInput = new ReleaseIdInputBuilder().setPoolName(NatConstants.ODL_VNI_POOL_NAME)
            .setIdKey(vniKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(releaseIdInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("NAT Service : Unable to release ID {} from OpenDaylight VXLAN VNI range pool. Error {}",
                        vniKey, rpcResult.getErrors());
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : getVNI Exception {}", e);
        }
    }

    public static void validateAndCreateVxlanVniPool(DataBroker broker, INeutronVpnManager neutronvpnManager,
            IdManagerService idManager, String poolName) {
        /*
         * 1. If a NatPool doesn't exist create it. 2. If a NatPool exists, but
         * the range value is changed incorrectly (say some allocations exist in
         * the old range), we should NOT honor the new range . Throw the WARN
         * but continue running NAT Service with Old range. 3. If a NatPool
         * exists, but the given range is wider than the earlier one, we should
         * attempt to allocate with the new range again(TODO)
         */
        long lowLimit = NatConstants.VNI_DEFAULT_LOW_VALUE;
        long highLimit = NatConstants.VNI_DEFAULT_HIGH_VALUE;
        String configureVniRange = neutronvpnManager.getOpenDaylightVniRangesConfig();
        if (configureVniRange != null) {
            String[] configureVniRangeSplit = configureVniRange.split(":");
            lowLimit = Long.parseLong(configureVniRangeSplit[0]);
            highLimit = Long.parseLong(configureVniRangeSplit[1]);
        }
        Optional<IdPool> existingIdPool = NatUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                getIdPoolInstance(poolName));
        if (existingIdPool.isPresent()) {
            IdPool odlVniIdPool = existingIdPool.get();
            long currentStartLimit = odlVniIdPool.getAvailableIdsHolder().getStart();
            long currentEndLimit = odlVniIdPool.getAvailableIdsHolder().getEnd();

            if (lowLimit == currentStartLimit && highLimit == currentEndLimit) {
                LOG.debug("NAT Service : OpenDaylight VXLAN VNI range pool already exists with configured Range");
            } else {
                if (odlVniIdPool.getIdEntries() != null && odlVniIdPool.getIdEntries().size() != 0) {
                    LOG.warn("NAT Service : Some Allocation already exists with old Range. "
                            + "Cannot modify existing limit of OpenDaylight VXLAN VNI range pool");
                } else {
                    LOG.debug("NAT Service : No VNI's allocated from OpenDaylight VXLAN VNI range pool."
                            + "Delete and re-create pool with new configured Range {}-{}",lowLimit, highLimit);
                    deleteOpenDaylightVniRangesPool(idManager, poolName);
                    createOpenDaylightVniRangesPool(idManager, poolName, lowLimit, highLimit);
                }
            }
        } else {
            createOpenDaylightVniRangesPool(idManager, poolName, lowLimit, highLimit);
        }
    }

    public static void createOpenDaylightVniRangesPool(IdManagerService idManager, String poolName, long lowLimit,
            long highLimit) {

        CreateIdPoolInput createPool = null;
        createPool = new CreateIdPoolInputBuilder().setPoolName(poolName).setLow(lowLimit).setHigh(highLimit).build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("NAT Service : Created OpenDaylight VXLAN VNI range pool {} with range {}-{}", poolName,
                        lowLimit, highLimit);
            } else {
                LOG.error("NAT Service : Failed to create OpenDaylight VXLAN VNI range pool {} with range {}-{}",
                        poolName, lowLimit, highLimit);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : Failed to create OpenDaylight VXLAN VNI range pool {} with range {}-{}", poolName,
                    lowLimit, highLimit);
        }
    }

    public static void deleteOpenDaylightVniRangesPool(IdManagerService idManager, String poolName) {

        DeleteIdPoolInput deletePool = new DeleteIdPoolInputBuilder().setPoolName(poolName).build();
        Future<RpcResult<Void>> result = idManager.deleteIdPool(deletePool);
        try {
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("NAT Service : Deleted OpenDaylight VXLAN VNI range pool {} successfully", poolName);
            } else {
                LOG.error("NAT Service : Failed to delete OpenDaylight VXLAN VNI range pool {} ", poolName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("NAT Service : Failed to delete OpenDaylight VXLAN VNI range pool {} ", poolName);
        }
    }

    private static InstanceIdentifier<IdPool> getIdPoolInstance(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idPoolBuilder = InstanceIdentifier.builder(IdPools.class)
                .child(IdPool.class, new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idPoolBuilder.build();
        return id;
    }

    //TODO this is duplicate in EVPN spec. Need to have one API
    // when both EVPN and VNI spec code in place
    public static void makePreDnatToSnatTableEntry(IMdsalApiManager mdsalManager,
             BigInteger naptDpnId, short tableId) {
        List<Instruction> preDnatToSnatInstructions = new ArrayList<>();
        preDnatToSnatInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
        LOG.info("NAT Service : Create Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ", NwConstants.PDNAT_TABLE,
                NwConstants.INBOUND_NAPT_TABLE, naptDpnId);
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                NatConstants.DEFAULT_PSNAT_FLOW_PRIORITY, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                matches, preDnatToSnatInstructions);

        mdsalManager.installFlow(naptDpnId, preDnatToSnatTableFlowEntity);
        LOG.debug("NAT Service : Successfully installed Pre-DNAT flow {} on NAPT DpnId {} ",
                preDnatToSnatTableFlowEntity,  naptDpnId);
    }

    private static String getFlowRefPreDnatToSnat(BigInteger dpnId, short tableId, String uniqueId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + uniqueId;
    }
}