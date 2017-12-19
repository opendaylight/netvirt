import logging
# from pprint import pformat


# TODO:
# - requests to get flow dumps via ovs-vsctl, ssh
# - group processing

logging.basicConfig(format="%(levelname)-8s [%(module)s:%(lineno)d] %(message)s",
                    level=logging.INFO)
logger = logging.getLogger(__name__)


def set_log_level(level):
    logger.setLevel(level)


def get_from_file(filename):
    lines = []
    with open(filename, 'r') as fp:
        for line in fp:
            # strip leading spaces; by default every flow line has a leading space: " cookie=..."
            lines.append(line.lstrip())
    logger.info("File: %s: processed %d lines", filename, len(lines))
    logger.debug("\n%s", "".join(lines))
    # logger.debug("\n%s", pformat(lines))
    return lines


def write_file(filename, lines):
    with open(filename, 'w') as fp:
        fp.writelines(lines)
