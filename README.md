# HDSLedger

## Introduction

HDSLedger is a simplified permissioned (closed membership) blockchain system with high dependability
guarantees. It uses the Istanbul BFT consensus algorithm to ensure that all nodes run commands
in the same order, achieving State Machine Replication (SMR) and guarantees that all nodes
have the same state.

## Requirements

- [Java 17](https://www.oracle.com/java/technologies/javase-jdk17-downloads.html) - Programming language;

- [Maven 3.8](https://maven.apache.org/) - Build and dependency management tool;

- [Python 3](https://www.python.org/downloads/) - Programming language;

---

# Configuration Files

### Node/Client configuration

Can be found inside the `src/main/resources/` folder of the `Common` module.

Config of a node:
```json
{
    "type" : "node",
    "id": <NODE_ID>,
    "isLeader": <IS_LEADER>,
    "hostname": "localhost",
    "port": <PORT>,
    "clientRequestPort": <ANOTHER_PORT>,
    "privateKeyPath" : "Keystore/<FILENAME_OF_PRIVATE_KEY>",
    "publicKeyPath" : "Keystore/<FILENAME_OF_PUBLIC_KEY>",
    "serviceConfig" : <SERVICE_CONFIG_FILENAME>

}
```

Config of a client:

```json
{
    "type" : "client",
    "id": <NODE_ID>,
    "hostname": "localhost",
    "port": <PORT>,
    "privateKeyPath" : "Keystore/<FILENAME_OF_PRIVATE_KEY>",
    "publicKeyPath" : "Keystore/<FILENAME_OF_PUBLIC_KEY>"
}
```

### Service Configuration

Can be found inside the `src/main/resources` folder of the `Service` module. Contains some general configurations about the service (e.g., transaction fee percentage, number of transactions in a block, etc.).

Example:

```json
{
    "initial_account_balance": 1000,
    "transaction_fee": 0.05,
    "num_transaction_in_single_block" : 1
}
```

## Dependencies

To install the necessary dependencies run the following command:

```bash
./install_deps.sh
```

This should install the following dependencies:

- [Google's Gson](https://github.com/google/gson) - A Java library that can be used to convert Java Objects into their JSON representation.
- [Junit](https://junit.org/junit4/) - Junit is a simple framework to write repeatable tests.

## Puppet Master

The puppet master is a python script `puppet-master.py` which is responsible for starting the nodes
of the blockchain.
The script runs with `kitty` terminal emulator by default since it's installed on the RNL labs.

To run the script you need to have `python3` installed.
The script has arguments which can be modified:

- `terminal` - the terminal emulator used by the script
- `server_config` - a string from the array `server_configs` which contains the possible configurations for the blockchain nodes

Run the script with the following command:

```bash
python3 puppet-master.py
```
Note: You may need to install **kitty** in your computer

## Maven

It's also possible to run the project manually by using Maven.

### Instalation

Compile and install all modules using:

```
mvn clean install
```

### Execution

#### Client

To run:

```
cd Client/
mvn compile exec:java -Dexec.args="<id_of_client> <filename_of_config> localhost <port> all <false>"
```

### Test Running

To run the test, first change to the `Test` module

```bash
cd Test
```

Then, to run a single test, perform the following command:

```bash
mvn test -Dtest=<NAME_OF_TEST>
```

To run all of the test, tou can simply execute the following command:

```bash
mvn test
```

---
