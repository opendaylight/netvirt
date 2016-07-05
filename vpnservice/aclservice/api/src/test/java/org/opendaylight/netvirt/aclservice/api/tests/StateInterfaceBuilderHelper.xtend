package org.opendaylight.netvirt.aclservice.api.tests

import static extension org.opendaylight.netvirt.aclservice.api.tests.BuilderExtensions.operator_doubleGreaterThan
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceBuilder
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.InterfaceKey
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier

class StateInterfaceBuilderHelper {

    def static Pair<InstanceIdentifier<Interface>, Interface> newStateInterfacePair(String interfaceName, String mac) {
        Pair.of(newStateInterfaceInstanceIdentifier(interfaceName), newStateInterface(interfaceName, mac))
    }

    def static newStateInterface(String interfaceName, String mac) {
        new InterfaceBuilder >> [
            name = interfaceName
            physAddress = new PhysAddress(mac)
        ]
    }

    def static newStateInterfaceInstanceIdentifier(String interfaceName) {
        InstanceIdentifier.builder(InterfacesState).child(Interface, new InterfaceKey(interfaceName)).build
    }

}
