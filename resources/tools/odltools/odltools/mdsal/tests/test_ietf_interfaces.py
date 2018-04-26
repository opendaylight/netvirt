import unittest

from odltools import logg
from odltools.mdsal.model import Model
from odltools.mdsal.models.ietf_interfaces import interfaces

ip = "127.0.0.1"
port = "8080"
path = "./resources"
# path = "/tmp/robotjob/s1-t1_Create_VLAN_Network_net_1/models"


class TestIetfInterfaces(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.interfaces = interfaces(Model.CONFIG, ip, port, path)

    def test_get_interfaces_by_key(self):
        d = self.interfaces.get_interfaces_by_key()
        self.assertIsNotNone(d['tun95fee4d7132'])

if __name__ == '__main__':
    unittest.main()
