# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import unittest
from odltools import logg
from odltools.mdsal.models.itm_state import dpn_endpoints
from odltools.mdsal.models.itm_state import DpnEndpoints
from odltools.mdsal.models.itm_state import tunnels_state
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests

logger = logging.getLogger("test.itmstate")


class TestItmState(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.itm_state_dpn_endpoints = dpn_endpoints(Model.CONFIG, args)
        self.itm_state_tunnels_state = tunnels_state(Model.OPERATIONAL, args)

    def test_read_file(self):
        logger.debug("dpn-endpoints: %s", self.itm_state_dpn_endpoints.data)
        logger.debug("dpn-endpoints: \n%s",
                     self.itm_state_dpn_endpoints.pretty_format(self.itm_state_dpn_endpoints.data))

    def test_get_ip_address(self):
        dpn_ids = self.itm_state_dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        ip_address = self.itm_state_dpn_endpoints.get_ip_address(dpn_id)
        logger.debug("dpn_id: %s, ip_address: %s", dpn_id, ip_address)
        self.assertEqual(dpn_id, 132319289050514)
        self.assertEqual(ip_address, "10.30.170.17")

    def test_get_all(self):
        logger.debug("dpn-endpoints: %s", self.itm_state_dpn_endpoints.data)
        logger.debug("dpn-endpoints: \n%s",
                     self.itm_state_dpn_endpoints.pretty_format(self.itm_state_dpn_endpoints.data))

        dpn_ids = self.itm_state_dpn_endpoints.get_dpn_ids()
        dpn_id = dpn_ids[0]
        dpn_teps_info = self.itm_state_dpn_endpoints.get_dpn_teps_info(dpn_id)
        logger.debug("dpn_teps_info for %s: %s", dpn_id, dpn_teps_info)

        ip_address = self.itm_state_dpn_endpoints.get_ip_address(dpn_id)
        logger.debug("ip_address: %s", ip_address)
        self.assertEqual(ip_address, "10.30.170.17")

        self.get_info(DpnEndpoints.CONTAINER)
        self.get_info(DpnEndpoints.CLIST)
        self.get_info(DpnEndpoints.TUNNEL_END_POINTS)
        self.get_info(DpnEndpoints.DPN_ID)

    def get_info(self, key):
        info = self.itm_state_dpn_endpoints.get_kv(key, self.itm_state_dpn_endpoints.data, values=[])
        logger.debug("dpn info for %s: %s", key, info)
        return info

    def test_get_ip_address_from_dpn_info(self):
        nodes = self.itm_state_dpn_endpoints.get_clist_by_key()
        node = nodes.get(132319289050514)
        self.assertIsNotNone(node)
        ip = self.itm_state_dpn_endpoints.get_ip_address_from_dpn_info(node)
        self.assertEqual("10.30.170.17", ip)

    def test_get_tunnels_state(self):
        d = self.itm_state_tunnels_state.get_clist_by_key()
        self.assertIsNotNone(d and d['tun428ee8c4fe7'])


if __name__ == '__main__':
    unittest.main()
