/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.api.tests;

import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import org.mockito.Mockito;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;

/**
 * IInterfaceManager implementation for tests.
 *
 * @author Michael Vorburger
 */
@SuppressWarnings("deprecation")
public abstract class TestInterfaceManager implements IInterfaceManager {

    // Instead of copy/paste, if it LATER (post merge) turns out that this is
    // useful for tests in other projects as well, then move this into
    // interfacemanager-api/src/test, à la
    // https://git.opendaylight.org/gerrit/#/c/43723.

    public static IInterfaceManager newInstance() {
        return Mockito.mock(TestInterfaceManager.class, realOrException());
    }
}
