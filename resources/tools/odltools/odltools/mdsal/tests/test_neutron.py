import logging
import unittest

from odltools import logg
from odltools.mdsal.models.neutron import neutron
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests


class TestNeutron(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.neutron = neutron(Model.CONFIG, args)

    def test_get_ports_by_key(self):
        d = self.neutron.get_networks_by_key()
        self.assertIsNotNone(d.get('bd8db3a8-2b30-4083-a8b3-b3fd46401142'))

    def test_get_networks_by_key(self):
        d = self.neutron.get_ports_by_key()
        self.assertIsNotNone(d.get('8e3c262e-7b45-4222-ac4e-528db75e5516'))

    @unittest.skip("skipping")
    def test_get_trunks_by_key(self):
        d = self.neutron.get_trunks_by_key()
        self.assertIsNotNone(d.get('8e3c262e-7b45-4222-ac4e-528db75e5516'))


if __name__ == '__main__':
    unittest.main()
