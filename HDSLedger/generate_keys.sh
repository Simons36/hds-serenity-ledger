#!/bin/bash

# This script generates pairs of private/public keys, and stores them in the Keystore/ folder.

mkdir -p Keystore
cd Keystore

for i in {1..4}
do

  private_key_file="private_id_${i}_key.der"
  public_key_file="public_id_${i}_key.der"

  openssl genrsa -out server.key

  openssl rsa -in server.key -pubout > "public_id_${i}_key.der"

  openssl rsa -in server.key -text > "private_id_${i}_key.pem"

  openssl pkcs8 -topk8 -inform PEM -outform DER -in "private_id_${i}_key.pem" -out "$private_key_file" -nocrypt

  openssl rsa -in "private_id_${i}_key.pem" -pubout -outform DER -out "$public_key_file"

  rm server.key
  rm *.pem
done
