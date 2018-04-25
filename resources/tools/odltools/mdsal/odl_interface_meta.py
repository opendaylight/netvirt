from model import Model


NAME = "odl-interface-meta"


def if_indexes_interface_map(store, ip=None, port=None, path=None):
    return IfIndexesInterfaceMap(NAME, IfIndexesInterfaceMap.CONTAINER, store, ip, port, path)


class IfIndexesInterfaceMap(Model):
    CONTAINER = "if-indexes-interface-map"
    IF_INDEX_INTERFACE = "if-index-interface"

    def get_if_index_interfaces(self):
        return self.data[self.CONTAINER][self.IF_INDEX_INTERFACE]

    def get_if_index_interfaces_by_key(self, key="if-index"):
        d = {}
        ifaces = self.get_if_index_interfaces()
        for iface in ifaces:
            d[iface[key]] = iface
        return d


class ElanInterfaces(Model):
    CONTAINER = "elan-interfaces"
    ELAN_INSTANCE = "elan-instance"

    def get_elan_interfaces(self):
        return self.data[self.CONTAINER][self.ELAN_INSTANCE]

    def get_elan_interfaces_by_key(self, key="name"):
        d = {}
        ifaces = self.get_elan_ifaces()
        for iface in ifaces:
            d[iface[key]] = iface
        return d
