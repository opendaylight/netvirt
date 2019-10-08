/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.mockito.Mockito;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeGre;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.InterfaceTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbTerminationPointAugmentationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.Options;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.OptionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.port._interface.attributes.OptionsKey;
import org.opendaylight.yangtools.yang.common.Uint64;

public abstract class TestInterfaceManager implements IInterfaceManager {
    public static TestInterfaceManager newInstance() {
        return Mockito.mock(TestInterfaceManager.class, realOrException());
    }

    @Override
    public List<OvsdbTerminationPointAugmentation> getTunnelPortsOnBridge(Uint64 dpnId) {
        if (GeniusProviderTestParams.DPN_ID_NO_EXIST.equals(dpnId.toJava())) {
            // Unfortunately, the getTunnelPortsOnBridge() method may return null
            return null;
        }

        if (GeniusProviderTestParams.DPN_ID_NO_PORTS.equals(dpnId.toJava())) {
            return Collections.emptyList();
        }

        OvsdbTerminationPointAugmentationBuilder tpAug = new OvsdbTerminationPointAugmentationBuilder();
        tpAug.setOfport(GeniusProviderTestParams.OF_PORT);

        if (GeniusProviderTestParams.DPN_ID_NO_VXGPE_PORTS.equals(dpnId.toJava())) {
            // Tunnel Termination Point that is NOT of type VXGPE
            tpAug.setInterfaceType(InterfaceTypeGre.class);
        } else {
            // Tunnel Termination Point that IS of type VXGPE
            tpAug.setInterfaceType(InterfaceTypeVxlan.class);
        }

        List<Options> opsList = new ArrayList<>();
        if (!GeniusProviderTestParams.DPN_ID_NO_OPTIONS.equals(dpnId.toJava())) {
            OptionsBuilder opsBuilder = new OptionsBuilder();
            opsBuilder.withKey(new OptionsKey(GeniusProvider.OPTION_KEY_REMOTE_IP));
            opsBuilder.setValue(GeniusProvider.OPTION_VALUE_FLOW);
            opsList.add(opsBuilder.build());
        }
        tpAug.setOptions(opsList);

        List<OvsdbTerminationPointAugmentation> tpAugList = new ArrayList<>();
        tpAugList.add(tpAug.build());

        return tpAugList;
    }
}
