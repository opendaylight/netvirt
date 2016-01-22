/*
 * Copyright (c) 2016 Inocybe Technologies and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.impl;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.nic.utils.MdsalUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.Intents;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.IntentsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intents.Intent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.rev150122.intents.IntentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.intent.types.rev150122.Uuid;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.api.support.membermodification.MemberModifier;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PrepareForTest({IntentServiceManager.class})
@RunWith(PowerMockRunner.class)
public class IntentServiceManagerTest {

    private static final String SRC_SITE_NAME = "site a";
    private static final String DST_SITE_NAME = "site b";
    private static final String INTENT_ALLOW_ACTION = "ALLOW";
    private IntentServiceManager intentServiceManager;
    @Mock private MdsalUtils mdsal;

    @Before
    public void setUp() throws Exception {
        intentServiceManager = mock(IntentServiceManager.class, Mockito.CALLS_REAL_METHODS);
        MemberModifier.field(IntentServiceManager.class, "mdsal").set(intentServiceManager, mdsal);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testAddIntent() throws Exception {
        IntentBuilder intentBldr = mock(IntentBuilder.class);
        PowerMockito.whenNew(IntentBuilder.class).withNoArguments().thenReturn(intentBldr);
        when(intentBldr.setId(any(Uuid.class))).thenReturn(intentBldr);
        when(intentBldr.setSubjects(any(List.class))).thenReturn(intentBldr);
        when(intentBldr.setActions(any(List.class))).thenReturn(intentBldr);
        when(intentBldr.setConstraints(any(List.class))).thenReturn(intentBldr);
        when(intentBldr.build()).thenReturn(mock(Intent.class));

        Intents currentIntents = mock(Intents.class);
        PowerMockito.when(mdsal.read(any(LogicalDatastoreType.class), any(InstanceIdentifier.class))).thenReturn(currentIntents);
        IntentsBuilder intentsBldr = mock(IntentsBuilder.class);
        PowerMockito.whenNew(IntentsBuilder.class).withNoArguments().thenReturn(intentsBldr);
        when(intentsBldr.setIntent(any(List.class))).thenReturn(intentsBldr);
        PowerMockito.when(mdsal.put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Intents.class))).thenReturn(true);

        intentServiceManager.addIntent(SRC_SITE_NAME, DST_SITE_NAME, INTENT_ALLOW_ACTION, "fast-reroute");
        verify(intentBldr).setId(any(Uuid.class));
        verify(mdsal).read(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
        verify(mdsal).put(any(LogicalDatastoreType.class), any(InstanceIdentifier.class), any(Intents.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRemoveIntents() {
        Uuid id = mock(Uuid.class);
        when(mdsal.delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class))).thenReturn(true);

        intentServiceManager.removeIntent(id);
        verify(mdsal).delete(any(LogicalDatastoreType.class), any(InstanceIdentifier.class));
    }
}
