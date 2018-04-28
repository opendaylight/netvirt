from odltools.mdsal.models.model import Model


NAME = "network-topology"


def network_topology(store, args, mid="ovsdb:1"):
    return NetworkTopology(NAME, NetworkTopology.CONTAINER, store, args, mid)


class NetworkTopology(Model):
    # TODO: class currently assumes the ovsdb:1 has been used so need to fix that to take a topology-id
    CONTAINER = "network-topology"
    TOPOLOGY = "topology"
    NODE = "node"
    OVSDB1 = "ovsdb:1"

    def get_topologies(self):
        return self.data[self.TOPOLOGY]

    def get_topology_by_tid(self, tid="ovsdb:1"):
        topologies = self.get_topologies()
        if topologies is None:
            return None
        for topology in topologies:
            if topology['topology-id'] == tid:
                return topology

    def get_nodes_by_tid(self, tid="ovsdb:1"):
        topology = self.get_topology_by_tid(tid)
        return topology[self.NODE]

    def get_nodes_by_tid_and_key(self, tid="ovsdb:1", key='node-id'):
        d = {}
        nodes = self.get_nodes_by_tid(tid)
        if nodes is None:
            return None
        for node in nodes:
            d[node[key]] = node
        return d
