import logging
import unittest

from odltools import logg
from odltools.mdsal.models.model import Model
from odltools.mdsal.models.network_topology import NetworkTopology
from odltools.mdsal.models.network_topology import network_topology
from odltools.mdsal import tests


class TestNetworkTopology(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.network_topology = network_topology(Model.CONFIG, args, NetworkTopology.OVSDB1)

    def test_get_topologies(self):
        self.assertIsNotNone(self.network_topology.get_clist())

    def test_get_nodes_by_key(self):
        d = self.network_topology.get_nodes_by_tid_and_key()
        self.assertIsNotNone(d.get('ovsdb://uuid/8eabb815-5570-42fc-9635-89c880ebc4ac/bridge/br-int'))


if __name__ == '__main__':
    unittest.main()
