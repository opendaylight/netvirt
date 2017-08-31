from pprint import pformat
import unittest
import request


class TestRequest(unittest.TestCase):
    def setUp(self):
        self.filename = "flow_dumps.txt"
        self.data = request.get_from_file(self.filename)

    def test_get_from_file(self):
        print "request: {}\n{}".format(self.filename, pformat(self.data))

if __name__ == '__main__':
    unittest.main()
