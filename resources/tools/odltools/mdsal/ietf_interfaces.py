from model import Model


NAME = "ietf-interfaces"


def interfaces(store, ip=None, port=None, path=None):
    return Interfaces(NAME, Interfaces.CONTAINER, store, ip, port, path)


def interfaces_state(store, ip=None, port=None, path=None):
    return Interfaces(NAME, InterfacesState.CONTAINER, store, ip, port, path)


class Interfaces(Model):
    CONTAINER = "interfaces"
    INTERFACE = "interface"

    def get_interfaces(self):
        return self.data[self.CONTAINER][self.INTERFACE]

    def get_interfaces_by_key(self, key="name"):
        d = {}
        ifaces = self.get_interfaces()
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
        for iface in ifaces:
            d[iface[key]] = iface
        return d
