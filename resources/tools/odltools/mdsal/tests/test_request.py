import unittest
from mdsal import request
from odltools import logg


class TestRequest(unittest.TestCase):
    def setUp(self):
        logg.Logger()
        self.filename = "./resources/config_itm-state:dpn-endpoints.json"

    def test_read_file(self):
        data = request.read_file(self.filename)
        self.assertEquals(len(data), 1)

if __name__ == '__main__':
    unittest.main()
