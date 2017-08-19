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
import java.util.List;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.test.ConstantSchemaAbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfDataPlaneLocatorName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfcName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SftTypeName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.RenderedServicePaths;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHopBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.ServiceFunctions;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.functions.service.function.sf.data.plane.locator.locator.type.LogicalInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public class SfcProviderTest extends ConstantSchemaAbstractDataBrokerTest {
    private static final String RSP_NAME = "RSP1";
    private static final String RSP_NAME_NOEXIST = "RSP_NOEXIST";
    private static final String SFC_NAME = "SFC1";
    private static final String SF_NAME = "SF1";
    private static final String SF_DPL_NAME = "SF_DPL";
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
        Optional<String> ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with no SF name
        rspBuilder = createRsp(rspName, true, false, false, false, false);
        ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF name, but SF doesnt exist
        rspBuilder = createRsp(rspName, true, true, false, false, false);
        ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF name, SF exists, but has no DPL
        rspBuilder = createRsp(rspName, true, true, true, false, false);
        ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP with SF name, SF exists, has DPL, but not of type LogicalInterfaceLocator
        rspBuilder = createRsp(rspName, true, true, true, true, false);
        ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertFalse(ifName.isPresent());

        // Check RSP when its all created correctly
        rspBuilder = createRsp(rspName, true, true, true, true, true);
        ifName = this.sfcProvider.getFirstHopSfInterfaceFromRsp(rspBuilder.build());
        assertTrue(ifName.isPresent());
        assertEquals(ifName.get(), LOGICAL_IF_NAME);
    }

    private RenderedServicePathBuilder createRsp(RspName rspName) {
        RenderedServicePathBuilder rspBuilder = new RenderedServicePathBuilder();
        rspBuilder.setName(rspName).setServiceChainName(SfcName.getDefaultInstance(SFC_NAME)).setPathId(PATH_ID);

        return rspBuilder;
    }

    private RenderedServicePathBuilder createRsp(RspName rspName, boolean hasHops, boolean hasSfName,
            boolean createSf, boolean createSfDpl, boolean createLogicalSfDpl) {
        RenderedServicePathBuilder rspBuilder = createRsp(rspName);
        SfName sfName = new SfName(SF_NAME);

        RenderedServicePathHopBuilder rspHopBuilder = new RenderedServicePathHopBuilder();
        if (hasSfName) {
            rspHopBuilder.setServiceFunctionName(sfName);
        }

        if (hasHops) {
            List<RenderedServicePathHop> hops = new ArrayList<>();
            hops.add(rspHopBuilder.build());
            rspBuilder.setRenderedServicePathHop(hops);
        }

        ServiceFunctionBuilder sfBuilder = new ServiceFunctionBuilder().setType(SftTypeName.getDefaultInstance("NAT"));
        SfDataPlaneLocatorBuilder sfDplBuilder = new SfDataPlaneLocatorBuilder();
        if (createLogicalSfDpl) {
            LogicalInterfaceBuilder liBuilder = new LogicalInterfaceBuilder();
            liBuilder.setInterfaceName(LOGICAL_IF_NAME);
            sfDplBuilder.setLocatorType(liBuilder.build());
        }

        if (createSfDpl) {
            List<SfDataPlaneLocator> sfDpls = new ArrayList<>();
            sfDplBuilder.setKey(new SfDataPlaneLocatorKey(new SfDataPlaneLocatorName(SF_DPL_NAME)));
            sfDplBuilder.setName(new SfDataPlaneLocatorName(SF_DPL_NAME));
            sfDpls.add(sfDplBuilder.build());
            sfBuilder.setSfDataPlaneLocator(sfDpls);
        }

        if (createSf) {
            sfBuilder.setName(sfName);
            storeSf(sfName, sfBuilder.build());
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
    private void storeSf(SfName sfName, ServiceFunction sf) {
        InstanceIdentifier<ServiceFunction> sfIid = InstanceIdentifier.builder(ServiceFunctions.class)
                .child(ServiceFunction.class, new ServiceFunctionKey(sfName)).build();

        MDSALUtil.syncWrite(getDataBroker(), LogicalDatastoreType.CONFIGURATION, sfIid, sf);
    }
}
