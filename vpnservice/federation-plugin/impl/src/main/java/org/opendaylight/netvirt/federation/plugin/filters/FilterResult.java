/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.filters;

/**
 * Applicable values for applyEgressFilter and applyIngressFilter
 * .<br>
 * Indicates weather DataObject should be federated to remote sites
 *
 */
public enum FilterResult {

    /**
     * Accept DataObject.
     */
    ACCEPT,
    /**
     * Filter out DataObject.
     */
    DENY,
    /**
     * No decision could be made based on the current state. Queue for future
     * processing
     */
    QUEUE,

}
