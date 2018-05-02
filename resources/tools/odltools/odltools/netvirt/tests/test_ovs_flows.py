import logging
import unittest

import os

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
        # print "pretty_print:\n{}".format(self.flows.pretty_print(self.flows.pdata))
        self.assertIsNotNone(self.flows.data)

    def test_format_data(self):
        # print "pretty_print:\n{}".format(self.flows.pretty_print(self.flows.fdata))
        self.assertIsNotNone(self.flows.fdata)

    def test_write_file(self):
        filename = "/tmp/flow_dumps.3.out.txt"
        self.flows.write_fdata(filename)
        self.assertTrue(os.path.exists(filename))


if __name__ == '__main__':
    unittest.main()
