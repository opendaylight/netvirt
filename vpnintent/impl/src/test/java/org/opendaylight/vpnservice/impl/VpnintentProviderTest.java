/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.impl;

import static org.mockito.Mockito.mock;

import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;

public class VpnintentProviderTest {
    @Ignore
    @Test
    public void testOnSessionInitiated() {
        VpnintentProvider provider = new VpnintentProvider();

        // ensure no exceptions
        // currently this method is empty
        provider.onSessionInitiated(mock(BindingAwareBroker.ProviderContext.class));
    }

    @Ignore
    @Test
    public void testClose() throws Exception {
        VpnintentProvider provider = new VpnintentProvider();

        // ensure no exceptions
        // currently this method is empty
        provider.close();
    }
}
