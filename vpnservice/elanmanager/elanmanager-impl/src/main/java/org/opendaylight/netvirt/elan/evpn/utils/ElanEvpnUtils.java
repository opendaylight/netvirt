/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.evpn.utils;


import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.EvpnRdToNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.evpn.rd.to.networks.EvpnRdToNetworkKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ElanEvpnUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ElanEvpnUtils.class);
    private final DataBroker dataBroker;
    private final ElanUtils elanUtils;
    private final IdManagerService idManager;
    private volatile IVpnManager vpnManager;

    public ElanEvpnUtils(DataBroker dataBroker, ElanUtils elanUtils, IdManagerService idManager) {
        this.dataBroker = dataBroker;
        this.elanUtils = elanUtils;
        this.idManager = idManager;
    }

    public void init() {
    }

    public void close() {
    }

    public void setVpnManager(IVpnManager vpnManager) {
        this.vpnManager = vpnManager;
    }

    public Long getElanTagByMacvrfiid(InstanceIdentifier<MacVrfEntry> macVrfEntryIid) {
        String elanName = getElanNameByMacvrfiid(macVrfEntryIid);
        if (elanName == null) {
            LOG.error("getElanTag: elanName is NULL for iid = {}", macVrfEntryIid);
        }
        ElanInstance elanInstance = ElanUtils.getElanInstanceByName(dataBroker, elanName);
        Long elanTag = elanInstance.getElanTag();
        if (elanTag == null || elanTag == 0L) {
            elanTag = elanUtils.retrieveNewElanTag(idManager, elanName);
        }
        return elanTag;
    }

    public String getElanNameByMacvrfiid(InstanceIdentifier<MacVrfEntry> instanceIdentifier) {
        ReadWriteTransaction tx = dataBroker.newReadWriteTransaction();
        String rd = instanceIdentifier.firstKeyOf(VrfTables.class).getRouteDistinguisher();
        String elanName = null;
        InstanceIdentifier<EvpnRdToNetwork> iidEvpnRdToNet =
                InstanceIdentifier.builder(EvpnRdToNetworks.class).child(EvpnRdToNetwork.class,
                        new EvpnRdToNetworkKey(rd)).build();
        try {
            Optional<EvpnRdToNetwork> evpnRdToNetwork =
                    tx.read(LogicalDatastoreType.OPERATIONAL, iidEvpnRdToNet).checkedGet();
            elanName = evpnRdToNetwork.get().getNetworkId();
        } catch (ReadFailedException e) {
            LOG.error("getElanName: unable to read elanName, exception ", e);
        }
        return elanName;
    }

}
