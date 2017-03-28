/*
 * Copyright (c) 2017 Ericsson and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.client;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;



import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.opendaylight.netvirt.bgpmanager.thrift.gen.BgpConfigurator;

public class BgpRouterAddNeighborTest {

    private static final String IP = "ip";
    private static final long AS = 1671;
    private static final String PASS = "pass";

    private static final int BGP_ERR_FAILED = BgpRouterException.BGP_ERR_FAILED;
    private static final String DEFAULT_BGP_ERR_FAILED_EXCEPTION_REP =
        rep(new BgpRouterException(BGP_ERR_FAILED));
    private static final String SETPEERSECRET_BGP_ERR_FAILED_EXCEPTION_REP =
        rep(new BgpRouterException(BgpRouterException.Function.SET_PEER_SECRET, BGP_ERR_FAILED));

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
    } // private method verifyClient


    @Before public void setUp() {
        MockitoAnnotations.initMocks(this);
        clientInOrder = inOrder(bgpClientMock);
        sut = BgpRouter.makeTestingRouter(bgpClientMock);
    } // test setUp


    @Test public void addNeighbor_noPass_happyCase() throws TException, BgpRouterException  {
        sut.addNeighbor(IP, AS, null);
        verifyClient(CREATE_PEER);
    } // test addNeighbor_wo_pass_happyCase


    @Test public void addNeighbor_wPass_happyCase() throws TException, BgpRouterException  {
        sut.addNeighbor(IP, AS, PASS);
        verifyClient(CREATE_PEER | SET_PEER_SECRET);
    } // test addNeighbor_wo_pass_happyCase


    @Test public void addNeighbor_noPass_createPeerFails() throws TException {
        when(bgpClientMock.createPeer(IP, AS)).thenReturn(BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, null);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER);
            assertThat("Wrong BgpRouterException", rep(e), equalTo(DEFAULT_BGP_ERR_FAILED_EXCEPTION_REP));
        }
    } // test addNeighbor_noPass_createPeerFails


    @Test public void addNeighbor_wPass_createPeerFails() throws TException {
        when(bgpClientMock.createPeer(IP, AS)).thenReturn(BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, PASS);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER);
            assertThat("Wrong BgpRouterException", rep(e), equalTo(DEFAULT_BGP_ERR_FAILED_EXCEPTION_REP));
        }
    } // test addNeighbor_wPass_createPeerFails


    @Test public void addNeighbor_wPass_setPeerSecretFails() throws TException {
        when(bgpClientMock.setPeerSecret(IP, PASS)).thenReturn(BGP_ERR_FAILED);
        try {
            sut.addNeighbor(IP, AS, PASS);
            fail("BgpRouterException expected");
        } catch (BgpRouterException e) {
            verifyClient(CREATE_PEER | SET_PEER_SECRET);
            assertThat("Wrong BgpRouterException", rep(e), equalTo(SETPEERSECRET_BGP_ERR_FAILED_EXCEPTION_REP));
        }
    } // test addNeighbor_wPass_setPeerSecretFails



    static String rep(BgpRouterException ex) {
        return new StringBuilder("(")
                .append(ex.getFunctionCode()).append(",").append(ex.getErrorCode()).append(")").toString();
    } // static protected method rep(BgpRouterException)

} // class BgpRouterAddNeighborTest

// vi: set ts=4 sw=4 expandtab tw=120 :
// EOF
