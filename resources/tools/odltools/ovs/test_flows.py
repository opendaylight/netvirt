from pprint import pformat
import unittest
from flows import Flows
import request
import tables


class TestFlows(unittest.TestCase):
    def setUp(self):
        self.filename = "flow_dumps.txt"
        self.data = request.get_from_file(self.filename)
        self.flows = Flows(self.data)

    def test_get_from_file(self):
        print "request: {}\n{}".format(self.filename, self.data)

    def test_process_data(self):
        pdata = self.flows.process_data()
        print "parsed data:\n{}".format(pformat(pdata))

    def test_format_data(self):
        fdata = self.flows.format_data()
        print "parsed data:\n{}".format(pformat(fdata))

    def test_write_file(self):
        self.flows.write_fdata("/tmp/flow_dumps.out.txt")

    def test_get_table_name(self):
        print "table: {} is the {} table".format(17, tables.get_table_name(17))

if __name__ == '__main__':
    unittest.main()
