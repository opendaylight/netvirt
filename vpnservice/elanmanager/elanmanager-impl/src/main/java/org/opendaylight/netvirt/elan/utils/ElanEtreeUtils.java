/*
 * Copyright © 2017 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import com.google.common.base.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.etree.rev160614.EtreeLeafTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

@Singleton
public class ElanEtreeUtils {
    private final DataBroker broker;

    @Inject
    public ElanEtreeUtils(DataBroker broker) {
        this.broker = broker;
    }

    public EtreeLeafTagName getEtreeLeafTagByElanTag(long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = ElanUtils.getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = ElanUtils.read(broker,
                LogicalDatastoreType.OPERATIONAL, elanId);
        if (existingElanInfo.isPresent()) {
            ElanTagName elanTagName = existingElanInfo.get();
            return elanTagName.getAugmentation(EtreeLeafTagName.class);
        }
        return null;
    }
}
