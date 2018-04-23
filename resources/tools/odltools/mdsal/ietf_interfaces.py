from models import Model


NAME = "ietf-interfaces"


class Interfaces(Model):
    ROOT = NAME
    CONTAINER = "interfaces"
    INTERFACE = "interface"

    def get_interfaces(self):
        return self.data[self.CONTAINER][self.INTERFACE]

    def get_interfaces_by_name(self):
        if_dict = {}
        ifaces = self.data[self.CONTAINER][self.INTERFACE]
        for iface in ifaces:
            if_dict[iface['name']] = iface
        return if_dict


def interfaces(store, ip=None, port=None, path=None):
    return Interfaces(NAME, Interfaces.CONTAINER, store, ip, port, path)
