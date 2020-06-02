/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.opendaylight.genius.datastoreutils.ExpectedDataObjectNotFoundException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ElanHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ElanHelper.class);

    private ElanHelper() {
        throw new AssertionError(ElanHelper.class.getName() + " cannot be initialized.");
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    public static Uint64 getElanMetadataLabel(long elanTag) {
        return MetaDataUtil.getElanTagMetadata(elanTag);
    }

    public static Uint64 getElanMetadataLabel(long elanTag, int lportTag) {
        return Uint64.fromLongBits(getElanMetadataLabel(elanTag).longValue()
                   | MetaDataUtil.getLportTagMetaData(lportTag).longValue());
    }

    public static Uint64 getElanMetadataMask() {
        return Uint64.fromLongBits(MetaDataUtil.METADATA_MASK_SERVICE.longValue()
                   | MetaDataUtil.METADATA_MASK_LPORT_TAG.longValue());
    }

    public static List<String> getDpnInterfacesInElanInstance(DataBroker broker, String elanInstanceName) {

        InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationDataPath(elanInstanceName);
        try {
            ElanDpnInterfacesList existingElanDpnInterfaces = SingleTransactionDataBroker.syncRead(broker,
                    LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            if (existingElanDpnInterfaces != null) {
                return new ArrayList<DpnInterfaces>(existingElanDpnInterfaces.nonnullDpnInterfaces().values()).stream()
                        .flatMap(v -> v.getInterfaces().stream()).collect(Collectors.toList());
            }
        } catch (ExpectedDataObjectNotFoundException e) {
            LOG.warn("Failed to read ElanDpnInterfacesList with error {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName)).build();

    }
}
