/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice;

public interface PolicyServiceConstants {

    String POLICY_CLASSIFIER_POOL_NAME = "policyClassifierPool";
    Long POLICY_CLASSIFIER_LOW_ID = 1L;
    Long POLICY_CLASSIFIER_HIGH_ID = 30000L;
    String POLICY_GROUP_POOL_NAME = "policyGroupPool";
    Long POLICY_GROUP_LOW_ID = 310000L;
    Long POLICY_GROUP_HIGH_ID = 320000L;
    long INVALID_ID = 0;
    int POLICY_DEFAULT_DISPATCHER_FLOW_PRIORITY = 10;
    int POLICY_FLOW_PRIOPITY = 42;
    int POLICY_ACL_L3VPN_FLOW_PRIOPITY = 50;
    int POLICY_ACL_L2VPN_FLOW_PRIOPITY = 100;
    int POLICY_ACL_TRUNK_INTERFACE_FLOW_PRIOPITY = 150;
    int POLICY_ACL_VLAN_INTERFACE_FLOW_PRIOPITY = 200;
}
