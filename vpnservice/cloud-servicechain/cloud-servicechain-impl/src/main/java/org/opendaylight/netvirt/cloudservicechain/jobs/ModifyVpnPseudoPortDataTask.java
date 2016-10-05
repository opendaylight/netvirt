/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.vpn.to.pseudo.port.list.VpnToPseudoPortDataKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

/**
 * Modifies VpnPseudoPort stateful data. Objects of this class are intended to
 * be used with DataStoreJobCoordinator
 */
public class ModifyVpnPseudoPortDataTask implements Callable<List<ListenableFuture<Void>>> {

    public enum Op {
        ADD, REMOVE
    }

    private final DataBroker dataBroker;
    private final String vpnRd;
    private final long vpnPseudoLportTag;
    private final short scfTableIdToGo;
    private final int scfTag;

    private final Op operation;

    private static final Logger logger = LoggerFactory.getLogger(ModifyVpnPseudoPortDataTask.class);

    public ModifyVpnPseudoPortDataTask(DataBroker dataBroker, String vpnRd, long vpnPseudoLportTag, short scfTableToGo,
                                       int scfTag, Op op) {
        this.dataBroker = dataBroker;
        this.vpnRd = vpnRd;
        this.vpnPseudoLportTag = vpnPseudoLportTag;
        this.scfTableIdToGo = scfTableToGo;
        this.scfTag = scfTag;
        this.operation = op;
    }

    public String getDsJobCoordinatorKey() {
        return "VpnPseudoPortDataUpdater." + this.vpnRd;
    }

    @Override
    public List<ListenableFuture<Void>> call() throws Exception {

        logger.debug("Modifiying VpnToPseudoPortMap: op={}  vpnRd={}  vpnPseudoLportTag={}  scfTag={}  scfTable={}",
                     this.operation == Op.ADD ? "Creation" : "Removal", this.vpnRd, this.vpnPseudoLportTag,
                     this.scfTag, this.scfTableIdToGo);
        List<ListenableFuture<Void>> result = new ArrayList<>();

        InstanceIdentifier<VpnToPseudoPortData> path = VpnServiceChainUtils.getVpnToPseudoPortTagIid(vpnRd);

        WriteTransaction writeTxn = dataBroker.newWriteOnlyTransaction();
        switch ( this.operation ) {
            case ADD:
                VpnToPseudoPortData newValue =
                    new VpnToPseudoPortDataBuilder().setKey(new VpnToPseudoPortDataKey(vpnRd))
                                                    .setVrfId(vpnRd)
                                                    .setScfTableId(scfTableIdToGo)
                                                    .setScfTag(scfTag)
                                                    .setVpnLportTag(vpnPseudoLportTag)
                                                    .build();
                logger.trace("Adding lportTag={} to VpnToLportTag map for VPN with rd={}", vpnPseudoLportTag, vpnRd);
                writeTxn.merge(LogicalDatastoreType.CONFIGURATION, path, newValue, true);
                MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, path, newValue);
                break;
            case REMOVE:
                logger.trace("Removing VpnToLportTag entry for VPN with rd={}", vpnRd);
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, path);
                break;
        }
        if ( writeTxn != null ) {
            result.add(writeTxn.submit());
        }

        return result;
    }

}
