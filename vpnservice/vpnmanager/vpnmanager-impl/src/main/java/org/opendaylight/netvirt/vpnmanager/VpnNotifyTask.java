/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// This class invokes notifyAll() but doesn't actually do anything hence the "Naked notify" violation. Perhaps it is
// intended to do something in the future so suppress the violation.
@SuppressFBWarnings("NN_NAKED_NOTIFY")
class VpnNotifyTask implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(VpnNotifyTask.class);

    @Override
    public void run() {
        LOG.debug("Notify Task is running for the task {}", this);
        synchronized (this) {
            notifyAll();
        }
    }

}
