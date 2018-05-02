import logging
import unittest

from odltools import logg
from odltools.netvirt import analyze
from odltools.netvirt import tests


class TestAnalyze(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.args = tests.Args(path=tests.get_resources_path(), ifname="98c2e265-b4f2-40a5-8f31-2fb5d2b2baf6")

    @unittest.skip("skipping")
    def test_analyze_trunks(self):
        analyze.analyze_trunks(self.args)

    def test_analyze_interface(self):
        analyze.analyze_interface(self.args)


if __name__ == '__main__':
    unittest.main()
