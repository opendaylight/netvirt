/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.alivenessmonitor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Matchers.argThat;

import java.util.Arrays;

import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.NotificationPublishService;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessMonitor;
import org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessProtocolHandler;
import org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessProtocolHandlerARP;
import org.opendaylight.vpnservice.alivenessmonitor.internal.AlivenessProtocolHandlerLLDP;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.EtherTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorPauseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorPauseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfileCreateInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfileCreateInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfileCreateOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfileDeleteInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorProfileDeleteInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStartInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStartInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStartOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStopInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorStopInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorUnpauseInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitorUnpauseInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.MonitoringMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629._interface.monitor.map.InterfaceMonitorEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629._interface.monitor.map.InterfaceMonitorEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.endpoint.endpoint.type.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.configs.MonitoringInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.params.DestinationBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.params.SourceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profile.create.input.ProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profiles.MonitorProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.profiles.MonitorProfileBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitor.start.input.ConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitorid.key.map.MonitoridKeyEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitorid.key.map.MonitoridKeyEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitoring.states.MonitoringState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.alivenessmonitor.rev150629.monitoring.states.MonitoringStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.idmanager.rev150403.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;

public class AlivenessMonitorTest {

    @Mock private DataBroker dataBroker;
    @Mock private IdManagerService idManager;
    @Mock private PacketProcessingService packetProcessingService;
    @Mock private NotificationPublishService notificationPublishService;
    private AlivenessMonitor alivenessMonitor;
    private AlivenessProtocolHandler arpHandler;
    private AlivenessProtocolHandler lldpHandler;
    private long mockId;
    @Mock private ReadOnlyTransaction readTx;
    @Mock private WriteTransaction writeTx;
    @Mock private ReadWriteTransaction readWriteTx;
    @Captor ArgumentCaptor<MonitoringState> stateCaptor;

    private <T extends DataObject> Matcher<InstanceIdentifier<T>> isType(final Class<T> klass) {
        return new TypeSafeMatcher<InstanceIdentifier<T>>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("Instance Identifier should have Target Type " + klass);
            }

