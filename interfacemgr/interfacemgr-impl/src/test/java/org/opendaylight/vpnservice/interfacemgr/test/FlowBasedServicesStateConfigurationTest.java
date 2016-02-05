package org.opendaylight.vpnservice.interfacemgr.test;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.interfacemgr.IfmConstants;
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.statehelpers.FlowBasedServicesStateBindHelper;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.statehelpers.FlowBasedServicesStateUnbindHelper;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceBindings;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FlowBasedServicesStateConfigurationTest {

    Interface interfaceEnabled = null;
    long portNum = 2;
    int instructionKeyval = 2;
    InstanceIdentifier<Interface> interfaceInstanceIdentifier = null;
    InstanceIdentifier<Flow> flowInstanceId = null;
    Flow ingressFlow = null;
    BigInteger dpId = BigInteger.valueOf(1);
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateInterface;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier = null;
    ServicesInfo servicesInfo = null;
    NodeConnectorId nodeConnectorId = null;
    BoundServices boundService = null;
    StypeOpenflow stypeOpenflow = null;
    Instruction instruction = null;
    InstructionKey instructionKey = null;
    List<Instruction>instructions = new ArrayList<>();
    short key =0;
    int ifIndexval = 100;
    int flowpriority = 2;
    String serviceName = "VPN";

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;

    FlowBasedServicesStateBindHelper bindHelper;
    FlowBasedServicesStateUnbindHelper unbindHelper;

    @Before
    public void setUp() throws Exception {
        when(dataBroker.registerDataChangeListener(
                any(LogicalDatastoreType.class),
                any(InstanceIdentifier.class),
                any(DataChangeListener.class),
                any(DataChangeScope.class)))
                .thenReturn(dataChangeListenerRegistration);
        setupMocks();
    }

    private void setupMocks(){

        interfaceEnabled = InterfaceManagerTestUtil.buildInterface(InterfaceManagerTestUtil.interfaceName, "Test Vlan Interface1", true, L2vlan.class, dpId);
        interfaceInstanceIdentifier = IfmUtil.buildId(InterfaceManagerTestUtil.interfaceName);
        nodeConnectorId = InterfaceManagerTestUtil.buildNodeConnectorId(BigInteger.valueOf(1), portNum);
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder ifaceBuilder = new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder();
        List<String> lowerLayerIfList = new ArrayList<>();
        lowerLayerIfList.add(nodeConnectorId.getValue());
        ifaceBuilder.setOperStatus(OperStatus.Up).setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up)
                .setPhysAddress(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"))
                .setIfIndex(ifIndexval)
                .setLowerLayerIf(lowerLayerIfList)
                .setKey(IfmUtil.getStateInterfaceKeyFromName(InterfaceManagerTestUtil.interfaceName))
                .setName(InterfaceManagerTestUtil.interfaceName).setType(interfaceEnabled.getType());
        stypeOpenflow = InterfaceManagerTestUtil.buildStypeOpenflow(dpId,flowpriority, NwConstants.LPORT_DISPATCHER_TABLE, instructions);
        instructionKey = new InstructionKey(instructionKeyval);
        BigInteger[] metadataValues = IfmUtil.mergeOpenflowMetadataWriteInstructions(instructions);
        boundService = InterfaceManagerTestUtil.buildBoundServices(serviceName,key,new BoundServicesKey(key),stypeOpenflow);
        short sIndex = boundService.getServicePriority();
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(ifaceBuilder.getIfIndex(),
                ++sIndex, metadataValues[0]);
        BigInteger mask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(
                MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                MetaDataUtil.METADATA_MASK_LPORT_TAG, metadataValues[1]);
        instruction = InterfaceManagerTestUtil.buildInstruction(InterfaceManagerTestUtil.buildWriteMetaDataCase(InterfaceManagerTestUtil.buildWriteMetaData(metadata, mask)),
                new InstructionKey(instructionKey));
        instructions.add(instruction);
        stateInterface = ifaceBuilder.build();
        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        List<BoundServices> lowerlayerIfList = new ArrayList<>();
        lowerlayerIfList.add(boundService);
        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(interfaceEnabled.getName());
        servicesInfo = InterfaceManagerTestUtil.buildServicesInfo(InterfaceManagerTestUtil.interfaceName,servicesInfoKey,lowerlayerIfList);

        String flowRef = InterfaceManagerTestUtil.buildflowRef(dpId, InterfaceManagerTestUtil.interfaceName, boundService.getServiceName(), boundService.getServicePriority());
        List<Instruction> instructionList = boundService.getAugmentation(StypeOpenflow.class).getInstruction();
        String serviceRef = boundService.getServiceName();
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getMetaDataForLPortDispatcher(ifaceBuilder.getIfIndex(), boundService.getServicePriority()),
                MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
        ingressFlow = MDSALUtil.buildFlowNew(stypeOpenflow.getDispatcherTableId(), flowRef, boundService.getServicePriority(), serviceRef, 0, 0,
                stypeOpenflow.getFlowCookie(), matches, instructionList);
        FlowKey flowKey = new FlowKey(new FlowId(ingressFlow.getId()));
        flowInstanceId = InterfaceManagerTestUtil.getFlowInstanceIdentifier(dpId,ingressFlow.getTableId(),flowKey);

        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testStateBindSingleService(){

        Optional<ServicesInfo>expectedservicesInfo = Optional.of(servicesInfo);
        Optional<Interface> expectedInterface = Optional.of(interfaceEnabled);
        Optional<NodeConnectorId>expectednodeconnectorId = Optional.of(nodeConnectorId);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateInterface = Optional.of(stateInterface);

        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        InstanceIdentifier.InstanceIdentifierBuilder<ServicesInfo> servicesInfoIdentifierBuilder =
                InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey);

        doReturn(Futures.immediateCheckedFuture(expectedservicesInfo)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION,servicesInfoIdentifierBuilder.build());
        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectednodeconnectorId)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier);

        bindHelper.bindServicesOnInterface(stateInterface,dataBroker);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION,flowInstanceId,ingressFlow, true);

    }

    @Test
    public void testStateUnbindSingleService(){

        Optional<ServicesInfo>expectedservicesInfo = Optional.of(servicesInfo);
        Optional<Interface> expectedInterface = Optional.of(interfaceEnabled);
        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateInterface = Optional.of(stateInterface);

        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        InstanceIdentifier.InstanceIdentifierBuilder<ServicesInfo> servicesInfoIdentifierBuilder =
                InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey);

        doReturn(Futures.immediateCheckedFuture(expectedservicesInfo)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION,servicesInfoIdentifierBuilder.build());
        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier);

        unbindHelper.unbindServicesFromInterface(stateInterface,dataBroker);

        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION,flowInstanceId);
    }
}
