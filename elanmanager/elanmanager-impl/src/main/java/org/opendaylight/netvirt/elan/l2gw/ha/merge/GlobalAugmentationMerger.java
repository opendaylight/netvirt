/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.merge;

import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LocalUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.LogicalSwitchesCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteMcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.RemoteUcastCmd;
import org.opendaylight.netvirt.elan.l2gw.ha.commands.SwitchesCmd;

public final class GlobalAugmentationMerger
        extends MergeCommandsAggregator {

    private GlobalAugmentationMerger() {
        addCommand(new RemoteMcastCmd());
        addCommand(new RemoteUcastCmd());
        addCommand(new LocalUcastCmd());
        addCommand(new LocalMcastCmd());
        addCommand(new LogicalSwitchesCmd());
        addCommand(new SwitchesCmd());
    }

    static GlobalAugmentationMerger instance = new GlobalAugmentationMerger();

    public static GlobalAugmentationMerger getInstance() {
        return instance;
    }
}
