import logging
import os
import unittest

from odltools import logg
from odltools.mdsal import request
from odltools.mdsal import tests


class TestRequest(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.filename = os.path.join(tests.get_resources_path(), 'config___itm-state__dpn-endpoints.json')

    def test_read_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 1)


if __name__ == '__main__':
    unittest.main()
