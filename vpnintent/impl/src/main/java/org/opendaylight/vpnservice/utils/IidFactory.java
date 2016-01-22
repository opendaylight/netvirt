/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.utils;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.labels.Label;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.labels.LabelKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class IidFactory {

    public static InstanceIdentifier<MplsLabels> getMplsLabelsIid() {
        return InstanceIdentifier.builder(MplsLabels.class).build();
    }

    public static InstanceIdentifier<Label> getLabelIid(Long label) {
        return InstanceIdentifier.create(MplsLabels.class).child(Label.class, new LabelKey(label));
    }
}
