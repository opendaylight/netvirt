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

logger = None
ch = None
fh = None


def debug():
    ch.setLevel(logging.DEBUG)
    # logger.setLevel(min([ch.level, fh.level]))


class Logger:
    def __init__(self, console_level=logging.INFO, file_level=logging.DEBUG):
        global logger
        global ch
        global fh

        logger = logging.getLogger()
        formatter = logging.Formatter('%(asctime)s | %(levelname).3s | %(name)-20s | %(lineno)04d | %(message)s')
        ch = logging.StreamHandler()
        ch.setLevel(console_level)
        ch.setFormatter(formatter)
        logger.addHandler(ch)
        fh = logging.FileHandler("/tmp/odltools.txt", "w")
        fh.setLevel(file_level)
        fh.setFormatter(formatter)
        logger.addHandler(fh)
        logger.setLevel(min([ch.level, fh.level]))
