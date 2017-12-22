#!/bin/bash
docker create --name=rook_daemon -p 1883:1883 -p 8080:8080 rook_daemon