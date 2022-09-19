#!/usr/bin/env bash

cat login.b64 | base64 -d > settings.xml

