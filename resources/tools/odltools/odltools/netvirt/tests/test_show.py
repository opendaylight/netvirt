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
from odltools.netvirt import show
from odltools.netvirt import tests
from odltools.netvirt.tests import capture


class TestShow(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.args = tests.Args(path=tests.get_resources_path())
        # self.args = tests.Args(path="/tmp/testmodels")

    def test_show_elan_instances(self):
        with capture.capture(show.show_elan_instances, self.args) as output:
            self.assertTrue("ElanInstance: a5fe7476-9aa1-4bfb-aec4-05d7a1376f45" in output)

    def test_show_groups(self):
        with capture.capture(show.show_groups, self.args) as output:
            self.assertTrue("Dpn: 74851789353527," in output)

    def test_show_flows_all(self):
        self.args.flowtype = "all"
        self.args.pretty_print = True
        self.args.modules = None
        self.args.metaonly = None
        with capture.capture(show.show_flows, self.args) as output:
            self.assertTrue("FlowId:748517893535270tunf68aef23130" in output)

    def test_show_stale_bindings(self):
        show.show_stale_bindings(self.args)

    @unittest.skip("skipping")
    # changed the show tables output to show names too
    def test_show_tables(self):
        # TODO: different tables for Fluorine
        expected = "[0, 17, 18, 19, 20, 21, 22, 23, 24, 36, 38, " \
                   "43, 45, 48, 50, 51, 52, 55, 60, 80, 81, 210, " \
                   "211, 212, 213, 214, 215, 216, 217, 90, 220, " \
                   "239, 240, 241, 242, 243, 244, 245, 246, 247]\n"
        with capture.capture(show.show_tables, self.args) as output:
            self.assertEqual(expected, output)
        # print(output)

    @unittest.skip("skipping")
    # Test is broken
    def test_show_idpools(self):
        parser = root_cli.create_parser()
        args = parser.parse_args(["show", "id-pools", "-p", "--path=" + tests.get_resources_path()])
        with capture.capture(args.func, args) as output:
            self.assertTrue("interfaces" in output)

    def test_show_neutron2(self):
        parser = root_cli.create_parser()
        args = parser.parse_args(["show", "neutron", "ports", "-p", "--path=" + tests.get_resources_path()])
        with capture.capture(args.func, args) as output:
            self.assertTrue("8e3c262e-7b45-4222-ac4e-528db75e5516" in output)

    def test_show_neutron(self):
        self.args.object = "ports"
        self.args.pretty_print = True
        self.args.short = True
        with capture.capture(show.show_neutron, self.args) as output:
            self.assertTrue("8e3c262e-7b45-4222-ac4e-528db75e5516" in output)


if __name__ == '__main__':
    unittest.main()
