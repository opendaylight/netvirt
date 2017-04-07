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
import static org.mockito.Matchers.any;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.RspName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.common.rev151017.SfName;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePathKey;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHop;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.rendered.service.path.RenderedServicePathHopBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocator;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.function.base.SfDataPlaneLocatorBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunction;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionBuilder;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.sf.rev140701.service.functions.ServiceFunctionKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.service.functions.service.function.sf.data.plane.locator.locator.type.LogicalInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(PowerMockRunner.class)
@PrepareForTest(MDSALUtil.class)
public class SfcProviderTest {
    private static final String RSP_NAME = "RSP1";
    private static final String RSP_NAME_NOEXIST = "RSP_NOEXIST";
    private static final String SFC_NAME = "SFC1";
    private static final String SF_NAME = "SF1";
    private static final String LOGICAL_IF_NAME = "eccb57ae-5a2e-467f-823e-45d7bb2a6a9a";
    private static final Logger LOG = LoggerFactory.getLogger(SfcProviderTest.class);

    @Mock
    private DataBroker dataBroker;

    private SfcProvider sfcProvider;
    private final Map<RspName, RenderedServicePath> rspNamesToRsps;
    private final Map<SfName, ServiceFunction> sfNamesToSfs;


    public SfcProviderTest() {
        rspNamesToRsps = new HashMap<>();
        sfNamesToSfs = new HashMap<>();
    }

    @Before
    public void setUp() throws Exception {
        sfcProvider = new SfcProvider(dataBroker);
        PowerMockito.mockStatic(MDSALUtil.class);

        //
        // Mock what gets returned by MDSALUtil.read()
        // Checks if the Iid is an RSP or an SF and return the corresponding
        // object from either sfNamesToSfs or rspNamesToRsps
        //
        InstanceIdentifier<DataObject> anyIid = any();
        PowerMockito.when(MDSALUtil.read(any(DataBroker.class), any(LogicalDatastoreType.class), anyIid))
            .thenAnswer(invocation -> {
                InstanceIdentifier<DataObject> objectIid =
                        (InstanceIdentifier<DataObject>) invocation.getArguments()[2];
                if (objectIid.getTargetType().equals(ServiceFunction.class)) {
                    InstanceIdentifier<ServiceFunction> sfIid =
                            (InstanceIdentifier<ServiceFunction>) invocation.getArguments()[2];
                    ServiceFunctionKey key = InstanceIdentifier.keyOf(sfIid);
                    return com.google.common.base.Optional.fromNullable(sfNamesToSfs.get(key.getName()));
                } else if (objectIid.getTargetType().equals(RenderedServicePath.class)) {
                    InstanceIdentifier<RenderedServicePath> rspIid =
                            (InstanceIdentifier<RenderedServicePath>) invocation.getArguments()[2];
                    RenderedServicePathKey key = InstanceIdentifier.keyOf(rspIid);
                    return com.google.common.base.Optional.fromNullable(rspNamesToRsps.get(key.getName()));
                } else {
                    LOG.info("MDSALUtil.read() TargetType not recognized [{}]", objectIid.getTargetType());
                    return com.google.common.base.Optional.absent();
                }
            });
    }

    @After
    public void tearDown() throws Exception {
        rspNamesToRsps.clear();
        sfNamesToSfs.clear();
    }

    @Test
    public void getRenderedServicePath() {
        RspName rspName = new RspName(RSP_NAME);
        RenderedServicePathBuilder rspBuilder = createRsp(rspName);
        rspNamesToRsps.put(rspName, rspBuilder.build());

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
        rspBuilder.setName(rspName);

        return rspBuilder;
    }

    private RenderedServicePathBuilder createRsp(RspName rspName, boolean hasHops, boolean hasSfName,
            boolean createSf, boolean createSfDpl, boolean createLogicalSfDp) {
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

        ServiceFunctionBuilder sfBuilder = new ServiceFunctionBuilder();
        SfDataPlaneLocatorBuilder sfDplBuilder = new SfDataPlaneLocatorBuilder();
        if (createLogicalSfDp) {
            LogicalInterfaceBuilder liBuilder = new LogicalInterfaceBuilder();
            liBuilder.setInterfaceName(LOGICAL_IF_NAME);
            sfDplBuilder.setLocatorType(liBuilder.build());
        }

        if (createSfDpl) {
            List<SfDataPlaneLocator> sfDpls = new ArrayList<>();
            sfDpls.add(sfDplBuilder.build());
            sfBuilder.setSfDataPlaneLocator(sfDpls);
        }

        if (createSf) {
            sfNamesToSfs.put(sfName, sfBuilder.build());
        }

        return rspBuilder;
    }
}
