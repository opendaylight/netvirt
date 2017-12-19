import logging
import unittest
import request


class TestRequest(unittest.TestCase):
    def setUp(self):
        self.filename = "./flow_dumps.1.txt"

    def test_get_from_file(self):
        request.logger.setLevel(logging.DEBUG)
        self.data = request.get_from_file(self.filename)
        self.assertEquals(len(self.data), 76)

if __name__ == '__main__':
    unittest.main()
