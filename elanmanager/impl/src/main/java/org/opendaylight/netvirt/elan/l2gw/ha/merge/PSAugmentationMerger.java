/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.TunnelIpCmd;

public class PSAugmentationMerger
        extends MergeCommandsAggregator {
    public PSAugmentationMerger() {
        addCommand(new TunnelCmd());
        addCommand(new TunnelIpCmd());
    }

    static PSAugmentationMerger instance = new PSAugmentationMerger();

    public static PSAugmentationMerger getInstance() {
        return instance;
    }
}
