package org.opendaylight.netvirt.netvirt.renderers.neutron;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.netvirt.utils.mdsal.utils.MdsalHelper;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import com.google.common.base.Optional;

/**
 * Unit test for {@link NeutronPortDataProcessor}
 */
//@RunWith(MockitoJUnitRunner.class)
public class NeutronPortDataProcessorTest extends AbstractDataBrokerTest {
    private static final Uuid portId = new Uuid("aaaaaaaa-bbbb-cccc-dddd-123456789012");
    private static final Uuid portId2 = new Uuid("11111111-2222-3333-4444-555555555555");
    private static final Uuid portId3 = new Uuid("33333333-3333-3333-3333-333333333333");


    private void addPort(Uuid uuid) throws Exception {
        ProviderContext session = mock(ProviderContext.class);
        when(session.getSALService(DataBroker.class)).thenReturn(getDataBroker());
        NeutronPortDataProcessor neutronPortDataProcessor = new NeutronPortDataProcessor(new NeutronProvider(), session.getSALService(DataBroker.class));

        //NeutronPortDataProcessor portDataProcessorSpy = Mockito.spy(neutronPortDataProcessor);
        Port neutronPort = mock(Port.class);
        when(neutronPort.getStatus()).thenReturn("Up");
        when(neutronPort.getName()).thenReturn("neutronTestPort");
        when(neutronPort.isAdminStateUp()).thenReturn(true);
        when(neutronPort.getDeviceOwner()).thenReturn("compute:nova");
        when(neutronPort.getDeviceId()).thenReturn("12345678-1234-1234-1234-123456789012");
        when(neutronPort.getUuid()).thenReturn(portId);
        when(neutronPort.getFixedIps()).thenReturn(null);

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> portIid =
                MdsalHelper.createPortInstanceIdentifier(neutronPort.getUuid());

        InstanceIdentifier<Port> instanceIdentifier = InstanceIdentifier.create(Ports.class).child(Port.class);
        neutronPortDataProcessor.add(instanceIdentifier, neutronPort);

        //read mdsal, verify that netvirt port was added.
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port netvirtPort = null;
        try {
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> data =
                    getDataBroker()
                    .newReadOnlyTransaction()
                    .read(LogicalDatastoreType.CONFIGURATION, portIid)
                    .get();
            if (data.isPresent()) {
                netvirtPort = data.get();
            }
        } catch (Exception e){
            throw e;
        }
        assertNotNull(netvirtPort);
        assertEquals("Error, status not correct", netvirtPort.getStatus(), neutronPort.getStatus());
        assertEquals("Error, name not correct", netvirtPort.getName(), neutronPort.getName());
        assertEquals("Error, admin state not correct", netvirtPort.isAdminStateUp(), neutronPort.isAdminStateUp());
        assertEquals("Error, dev id is not correct", netvirtPort.getDeviceUuid().getValue(), neutronPort.getDeviceId());
        assertEquals("Error, uuid is not correct", netvirtPort.getUuid().getValue(), neutronPort.getUuid().getValue());
    }

    private void deletePort(Uuid uuid) throws Exception {
        ProviderContext session = mock(ProviderContext.class);
        when(session.getSALService(DataBroker.class)).thenReturn(getDataBroker());
        NeutronPortDataProcessor neutronPortDataProcessor = new NeutronPortDataProcessor(new NeutronProvider(), session.getSALService(DataBroker.class));


        InstanceIdentifier<Port> instanceIdentifier = InstanceIdentifier.create(Ports.class).child(Port.class);
        Port neutronPort = mock(Port.class);
        when(neutronPort.getUuid()).thenReturn(portId2);
        neutronPortDataProcessor.remove(instanceIdentifier, neutronPort);

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> portIid =
                MdsalHelper.createPortInstanceIdentifier(neutronPort.getUuid());

        //read mdsal, verify that netvirt port was removed
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port netvirtPort = null;
        try {
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> data =
                    getDataBroker()
                    .newReadOnlyTransaction()
                    .read(LogicalDatastoreType.CONFIGURATION, portIid)
                    .get();
            assertFalse(data.isPresent());
        } catch (Exception e){
            throw e;
        }
    }

    private void updatePort(Uuid uuid) throws Exception {
        ProviderContext session = mock(ProviderContext.class);
        when(session.getSALService(DataBroker.class)).thenReturn(getDataBroker());
        NeutronPortDataProcessor neutronPortDataProcessor = new NeutronPortDataProcessor(new NeutronProvider(), session.getSALService(DataBroker.class));

        //NeutronPortDataProcessor portDataProcessorSpy = Mockito.spy(neutronPortDataProcessor);
        Port neutronPort = mock(Port.class);
        when(neutronPort.getStatus()).thenReturn("Up");
        when(neutronPort.getName()).thenReturn("neutronTestPort");
        when(neutronPort.isAdminStateUp()).thenReturn(true);
        when(neutronPort.getDeviceOwner()).thenReturn("compute:nova");
        when(neutronPort.getDeviceId()).thenReturn("12345678-1234-1234-1234-123456789012");
        when(neutronPort.getUuid()).thenReturn(uuid);
        when(neutronPort.getFixedIps()).thenReturn(null);

        Port neutronPort1 = mock(Port.class);
        when(neutronPort1.getStatus()).thenReturn("Down");
        when(neutronPort1.getName()).thenReturn("neutronTestPortUpdate");
        when(neutronPort1.isAdminStateUp()).thenReturn(false);
        when(neutronPort1.getDeviceOwner()).thenReturn("compute:nova");
        when(neutronPort1.getDeviceId()).thenReturn("12345678-1234-1234-1234-123456789012");
        when(neutronPort1.getUuid()).thenReturn(uuid);
        when(neutronPort1.getFixedIps()).thenReturn(null);

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> portIid =
                MdsalHelper.createPortInstanceIdentifier(neutronPort.getUuid());

        InstanceIdentifier<Port> instanceIdentifier = InstanceIdentifier.create(Ports.class).child(Port.class);
        neutronPortDataProcessor.update(instanceIdentifier, neutronPort, neutronPort1);

         //read mdsal, verify that netvirt port was updated
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port netvirtPort = null;
        try {
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.ports.rev151227.ports.Port> data =
                    getDataBroker()
                    .newReadOnlyTransaction()
                    .read(LogicalDatastoreType.CONFIGURATION, portIid)
                    .get();
            if (data.isPresent()) {
                netvirtPort = data.get();
            }
        } catch (Exception e){
            throw e;
        }
        assertNotNull(netvirtPort);
        assertEquals("Error, status not updated", netvirtPort.getStatus(), neutronPort1.getStatus());
        assertEquals("Error, name not updated", netvirtPort.getName(), neutronPort1.getName());
        assertEquals("Error, admin state not updated", netvirtPort.isAdminStateUp(), neutronPort1.isAdminStateUp());
        assertEquals("Error, dev id not updated", netvirtPort.getDeviceUuid().getValue(), neutronPort1.getDeviceId());
        assertEquals("Error, uuid not updated", netvirtPort.getUuid().getValue(), neutronPort1.getUuid().getValue());

    }

    @Test
    public void testRemove() throws Exception {
        addPort(portId2);
        deletePort(portId2);
    }

    @Test
    public void testUpdate() throws Exception {
        addPort(portId3);
        updatePort(portId3);
    }

    @Test
    public void testAdd() throws Exception {
        addPort(portId);
    }

}