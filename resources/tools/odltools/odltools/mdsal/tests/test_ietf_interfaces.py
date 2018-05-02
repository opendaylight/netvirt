import logging
import unittest

from odltools import logg
from odltools.mdsal.models.ietf_interfaces import interfaces
from odltools.mdsal.models.ietf_interfaces import interfaces_state
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests


class TestIetfInterfaces(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.interfaces = interfaces(Model.CONFIG, args)
        self.interfaces_state = interfaces_state(Model.OPERATIONAL, args)

    def test_get_interfaces_by_key(self):
        d = self.interfaces.get_clist_by_key()
        self.assertIsNotNone(d.get('tun95fee4d7132'))

    def test_get_interfaces_state_by_key(self):
        d = self.interfaces_state.get_clist_by_key()
        self.assertIsNotNone(d.get('tap67eb9b7f-db'))


if __name__ == '__main__':
    unittest.main()
