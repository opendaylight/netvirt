import unittest

from odltools import logg
from odltools.mdsal.models.ietf_interfaces import interfaces
from odltools.mdsal.models.model import Model
from odltools.mdsal.tests import Args


class TestIetfInterfaces(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        args = Args(path="../../tests/resources")
        self.interfaces = interfaces(Model.CONFIG, args)

    def test_get_interfaces_by_key(self):
        d = self.interfaces.get_interfaces_by_key()
        self.assertIsNotNone(d and d['tun95fee4d7132'])

if __name__ == '__main__':
    unittest.main()
