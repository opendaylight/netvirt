import logging


# TODO:
# - requests to get flow dumps via ovs-vsctl, ssh
# - group processing

logger = logging.getLogger("ovs.request")


def read_file(filename):
    lines = []
    with open(filename, 'r') as fp:
        for line in fp:
            # strip leading spaces; by default every flow line has a leading space: " cookie=..."
            lines.append(line.lstrip())
    logger.info("read_file: File: %s: processed %d lines", filename, len(lines))
    # logger.debug("read_file: lines:\n%s", "".join(lines))
    return lines


def write_file(filename, lines):
    with open(filename, 'w') as fp:
        fp.writelines(lines)
    logger.info("write_file: File: %s: wrote %d lines", filename, len(lines))
