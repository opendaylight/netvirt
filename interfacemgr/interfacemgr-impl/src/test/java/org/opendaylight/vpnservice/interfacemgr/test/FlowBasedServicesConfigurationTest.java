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
import org.opendaylight.vpnservice.interfacemgr.IfmUtil;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers.FlowBasedServicesConfigBindHelper;
import org.opendaylight.vpnservice.interfacemgr.servicebindings.flowbased.confighelpers.FlowBasedServicesConfigUnbindHelper;
import org.opendaylight.vpnservice.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.L2vlan;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.OperStatus;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.TunnelTypeGre;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FlowBasedServicesConfigurationTest {

    BigInteger dpId = BigInteger.valueOf(1);
    int flowPriority = 0;
    int instructionKeyval = 2;
    long portNum = 2;
    Interface interfaceEnabled = null;
    String serviceName = "VPN";
    InstanceIdentifier<BoundServices> boundServicesIid = null;
    org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface stateInterface;
    InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> interfaceStateIdentifier = null;
    BoundServices boundServiceNew = null;
    NodeConnectorId nodeConnectorId = null;
    ServicesInfo servicesInfo = null;
    ServicesInfo servicesInfoUnbind = null;
    StypeOpenflow stypeOpenflow = null;
    InstanceIdentifier<Interface> interfaceInstanceIdentifier = null;
    InstanceIdentifier<Flow> flowInstanceId = null;
    Flow ingressFlow = null;
    Instruction instruction = null;
    InstructionKey instructionKey = null;
    List<Instruction>instructions = new ArrayList<>();
    short key = 0;
    int ifIndexval = 100;

    @Mock DataBroker dataBroker;
    @Mock ListenerRegistration<DataChangeListener> dataChangeListenerRegistration;
    @Mock ReadOnlyTransaction mockReadTx;
    @Mock WriteTransaction mockWriteTx;

    FlowBasedServicesConfigBindHelper bindHelper;
    FlowBasedServicesConfigUnbindHelper unbindHelper;

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

        interfaceEnabled = InterfaceManagerTestUtil.buildInterface(InterfaceManagerTestUtil.interfaceName, "Test Vlan Interface1",true,L2vlan.class,dpId);
        nodeConnectorId = InterfaceManagerTestUtil.buildNodeConnectorId(dpId, portNum);
        interfaceInstanceIdentifier = IfmUtil.buildId(InterfaceManagerTestUtil.interfaceName);

        InterfaceBuilder ifaceBuilder = new InterfaceBuilder();
        List<String> lowerLayerIfList = new ArrayList<>();
        lowerLayerIfList.add(nodeConnectorId.getValue());
        ifaceBuilder.setOperStatus(OperStatus.Up).setAdminStatus(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface.AdminStatus.Up)
                .setPhysAddress(PhysAddress.getDefaultInstance("AA:AA:AA:AA:AA:AA"))
                .setIfIndex(ifIndexval)
                .setLowerLayerIf(lowerLayerIfList)
                .setKey(IfmUtil.getStateInterfaceKeyFromName(InterfaceManagerTestUtil.interfaceName))
                .setName(InterfaceManagerTestUtil.interfaceName);

        stypeOpenflow = InterfaceManagerTestUtil.buildStypeOpenflow(dpId, flowPriority,NwConstants.LPORT_DISPATCHER_TABLE,instructions);
        boundServiceNew = InterfaceManagerTestUtil.buildBoundServices(serviceName, key, new BoundServicesKey(key), stypeOpenflow);
        instructionKey = new InstructionKey(instructionKeyval);
        BigInteger[] metadataValues = IfmUtil.mergeOpenflowMetadataWriteInstructions(instructions);
        short sIndex = boundServiceNew.getServicePriority();
        BigInteger metadata = MetaDataUtil.getMetaDataForLPortDispatcher(ifaceBuilder.getIfIndex(),
                ++sIndex, metadataValues[0]);
        BigInteger mask = MetaDataUtil.getMetaDataMaskForLPortDispatcher(
                MetaDataUtil.METADATA_MASK_SERVICE_INDEX,
                MetaDataUtil.METADATA_MASK_LPORT_TAG, metadataValues[1]);

        instruction = InterfaceManagerTestUtil.buildInstruction(InterfaceManagerTestUtil.buildWriteMetaDataCase(InterfaceManagerTestUtil.buildWriteMetaData(metadata, mask)),
                new InstructionKey(instructionKey));
        instructions.add(instruction);
        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        boundServicesIid =  InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey).
                child(BoundServices.class, new BoundServicesKey(key)).build();

        interfaceStateIdentifier = IfmUtil.buildStateInterfaceId(interfaceEnabled.getName());
        stateInterface = ifaceBuilder.build();
        List<BoundServices> boundServiceslist = new ArrayList<>();
        boundServiceslist.add(boundServiceNew);
        servicesInfo = InterfaceManagerTestUtil.buildServicesInfo(InterfaceManagerTestUtil.interfaceName, servicesInfoKey, boundServiceslist);
        servicesInfoUnbind = InterfaceManagerTestUtil.buildServicesInfo(InterfaceManagerTestUtil.interfaceName,servicesInfoKey,new ArrayList<>());

        String flowRef = InterfaceManagerTestUtil.buildflowRef(dpId,InterfaceManagerTestUtil.interfaceName,boundServiceNew.getServiceName(),boundServiceNew.getServicePriority());
        List<Instruction> instructionList = boundServiceNew.getAugmentation(StypeOpenflow.class).getInstruction();
        String serviceRef = boundServiceNew.getServiceName();
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                MetaDataUtil.getMetaDataForLPortDispatcher(ifaceBuilder.getIfIndex(), boundServiceNew.getServicePriority()),
                MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
        ingressFlow = MDSALUtil.buildFlowNew(NwConstants.LPORT_DISPATCHER_TABLE, flowRef, boundServiceNew.getServicePriority(), serviceRef, 0, 0,
                stypeOpenflow.getFlowCookie(), matches, instructionList);
        FlowKey flowKey = new FlowKey(new FlowId(ingressFlow.getId()));
        flowInstanceId = InterfaceManagerTestUtil.getFlowInstanceIdentifier(dpId,ingressFlow.getTableId(),flowKey);

        when(dataBroker.newReadOnlyTransaction()).thenReturn(mockReadTx);
        when(dataBroker.newWriteOnlyTransaction()).thenReturn(mockWriteTx);
    }

    @Test
    public void testConfigBindSingleService(){

        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateInterface = Optional.of(stateInterface);
        Optional<Interface> expectedInterface = Optional.of(interfaceEnabled);
        Optional<ServicesInfo>expectedservicesInfo = Optional.of(servicesInfo);

        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        InstanceIdentifier.InstanceIdentifierBuilder<ServicesInfo> servicesInfoIdentifierBuilder =
                InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey);


        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedservicesInfo)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, servicesInfoIdentifierBuilder.build());

        bindHelper.bindService(boundServicesIid,boundServiceNew,dataBroker);

        verify(mockWriteTx).put(LogicalDatastoreType.CONFIGURATION,flowInstanceId,ingressFlow, true);
    }

    @Test
    public void testConfigUnbindSingleService(){

        Optional<org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface> expectedStateInterface = Optional.of(stateInterface);
        Optional<Interface> expectedInterface = Optional.of(interfaceEnabled);
        Optional<ServicesInfo>expectedservicesInfo = Optional.of(servicesInfoUnbind);

        ServicesInfoKey servicesInfoKey = new ServicesInfoKey(InterfaceManagerTestUtil.interfaceName);
        InstanceIdentifier.InstanceIdentifierBuilder<ServicesInfo> servicesInfoIdentifierBuilder =
                InstanceIdentifier.builder(ServiceBindings.class).child(ServicesInfo.class, servicesInfoKey);

        doReturn(Futures.immediateCheckedFuture(expectedStateInterface)).when(mockReadTx).read(
                LogicalDatastoreType.OPERATIONAL,interfaceStateIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedInterface)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION, interfaceInstanceIdentifier);
        doReturn(Futures.immediateCheckedFuture(expectedservicesInfo)).when(mockReadTx).read(
                LogicalDatastoreType.CONFIGURATION,servicesInfoIdentifierBuilder.build());

        unbindHelper.unbindService(boundServicesIid,boundServiceNew,dataBroker);

        verify(mockWriteTx).delete(LogicalDatastoreType.CONFIGURATION,flowInstanceId);
    }

}