/*
 * Copyright © 2017 Ericsson, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.providers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfcName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SffName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHopBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.ServiceFunctionForwarders;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.SffDataPlaneLocatorKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarder.base.sff.data.plane.locator.DataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.ServiceFunctionForwarderKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.ServiceFunctionDictionaryBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sff.rev140701.service.function.forwarders.service.function.forwarder.service.function.dictionary.SffSfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.function.forwarders.service.function.forwarder.sff.data.plane.locator.data.plane.locator.locator.type.LogicalInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class SfcProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    private static final String RSP_NAME = "RSP1";
    private static final String RSP_NAME_NOEXIST = "RSP_NOEXIST";
    private static final String SFC_NAME = "SFC1";
    private static final String SF_NAME = "SF1";
    private static final String SFF_NAME = "SFF1";
    private static final String SFF_DPL_NAME = "SFF_DPL";
    private static final String LOGICAL_IF_NAME = "eccb57ae-5a2e-467f-823e-45d7bb2a6a9a";
    private static final Long PATH_ID = Long.valueOf(1);

    private SfcProvider sfcProvider;

    @Before
    public void setUp() throws Exception {
        sfcProvider = new SfcProvider(getDataBroker());
    }

    @Test
    public void getRenderedServicePath() {
        RspName rspName = new RspName(RSP_NAME);
        RenderedServicePathBuilder rspBuilder = createRsp(rspName);
        storeRsp(rspName, rspBuilder.build());

        Optional<RenderedServicePath> rspOptional = this.sfcProvider.getRenderedServicePath(RSP_NAME);
        assertTrue(rspOptional.isPresent());
    }

    @Test
    public void getRenderedServicePathNonExistantRsp() {
        Optional<RenderedServicePath> rspOptional = this.sfcProvider.getRenderedServicePath(RSP_NAME_NOEXIST);
        assertFalse(rspOptional.isPresent());
    }

    @Test
    public void getRenderedServicePathFromSfc() {
        // This method isnt implemented yet, so it should return empty
        Optional<RenderedServicePath> rspOptional = this.sfcProvider.getRenderedServicePathFromSfc(SFC_NAME);
        assertFalse(rspOptional.isPresent());
    }

    @Test
    public void getFirstHopSfInterfaceFromRsp() {
        RspName rspName = new RspName(RSP_NAME);

        // Check RSP with no hops
        RenderedServicePathBuilder rspBuilder = createRsp(rspName);
        Optional<String> ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with no SF name
        rspBuilder = createRsp(rspName, true, false, false, false, false, false, false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF name, but no SFF name
        rspBuilder = createRsp(rspName, true, true, false, false, false, false,false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF name, but SFF doesnt exist
        rspBuilder = createRsp(rspName, true, true, true, false, false, false,false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF and SFF, but SFF has no dictionary
        rspBuilder = createRsp(rspName, true, true, true, false, false, true, false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF and SFF, but SFF has no dictionary entry for SF
        rspBuilder = createRsp(rspName, true, true, true, true, false, true, false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF, SFF name, SFF exists, but has no DPL
        rspBuilder = createRsp(rspName, true, true, true, true, true, true, false, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with Sfm SFF name, SFF exists, has DPL, but not of type LogicalInterfaceLocator
        rspBuilder = createRsp(rspName, true, true, true, true, true, true, true, false);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP when its all created correctly
        rspBuilder = createRsp(rspName, true, true, true, true, true, true, true, true);
        ifName = this.sfcProvider.getFirstHopIngressInterfaceFromRsp(rspBuilder.build());
        assertTrue(ifName.isPresent());
        assertEquals(ifName.get(), LOGICAL_IF_NAME);
    }

    private RenderedServicePathBuilder createRsp(RspName rspName) {
        return new RenderedServicePathBuilder()
                .setName(rspName)
                .setServiceChainName(SfcName.getDefaultInstance(SFC_NAME))
                .setPathId(PATH_ID)
                .setReversePath(false);
    }

    private RenderedServicePathBuilder createRsp(RspName rspName, boolean hasHops, boolean hasSfName,
                                                 boolean hasSffName, boolean hasDict, boolean hasSfDict,
                                                 boolean createSff, boolean createSffDpl,
                                                 boolean createLogicalSfDpl) {
        RenderedServicePathBuilder rspBuilder = createRsp(rspName);
        SffName sffName = new SffName(SFF_NAME);
        SfName sfName = new SfName(SF_NAME);

        RenderedServicePathHopBuilder rspHopBuilder = new RenderedServicePathHopBuilder();
        if (hasSfName) {
            rspHopBuilder.setServiceFunctionName(sfName);
        }

        if (hasSffName) {
            rspHopBuilder.setServiceFunctionForwarder(sffName);
        }

        if (hasHops) {
            List<RenderedServicePathHop> hops = new ArrayList<>();
            hops.add(rspHopBuilder.build());
            rspBuilder.setRenderedServicePathHop(hops);
        }

        ServiceFunctionForwarderBuilder sffBuilder = new ServiceFunctionForwarderBuilder();
        ServiceFunctionDictionaryBuilder serviceFunctionDictionaryBuilder = new ServiceFunctionDictionaryBuilder();
        serviceFunctionDictionaryBuilder.setName(sfName);

        if (hasSfDict) {
            SffSfDataPlaneLocatorBuilder sffSfDataPlaneLocatorBuilder = new SffSfDataPlaneLocatorBuilder();
            sffSfDataPlaneLocatorBuilder.setSffDplName(new SffDataPlaneLocatorName(SFF_DPL_NAME));
            serviceFunctionDictionaryBuilder.setSffSfDataPlaneLocator(sffSfDataPlaneLocatorBuilder.build());
        }

        if (hasDict) {
            sffBuilder.setServiceFunctionDictionary(
                    Collections.singletonList(serviceFunctionDictionaryBuilder.build()));
        }

        SffDataPlaneLocatorBuilder sffDplBuilder = new SffDataPlaneLocatorBuilder();
        DataPlaneLocatorBuilder dataPlaneLocatorBuilder = new DataPlaneLocatorBuilder();
        if (createLogicalSfDpl) {
            LogicalInterfaceBuilder liBuilder = new LogicalInterfaceBuilder();
            liBuilder.setInterfaceName(LOGICAL_IF_NAME);
            dataPlaneLocatorBuilder.setLocatorType(liBuilder.build());
            sffDplBuilder.setDataPlaneLocator(dataPlaneLocatorBuilder.build());
        }

        if (createSffDpl) {
            List<SffDataPlaneLocator> sffDpls = new ArrayList<>();
            sffDplBuilder.setKey(new SffDataPlaneLocatorKey(new SffDataPlaneLocatorName(SFF_DPL_NAME)));
            sffDplBuilder.setName(new SffDataPlaneLocatorName(SFF_DPL_NAME));
            sffDpls.add(sffDplBuilder.build());
            sffBuilder.setSffDataPlaneLocator(sffDpls);
        }

        if (createSff) {
            sffBuilder.setName(sffName);
            storeSff(sffName, sffBuilder.build());
        }

        return rspBuilder;
    }

    @SuppressWarnings("deprecation")
    private void storeRsp(RspName rspName, RenderedServicePath rsp) {
        InstanceIdentifier<RenderedServicePath> rspIid = InstanceIdentifier.builder(RenderedServicePaths.class)
                .child(RenderedServicePath.class, new RenderedServicePathKey(rspName)).build();

        MDSALUtil.syncWrite(getDataBroker(), LogicalDatastoreType.OPERATIONAL, rspIid, rsp);
    }

    @SuppressWarnings("deprecation")
    private void storeSff(SffName sffName, ServiceFunctionForwarder sff) {
        InstanceIdentifier<ServiceFunctionForwarder> sffIid;
        sffIid = InstanceIdentifier.builder(ServiceFunctionForwarders.class)
                .child(ServiceFunctionForwarder.class, new ServiceFunctionForwarderKey(sffName))
                .build();

        MDSALUtil.syncWrite(getDataBroker(), LogicalDatastoreType.CONFIGURATION, sffIid, sff);
    }
}
