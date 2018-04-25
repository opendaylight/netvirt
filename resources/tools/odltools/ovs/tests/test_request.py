import logging
import unittest
from odltools import logg
from ovs import request


class TestRequest(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.DEBUG, logging.DEBUG)
        self.filename = "./resources/flow_dumps.1.txt"

    def test_read_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 76)

    def test_write_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 76)
        request.write_file("/tmp/somedir/flow_dumps.1.out.txt", data)

if __name__ == '__main__':
    unittest.main()
