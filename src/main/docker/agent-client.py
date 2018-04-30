#!/usr/bin/env python

import urllib2
import sys
import json

def main():

  cid = None
  f = open('/proc/self/cgroup')
  for l in f:
    if 'cpu' in l:
      l = l.strip()
      cid = l[l.rindex('/') + 1:]
      if '-' in cid:
        # old docker version: 6:cpuset:/system.slice/docker-59a25eecd83137094065307019b61e3fbdccfc8985b33f2c3ef8738536a71154.scope
        cid = l[l.rindex('-') + 1:l.rindex('.')]
  f.close()

  url = "http://dockerhost:8080/container/%s/portbinding?port=2052&protocol=tcp"  %  cid

  response = urllib2.urlopen(url).read()
  payload = json.loads(response)
  print(payload[0]['HostPort'])


if __name__ == "__main__":
  main()

