#!/bin/bash
#
# E-Voting Machine © 2024 by Sergey Dolin is licensed under CC BY-NC-ND 4.0.
# To view a copy of this license, visit https://creativecommons.org/licenses/by-nc-nd/4.0/
#

cd ../protobuffers
protoc --plugin=protoc-gen-ts_proto=../node_modules/.bin/protoc-gen-ts_proto \
  --ts_proto_out=../web-ui/src/e-voting/proto \
  --ts_proto_opt=outputServices=none,outputEncodeMethods=false,outputJsonMethods=false \
  ballots-service.proto

