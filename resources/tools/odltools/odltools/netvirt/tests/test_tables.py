import logging
import unittest

from odltools import logg
from odltools.netvirt import tables


class TestTables(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)

    def test_get_table_name(self):
        self.assertEqual(tables.get_table_name(17), "DISPATCHER")


if __name__ == '__main__':
    unittest.main()