            @Override
            protected boolean matchesSafely(InstanceIdentifier<T> id) {
                return id.getTargetType().equals(klass);
            }
        };
    }

    private Matcher<RpcError> hasErrorType(final ErrorType errorType) {
        return new TypeSafeMatcher<RpcError>() {
            @Override
            public void describeTo(Description desc) {
                desc.appendText("Error type do not match " + errorType);
            }

            @Override
            protected boolean matchesSafely(RpcError error) {
                return error.getErrorType().equals(errorType);
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        alivenessMonitor = new AlivenessMonitor(dataBroker);
        when(idManager.createIdPool(any(CreateIdPoolInput.class)))
                  .thenReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build()));
        alivenessMonitor.setIdManager(idManager);
        alivenessMonitor.setNotificationPublishService(notificationPublishService);
        alivenessMonitor.setPacketProcessingService(packetProcessingService);

        arpHandler = new AlivenessProtocolHandlerARP(alivenessMonitor);
        alivenessMonitor.registerHandler(EtherTypes.Arp, arpHandler);

        lldpHandler = new AlivenessProtocolHandlerLLDP(alivenessMonitor);
        alivenessMonitor.registerHandler(EtherTypes.Lldp, lldpHandler);
        mockId = 1L;
        when(idManager.allocateId(any(AllocateIdInput.class)))
                  .thenReturn(Futures.immediateFuture(RpcResultBuilder.success(new AllocateIdOutputBuilder().setIdValue(mockId++).build()).build()));
        when(idManager.releaseId(any(ReleaseIdInput.class))).thenReturn(Futures.immediateFuture(RpcResultBuilder.<Void>success().build()));
        doReturn(readTx).when(dataBroker).newReadOnlyTransaction();
        doReturn(writeTx).when(dataBroker).newWriteOnlyTransaction();
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();
        doNothing().when(writeTx).put(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class), any(DataObject.class));
        doReturn(Futures.immediateCheckedFuture(null)).when(writeTx).submit();
        doReturn(Futures.immediateCheckedFuture(null)).when(readWriteTx).submit();
    }

    @After
    public void tearDown() throws Exception {
        alivenessMonitor.close();
    }

    @Test
    public void testMonitorProfileCreate() throws Throwable {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder().setProfile(new ProfileBuilder().setFailureThreshold(10L)
                .setMonitorInterval(10000L).setMonitorWindow(10L).setProtocolType(EtherTypes.Arp).build()).build();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(readWriteTx).read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)));
        doReturn(Futures.immediateCheckedFuture(null)).when(readWriteTx).submit();
        RpcResult<MonitorProfileCreateOutput> output = alivenessMonitor.monitorProfileCreate(input).get();
        assertTrue("Monitor Profile Create result", output.isSuccessful());
        assertNotNull("Monitor Profile Output", output.getResult().getProfileId());
    }

    @Test
    public void testMonitorProfileCreateAlreadyExist() throws Throwable {
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder().setProfile(new ProfileBuilder().setFailureThreshold(10L)
                .setMonitorInterval(10000L).setMonitorWindow(10L).setProtocolType(EtherTypes.Arp).build()).build();
        @SuppressWarnings("unchecked")
        Optional<MonitorProfile> optionalProfile = (Optional<MonitorProfile>)mock(Optional.class);
        CheckedFuture<Optional<MonitorProfile>, ReadFailedException> proFuture = Futures.immediateCheckedFuture(optionalProfile);
        doReturn(true).when(optionalProfile).isPresent();
        doReturn(proFuture).when(readWriteTx).read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)));
        RpcResult<MonitorProfileCreateOutput> output = alivenessMonitor.monitorProfileCreate(input).get();
        assertTrue("Monitor Profile Create result", output.isSuccessful());
        assertThat(output.getErrors(), CoreMatchers.hasItem(hasErrorType(ErrorType.PROTOCOL)));
    }

    @Test
    public void testMonitorStart() throws Throwable {
        Long profileId = createProfile();
        MonitorStartInput input = new MonitorStartInputBuilder().setConfig(new ConfigBuilder()
                                                                .setDestination(new DestinationBuilder().setEndpointType(getInterface("10.0.0.1")).build())
                                                                .setSource(new SourceBuilder().setEndpointType(getInterface("testInterface", "10.1.1.1")).build())
                                                                .setMode(MonitoringMode.OneOne)
                                                                .setProfileId(profileId).build()).build();
        @SuppressWarnings("unchecked")
        Optional<MonitorProfile> optionalProfile = (Optional<MonitorProfile>)mock(Optional.class);
        CheckedFuture<Optional<MonitorProfile>, ReadFailedException> proFuture = Futures.immediateCheckedFuture(optionalProfile);
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)))).thenReturn(proFuture);
        doReturn(true).when(optionalProfile).isPresent();
        doReturn(getTestMonitorProfile()).when(optionalProfile).get();
        CheckedFuture<Optional<MonitoringInfo>, ReadFailedException> outFuture = Futures.immediateCheckedFuture(Optional.<MonitoringInfo>absent());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringInfo.class)))).thenReturn(outFuture);
        RpcResult<MonitorStartOutput> output = alivenessMonitor.monitorStart(input).get();
        verify(idManager, times(2)).allocateId(any(AllocateIdInput.class));
        assertTrue("Monitor start output result", output.isSuccessful());
        assertNotNull("Monitor start output", output.getResult().getMonitorId());
    }

    @Test
    public void testMonitorPause() throws Throwable {
        MonitorPauseInput input = new MonitorPauseInputBuilder().setMonitorId(2L).build();
        Optional<MonitoringState> optState = Optional.of(new MonitoringStateBuilder().setStatus(MonitorStatus.Started).build());
        when(readWriteTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringState.class)))).
                         thenReturn(Futures.<Optional<MonitoringState>, ReadFailedException>immediateCheckedFuture(optState));
        Optional<MonitoridKeyEntry> optMap = Optional.of(new MonitoridKeyEntryBuilder().setMonitorId(2L).setMonitorKey("Test monitor Key").build());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoridKeyEntry.class)))).
                                               thenReturn(Futures.<Optional<MonitoridKeyEntry>, ReadFailedException>immediateCheckedFuture(optMap));
        alivenessMonitor.monitorPause(input).get();
        verify(readWriteTx).merge(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringState.class)), stateCaptor.capture());
        assertEquals(MonitorStatus.Paused, stateCaptor.getValue().getStatus());
    }

    @Test
    public void testMonitorUnpause() throws Throwable {
        MonitorUnpauseInput input = new  MonitorUnpauseInputBuilder().setMonitorId(2L).build();
        Optional<MonitoringState> optState = Optional.of(new MonitoringStateBuilder().setStatus(MonitorStatus.Paused).build());
        when(readWriteTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringState.class)))).
                         thenReturn(Futures.<Optional<MonitoringState>, ReadFailedException>immediateCheckedFuture(optState));
        Optional<MonitoringInfo> optInfo = Optional.of(new MonitoringInfoBuilder().setId(2L).setProfileId(1L).build());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringInfo.class)))).
                         thenReturn(Futures.<Optional<MonitoringInfo>, ReadFailedException>immediateCheckedFuture(optInfo));
        Optional<MonitorProfile> optProfile = Optional.of(getTestMonitorProfile());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)))).
                         thenReturn(Futures.<Optional<MonitorProfile>, ReadFailedException>immediateCheckedFuture(optProfile));
        Optional<MonitoridKeyEntry> optMap = Optional.of(new MonitoridKeyEntryBuilder().setMonitorId(2L).setMonitorKey("Test monitor Key").build());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoridKeyEntry.class)))).
                                               thenReturn(Futures.<Optional<MonitoridKeyEntry>, ReadFailedException>immediateCheckedFuture(optMap));
        RpcResult<Void> result = alivenessMonitor.monitorUnpause(input).get();
        verify(readWriteTx).merge(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringState.class)), stateCaptor.capture());
        assertEquals(MonitorStatus.Started, stateCaptor.getValue().getStatus());
        assertTrue("Monitor unpause rpc result", result.isSuccessful());
    }

    @Test
    public void testMonitorStop() throws Throwable {
        MonitorStopInput input = new MonitorStopInputBuilder().setMonitorId(2L).build();
        Optional<MonitoringInfo> optInfo = Optional.of(
                new MonitoringInfoBuilder().setSource(new SourceBuilder().setEndpointType(getInterface("testInterface", "10.1.1.1")).build()).build());
        CheckedFuture<Optional<MonitoringInfo>, ReadFailedException> outFuture = Futures.immediateCheckedFuture(optInfo);
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoringInfo.class)))).thenReturn(outFuture);
        Optional<MonitoridKeyEntry> optMap = Optional.of(new MonitoridKeyEntryBuilder().setMonitorId(2L).setMonitorKey("Test monitor Key").build());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitoridKeyEntry.class)))).
                                               thenReturn(Futures.<Optional<MonitoridKeyEntry>, ReadFailedException>immediateCheckedFuture(optMap));
        Optional<MonitorProfile> optProfile = Optional.of(getTestMonitorProfile());
        when(readTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)))).
                                               thenReturn(Futures.<Optional<MonitorProfile>, ReadFailedException>immediateCheckedFuture(optProfile));
        Optional<InterfaceMonitorEntry> optEntry = Optional.of(getInterfaceMonitorEntry());
        when(readWriteTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(InterfaceMonitorEntry.class)))).
                                               thenReturn(Futures.<Optional<InterfaceMonitorEntry>, ReadFailedException>immediateCheckedFuture(optEntry));
        RpcResult<Void> result = alivenessMonitor.monitorStop(input).get();
        verify(idManager).releaseId(any(ReleaseIdInput.class));
        verify(writeTx, times(2)).delete(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        assertTrue("Monitor stop rpc result", result.isSuccessful());
    }

    @Test
    public void testMonitorProfileDelete() throws Throwable {
        MonitorProfileDeleteInput input = new MonitorProfileDeleteInputBuilder().setProfileId(1L).build();
        Optional<MonitorProfile> optProfile = Optional.of(getTestMonitorProfile());
        when(readWriteTx.read(eq(LogicalDatastoreType.OPERATIONAL), argThat(isType(MonitorProfile.class)))).
                      thenReturn(Futures.<Optional<MonitorProfile>, ReadFailedException>immediateCheckedFuture(optProfile));
        RpcResult<Void> result = alivenessMonitor.monitorProfileDelete(input).get();
        verify(idManager).releaseId(any(ReleaseIdInput.class));
        verify(readWriteTx).delete(eq(LogicalDatastoreType.OPERATIONAL), Matchers.<InstanceIdentifier<MonitorProfile>>any());
        assertTrue("Monitor profile delete result", result.isSuccessful());
    }

    @SuppressWarnings("unchecked")
    private long createProfile() throws Throwable{
        MonitorProfileCreateInput input = new MonitorProfileCreateInputBuilder().setProfile(new ProfileBuilder().setFailureThreshold(10L)
                                                           .setMonitorInterval(10000L).setMonitorWindow(10L).setProtocolType(EtherTypes.Arp).build()).build();
        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(readWriteTx).read(eq(LogicalDatastoreType.OPERATIONAL), any(InstanceIdentifier.class));
        doReturn(Futures.immediateCheckedFuture(null)).when(readWriteTx).submit();
        RpcResult<MonitorProfileCreateOutput> output = alivenessMonitor.monitorProfileCreate(input).get();
        return output.getResult().getProfileId();
    }

    private MonitorProfile getTestMonitorProfile() {
        return new MonitorProfileBuilder().setFailureThreshold(10L).setMonitorInterval(10000L)
                                             .setMonitorWindow(10L).setProtocolType(EtherTypes.Arp).build();
    }

    private InterfaceMonitorEntry getInterfaceMonitorEntry() {
        return new InterfaceMonitorEntryBuilder().setInterfaceName("test-interface").setMonitorIds(Arrays.asList(1L, 2L)).build();
    }

    private Interface getInterface(String ipAddress) {
        return new InterfaceBuilder().setInterfaceIp(IpAddressBuilder.getDefaultInstance(ipAddress)).build();
    }

    private Interface getInterface(String interfaceName, String ipAddress) {
        return new InterfaceBuilder().setInterfaceIp(IpAddressBuilder.getDefaultInstance(ipAddress)).setInterfaceName(interfaceName).build();
    }
}
