import logging
import unittest

from odltools import logg
from odltools.mdsal.models.opendaylight_inventory import nodes
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests


class TestOpendaylightInventory(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.nodes = nodes(Model.CONFIG, args)

    def test_get_clist_by_key(self):
        d = self.nodes.get_clist_by_key()
        self.assertIsNotNone(d.get('openflow:132319289050514'))

    def test_get_groups(self):
        d = self.nodes.get_groups()
        self.assertIsNotNone(d.get('132319289050514'))

    def test_get_dpn_host_mapping(self):
        d = self.nodes.get_dpn_host_mapping()
        self.assertIsNotNone(d.get('132319289050514'))


if __name__ == '__main__':
    unittest.main()
