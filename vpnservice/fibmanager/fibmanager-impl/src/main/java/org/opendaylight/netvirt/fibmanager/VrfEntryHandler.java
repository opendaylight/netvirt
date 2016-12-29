/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;


import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;

public interface VrfEntryHandler {
    /*Types of VrfEntry Handlers*/
    short BGP_ROUTE_HANDLER = 1;
    short SUBNET_ROUTE_HANDLER  = 2;
    short IMP_EXP_ROUTE_HANDLER = 3;

    /*Action type for processing VrfEntry */
    short VRF_PROCESSING_COMPLETED = 1;
    short VRF_PROCESSING_CONTINUE = 2;

    String vrfHandlerType[] = {"BGP-ROUTE-HANDLER",
                                "SUBNET-ROUTER-HANDLER",
                                "IMP-EXP-ROUTE-HANDLER"};

    void subscribeWithVrfListener();
    boolean wantToProcessVrfEntry(VrfEntry vrfEntry);
}
