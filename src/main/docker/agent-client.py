#!/usr/bin/env python

import urllib2
import sys
import json

def main():

  url = "http://dockerhost:8080/container/%s/portbinding?port=2052&protocol=tcp"  % sys.argv[1]

  response = urllib2.urlopen(url).read()
  payload = json.loads(response)
  print(payload[0]['HostPort'])


if __name__ == "__main__":
  main()

