from odltools.mdsal.models.model import Model


NAME = "odl-interface-meta"


def if_indexes_interface_map(store, args):
    return IfIndexesInterfaceMap(NAME, IfIndexesInterfaceMap.CONTAINER, store, args)


class IfIndexesInterfaceMap(Model):
    CONTAINER = "if-indexes-interface-map"
    IF_INDEX_INTERFACE = "if-index-interface"

    def get_if_index_interfaces(self):
        return self.data[self.CONTAINER][self.IF_INDEX_INTERFACE]

    def get_if_index_interfaces_by_key(self, key="if-index"):
        d = {}
        ifaces = self.get_if_index_interfaces()
        if ifaces is None:
            return None
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
        ifaces = self.get_elan_interfaces()
        if ifaces is None:
            return None
        for iface in ifaces:
            d[iface[key]] = iface
        return d
