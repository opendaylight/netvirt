import unittest
from odltools import logg
from ovs import flows
from ovs import request


class TestFlows(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.filename = "./resources/flow_dumps.3.txt"
        self.data = request.read_file(self.filename)
        self.flows = flows.Flows(self.data)

    def test_process_data(self):
        print "pretty_print:\n{}".format(self.flows.pretty_print(self.flows.pdata))

    def test_format_data(self):
        print "pretty_print:\n{}".format(self.flows.pretty_print(self.flows.fdata))

    def test_write_file(self):
        self.flows.write_fdata("/tmp/flow_dumps.3.out.txt")

if __name__ == '__main__':
    unittest.main()
