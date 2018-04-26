import unittest

from odltools import logg
from odltools.mdsal.model import Model
from odltools.mdsal.models.network_topology import NetworkTopology
from odltools.mdsal.models.network_topology import network_topology

ip = "127.0.0.1"
port = "8080"
path = "./resources"
# path = "/tmp/robotjob/s1-t1_Create_VLAN_Network_net_1/models"


class TestNetworkTopology(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.network_topology = network_topology(Model.CONFIG, ip, port, path, NetworkTopology.OVSDB1)

    def test_get_topologies(self):
        self.assertIsNotNone(self.network_topology.get_topologies())

    def test_get_nodes_by_key(self):
        d = self.network_topology.get_nodes_by_tid_and_key()
        self.assertIsNotNone(d['ovsdb://uuid/8eabb815-5570-42fc-9635-89c880ebc4ac/bridge/br-int'])

if __name__ == '__main__':
    unittest.main()
