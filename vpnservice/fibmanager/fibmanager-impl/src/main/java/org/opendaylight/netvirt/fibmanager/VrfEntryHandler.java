/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;


import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public interface VrfEntryHandler {
    /*VrfEntry processing status.
    * VRF_PROCESSING_COMPLETED - means no more handlers are required to process the VRF entry.
    * VRF_PROCESSING_CONTINUE - means next VRF Handler can process the VRF entry if required.
    * */
    short VRF_PROCESSING_COMPLETED = 1;
    short VRF_PROCESSING_CONTINUE = 2;

    void subscribeWithVrfListener();

    void unSubscribeWithVrfListener();

    boolean wantToProcessVrfEntry(VrfEntry vrfEntry);

    String getHandlerType();

    short createFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd);

    short updateFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry original, VrfEntry update, String rd);

    short removeFlows(InstanceIdentifier<VrfEntry> identifier, VrfEntry vrfEntry, String rd);
}
