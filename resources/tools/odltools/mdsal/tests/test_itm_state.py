import unittest
from mdsal import itm_state
from mdsal.models import Model
from mdsal.itm_state import DpnEndpoints
from odltools import logg

ip = "127.0.0.1"
port = "8080"


class TestItmState(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.dpn_endpoints = itm_state.dpn_endpoints(Model.CONFIG, ip, port)
        self.data = self.dpn_endpoints.read_file("./resources/itm-state_dpn-endpoints.json")

    def test_read_file(self):
        print "dpn-endpoints: {}".format(self.data)
        print "dpn-endpoints: \n{}".format(self.dpn_endpoints.pretty_format(self.data))

    def test_get_ip_address(self):
        dpn_ids = self.dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        ip_address = self.dpn_endpoints.get_ip_address(dpn_id)
        print "dpn_id: {}, ip_address: {}".format(dpn_id, ip_address)
        self.assertEqual(dpn_id, "13878168265586")
        self.assertEqual(ip_address, "10.29.13.165")

    def test_get_all(self):
        print "dpn-endpoints: {}".format(self.data)
        print "dpn-endpoints: \n{}".format(self.dpn_endpoints.pretty_format(self.data))

        dpn_ids = self.dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        dpn_teps_info = self.dpn_endpoints.get_dpn_teps_info(dpn_id)
        print "dpn_teps_info for {}: {}".format(dpn_id, dpn_teps_info)

        ip_address = self.dpn_endpoints.get_ip_address(dpn_id)
        print "ip_address: {}".format(ip_address)
        self.assertEqual(ip_address, "10.29.13.165")

        self.get_info(DpnEndpoints.CONTAINER)
        self.get_info(DpnEndpoints.DPN_TEPS_INFO)
        self.get_info(DpnEndpoints.TUNNEL_END_POINTS)
        self.get_info(DpnEndpoints.DPN_ID)

    def get_info(self, key):
        info = self.dpn_endpoints.get_kv(key, self.data, values=[])
        print "dpn info for {}: {}".format(key, info)
        return info

if __name__ == '__main__':
    unittest.main()
