from odltools.mdsal.models.model import Model


NAME = "ietf-interfaces"


def interfaces(store, args):
    return Interfaces(NAME, Interfaces.CONTAINER, store, args)


def interfaces_state(store, args):
    return InterfacesState(NAME, InterfacesState.CONTAINER, store, args)


class Interfaces(Model):
    CONTAINER = "interfaces"
    INTERFACE = "interface"

    def get_interfaces(self):
        return self.data and self.data[self.CONTAINER][self.INTERFACE]

    def get_interfaces_by_key(self, key="name"):
        d = {}
        ifaces = self.get_interfaces()
        if ifaces is None:
            return None
        for iface in ifaces:
            d[iface[key]] = iface
        return d


class InterfacesState(Model):
    ROOT = NAME
    CONTAINER = "interfaces-state"
    INTERFACE = "interface"

    def get_interfaces(self):
        return self.data[self.CONTAINER][self.INTERFACE]

    def get_interfaces_by_key(self, key="name"):
        d = {}
        ifaces = self.get_interfaces()
        if ifaces is None:
            return None
        for iface in ifaces:
            d[iface[key]] = iface
        return d
