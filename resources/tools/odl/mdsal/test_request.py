import logging
import unittest
import request


class TestRequest(unittest.TestCase):
    def setUp(self):
        self.filename = "./itm-state_dpn-endpoints.json"

    def test_get_from_file(self):
        request.logger.setLevel(logging.DEBUG)
        self.data = request.get_from_file(self.filename)
        self.assertIsNotNone(self.data)

if __name__ == '__main__':
    unittest.main()
