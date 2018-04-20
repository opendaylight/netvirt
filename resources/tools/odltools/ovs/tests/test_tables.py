import unittest
from odltools import logg
from ovs import tables


class TestTables(unittest.TestCase):
    def setUp(self):
        logg.Logger()

    def test_get_table_name(self):
        self.assertEqual(tables.get_table_name(17), "DISPATCHER")

if __name__ == '__main__':
    unittest.main()
