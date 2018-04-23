from model import Model


NAME = "neutron"


def neutron(store, ip=None, port=None, path=None):
    return Neutron(NAME, Neutron.CONTAINER, store, ip, port, path)


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
        for port in ports:
            d[port[key]] = port
        return d

    def get_trunks(self):
        return self.data[self.CONTAINER][self.TRUNKS][self.TRUNK]

    def get_trunks_by_key(self, key="uuid"):
        d = {}
        trunks = self.get_trunks()
        for trunk in trunks:
            d[trunk[key]] = trunk
        return d
