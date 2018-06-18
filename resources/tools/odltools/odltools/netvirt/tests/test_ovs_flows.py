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
import os
import unittest
from odltools import logg
from odltools.netvirt import ovs_flows
from odltools.netvirt import request
from odltools.netvirt import tests


class TestFlows(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.filename = "{}/flow_dumps.1.txt".format(tests.get_resources_path())
        self.data = request.read_file(self.filename)
        self.flows = ovs_flows.Flows(self.data)

    def test_process_data(self):
        # print("pretty_print:\n{}".format(self.flows.pretty_print(self.flows.pdata)))
        self.assertIsNotNone(self.flows.data)

    def test_format_data(self):
        # print("pretty_print:\n{}".format(self.flows.pretty_print(self.flows.fdata)))
        self.assertIsNotNone(self.flows.fdata)

    def test_write_file(self):
        filename = "/tmp/flow_dumps.3.out.txt"
        self.flows.write_fdata(filename)
        self.assertTrue(os.path.exists(filename))


if __name__ == '__main__':
    unittest.main()
