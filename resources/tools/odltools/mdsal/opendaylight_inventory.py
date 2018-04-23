from model import Model


NAME = "opendaylight-inventory"


def nodes(store, ip=None, port=None, path=None):
    return Nodes(NAME, Nodes.CONTAINER, store, ip, port, path)


class Nodes(Model):
    CONTAINER = "nodes"
    NODE = "node"

    def get_nodes(self):
        return self.data[self.CONTAINER][self.NODE]

    def get_nodes_by_key(self, key="id"):
        d = {}
        nodez = self.get_nodes()
        for node in nodez:
            d[node[key]] = node
        return d
