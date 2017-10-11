/*
 * Copyright (c) 2017 Ericsson and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.client;

import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.google.common.truth.Expect;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.opendaylight.netvirt.bgpmanager.thrift.gen.BgpConfigurator;

public class BgpRouterAddNeighborTest {

    private static final String IP = "ip";
    private static final long AS = 1671;
    private static final String PASS = "pass";

    @Rule public final Expect expect = Expect.create();

    @Mock private BgpConfigurator.Client bgpClientMock;
    private InOrder clientInOrder;

    private BgpRouter sut = null;

    private static final int CREATE_PEER = 1;
    private static final int SET_PEER_SECRET = 2;

    private void verifyClient(final int mask) throws TException {
        if (0 != (mask & CREATE_PEER)) {
            clientInOrder.verify(bgpClientMock).createPeer(IP, AS);
        }
        if (0 != (mask & SET_PEER_SECRET)) {
            clientInOrder.verify(bgpClientMock).setPeerSecret(IP, PASS);
        }
        clientInOrder.verifyNoMoreInteractions();
    }


    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
        clientInOrder = inOrder(bgpClientMock);
        sut = BgpRouter.newTestingInstance(bgpClientMock);
    }


    @Test public void addNeighbor_noPass_happyCase() throws TException, BgpRouterException  {
        sut.addNeighbor(IP, AS, null);
        verifyClient(CREATE_PEER);
    }


    @Test public void addNeighbor_wPass_happyCase() throws TException, BgpRouterException  {
        sut.addNeighbor(IP, AS, PASS);
        verifyClient(CREATE_PEER | SET_PEER_SECRET);
    }


    @Test public void addNeighbor_noPass_createPeerFails() throws TException {
        when(bgpClientMock.createPeer(IP, AS)).thenReturn(BgpRouterException.BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, null);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER);
            expect.that(e.getErrorCode()).isEqualTo(BgpRouterException.BGP_ERR_FAILED);
            expect.that(e.getFunctionCode()).isEqualTo(BgpRouterException.Function.DEFAULT);
        }
    }


    @Test public void addNeighbor_wPass_createPeerFails() throws TException {
        when(bgpClientMock.createPeer(IP, AS)).thenReturn(BgpRouterException.BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, PASS);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER);
            expect.that(e.getErrorCode()).isEqualTo(BgpRouterException.BGP_ERR_FAILED);
            expect.that(e.getFunctionCode()).isEqualTo(BgpRouterException.Function.DEFAULT);
        }
    }


    @Test public void addNeighbor_wPass_setPeerSecretFails() throws TException {
        when(bgpClientMock.setPeerSecret(IP, PASS)).thenReturn(BgpRouterException.BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, PASS);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER | SET_PEER_SECRET);
            expect.that(e.getErrorCode()).isEqualTo(BgpRouterException.BGP_ERR_FAILED);
            expect.that(e.getFunctionCode()).isEqualTo(BgpRouterException.Function.SET_PEER_SECRET);
        }
    }


}


// vi: set ts=4 sw=4 expandtab tw=120 :
// EOF
