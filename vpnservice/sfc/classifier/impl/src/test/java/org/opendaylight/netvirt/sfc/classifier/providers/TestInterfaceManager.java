/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import java.math.BigInteger;
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

public abstract class TestInterfaceManager implements IInterfaceManager {
    public static final BigInteger DPN_ID = new BigInteger("1234567890");
    public static final BigInteger DPN_ID_NO_PORTS = new BigInteger("111111111");
    public static final BigInteger DPN_ID_NO_VXGPE_PORTS = new BigInteger("222222222");
    public static final BigInteger DPN_ID_NO_OPTIONS = new BigInteger("333333333");
    public static final BigInteger DPN_ID_NO_EXIST = new BigInteger("999999999");
    public static final long OF_PORT = 42L;

    public static TestInterfaceManager newInstance() {
        return Mockito.mock(TestInterfaceManager.class, realOrException());
    }

    @Override
    public List<OvsdbTerminationPointAugmentation> getTunnelPortsOnBridge(BigInteger dpnId) {
        if (dpnId == DPN_ID_NO_EXIST) {
            // Unfortunately, the getTunnelPortsOnBridge() method may return null
            return null;
        }

        if (dpnId == DPN_ID_NO_PORTS) {
            return Collections.emptyList();
        }

        OvsdbTerminationPointAugmentationBuilder tpAug = new OvsdbTerminationPointAugmentationBuilder();
        tpAug.setOfport(OF_PORT);

        if (dpnId == DPN_ID_NO_VXGPE_PORTS) {
            // Tunnel Termination Point that is NOT of type VXGPE
            tpAug.setInterfaceType(InterfaceTypeGre.class);
        } else {
            // Tunnel Termination Point that IS of type VXGPE
            tpAug.setInterfaceType(InterfaceTypeVxlan.class);
        }

        List<Options> opsList = new ArrayList<>();
        if (dpnId != DPN_ID_NO_OPTIONS) {
            OptionsBuilder opsBuilder = new OptionsBuilder();
            opsBuilder.setKey(new OptionsKey(GeniusProvider.OPTION_KEY_EXTS));
            opsBuilder.setValue(GeniusProvider.OPTION_VALUE_EXTS_GPE);
            opsList.add(opsBuilder.build());
        }
        tpAug.setOptions(opsList);

        List<OvsdbTerminationPointAugmentation> tpAugList = new ArrayList<>();
        tpAugList.add(tpAug.build());

        return tpAugList;
    }
}
