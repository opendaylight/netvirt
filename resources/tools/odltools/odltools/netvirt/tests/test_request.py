# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import logging
import os
import unittest
from odltools import logg
from odltools.netvirt import request
from odltools.netvirt import tests


class TestRequest(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.filename = "{}/flow_dumps.1.txt".format(tests.get_resources_path())
        self.outpath = "/tmp/flow_dumps.1.out.txt"

    def test_read_file(self):
        data = request.read_file(self.filename)
        self.assertEqual(len(data), 76)

    def test_write_file(self):
        data = request.read_file(self.filename)
        self.assertEqual(len(data), 76)
        request.write_file(self.outpath, data)
        self.assertTrue(os.path.exists(self.outpath))


if __name__ == '__main__':
    unittest.main()
