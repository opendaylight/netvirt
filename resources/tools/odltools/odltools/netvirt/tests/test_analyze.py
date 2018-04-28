import unittest

from odltools import logg
from odltools.netvirt import analyze
from odltools.netvirt.tests import Args


class TestAnalyze(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger()
        self.args = Args(path="../../tests/resources")

    def test_analyze_trunks(self):
        analyze.analyze_trunks(self.args)

if __name__ == '__main__':
    unittest.main()
