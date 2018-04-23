import unittest
from mdsal import ietf_interfaces
from mdsal.models import Model
from mdsal.ietf_interfaces import interfaces
from odltools import logg

ip = "127.0.0.1"
port = "8080"
path = "./resources"
# path = "/tmp/robotjob/s1-t1_Create_VLAN_Network_net_1/models"


class TestIetfInterfaces(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.interfaces = interfaces(Model.CONFIG, ip, port, path)

    def test_get_interfaces_by_name(self):
        if_dict = self.interfaces.get_interfaces_by_name()
        self.assertIsNotNone(if_dict['tun95fee4d7132'])

if __name__ == '__main__':
    unittest.main()
