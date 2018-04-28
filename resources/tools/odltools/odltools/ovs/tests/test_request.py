import logging
import os
import unittest

from odltools import logg
from odltools.ovs import request


class TestRequest(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.DEBUG, logging.DEBUG)
        self.filename = "./resources/flow_dumps.1.txt"
        self.outpath = "/tmp/flow_dumps.1.out.txt"

    def test_read_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 76)

    def test_write_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 76)
        request.write_file(self.outpath, data)
        self.assertTrue(os.path.exists(self.outpath))

if __name__ == '__main__':
    unittest.main()
