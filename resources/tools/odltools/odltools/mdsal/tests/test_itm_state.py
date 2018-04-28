import unittest

from odltools import logg
from odltools.mdsal.models.itm_state import dpn_endpoints
from odltools.mdsal.models.itm_state import DpnEndpoints
from odltools.mdsal.models.model import Model
from odltools.mdsal.tests import Args


class TestItmState(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        args = Args(path="../../tests/resources")
        self.dpn_endpoints = dpn_endpoints(Model.CONFIG, args)

    def test_read_file(self):
        print "dpn-endpoints: {}".format(self.dpn_endpoints.data)
        print "dpn-endpoints: \n{}".format(self.dpn_endpoints.pretty_format(self.dpn_endpoints.data))

    def test_get_ip_address(self):
        dpn_ids = self.dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        ip_address = self.dpn_endpoints.get_ip_address(dpn_id)
        print "dpn_id: {}, ip_address: {}".format(dpn_id, ip_address)
        self.assertEqual(dpn_id, 132319289050514)
        self.assertEqual(ip_address, "10.30.170.17")

    def test_get_all(self):
        print "dpn-endpoints: {}".format(self.dpn_endpoints.data)
        print "dpn-endpoints: \n{}".format(self.dpn_endpoints.pretty_format(self.dpn_endpoints.data))

        dpn_ids = self.dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        dpn_teps_info = self.dpn_endpoints.get_dpn_teps_info(dpn_id)
        print "dpn_teps_info for {}: {}".format(dpn_id, dpn_teps_info)

        ip_address = self.dpn_endpoints.get_ip_address(dpn_id)
        print "ip_address: {}".format(ip_address)
        self.assertEqual(ip_address, "10.30.170.17")

        self.get_info(DpnEndpoints.CONTAINER)
        self.get_info(DpnEndpoints.DPN_TEPS_INFO)
        self.get_info(DpnEndpoints.TUNNEL_END_POINTS)
        self.get_info(DpnEndpoints.DPN_ID)

    def get_info(self, key):
        info = self.dpn_endpoints.get_kv(key, self.dpn_endpoints.data, values=[])
        print "dpn info for {}: {}".format(key, info)
        return info

if __name__ == '__main__':
    unittest.main()
