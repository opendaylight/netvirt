/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.jobs;

import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.cloudservicechain.utils.VpnServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortDataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev160711.vpn.to.pseudo.port.list.VpnToPseudoPortDataKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddVpnPseudoPortDataJob extends VpnPseudoPortDataBaseJob {

    private static final Logger LOG = LoggerFactory.getLogger(AddVpnPseudoPortDataJob.class);

    private final long vpnPseudoLportTag;
    private final short scfTableIdToGo;
    private final int scfTag;

    public AddVpnPseudoPortDataJob(DataBroker dataBroker, String vpnRd, long vpnPseudoLportTag, short scfTableToGo,
                                   int scfTag) {
        super(dataBroker, vpnRd);

        this.vpnPseudoLportTag = vpnPseudoLportTag;
        this.scfTag = scfTag;
        this.scfTableIdToGo = scfTableToGo;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        LOG.debug("Adding VpnToPseudoPortMap: vpnRd={}  vpnPseudoLportTag={}  scfTag={}  scfTable={}",
                  super.vpnRd, vpnPseudoLportTag, scfTag, scfTableIdToGo);

        VpnToPseudoPortData newValue =
            new VpnToPseudoPortDataBuilder().withKey(new VpnToPseudoPortDataKey(super.vpnRd)).setVrfId(super.vpnRd)
                                            .setScfTableId(scfTableIdToGo).setScfTag(scfTag)
                                            .setVpnLportTag(vpnPseudoLportTag).build();
        LOG.trace("Adding lportTag={} to VpnToLportTag map for VPN with rd={}", vpnPseudoLportTag, vpnRd);
        InstanceIdentifier<VpnToPseudoPortData> path = VpnServiceChainUtils.getVpnToPseudoPortTagIid(vpnRd);

        return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
            tx -> tx.put(LogicalDatastoreType.CONFIGURATION, path, newValue,
                    WriteTransaction.CREATE_MISSING_PARENTS)));
    }
}
