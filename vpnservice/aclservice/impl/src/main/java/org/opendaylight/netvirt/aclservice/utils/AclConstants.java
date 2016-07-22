/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.utils;

/**
 * The class to have ACL related constants.
 */
public final class AclConstants {

    // TODO: Move all service related constants across all modules to a common
    // place
    public static final short EGRESS_ACL_TABLE_ID = 40;
    public static final short EGRESS_ACL_NEXT_TABLE_ID = 41;
    public static final short EGRESS_ACL_SERVICE_PRIORITY = 2;
    public static final short EGRESS_ACL_DEFAULT_FLOW_PRIORITY = 11;

    public static final short INGRESS_ACL_TABLE_ID = 251;
    public static final short INGRESS_ACL_NEXT_TABLE_ID = 252;
    public static final short INGRESS_ACL_SERVICE_PRIORITY = 10;
    public static final short INGRESS_ACL_DEFAULT_FLOW_PRIORITY = 1;

    private AclConstants() {
    }
}
