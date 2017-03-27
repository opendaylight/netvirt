/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator.tests;

import java.util.concurrent.Future;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.CreateRenderedPathInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.CreateRenderedPathOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.DeleteRenderedPathInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.DeleteRenderedPathOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.ReadRenderedServicePathFirstHopInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.ReadRenderedServicePathFirstHopOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.ReadRspFirstHopBySftListInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.ReadRspFirstHopBySftListOutput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePathService;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.TraceRenderedServicePathInput;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.TraceRenderedServicePathOutput;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class TestRenderedServicePathService implements RenderedServicePathService {

    @Override
    public Future<RpcResult<DeleteRenderedPathOutput>> deleteRenderedPath(DeleteRenderedPathInput input) {
        throw new UnsupportedOperationException("TODO Implement me, if needed in a component test");
    }

    @Override
    public Future<RpcResult<CreateRenderedPathOutput>> createRenderedPath(CreateRenderedPathInput input) {
        throw new UnsupportedOperationException("TODO Implement me, if needed in a component test");
    }

    @Override
    public Future<RpcResult<ReadRenderedServicePathFirstHopOutput>> readRenderedServicePathFirstHop(
            ReadRenderedServicePathFirstHopInput input) {
        throw new UnsupportedOperationException("TODO Implement me, if needed in a component test");
    }

    @Override
    public Future<RpcResult<TraceRenderedServicePathOutput>> traceRenderedServicePath(
            TraceRenderedServicePathInput input) {
        throw new UnsupportedOperationException("TODO Implement me, if needed in a component test");
    }

    @Override
    public Future<RpcResult<ReadRspFirstHopBySftListOutput>> readRspFirstHopBySftList(
            ReadRspFirstHopBySftListInput input) {
        throw new UnsupportedOperationException("TODO Implement me, if needed in a component test");
    }

}
