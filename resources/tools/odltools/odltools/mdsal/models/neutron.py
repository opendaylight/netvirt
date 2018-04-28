from odltools.mdsal.models.model import Model


NAME = "neutron"


def neutron(store, args):
    return Neutron(NAME, Neutron.CONTAINER, store, args)


class Neutron(Model):
    CONTAINER = "neutron"
    NETWORKS = "networks"
    NETWORK = "network"
    PORTS = "ports"
    PORT = "port"
    ROUTERS = "routers"
    ROUTER = "router"
    TRUNKS = "trunks"
    TRUNK = "trunk"
    NAME = "name"
    UUID = "uuid"

    def get_ports(self):
        return self.data[self.CONTAINER][self.PORTS][self.PORT]

    def get_ports_by_key(self, key="uuid"):
        d = {}
        ports = self.get_ports()
        if ports is None:
            return None
        for port in ports:
            d[port[key]] = port
        return d

    def get_trunks(self):
        return self.data[self.CONTAINER][self.TRUNKS][self.TRUNK]

    def get_trunks_by_key(self, key="uuid"):
        d = {}
        trunks = self.get_trunks()
        if trunks is None:
            return None
        for trunk in trunks:
            d[trunk[key]] = trunk
        return d
