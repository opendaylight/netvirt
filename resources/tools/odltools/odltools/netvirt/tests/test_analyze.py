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
from odltools import cli as root_cli
from odltools.netvirt import analyze
from odltools.netvirt import tests
from odltools.netvirt.tests import capture


class TestAnalyze(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.args = tests.Args(path=tests.get_resources_path())

    @unittest.skip("skipping")
    def test_analyze_trunks(self):
        analyze.analyze_trunks(self.args)

    def test_analyze_interface(self):
        self.args.ifname = "98c2e265-b4f2-40a5-8f31-2fb5d2b2baf6"
        with capture.capture(analyze.analyze_interface, self.args) as output:
            self.assertTrue("98c2e265-b4f2-40a5-8f31-2fb5d2b2baf6" in output)

    def test_analyze_inventory(self):
        self.args.store = "config"
        self.args.nodeid = "132319289050514"
        with capture.capture(analyze.analyze_inventory, self.args) as output:
            self.assertTrue("132319289050514" in output)
        self.args.store = "operational"
        self.args.nodeid = "233201308854882"
        # not a great test, but there are no flows in the operational
        with capture.capture(analyze.analyze_inventory, self.args) as output:
            self.assertTrue("Inventory Operational" in output)

    @unittest.skip("skipping")
    def test_analyze_nodes(self):
        parser = root_cli.create_parser()
        args = parser.parse_args(["analyze", "nodes", "-p", "--path=" + tests.get_resources_path()])
        with capture.capture(args.func, args) as output:
            self.assertTrue("203251201875890" in output)


if __name__ == '__main__':
    unittest.main()
