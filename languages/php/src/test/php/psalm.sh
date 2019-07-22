#!/usr/bin/env bash

composer install
vendor/bin/psalm --threads=4
