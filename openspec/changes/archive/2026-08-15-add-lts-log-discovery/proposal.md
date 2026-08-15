# Add LTS log discovery

`query_lts_logs` requires real log-group and log-stream identifiers, but the MCP surface cannot discover them.
Add two read-only discovery tools backed by Huawei Cloud LTS SDK v2 so investigations can proceed autonomously.
