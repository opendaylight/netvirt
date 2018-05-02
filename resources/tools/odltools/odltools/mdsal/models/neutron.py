from odltools.mdsal.models.model import Model


MODULE = "neutron"


def neutron(store, args):
    return Neutron(MODULE, store, args)


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
    MODULE = "name"
    UUID = "uuid"

    def get_clist(self):
        return self.data[self.CONTAINER]

    def get_ccl(self, parent, child, item):
        c = self.data and self.data.get(parent, {})
        lst = self.get_list(c, child, item)
        return lst

    def get_ccl_by_key(self, parent, child, item, key="uuid"):
        d = {}
        lst = self.get_ccl(parent, child, item)
        for l in lst:
            d[l[key]] = l
        return d

    def get_networks_by_key(self, key="uuid"):
        return self.get_ccl_by_key(self.CONTAINER, self.NETWORKS, self.NETWORK, key)

    def get_ports_by_key(self, key="uuid"):
        return self.get_ccl_by_key(self.CONTAINER, self.PORTS, self.PORT, key)

    def get_trunks_by_key(self, key="uuid"):
        return self.get_ccl_by_key(self.CONTAINER, self.TRUNKS, self.TRUNK, key)
