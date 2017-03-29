/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

public class PolicyServiceConstants {

    public static final String POLICY_CLASSIFIER_POOL_NAME = "policyClassifierPool";
    public static final Long POLICY_CLASSIFIER_LOW_ID = 1L;
    public static final Long POLICY_CLASSIFIER_HIGH_ID = 30000L;
    public static final String POLICY_GROUP_POOL_NAME = "policyGroupPool";
    public static final Long POLICY_GROUP_LOW_ID = 310000L;
    public static final Long POLICY_GROUP_HIGH_ID = 320000L;
    public static final long INVALID_ID = 0;
    public static final int POLICY_DEFAULT_DISPATCHER_FLOW_PRIORITY = 10;
    public static final int POLICY_FLOW_PRIOPITY = 42;
    public static final int POLICY_ACL_L3VPN_FLOW_PRIOPITY = 50;
    public static final int POLICY_ACL_L2VPN_FLOW_PRIOPITY = 100;
    public static final int POLICY_ACL_TRUNK_INTERFACE_FLOW_PRIOPITY = 150;
    public static final int POLICY_ACL_VLAN_INTERFACE_FLOW_PRIOPITY = 200;
}
