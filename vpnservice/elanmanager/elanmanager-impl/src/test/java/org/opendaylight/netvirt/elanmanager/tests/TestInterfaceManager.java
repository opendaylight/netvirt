/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.mockito.Mockito;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;

/**
 * IInterfaceManager implementation for tests.
 *
 * @author Michael Vorburger
 */
public abstract class TestInterfaceManager implements IInterfaceManager {

    // Please do not copy/paste this class into other projects.
    // If it turns out that this fake is useful for tests in other projects as well,
    // then move this e.g. either into interfacemanager-api/src/test (à la
    // https://git.opendaylight.org/gerrit/#/c/43723), or into a new
    // e.g. netvirt-testfakes project.

    // Implementation similar to e.g. the org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager

    public static TestInterfaceManager newInstance() {
        TestInterfaceManager testInterfaceManager = Mockito.mock(TestInterfaceManager.class, realOrException());
        testInterfaceManager.interfaceInfos = new ConcurrentHashMap<>();
        return testInterfaceManager;
    }

    private Map<String, InterfaceInfo> interfaceInfos;

    public void addInterfaceInfo(InterfaceInfo interfaceInfo) {
        interfaceInfos.put(interfaceInfo.getInterfaceName(), interfaceInfo);
    }

    @Override
    public InterfaceInfo getInterfaceInfo(String interfaceName) {
        InterfaceInfo interfaceInfo = interfaceInfos.get(interfaceName);
        if (interfaceInfo == null) {
            throw new IllegalStateException(
                    "must addInterfaceInfo() to TestInterfaceManager before getInterfaceInfo: " + interfaceName);
        }
        return interfaceInfo;
    }
}
